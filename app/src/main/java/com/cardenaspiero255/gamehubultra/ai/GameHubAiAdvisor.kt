package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.voice.NaturalLanguageIntentResolver
import com.cardenaspiero255.gamehubultra.voice.VoiceCommand
import java.text.Normalizer
import java.util.Locale

class GameHubAiAdvisor(
    private val modelAdapter: LocalAiModelAdapter? = null,
    private val memoryGateway: UltraLongTermMemoryGateway? = null,
    private val aiCore: UltraAiCoreGateway = UltraAiCore2()
) : UltraAssistantGateway {

    override fun hasLocalModelProvider(): Boolean = modelAdapter != null

    override fun isLocalModelAvailable(): Boolean =
        runCatching { modelAdapter?.isAvailable() == true }.getOrDefault(false)

    /**
     * Returns only a substantive local-model answer for stable knowledge.
     * Unlike chat(), this never substitutes the generic capability boilerplate.
     */
    override fun generalKnowledgeChatOrNull(
        message: String,
        context: GameHubAiContext,
        conversation: List<String>
    ): String? {
        UltraFrontierWorldStateRegistry.update(context)
        val modelAnswer = runCatching {
            modelAdapter
                ?.takeIf { it.isAvailable() }
                ?.chat(message, context, conversation.takeLast(18))
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { AiChatSafetyFilter.sanitize(it, message) }
            ?.takeUnless(::looksPredominantlyEnglish)

        return modelAnswer ?: deterministicStableKnowledgeOrNull(message)
    }

    override fun advise(
        question: String,
        context: GameHubAiContext
    ): GameHubAiAdvice {
        UltraFrontierWorldStateRegistry.update(context)
        return adviseInternal(
            question = question,
            context = context,
            memories = recallMemorySignals(question, context)
        )
    }

    private fun adviseInternal(
        question: String,
        context: GameHubAiContext,
        memories: List<UltraAiMemorySignal>
    ): GameHubAiAdvice {
        val modelCandidate = runCatching {
            modelAdapter
                ?.takeIf { it.isAvailable() }
                ?.advise(question, context)
        }.getOrNull()
            ?.takeIf(AiActionAllowlist::validate)

        if (modelCandidate != null) {
            val modelAdvice = adviceFromAllowlistedAction(
                candidate = modelCandidate,
                context = context
            )
            return recoverAdviceWithFeedback(modelAdvice, context)
        }

        return deterministicAdvice(question, context, memories)
    }

    private fun recallMemorySignals(
        question: String,
        context: GameHubAiContext
    ): List<UltraAiMemorySignal> {
        val scope = UltraMemoryScope(
            userId = "local",
            gamePackage = context.selectedGamePackage
        )
        return runCatching {
            memoryGateway
                ?.recallContext(question, scope, limit = MEMORY_RECALL_LIMIT)
                .orEmpty()
                .asSequence()
                .filter { it.record.text.isNotBlank() }
                .take(MEMORY_RECALL_LIMIT)
                .map(UltraAiMemorySignal::from)
                .toList()
        }.getOrDefault(emptyList())
    }

    override fun chat(
        message: String,
        context: GameHubAiContext,
        conversation: List<String>
    ): String {
        UltraFrontierWorldStateRegistry.update(context)
        val memoryScope = UltraMemoryScope(
            userId = "local",
            gamePackage = context.selectedGamePackage
        )
        val memoryCommandResponse = runCatching {
            memoryGateway?.handleCommand(message, memoryScope)
        }.getOrNull()
        if (memoryCommandResponse != null) return memoryCommandResponse

        val visibleTexts = conversation
            .map { normalize(it.substringAfter(':').trim()) }
            .filter(String::isNotBlank)
            .toMutableSet()
            .apply { add(normalize(message)) }

        val recalled = runCatching {
            memoryGateway
                ?.recallContext(message, memoryScope, limit = 6)
                .orEmpty()
        }.getOrDefault(emptyList())
            .filterNot { recall ->
                recall.record.kind == UltraMemoryKind.CONVERSATION &&
                    normalize(recall.record.text) in visibleTexts
            }
        val recalledConversation = recalled.map { recall ->
            val source = when (recall.provenance) {
                UltraMemoryProvenance.REMEMBERED_FACT -> "hecho recordado"
                UltraMemoryProvenance.PRIOR_CONVERSATION -> "conversación anterior"
                UltraMemoryProvenance.SUMMARY -> "resumen anterior"
            }
            "[Memoria previa · $source] ${recall.record.text}"
        }
        val modelConversation = (
            recalledConversation + conversation.takeLast(12)
        ).takeLast(18)

        val normalized = normalize(message)
        val feedbackAwareProfileQuestion =
            isProfileRecommendationQuestion(normalized) &&
                context.optimizationObservations.any {
                    it.feedbackDecision == com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision.REJECTED ||
                        it.feedbackDecision == com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision.REVERTED
                }

        val local = runCatching {
            modelAdapter
                ?.takeIf { it.isAvailable() }
                ?.chat(message, context, modelConversation)
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { AiChatSafetyFilter.sanitize(it, message) }
            ?.takeUnless(::looksPredominantlyEnglish)
        if (local != null && !feedbackAwareProfileQuestion) return local
        val memoryRecallQuestion = listOf(
            "que recuerdas",
            "que sabes de mi",
            "recuerdas de mi",
            "what do you remember",
            "what do you know about me"
        ).any(normalized::contains)
        if (memoryRecallQuestion && recalled.isNotEmpty()) {
            val memoryText = recalled
                .take(4)
                .joinToString(" · ") { it.record.text }
            return "Recuerdo: $memoryText"
        }

        deterministicStableKnowledgeOrNull(message)?.let { return it }

        val advice = adviseInternal(
            question = message,
            context = context,
            memories = recalled.map(UltraAiMemorySignal::from)
        )
        val profile = profileLabel(advice.suggestedProfile)

        return when {
            normalized.contains("temperatura") || normalized.contains("caliente") ||
                normalized.contains("temperature") || normalized.contains("hot") ->
                "Puedo ayudarte con la temperatura. El estado térmico actual es " +
                    (context.thermalStatus?.toString() ?: "no disponible") +
                    " y el margen térmico es " +
                    (context.thermalHeadroom?.let { (it * 100).toInt().toString() + "%" } ?: "no disponible") +
                    ". Para priorizar estabilidad, " + profile + " es la opción conservadora."

            normalized.contains("bateria") || normalized.contains("battery") ->
                "La batería actual es " +
                    (context.batteryPercent?.let { "$it%" } ?: "no disponible") +
                    (if (context.charging) " y está cargando" else "") +
                    ". Si está baja, recomiendo FPS balanceado para reducir el coste sostenido."

            normalized.contains("fps") || normalized.contains("modo") || normalized.contains("perfil") ||
                normalized.contains("mode") || normalized.contains("profile") ->
                "Con los datos actuales, mi recomendación es " + profile +
                    ". Preparación gaming estimada: " + advice.readiness + "/100." +
                    advice.recoveryExplanation?.let { " " + it }.orEmpty()

            normalized.contains("red") || normalized.contains("latencia") || normalized.contains("internet") ||
                normalized.contains("network") || normalized.contains("latency") ->
                "La red validada es " + (if (context.networkValidated) "sí" else "no") +
                    " y la latencia es " +
                    (context.networkLatencyMs?.let { "$it ms" } ?: "no medida") +
                    ". Puedo analizarla, pero no puedo modificar la conexión de otra aplicación."

            normalized.contains("hola") || normalized.contains("quien eres") ||
                normalized.contains("hello") || normalized.contains("who are you") ->
                "Soy Ultra, el asistente de GameHub Ultra. Puedo conversar, analizar el estado disponible del dispositivo y ayudarte con juegos, rendimiento y consultas generales."

            else ->
                "Soy Ultra. Puedo ayudarte en español con rendimiento, FPS, temperatura, batería, red, perfiles de GameHub Ultra y consultas generales. Si una respuesta necesita datos externos, intentaré usar información verificada."
        }
    }

    private fun deterministicStableKnowledgeOrNull(message: String): String? {
        val normalized = normalize(message)
        val query = normalized
            .removePrefix("gamehub ultra ")
            .removePrefix("gamehub ")
            .removePrefix("ultra ")
            .trimStart()

        val asksStableQuestion = listOf(
            "que es ",
            "que son ",
            "que significa ",
            "define ",
            "definicion de ",
            "por que ",
            "para que sirve ",
            "como funciona ",
            "explicame ",
            "explica ",
            "hablame de ",
            "hablame del ",
            "hablame sobre ",
            "hablame acerca de ",
            "cuentame de ",
            "cuentame sobre ",
            "cuentame acerca de ",
            "dime sobre ",
            "dime algo de ",
            "dime algo sobre ",
            "quiero saber de ",
            "quiero saber sobre ",
            "quiero saber acerca de ",
            "que sabes de ",
            "que sabes sobre ",
            "dame informacion de ",
            "dame informacion sobre ",
            "informame de ",
            "informame sobre ",
            "informame acerca de ",
            "describeme ",
            "cual es ",
            "cuales son ",
            "quien es ",
            "quienes son ",
            "cuantos ",
            "como se llama ",
            "donde esta ",
            "cuando fue ",
            "what is ",
            "what are ",
            "why ",
            "how does ",
            "explain ",
            "how many ",
            "where is ",
            "when was "
        ).any(query::startsWith)
        if (!asksStableQuestion) return null

        val requiresFreshData = listOf(
            " actual ",
            " actualmente ",
            " ahora ",
            " hoy ",
            " precio ",
            " precios ",
            " noticias ",
            " novedades ",
            " latest ",
            " current ",
            " today ",
            " price ",
            " news ",
            " release date ",
            " fecha de lanzamiento "
        ).any { signal -> " $query ".contains(signal) }
        if (requiresFreshData) return null

        return when {
            Regex("""\btik\s*tok\b|\btiktok\b""").containsMatchIn(normalized) ->
                "TikTok es una plataforma social centrada en videos cortos donde las personas pueden crear, descubrir y compartir contenido. También ofrece recomendaciones personalizadas según la interacción del usuario."

            Regex("""\byou\s*tube\b|\byoutube\b""").containsMatchIn(normalized) ->
                "YouTube es una plataforma de video en línea donde las personas pueden publicar, ver y compartir videos, transmisiones y otros contenidos audiovisuales."

            normalized.contains("instagram") ->
                "Instagram es una red social centrada en compartir fotos, videos, historias y mensajes, y permite seguir cuentas y descubrir contenido recomendado."

            normalized.contains("chatgpt") || normalized.contains("chat gpt") ->
                "ChatGPT es un asistente conversacional de inteligencia artificial de OpenAI basado en modelos de lenguaje. Puede comprender instrucciones y generar texto para responder preguntas, explicar temas, redactar, programar y ayudar con muchas otras tareas."

            normalized.contains("openai") || normalized.contains("open ai") ->
                "OpenAI es una organización dedicada a investigar y desarrollar sistemas de inteligencia artificial, incluidos modelos capaces de trabajar con lenguaje, imágenes y otras modalidades."

            normalized.contains("whatsapp") || normalized.contains("whats app") ->
                "WhatsApp es una aplicación de mensajería que permite enviar textos, archivos y mensajes de voz, además de realizar llamadas y videollamadas a través de Internet."

            normalized.contains("discord") ->
                "Discord es una plataforma de comunicación organizada en servidores y canales que permite conversar por texto, voz y video, y es muy usada por comunidades y grupos de juego."

            normalized.contains("reddit") ->
                "Reddit es una plataforma de comunidades temáticas donde las personas publican enlaces, textos, imágenes y comentarios que otros usuarios pueden votar y debatir."

            normalized.contains("twitch") ->
                "Twitch es una plataforma de transmisiones en directo especialmente conocida por videojuegos, aunque también alberga contenido de conversación, música y otras categorías."

            normalized.contains("spotify") ->
                "Spotify es un servicio de audio en streaming que permite escuchar música, podcasts y otros contenidos, con reproducción bajo demanda y recomendaciones personalizadas."

            normalized.contains("netflix") ->
                "Netflix es un servicio de streaming por suscripción que ofrece películas, series, documentales y otros contenidos audiovisuales a través de Internet."

            normalized.contains("wikipedia") ->
                "Wikipedia es una enciclopedia en línea colaborativa y de acceso libre cuyos artículos son creados y editados por voluntarios y suelen incluir referencias a fuentes externas."

            normalized.contains("modelo de lenguaje") ||
                normalized.contains("large language model") ||
                normalized.contains(" llm") ->
                "Un modelo de lenguaje es un sistema que aprende patrones del lenguaje a partir de grandes cantidades de texto para estimar, comprender y generar secuencias de palabras. Los modelos de lenguaje grandes o LLM usan muchos parámetros y datos para realizar tareas variadas."

            normalized.contains("computacion en la nube") || normalized.contains("cloud computing") ->
                "La computación en la nube consiste en usar servidores y servicios informáticos remotos a través de Internet para almacenar datos, ejecutar aplicaciones o disponer de capacidad de cómputo bajo demanda."

            normalized.contains("sistema operativo") || normalized.contains("operating system") ->
                "Un sistema operativo es el software principal que administra el hardware y los recursos de un dispositivo y ofrece servicios básicos para que las aplicaciones puedan ejecutarse."

            normalized.contains("internet") ->
                "Internet es una red mundial de redes informáticas interconectadas que usan protocolos comunes para intercambiar datos y ofrecer servicios como la Web, mensajería, streaming y comunicaciones."

            Regex("""\bpatogenos?\b|\bpathogens?\b""").containsMatchIn(normalized) ->
                "Un patógeno es un agente biológico capaz de causar enfermedad, como ciertos virus, bacterias, hongos, parásitos u otros agentes infecciosos."

            Regex("""\bosos?\b|\bbears?\b""").containsMatchIn(normalized) ->
                "Un oso es un mamífero de la familia Ursidae. Los osos son grandes, robustos y omnívoros en muchas especies, aunque su dieta y hábitat varían según la especie."

            Regex("""\bsentimientos?\b""").containsMatchIn(normalized) ->
                "Los sentimientos son experiencias afectivas conscientes que surgen al interpretar emociones, pensamientos y situaciones. Pueden influir en cómo percibimos, decidimos y actuamos, y suelen durar más que una reacción emocional instantánea."

            Regex("""\bemocion(?:es)?\b""").containsMatchIn(normalized) ->
                "Las emociones son respuestas psicofisiológicas ante estímulos internos o externos. Suelen aparecer rápidamente, preparan al organismo para responder y pueden dar lugar a sentimientos cuando las interpretamos conscientemente."

            normalized.contains("fotosintesis") || normalized.contains("photosynthesis") ->
                "La fotosíntesis es el proceso mediante el cual plantas, algas y algunas bacterias usan la luz para transformar agua y dióxido de carbono en energía química almacenada en azúcares, liberando oxígeno como subproducto."

            normalized.contains("agujero negro") || normalized.contains("black hole") ->
                "Un agujero negro es una región del espacio donde la gravedad es tan intensa que, más allá de su horizonte de sucesos, ni siquiera la luz puede escapar."

            Regex("""\bexo\s*planetas?\b|\bexoplanetas?\b|\bexoplanets?\b""").containsMatchIn(normalized) ->
                "Un exoplaneta es un planeta que orbita una estrella distinta del Sol. Se detecta mediante técnicas como tránsitos, velocidad radial, imagen directa y otros métodos astronómicos."

            Regex("""\bcapa de ozono\b|\bozone layer\b""").containsMatchIn(normalized) ->
                "La capa de ozono es una región de la estratosfera con una concentración relativamente alta de ozono. Absorbe gran parte de la radiación ultravioleta dañina del Sol y ayuda a proteger la vida en la Tierra."

            Regex("""\bvelociraptors?\b""").containsMatchIn(normalized) ->
                "El Velociraptor fue un dinosaurio terópodo pequeño y ágil que vivió durante el Cretácico tardío. Tenía plumas y una gran garra curva en cada pie, y era mucho más pequeño que su representación habitual en el cine."

            Regex("""\bsol\b|\bsun\b""").containsMatchIn(normalized) ->
                "El Sol es la estrella situada en el centro del sistema solar. Su energía procede principalmente de la fusión nuclear de hidrógeno en helio y proporciona la mayor parte de la luz y el calor que recibe la Tierra."

            Regex("""\bluna\b|\bmoon\b""").containsMatchIn(normalized) ->
                "La Luna es el satélite natural de la Tierra. Orbita nuestro planeta y su gravedad contribuye de forma importante a las mareas."

            Regex("""\bdinosaurios?\b|\bdinosaurs?\b""").containsMatchIn(normalized) ->
                "Los dinosaurios fueron un grupo diverso de reptiles que dominaron muchos ecosistemas terrestres durante gran parte de la era Mesozoica. Las aves modernas forman parte del linaje de los dinosaurios terópodos."

            Regex("""\btsunamis?\b""").containsMatchIn(normalized) ->
                "Un tsunami es una serie de olas de gran longitud de onda causada por un desplazamiento brusco de mucha agua, por ejemplo debido a un terremoto submarino, un deslizamiento o una erupción volcánica."

            Regex("""\bterremotos?\b|\bearthquakes?\b""").containsMatchIn(normalized) ->
                "Un terremoto es una liberación repentina de energía en la corteza terrestre que genera ondas sísmicas, normalmente por el movimiento de fallas asociado a las placas tectónicas."

            normalized.contains("efecto invernadero") || normalized.contains("greenhouse effect") ->
                "El efecto invernadero es el proceso por el que ciertos gases de la atmósfera absorben y reemiten radiación infrarroja, reteniendo parte del calor. Es natural y necesario, pero su intensificación eleva la temperatura media del planeta."

            normalized.contains("cambio climatico") || normalized.contains("climate change") ->
                "El cambio climático es una alteración persistente de los patrones del clima durante décadas o más. El calentamiento global actual está impulsado principalmente por el aumento de gases de efecto invernadero debido a actividades humanas."

            Regex("""\bproteinas?\b|\bproteins?\b""").containsMatchIn(normalized) ->
                "Una proteína es una molécula formada por cadenas de aminoácidos plegadas en estructuras específicas. Las proteínas cumplen funciones como catalizar reacciones, formar tejidos, transportar sustancias y participar en señales celulares."

            Regex("""\bneuronas?\b|\bneurons?\b""").containsMatchIn(normalized) ->
                "Una neurona es una célula especializada del sistema nervioso que recibe, procesa y transmite información mediante señales eléctricas y químicas."

            Regex("""\bmitocondrias?\b|\bmitochondri(?:a|on)s?\b""").containsMatchIn(normalized) ->
                "La mitocondria es un orgánulo celular que participa de forma central en la producción de energía utilizable, especialmente ATP, mediante procesos de respiración celular."

            Regex("""\bfosil(?:es)?\b|\bfossils?\b""").containsMatchIn(normalized) ->
                "Un fósil es un resto, huella o evidencia de un organismo del pasado preservado en materiales geológicos, como huesos mineralizados, impresiones o rastros."

            Regex("""\bsupernovas?\b""").containsMatchIn(normalized) ->
                "Una supernova es una explosión estelar extremadamente energética que puede ocurrir al final de la evolución de ciertas estrellas o por procesos explosivos en sistemas con enanas blancas."

            Regex("""\bnubes?\b|\bclouds?\b""").containsMatchIn(normalized) ->
                "Una nube es un conjunto visible de diminutas gotas de agua, cristales de hielo o ambos suspendidos en la atmósfera. Se forma cuando el vapor de agua se enfría y condensa alrededor de pequeñas partículas."

            Regex("""\bintrovertid[oa]s?\b|\bintroversion(?:es)?\b|\bintroverts?\b|\bintroverted\b""").containsMatchIn(normalized) ->
                "Una persona introvertida suele orientar más su atención hacia su mundo interno y puede preferir ambientes con menor estimulación social. La introversión es un rasgo de personalidad, no implica necesariamente timidez ni un trastorno."

            Regex("""\badn\b|\bdna\b""").containsMatchIn(normalized) ->
                "El ADN es la molécula que almacena la información genética usada por los seres vivos para desarrollarse, funcionar y transmitir rasgos hereditarios."

            Regex("""\bcelulas?\b|\bcells?\b""").containsMatchIn(normalized) ->
                "Una célula es la unidad básica de la vida: puede realizar funciones esenciales como obtener energía, mantener su estructura y reproducirse."

            Regex("""\bvolcan(?:es)?\b|\bvolcano(?:es)?\b""").containsMatchIn(normalized) ->
                "Un volcán es una abertura de la corteza terrestre por la que pueden salir magma, gases y materiales sólidos desde el interior del planeta."

            normalized.contains("gravedad") || normalized.contains("gravity") ->
                "La gravedad es la interacción por la que los cuerpos con masa se atraen; cerca de la Tierra hace que los objetos aceleren hacia el suelo."

            normalized.contains("electricidad") || normalized.contains("electricity") ->
                "La electricidad describe fenómenos asociados a las cargas eléctricas y a su movimiento; una corriente eléctrica es un flujo ordenado de carga."

            normalized.contains("energia cinetica") || normalized.contains("kinetic energy") ->
                "La energía cinética es la energía que posee un cuerpo debido a su movimiento."

            normalized.contains("energia potencial") || normalized.contains("potential energy") ->
                "La energía potencial es energía almacenada por la posición o configuración de un sistema, como un objeto elevado en un campo gravitatorio."

            Regex("""\bmoleculas?\b|\bmolecules?\b""").containsMatchIn(normalized) ->
                "Una molécula es un conjunto de dos o más átomos unidos mediante enlaces químicos que forman una unidad definida."

            Regex("""\batomos?\b|\batoms?\b""").containsMatchIn(normalized) ->
                "Un átomo es una unidad básica de la materia formada por un núcleo con protones y neutrones, rodeado por electrones."

            normalized.contains("via lactea") || normalized.contains("milky way") ->
                "La Vía Láctea es la galaxia espiral barrada en la que se encuentra el sistema solar."

            normalized.contains("sistema solar") || normalized.contains("solar system") ->
                "El sistema solar es el conjunto formado por el Sol y los cuerpos ligados gravitacionalmente a él, incluidos ocho planetas, lunas, asteroides y cometas."

            Regex("""\bgalaxias?\b|\bgalax(?:y|ies)\b""").containsMatchIn(normalized) ->
                "Una galaxia es un enorme sistema de estrellas, gas, polvo y materia oscura unido principalmente por la gravedad."

            normalized.contains("eclipse") ->
                "Un eclipse ocurre cuando un cuerpo celeste oculta total o parcialmente a otro desde la perspectiva de un observador."

            normalized.contains("evaporacion") || normalized.contains("evaporation") ->
                "La evaporación es el paso gradual de un líquido a gas desde su superficie cuando algunas moléculas adquieren suficiente energía."

            normalized.contains("condensacion") || normalized.contains("condensation") ->
                "La condensación es el cambio de estado por el que un gas pierde energía y se transforma en líquido."

            normalized.contains("presion atmosferica") || normalized.contains("atmospheric pressure") ->
                "La presión atmosférica es la fuerza por unidad de área ejercida por el peso del aire de la atmósfera."

            normalized.contains("ecosistema") || normalized.contains("ecosystem") ->
                "Un ecosistema es el conjunto de seres vivos de un lugar, el ambiente físico y las interacciones entre ambos."

            normalized.contains("cadena alimentaria") || normalized.contains("food chain") ->
                "Una cadena alimentaria representa cómo la energía y la materia pasan de unos organismos a otros cuando unos se alimentan de otros."

            normalized.contains("seleccion natural") || normalized.contains("natural selection") ->
                "La selección natural es el proceso evolutivo por el que rasgos heredables que favorecen supervivencia o reproducción tienden a hacerse más frecuentes."

            Regex("""\bvacunas?\b|\bvaccines?\b""").containsMatchIn(normalized) ->
                "Una vacuna entrena al sistema inmunitario para reconocer un agente o componente específico y responder con mayor rapidez frente a una exposición futura."

            Regex("""\bbacterias?\b|\bbacteria\b""").containsMatchIn(normalized) ->
                "Una bacteria es un microorganismo unicelular procariota; muchas son inofensivas o beneficiosas y algunas pueden causar enfermedades."

            Regex("""\bvirus\b""").containsMatchIn(normalized) ->
                "Un virus es una entidad infecciosa compuesta por material genético y una cubierta que necesita células huésped para replicarse."

            Regex("""\balgoritmos?\b|\balgorithms?\b""").containsMatchIn(normalized) ->
                "Un algoritmo es una secuencia finita y ordenada de pasos para resolver un problema o realizar una tarea."

            Regex("""\bapi\b""").containsMatchIn(normalized) ->
                "Una API es una interfaz que define cómo distintos componentes de software pueden comunicarse mediante operaciones y datos acordados."

            normalized.contains("base de datos") || normalized.contains("database") ->
                "Una base de datos es una colección organizada de información diseñada para almacenarse, consultarse y actualizarse de forma eficiente."

            normalized.contains("memoria ram") ||
                normalized.contains("random access memory") ||
                Regex("""\bram\b""").containsMatchIn(normalized) ->
                "La memoria RAM es la memoria de trabajo rápida que mantiene temporalmente datos y programas que el sistema está usando en ese momento."

            Regex("""\bgpu\b""").containsMatchIn(normalized) ->
                "Una GPU es un procesador especializado en realizar muchas operaciones en paralelo, originalmente para gráficos y también útil en cómputo e inteligencia artificial."

            normalized.contains("vulkan") ->
                "Vulkan es una API gráfica y de cómputo de bajo nivel y multiplataforma diseñada para dar a las aplicaciones un control explícito y eficiente de la GPU."

            normalized.contains("compilador") || normalized.contains("compiler") ->
                "Un compilador es un programa que traduce código escrito en un lenguaje fuente a otra representación ejecutable o intermedia."

            normalized.contains("variable en programacion") || normalized.contains("programming variable") ->
                "Una variable en programación es un nombre asociado a un valor o referencia que un programa puede consultar y, según el lenguaje, modificar."

            normalized.contains("funcion en programacion") || normalized.contains("programming function") ->
                "Una función en programación es un bloque reutilizable de código que realiza una tarea y puede recibir entradas y devolver un resultado."

            normalized.contains("red neuronal") || normalized.contains("neural network") ->
                "Una red neuronal artificial es un modelo de aprendizaje automático formado por capas de unidades conectadas que ajustan parámetros para aprender patrones a partir de datos."

            normalized.contains("aprendizaje automatico") || normalized.contains("machine learning") ->
                "El aprendizaje automático es una rama de la inteligencia artificial en la que los sistemas aprenden patrones a partir de datos para realizar predicciones o decisiones."

            normalized.contains("inteligencia artificial") || normalized.contains("artificial intelligence") ->
                "La inteligencia artificial es el campo que desarrolla sistemas capaces de realizar tareas asociadas al razonamiento, percepción, aprendizaje, lenguaje o toma de decisiones."

            (normalized.contains("por que el cielo") || normalized.contains("why the sky")) &&
                (normalized.contains("azul") || normalized.contains("blue")) ->
                "El cielo se ve azul principalmente porque las moléculas del aire dispersan con más intensidad las longitudes de onda cortas de la luz solar, como el azul."

            (normalized.contains("por que flotan") || normalized.contains("why do")) &&
                (normalized.contains("barcos") || normalized.contains("boats")) ->
                "Los barcos flotan cuando el empuje hacia arriba del agua iguala su peso; su forma hace que desplacen suficiente agua para lograrlo."

            normalized.contains("motor electrico") || normalized.contains("electric motor") ->
                "Un motor eléctrico convierte energía eléctrica en movimiento mediante fuerzas magnéticas entre corrientes eléctricas y campos magnéticos."

            normalized.contains("panel solar") || normalized.contains("solar panel") ->
                "Un panel solar fotovoltaico convierte parte de la energía de la luz en electricidad mediante celdas semiconductoras que generan corriente al recibir fotones."

            normalized.contains("refrigerador") || normalized.contains("refrigerator") ->
                "Un refrigerador extrae calor de su interior y lo expulsa al exterior mediante un ciclo de compresión, condensación, expansión y evaporación de un refrigerante."

            normalized.contains("cuantos planetas") || normalized.contains("how many planets") ->
                "Hay ocho planetas reconocidos en el sistema solar: Mercurio, Venus, Tierra, Marte, Júpiter, Saturno, Urano y Neptuno."

            normalized.contains("capital de francia") || normalized.contains("capital of france") ->
                "La capital de Francia es París."

            normalized.contains("monte everest") || normalized.contains("mount everest") ->
                "El monte Everest está en el Himalaya, en la frontera entre Nepal y la Región Autónoma del Tíbet de China."

            normalized.contains("revolucion francesa") || normalized.contains("french revolution") ->
                "La Revolución Francesa comenzó en 1789 y transformó profundamente el sistema político y social de Francia."

            Regex("""\bavion(?:es)?\b|\bairplane(?:s)?\b|\baircraft\b""").containsMatchIn(normalized) ->
                "Un avión es una aeronave de ala fija diseñada para volar gracias a la sustentación generada por sus alas y al empuje de uno o más motores."

            Regex("""\bpsicopata(?:s)?\b|\bpsychopath(?:s)?\b""").containsMatchIn(normalized) ->
                "Psicópata es un término de uso común para describir a una persona con ciertos rasgos persistentes, como baja empatía, manipulación o escaso remordimiento. No es por sí solo un diagnóstico clínico independiente y su evaluación corresponde a profesionales de salud mental."

            Regex("""\blapiz(?:es)?\b|\bpencil(?:s)?\b""").containsMatchIn(normalized) ->
                "Un lápiz es un instrumento para escribir o dibujar que suele tener una mina de grafito u otro material encerrada en madera o en un cuerpo mecánico."

            Regex("""\bmotor(?:es)?\b|\bengine(?:s)?\b""").containsMatchIn(normalized) ->
                "Un motor es una máquina que transforma una forma de energía en movimiento o trabajo mecánico."

            else -> null
        }
    }

    private fun profileLabel(profile: PerformanceProfile): String =
        when (profile) {
            PerformanceProfile.BALANCED -> "FPS balanceado"
            PerformanceProfile.FRAME_INTERPOLATION -> "Priorizar interpolación"
            PerformanceProfile.X4 -> "X4"
        }

    private fun looksPredominantlyEnglish(value: String): Boolean {
        val englishWords = setOf(
            "i", "am", "m", "is", "are", "was", "were", "the", "a", "an",
            "to", "of", "for", "you", "your", "can", "could", "will", "would",
            "that", "this", "with", "and", "or", "but", "sure", "explain",
            "how", "what", "why", "low", "level", "graphics", "current",
            "hello", "help"
        )
        val spanishWords = setOf(
            "yo", "soy", "es", "son", "el", "la", "los", "las", "un", "una",
            "de", "del", "para", "que", "tu", "tus", "puedo", "puede", "con",
            "y", "o", "pero", "claro", "explicar", "como", "por", "bajo",
            "nivel", "grafica", "graficos", "actual", "hola", "ayudar"
        )

        val originalNormalized = normalize(value)
        val originalTokens = originalNormalized
            .split(' ')
            .filter(String::isNotBlank)
        val originalSpanishScore = originalTokens.count(spanishWords::contains)

        val valueForScoring = if (originalSpanishScore > 0) {
            value.replace(
                Regex(
                    """\b[A-Z][\p{L}\p{N}'’.-]*(?:\s+(?:(?:of|the|and|to|in|on|for)\s+)?[A-Z][\p{L}\p{N}'’.-]*)+\b"""
                ),
                " "
            )
        } else {
            value
        }

        val normalized = normalize(valueForScoring)
        val padded = " $normalized "
        val reliableEnglishPhrases = listOf(
            " how are you ",
            " who are you ",
            " help you "
        )
        if (reliableEnglishPhrases.any(padded::contains)) return true
        if (originalSpanishScore == 0 && padded.contains(" hello ")) return true

        val tokens = normalized.split(' ').filter(String::isNotBlank)
        val englishScore = tokens.count(englishWords::contains)
        val spanishScore = tokens.count(spanishWords::contains)
        return englishScore >= 2 && englishScore > spanishScore
    }

    override fun intentResolver(): NaturalLanguageIntentResolver =
        object : NaturalLanguageIntentResolver {
            override fun resolve(transcript: String): VoiceCommand? {
                val clean = normalize(transcript)
                if (clean.isBlank()) return null
                return when {
                    containsAny(
                        clean,
                        "que modo me recomiendas",
                        "que perfil me recomiendas",
                        "cual modo me recomiendas",
                        "cual perfil me recomiendas",
                        "recomiendame un modo",
                        "recomiendame un perfil",
                        "optimiza mi juego",
                        "optimiza el juego",
                        "estoy listo para jugar",
                        "is my device ready",
                        "what mode do you recommend",
                        "what profile do you recommend",
                        "optimize my game"
                    ) -> VoiceCommand.AskAi(transcript)

                    else -> null
                }
            }
        }

    override fun close() {
        modelAdapter?.runCatching { close() }
    }

    private fun deterministicAdvice(
        question: String,
        context: GameHubAiContext,
        memories: List<UltraAiMemorySignal> = emptyList()
    ): GameHubAiAdvice {
        val readiness = readinessScore(context)
        val hot = context.thermalHeadroom?.let { it >= 0.80f } == true ||
            context.thermalStatus != null && context.thermalStatus >= 3
        val lowBattery = context.batteryPercent?.let { it < 20 } == true
        val poorNetwork = !context.networkValidated ||
            context.networkLatencyMs?.let { it > 120L } == true
        val lowStorage = context.storageFreePercent < 10
        val normalized = normalize(question)

        val suggested = when {
            hot || lowBattery || lowStorage ->
                PerformanceProfile.BALANCED
            normalized.contains("interpol") && readiness >= 70 ->
                PerformanceProfile.FRAME_INTERPOLATION
            readiness >= 80 &&
                context.refreshRateHz?.let { it >= 90f } == true &&
                context.gpuAvailable &&
                context.sustainedPerformanceSupported ->
                PerformanceProfile.X4
            else ->
                PerformanceProfile.BALANCED
        }

        val reason = when {
            hot -> AiAdviceReason.THERMAL
            lowBattery -> AiAdviceReason.LOW_BATTERY
            lowStorage -> AiAdviceReason.LOW_STORAGE
            poorNetwork -> AiAdviceReason.NETWORK
            suggested == PerformanceProfile.X4 -> AiAdviceReason.X4_READY
            suggested == PerformanceProfile.FRAME_INTERPOLATION ->
                AiAdviceReason.INTERPOLATION
            else -> AiAdviceReason.BALANCED_GENERAL
        }

        val baseAdvice = GameHubAiAdvice(
            readiness = readiness,
            suggestedProfile = suggested,
            reason = reason,
            localModelUsed = false,
            fallbackUsed = true
        )

        val feedback = feedbackSnapshot(context)
        val coreResult = runCatching {
            aiCore.evaluate(
                observation = UltraAiObservation.from(context).copy(
                    proposedProfileId = baseAdvice.suggestedProfile.name
                ),
                feedback = feedback,
                memories = memories
            )
        }.getOrNull()

        val coreProfile = coreResult
            ?.takeIf { !it.requiresCloud }
            ?.recommendation
            ?.profileId
            ?.let { profileId ->
                PerformanceProfile.entries.firstOrNull {
                    it.name.equals(profileId, ignoreCase = true)
                }
            }

        val safetyConstrained = hot || lowBattery || lowStorage
        val coreOverrideAllowed = coreResult?.overrideBaseRecommendation == true
        val safeCoreProfile = coreProfile
            ?.takeIf { candidate ->
                coreOverrideAllowed &&
                    !safetyConstrained &&
                    (candidate != PerformanceProfile.X4 ||
                        context.sustainedPerformanceSupported)
            }
            ?.let { candidate ->
                if (coreResult.recoveryExplanation != null) {
                    preferSafeCurrentProfile(
                        proposed = baseAdvice.suggestedProfile,
                        recovered = candidate,
                        context = context,
                        feedback = feedback
                    )
                } else {
                    candidate
                }
            }
        val finalProfile = safeCoreProfile ?: baseAdvice.suggestedProfile
        val finalReason = if (finalProfile == baseAdvice.suggestedProfile) {
            baseAdvice.reason
        } else {
            when (finalProfile) {
                PerformanceProfile.BALANCED -> AiAdviceReason.BALANCED_GENERAL
                PerformanceProfile.FRAME_INTERPOLATION -> AiAdviceReason.INTERPOLATION
                PerformanceProfile.X4 -> AiAdviceReason.X4_READY
            }
        }

        return baseAdvice.copy(
            suggestedProfile = finalProfile,
            reason = finalReason,
            recoveryExplanation = coreResult?.recoveryExplanation
                ?.takeIf { safeCoreProfile != null }
        )
    }

    private fun adviceFromAllowlistedAction(
        candidate: LocalAiActionCandidate,
        context: GameHubAiContext
    ): GameHubAiAdvice {
        val readiness = readinessScore(context)
        return when (candidate.action) {
            AiActionAllowlist.PROFILE_BALANCED ->
                GameHubAiAdvice(
                    readiness = readiness,
                    suggestedProfile = PerformanceProfile.BALANCED,
                    reason = AiAdviceReason.LOCAL_MODEL_BALANCED,
                    localModelUsed = true,
                    fallbackUsed = false
                )

            AiActionAllowlist.PROFILE_INTERPOLATION ->
                GameHubAiAdvice(
                    readiness = readiness,
                    suggestedProfile = PerformanceProfile.FRAME_INTERPOLATION,
                    reason = AiAdviceReason.LOCAL_MODEL_INTERPOLATION,
                    localModelUsed = true,
                    fallbackUsed = false
                )

            AiActionAllowlist.PROFILE_X4 ->
                if (context.sustainedPerformanceSupported) {
                    GameHubAiAdvice(
                        readiness = readiness,
                        suggestedProfile = PerformanceProfile.X4,
                        reason = AiAdviceReason.LOCAL_MODEL_X4,
                        localModelUsed = true,
                        fallbackUsed = false
                    )
                } else {
                    deterministicAdvice("local model unsupported x4", context)
                }

            AiActionAllowlist.ADVICE ->
                deterministicAdvice("local model advice", context).copy(
                    localModelUsed = true,
                    fallbackUsed = false
                )

            else ->
                deterministicAdvice("invalid local action", context)
        }
    }

    private fun feedbackSnapshot(
        context: GameHubAiContext
    ): UltraAiFeedbackSnapshot =
        UltraAiFeedbackSnapshot.fromObservations(context.optimizationObservations)

    private fun preferSafeCurrentProfile(
        proposed: PerformanceProfile,
        recovered: PerformanceProfile,
        context: GameHubAiContext,
        feedback: UltraAiFeedbackSnapshot
    ): PerformanceProfile {
        val current = context.selectedProfile
        val currentSupported =
            current != PerformanceProfile.X4 ||
                context.sustainedPerformanceSupported
        val currentIsSafeAlternative =
            currentSupported &&
                current != proposed &&
                feedback.poorOutcomeCount(current.name) == 0
        val recoveredLooksLikeDefaultFallback =
            recovered == PerformanceProfile.BALANCED &&
                !feedback.isAccepted(PerformanceProfile.BALANCED.name)

        return if (
            recovered != proposed &&
            recoveredLooksLikeDefaultFallback &&
            currentIsSafeAlternative
        ) {
            current
        } else {
            recovered
        }
    }

    private fun recoverAdviceWithFeedback(
        advice: GameHubAiAdvice,
        context: GameHubAiContext
    ): GameHubAiAdvice {
        if (isSafetyConstrained(context)) {
            return deterministicAdvice(
                question = "safety constrained feedback recovery",
                context = context
            ).copy(
                localModelUsed = advice.localModelUsed,
                fallbackUsed = true,
                recoveryExplanation = null
            )
        }

        val feedback = feedbackSnapshot(context)
        val hasFeedback =
            feedback.acceptedProfileIds.isNotEmpty() ||
                feedback.rejectedProfileIds.isNotEmpty() ||
                feedback.revertedProfileIds.isNotEmpty()
        if (!hasFeedback) return advice

        val coreResult = runCatching {
            aiCore.evaluate(
                observation = UltraAiObservation.from(context).copy(
                    proposedProfileId = advice.suggestedProfile.name
                ),
                feedback = feedback,
                memories = emptyList()
            )
        }.getOrNull()
            ?.takeIf { !it.requiresCloud }
            ?: return advice

        val coreRecoveredProfile =
            PerformanceProfile.entries.firstOrNull { profile ->
                profile.name.equals(
                    coreResult.recommendation.profileId,
                    ignoreCase = true
                )
            } ?: return advice
        val recoveredProfile =
            if (coreResult.recoveryExplanation != null) {
                preferSafeCurrentProfile(
                    proposed = advice.suggestedProfile,
                    recovered = coreRecoveredProfile,
                    context = context,
                    feedback = feedback
                )
            } else {
                coreRecoveredProfile
            }

        if (recoveredProfile == PerformanceProfile.X4 &&
            !context.sustainedPerformanceSupported) {
            return advice
        }
        if (recoveredProfile == advice.suggestedProfile) {
            return advice.copy(
                recoveryExplanation = coreResult.recoveryExplanation
            )
        }

        return advice.copy(
            suggestedProfile = recoveredProfile,
            reason = when (recoveredProfile) {
                PerformanceProfile.BALANCED -> AiAdviceReason.BALANCED_GENERAL
                PerformanceProfile.FRAME_INTERPOLATION -> AiAdviceReason.INTERPOLATION
                PerformanceProfile.X4 -> AiAdviceReason.X4_READY
            },
            fallbackUsed = true,
            recoveryExplanation = coreResult.recoveryExplanation
        )
    }

    private fun isProfileRecommendationQuestion(normalized: String): Boolean {
        val query = normalized
            .removePrefix("gamehub ultra ")
            .removePrefix("gamehub ")
            .removePrefix("ultra ")
            .trim()
        if (GENERIC_PROFILE_RECOMMENDATION_PATTERN.matches(query)) return true

        val tokens = query.split(' ').filter(String::isNotBlank)
        val hasProfileSubject = tokens.any { it in PROFILE_RECOMMENDATION_TOKENS }
        val hasRecommendationIntent = tokens.any { it in PROFILE_RECOMMENDATION_INTENT_TOKENS }
        val hasGamingContext = tokens.any { it in PROFILE_GAMING_SIGNAL_TOKENS }
        return hasProfileSubject && hasRecommendationIntent && hasGamingContext
    }

    private fun isSafetyConstrained(context: GameHubAiContext): Boolean =
        context.thermalStatus?.let { it >= 3 } == true ||
            context.thermalHeadroom?.let { it >= 0.80f } == true ||
            context.batteryPercent?.let { it < 20 } == true ||
            context.storageFreePercent < 10

    private fun readinessScore(context: GameHubAiContext): Int {
        var score = 50
        if (context.cpuCores >= 8) score += 10 else if (context.cpuCores >= 4) score += 5
        if (context.totalRamMb >= 8192) score += 10 else if (context.totalRamMb >= 4096) score += 5
        if (context.gpuAvailable) score += 8
        if (context.sustainedPerformanceSupported) score += 5
        if (context.thermalStatus == 0) score += 8
        if (context.thermalHeadroom != null && context.thermalHeadroom <= 0.60f) score += 7
        if (context.batteryPercent == null || context.batteryPercent >= 50) score += 5
        if (context.charging) score += 2
        if (context.refreshRateHz != null && context.refreshRateHz >= 90f) score += 5
        if (context.networkValidated) score += 3
        if (context.networkLatencyMs != null && context.networkLatencyMs <= 80L) score += 3
        if (context.storageFreePercent >= 20) score += 2
        return score.coerceIn(0, 100)
    }

    private fun containsAny(value: String, vararg patterns: String): Boolean =
        patterns.any(value::contains)

    private companion object {
        const val MEMORY_RECALL_LIMIT = 6
        val PROFILE_RECOMMENDATION_TOKENS = setOf("modo", "perfil", "mode", "profile")
        val PROFILE_GAMING_SIGNAL_TOKENS = setOf(
            "fps", "x4", "interpolacion", "interpolation", "balanceado", "balanced",
            "gaming", "rendimiento", "performance", "juego", "juegos", "game", "games",
            "gamehub", "ultra"
        )
        val PROFILE_RECOMMENDATION_INTENT_TOKENS = setOf(
            "recomienda", "recomiendas", "recomendacion", "recomendado",
            "recommend", "recommends", "recommendation", "recommended",
            "mejor", "best", "usar", "use", "choose", "elegir", "elige",
            "switch", "cambiar", "cambia", "activar", "activa", "set",
            "conviene", "should"
        )
        val GENERIC_PROFILE_RECOMMENDATION_PATTERN = Regex(
            """^(?:(?:que|cual) (?:modo|perfil) (?:me )?(?:recomiendas|recomienda|conviene|debo usar|deberia usar|uso|elijo)|(?:which|what) (?:mode|profile) (?:do you recommend|should i use|is best|should i choose))$"""
        )
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
