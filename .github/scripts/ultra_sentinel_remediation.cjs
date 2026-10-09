'use strict';
const RULES=Object.freeze({
UNSCOPED_COROUTINE:{
 title:'Corutina asociada al ciclo de vida',
 why:'GlobalScope puede sobrevivir a la Activity/Service.',
 steps:['Identifica propietario y scope real.','Reemplaza GlobalScope por lifecycleScope, viewModelScope o scope cancelable del servicio.','Comprueba cancelación y dispatcher.'],
 sample:'// Solo Activity: adaptar según propietario\nlifecycleScope.launch { operacionSuspendida() }',
 test:'Cerrar el componente durante trabajo pendiente y verificar cancelación.',
 caution:'No pegar lifecycleScope si no existe en el componente.'},
SPEECH_REENTRANT_RETRY:{
 title:'Desacoplar reinicio de SpeechRecognizer',
 why:'onError seguido de startListening síncrono puede encadenar callbacks.',
 steps:['Reutiliza VoiceRecognitionRetryGate existente.','Programa reinicio con Handler y backoff acotado.','Cancela reintentos en onDestroy y al parar escucha.'],
 sample:'// Ejemplo orientativo; adaptar API del gate:\nif (retryGate.canRetry(error) && sessionActive) {\n  mainHandler.postDelayed({ if (sessionActive) restartSafely() }, retryDelayMs)\n}',
 test:'Inyectar 100 onError síncronos: pila y tareas pendientes acotadas.',
 caution:'canRetry y sessionActive son nombres ilustrativos.'},
UNBOUNDED_RECURSION:{
 title:'Límite de profundidad o estructura iterativa',
 why:'Posible autollamada sin condición base visible.',
 steps:['Inspecciona función completa y deduplica falsos positivos.','Define condición de terminación, presupuesto y visited.','Considera una iteración en entradas cíclicas.'],
 sample:'// Ejemplo abstracto: requiere tipos reales\nif (depth >= policy.maxDepth) return fallback\nif (!visited.add(nodeId)) return fallback',
 test:'Entrada cíclica y extrema: termina sin StackOverflowError.',
 caution:'fallback debe ser resultado seguro para el dominio.'},
BLOCKING_ANDROID_CALL:{
 title:'Mover bloqueo fuera de main thread',
 why:'Thread.sleep/runBlocking pueden bloquear UI o callbacks.',
 steps:['Confirma el hilo con traza.','Usa delay en suspend o withContext(Dispatchers.IO) para E/S.','Comprueba ANR y rendimiento.'],
 sample:'// En contexto suspend:\ndelay(esperaMs)\nwithContext(Dispatchers.IO) { operacionBloqueante() }',
 test:'StrictMode y test con timeout del main looper.',
 caution:'delay no se puede pegar en función no suspend.'},
POTENTIAL_HARDCODED_SECRET:{
 title:'Rotar secreto y retirarlo del código',
 why:'Una constante podría exponer una credencial.',
 steps:['Confirma sin publicar el valor.','Si es real, revoca y rota.','Usa secret store con permisos mínimos.'],
 sample:'// No pegar credenciales en source, logs o comentarios.\n// Leer solo de almacenamiento seguro.',
 test:'Scan de secretos y comprobación de credencial revocada.',
 caution:'Nunca copiar el valor original.'},
POTENTIAL_PRIVATE_LOG:{
 title:'Eliminar datos privados de Logcat',
 why:'Logs pueden exponer transcripciones o credenciales.',
 steps:['Registrar estados no sensibles, nunca texto privado.','Revisar debug y release.','Auditar Sentry/Logcat.'],
 sample:'Log.d(TAG, "Estado: " + estadoSeguro)',
 test:'Búsqueda de transcripciones y credenciales en logs.',
 caution:'estadoSeguro debe ser no sensible.'},
PRIVILEGED_UNTRUSTED_CHECKOUT:{
 title:'Separar código PR de job privilegiado',
 why:'pull_request_target con checkout no confiable expone secretos.',
 steps:['Checkout solo de main en job con permisos.','Trata diff del PR como datos no confiables.','Evita dar tokens de escritura a código de fork.'],
 sample:'# Ejemplo; usar SHA de checkout validado:\n- uses: actions/checkout@SHA_VERIFICADO\n  with:\n    ref: ${{ github.event.repository.default_branch }}\n    persist-credentials: false',
 test:'PR de fork malicioso no obtiene secrets.',
 caution:'SHA_VERIFICADO debe sustituirse por SHA auditado.'},
NON_CANCELLABLE_LOOP:{
 title:'Cancelar bucle sin salida',
 why:'Bucle sin condición visible puede consumir CPU indefinidamente.',
 steps:['Comprobar salidas existentes fuera del diff.','Añadir condición de terminación y cancelación.','Medir CPU y memoria.'],
 sample:'// Solo corrutina:\nwhile (currentCoroutineContext().isActive) {\n  procesarElemento()\n  yield()\n}',
 test:'Cancelación bajo carga detiene trabajo en tiempo acotado.',
 caution:'yield no reemplaza criterio de terminación.'},
FORCED_GC:{
 title:'Eliminar System.gc en producción',
 why:'GC forzado puede aumentar jank.',
 steps:['Comprobar que no es benchmark.','Retirar GC explícito.','Medir frame pacing antes/después.'],
 sample:'// Evitar System.gc() en ruta crítica; ART gestiona GC.',
 test:'Macrobenchmark frame p95/p99 y pausas GC.',
 caution:'Verificar diferencias reales.'},
REGRESSION_TEST_COVERAGE:{
 title:'Añadir prueba de regresión primero',
 why:'Modificación de código principal sin tests cambiados.',
 steps:['Buscar cobertura indirecta existente.','Crear prueba RED para el comportamiento cambiado.','Arreglar y volver a ejecutar suite.'],
 sample:'// Plantilla orientativa:\n@Test fun edgeCase_doesNotCrash() { /* Arrange Act Assert */ }',
 test:'RED antes y GREEN después de fix.',
 caution:'Ausencia de tests modificados no confirma bug.'}
});
const FALLBACK={title:'Investigar con evidencia',why:'Riesgo hipotético sin contexto completo.',
 steps:['Inspeccionar SHA, código entero, logs y tests.','Crear prueba RED.','Aplicar cambio mínimo y verificar CI.'],
 sample:'// No existe parche universal seguro para este caso.',
 test:'Prueba reproducible de regresión.',
 caution:'Requiere investigación humana.'};
function scrub(s,n=300){
 return String(s??'').replace(/Bearer\s+\S+/gi,'Bearer [REDACTED]')
 .replace(/(?:sk-|ghp_|github_pat_)[A-Za-z0-9_-]{8,}/g,'[REDACTED]')
 .replace(/[<>]/g,'?').slice(0,n);
}
function makeSuggestion(f){
 const k=RULES[f.rule]||FALLBACK;
 const secret=/SECRET|PRIVATE_LOG/.test(String(f.rule));
 const path=scrub(f.path),line=Number.isSafeInteger(f.line)?f.line:0;
 const info={
  rule:scrub(f.rule,100),severity:scrub(f.severity,20),confidence:scrub(f.confidence,12),
  path,line,title:k.title,diagnosis:k.why,
  evidence:secret?'[REDACTED]':scrub(f.evidence,180),
  steps:k.steps.slice(),sample:k.sample,test:k.test,caution:k.caution,
  status:'DRAFT_REQUIRES_VALIDATION',autofix:false
 };
 info.handoff=[
 'Investiga SHA actual, archivo '+path+':'+line+'.','Hipótesis: '+k.why,
 'Plan de solución: '+k.steps.join(' '),
 'Plantilla ILLUSTRATIVA, NO pegar sin adaptar:\n'+k.sample,
 'Escribe prueba que falle primero: '+k.test,
 'Advertencia: '+k.caution,
 'Revisar Android Build, Coverage, Smoke, CodeRabbit/Qodo y logs antes de fusionar.'
 ].join('\n').slice(0,3000);
 return info;
}
function buildRemediations(review){
 return {engine:'Ultra Sentinel Repair Planner',version:'2.0.0',sha:review?.sha||'unknown',
 verified:false,autoMerge:false,scope:'human-supervised',
 suggestions:(Array.isArray(review?.findings)?review.findings:[]).slice(0,35).map(makeSuggestion)};
}
function markdown(rem){
 const x=['### Soluciones propuestas por Ultra Sentinel','_Borradores, no parches verificados. Exigen TDD y adaptación._'];
 for(const f of rem.suggestions)x.push('','**'+f.severity+' — '+f.rule+'**  '+f.path+':'+f.line,
  'Hipótesis: '+f.diagnosis,'Pasos: '+f.steps.join(' '),'~~~text',f.sample,'~~~',
  'Test RED: '+f.test,'Riesgo: '+f.caution);
 return x.join('\n').slice(0,36000);
}
module.exports={RULES,makeSuggestion,buildRemediations,markdown};
