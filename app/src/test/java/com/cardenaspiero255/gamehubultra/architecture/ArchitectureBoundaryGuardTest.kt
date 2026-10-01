package com.cardenaspiero255.gamehubultra.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureBoundaryGuardTest {

    @Test
    fun gameHubViewModelConsumesCompositionBoundary() {
        val source = sourceFile("com/cardenaspiero255/gamehubultra/ui/GameHubViewModel.kt").readText()
        val violations = buildList {
            if (source.contains("GameHubPreferencesRepository(")) {
                add("GameHubViewModel constructs GameHubPreferencesRepository directly")
            }
            if (!source.contains("dependencies: GameHubViewModelDependencies")) {
                add("GameHubViewModel does not receive GameHubViewModelDependencies")
            }
        }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "GameHubViewModel state dependencies must come from production composition:\n",
                separator = "\n"
            )
        )
    }


    @Test
    fun mainActivityStaysBootstrapOnly() {
        val source = sourceFile("com/cardenaspiero255/gamehubultra/MainActivity.kt")
        val violations = mutableListOf<String>()

        val nonBlankLines = source.readLines().count { it.isNotBlank() }
        if (nonBlankLines > 90) {
            violations += "MainActivity has $nonBlankLines non-blank lines; bootstrap limit is 90."
        }

        val forbiddenPrefixes = listOf(
            "com.cardenaspiero255.gamehubultra.ai.",
            "com.cardenaspiero255.gamehubultra.data.",
            "com.cardenaspiero255.gamehubultra.domain.",
            "com.cardenaspiero255.gamehubultra.network.",
            "com.cardenaspiero255.gamehubultra.platform.",
            "com.cardenaspiero255.gamehubultra.tools.",
            "com.cardenaspiero255.gamehubultra.voice."
        )
        source.readLines()
            .filter { it.startsWith("import ") }
            .map { it.removePrefix("import ").trim() }
            .filter { imported -> forbiddenPrefixes.any(imported::startsWith) }
            .forEach { violations += "MainActivity imports runtime implementation: $it" }

        source.readLines()
            .filterNot { it.startsWith("import ") }
            .filter { line -> forbiddenPrefixes.any(line::contains) }
            .forEach { violations += "MainActivity references runtime implementation: $it" }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "MainActivity must remain a bootstrap-only composition entry point:\n",
                separator = "\n"
            )
        )
    }




    @Test
    fun settingsDoesNotConstructConnectedAccountPersistence() {
        val source = sourceFile(
            "com/cardenaspiero255/gamehubultra/ui/components/SettingsComponents.kt"
        ).readText()

        assertTrue(
            !source.contains("ConnectedGameAccountsStore("),
            "SettingsComponents must receive connected-account persistence through a boundary"
        )
    }

    @Test
    fun storeConnectionActivityDoesNotConstructConnectedAccountPersistence() {
        val source = sourceFile(
            "com/cardenaspiero255/gamehubultra/store/StoreConnectionActivity.kt"
        ).readText()

        assertTrue(
            !source.contains("ConnectedGameAccountsStore("),
            "StoreConnectionActivity must receive connected-account persistence through a boundary"
        )
    }

    @Test
    fun settingsDoesNotConstructStoreLibraryPersistence() {
        val source = sourceFile(
            "com/cardenaspiero255/gamehubultra/ui/components/SettingsComponents.kt"
        ).readText()

        assertTrue(
            !source.contains("StoreLibraryStore("),
            "SettingsComponents must receive store-library persistence through a boundary"
        )
    }


    @Test
    fun connectedAccountConsumersDependOnOwnershipBoundary() {
        val guardedFiles = listOf(
            sourceFile("com/cardenaspiero255/gamehubultra/ui/components/SettingsComponents.kt"),
            sourceFile("com/cardenaspiero255/gamehubultra/store/StoreConnectionActivity.kt")
        )

        val violations = guardedFiles.flatMap { file ->
            val source = file.readText()
            buildList {
                if (source.contains("val store = remember(context) { ConnectedGameAccountsStore(")) {
                    add("${file.name} exposes concrete connected-account ownership")
                }
                if (source.contains("ConnectedGameAccountsStore(this).upsert(")) {
                    add("${file.name} constructs connected-account storage at the write site")
                }
                if (source.contains("ConnectedGameAccountsStore(this@StoreConnectionActivity).upsert(")) {
                    add("${file.name} constructs connected-account storage at the write site")
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Connected-account consumers must depend on ConnectedGameAccountsStateRepository:\n",
                separator = "\n"
            )
        )
    }


    @Test
    fun voiceServicesDoNotConstructSelectionPersistence() {
        val guardedFiles = listOf(
            sourceFile("com/cardenaspiero255/gamehubultra/voice/UltraWakeService.kt"),
            sourceFile("com/cardenaspiero255/gamehubultra/voice/GameHubVoiceInteractionService.kt")
        )

        val violations = guardedFiles
            .filter { it.readText().contains("GameHubPreferencesRepository(") }
            .map { "${it.name} constructs selection persistence directly" }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Voice services must receive selection persistence through composition:\n",
                separator = "\n"
            )
        )
    }

    @Test
    fun aliasConsumersDoNotConstructSharedPreferencesPersistence() {
        val guardedFiles = listOf(
            sourceFile("com/cardenaspiero255/gamehubultra/GameHubUltraApp.kt"),
            sourceFile("com/cardenaspiero255/gamehubultra/voice/UltraWakeService.kt"),
            sourceFile("com/cardenaspiero255/gamehubultra/voice/GameHubVoiceInteractionService.kt")
        )

        val violations = guardedFiles
            .filter { it.readText().contains("SharedPreferencesGameAliasStateRepository(") }
            .map { "${it.name} constructs alias persistence directly" }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Alias consumers must receive GameAliasStateRepository through composition:\n",
                separator = "\n"
            )
        )
    }

    @Test
    fun ultraWakeServiceDoesNotOwnConversationMemoryPersistence() {
        val source = sourceFile(
            "com/cardenaspiero255/gamehubultra/voice/UltraWakeService.kt"
        ).readText()

        assertTrue(
            !source.contains("UltraConversationMemoryStore.get("),
            "UltraWakeService must receive Ultra conversation memory through production composition"
        )
    }

    @Test
    fun optimizationMemoryConsumerDependsOnOwnershipBoundary() {
        val file = sourceFile("com/cardenaspiero255/gamehubultra/GameHubUltraApp.kt")
        val source = file.readText()

        assertTrue(
            source.contains(
                "optimizationMemoryStore: GameOptimizationMemoryStateRepository"
            ),
            "GameHubUltraApp must type optimizationMemoryStore as " +
                "GameOptimizationMemoryStateRepository"
        )
        assertTrue(
            !Regex("""optimizationMemoryStore\\s*:\\s*GameOptimizationMemoryStore""")
                .containsMatchIn(source),
            "GameHubUltraApp must not expose GameOptimizationMemoryStore as its dependency type"
        )
        assertTrue(
            !source.contains("GameOptimizationMemoryStore("),
            "GameHubUltraApp must not construct optimization-memory persistence directly"
        )
    }


    @Test
    fun storeLibraryConsumersDependOnOwnershipBoundary() {
        val guardedFiles = listOf(
            sourceFile("com/cardenaspiero255/gamehubultra/GameHubUltraApp.kt"),
            sourceFile("com/cardenaspiero255/gamehubultra/store/StoreConnectionActivity.kt"),
            sourceFile("com/cardenaspiero255/gamehubultra/ui/components/SettingsComponents.kt")
        )

        val violations = guardedFiles.flatMap { file ->
            val source = file.readText()
            buildList {
                if (!source.contains("storeLibraryRepository: StoreLibraryStateRepository")) {
                    add("${file.name} must type storeLibraryRepository as StoreLibraryStateRepository")
                }
                val concreteTypedRepository = Regex(
                    """storeLibraryRepository\s*:\s*StoreLibraryStore"""
                )
                if (concreteTypedRepository.containsMatchIn(source)) {
                    add("${file.name} exposes StoreLibraryStore as the repository dependency type")
                }
                if (
                    file.name == "GameHubUltraApp.kt" &&
                    source.contains("StoreLibraryStore(")
                ) {
                    add("${file.name} constructs store-library persistence directly")
                }
                val directConcreteCalls = listOf(
                    "StoreLibraryStore(context).getAll()",
                    "StoreLibraryStore(context).removeForAccount(",
                    "StoreLibraryStore(this).replaceForAccount(",
                    "StoreLibraryStore(this@StoreConnectionActivity)"
                )
                directConcreteCalls
                    .filter(source::contains)
                    .forEach {
                        add("${file.name} constructs store-library persistence at a consumer call site")
                    }
            }
        }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Store-library consumers must depend on StoreLibraryStateRepository:\n",
                separator = "\n"
            )
        )
    }


    @Test
    fun sessionConsumersDependOnOwnershipBoundary() {
        val guardedFiles = listOf(
            sourceFile("com/cardenaspiero255/gamehubultra/ui/GameHubViewModel.kt"),
            sourceFile("com/cardenaspiero255/gamehubultra/data/GameSessionLifecycleCoordinator.kt")
        )

        val violations = guardedFiles.flatMap { file ->
            val source = file.readText()
            buildList {
                if (source.contains("private val store: GameSessionStore")) {
                    add("${file.name} depends on concrete GameSessionStore")
                }
                if (
                    file.name == "GameHubViewModel.kt" &&
                    source.contains("GameSessionStore(")
                ) {
                    add("${file.name} constructs GameSessionStore directly")
                }
                if (
                    file.name == "GameHubViewModel.kt" &&
                    source.contains("GameSessionLifecycleCoordinator(")
                ) {
                    add("${file.name} constructs GameSessionLifecycleCoordinator directly")
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Session consumers must depend on GameSessionStateRepository:\n",
                separator = "\n"
            )
        )
    }


    @Test
    fun aliasConsumersDependOnOwnershipBoundary() {
        val guardedFiles = listOf(
            sourceFile("com/cardenaspiero255/gamehubultra/GameHubUltraApp.kt"),
            sourceFile("com/cardenaspiero255/gamehubultra/voice/UltraWakeService.kt"),
            sourceFile("com/cardenaspiero255/gamehubultra/voice/GameHubVoiceInteractionService.kt")
        )

        val violations = guardedFiles
            .filter { it.readText().contains("GameAliasStore") }
            .map { "${it.name} reaches GameAliasStore directly" }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Alias consumers must depend on GameAliasStateRepository:\n",
                separator = "\n"
            )
        )
    }


    @Test
    fun uiDoesNotReachConcreteUltraResearchOrGamingImplementations() {
        val uiFiles = listOf(
            sourceFile("com/cardenaspiero255/gamehubultra/GameHubUltraApp.kt")
        ) + sourceDirectory("com/cardenaspiero255/gamehubultra/ui")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        val forbiddenSymbols = setOf(
            "UltraProductionQueryExecutor",
            "UltraVerifiedResearchEngine",
            "UltraProductionResearchProviderSource",
            "GeminiNanoLocalAiModelAdapter",
            "GameHubAiAdvisor",
            "UltraUnifiedAgentRouter",
            "UltraConversationMemoryStore",
            "UltraConversationSessionMemoryAdapter",
            "UltraNetworkGamingRuntimeController",
            "NetworkRuntimeOptimizer"
        )

        val violations = uiFiles.flatMap { sourceFile ->
            val source = sourceFile.readText()
            forbiddenSymbols
                .filter(source::contains)
                .map { symbol -> "${sourceFile.name} reaches concrete implementation $symbol" }
        }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "UI must depend on boundaries/controllers, not concrete Ultra/Research/Gaming implementations:\n",
                separator = "\n"
            )
        )
    }


    @Test
    fun uiAndResearchCoordinatorsDoNotReachSupabaseProviderDirectly() {
        val guardedFiles = listOf(
            sourceFile("com/cardenaspiero255/gamehubultra/GameHubUltraApp.kt"),
            sourceFile("com/cardenaspiero255/gamehubultra/ai/UltraGeneralResearchCoordinator.kt"),
            sourceFile("com/cardenaspiero255/gamehubultra/ai/UltraQueryExecutionCoordinator.kt")
        ) + sourceDirectory("com/cardenaspiero255/gamehubultra/ui")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        val violations = guardedFiles
            .filter { it.readText().contains("SupabaseUltraResearchProvider") }
            .map { "${it.name} reaches SupabaseUltraResearchProvider directly" }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Ultra/UI coordinators must depend on the research boundary, not Supabase:\n",
                separator = "\n"
            )
        )
    }

    @Test
    fun gameHubUltraAppDoesNotUseProductionCompositionAsServiceLocator() {
        val source = sourceFile(
            "com/cardenaspiero255/gamehubultra/GameHubUltraApp.kt"
        ).readText()

        assertTrue(
            !source.contains("GameHubProductionComposition"),
            "GameHubUltraApp must receive production dependencies through the presentation boundary"
        )
    }

    @Test
    fun gameHubUltraAppDoesNotOwnAndroidVoiceInfrastructure() {
        val source = sourceFile(
            "com/cardenaspiero255/gamehubultra/GameHubUltraApp.kt"
        ).readText()

        val forbiddenSymbols = listOf(
            "private object VoiceDeviceStatusProvider",
            "private object VoiceResponseFormatter"
        )
        val violations = forbiddenSymbols
            .filter(source::contains)
            .map { symbol -> "GameHubUltraApp owns voice infrastructure: $symbol" }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Android voice infrastructure belongs behind the voice boundary:\n",
                separator = "\n"
            )
        )
    }

    @Test
    fun gameHubUltraAppMonolithHasStrictSizeCeiling() {
        val app = sourceFile("com/cardenaspiero255/gamehubultra/GameHubUltraApp.kt")
        val lineCount = app.readLines().size

        assertTrue(
            lineCount <= 2250,
            "GameHubUltraApp.kt must keep shrinking during screen-state block 7; " +
                "current line count: $lineCount, ceiling: 2250"
        )
    }

    @Test
    fun homeAndLibraryPresentationStateStaysExtractedFromCompose() {
        val app = sourceFile("com/cardenaspiero255/gamehubultra/GameHubUltraApp.kt").readText()
        val homeScreen = sourceFile(
            "com/cardenaspiero255/gamehubultra/ui/home/HomeScreen.kt"
        ).readText()
        val libraryScreen = sourceFile(
            "com/cardenaspiero255/gamehubultra/ui/library/LibraryScreen.kt"
        ).readText()

        val forbiddenLegacyState = listOf(
            "var localGameCount by remember",
            "var quickVoiceOpen by remember",
            "var quickVoiceRevealRequest by remember",
            "var discovery by remember",
            "var launchableApps by remember",
            "var query by rememberSaveable",
            "var launchFailed by rememberSaveable",
            "var addGameDialogVisible by rememberSaveable",
            "var selectedGameDetailsVisible by rememberSaveable",
            "shouldRevealQuickVoiceControls"
        )
        val violations = forbiddenLegacyState
            .filter(app::contains)
            .map { symbol -> "GameHubUltraApp.kt still owns legacy screen state: $symbol" }

        assertTrue(
            homeScreen.contains("rememberHomeUiStateHolder()"),
            "HomeScreen must obtain presentation state from HomeUiStateHolder"
        )
        assertTrue(
            libraryScreen.contains("rememberLibraryUiStateHolder()"),
            "LibraryScreen must obtain presentation state from LibraryUiStateHolder"
        )
        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Home and Library presentation state must stay outside Compose-local state:\n",
                separator = "\n"
            )
        )
    }

    @Test
    fun screenStateHoldersStayFreeOfRuntimeSideEffects() {
        val stateFiles = listOf(
            sourceFile("com/cardenaspiero255/gamehubultra/ui/home/state/HomeUiState.kt"),
            sourceFile("com/cardenaspiero255/gamehubultra/ui/library/state/LibraryUiState.kt")
        )
        val forbiddenRuntimeSymbols = listOf(
            "GameLibrary.",
            "GameLauncher.",
            "GameSelectionStore",
            "ProfileSelectionStore",
            "LocalContext",
            "Dispatchers.",
            "withContext(",
            "LifecycleEventObserver"
        )

        val violations = stateFiles.flatMap { file ->
            val source = file.readText()
            forbiddenRuntimeSymbols
                .filter(source::contains)
                .map { symbol -> "${file.name} owns runtime side effect $symbol" }
        }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Screen state holders must remain presentation-only:\n",
                separator = "\n"
            )
        )
    }

    @Test
    fun productionDoesNotUseLegacySelectionStores() {
        val productionFiles = sourceRoot()
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val forbiddenSymbols = listOf(
            "GameSelectionStore",
            "ProfileSelectionStore"
        )

        val violations = productionFiles.flatMap { file ->
            val source = file.readText()
            forbiddenSymbols
                .filter(source::contains)
                .map { symbol -> "${file.name} still depends on legacy selection store $symbol" }
        }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Selection/profile persistence must flow through GameSelectionStateRepository:\n",
                separator = "\n"
            )
        )
    }

    @Test
    fun gameHubViewModelRoutesPersistentLibraryStateThroughLibraryBoundary() {
        val source = sourceFile(
            "com/cardenaspiero255/gamehubultra/ui/GameHubViewModel.kt"
        ).readText()

        assertTrue(
            source.contains("private val libraryRepository: GameLibraryStateRepository = dependencies.libraryRepository"),
            "GameHubViewModel must expose persistent Library state through GameLibraryStateRepository"
        )

        val forbiddenConcreteCalls = listOf(
            "repository.favoriteGamesFlow()",
            "repository.recentGamesFlow()",
            "repository.manualGamesFlow()",
            "repository.setFavoriteGame(",
            "repository.recordRecentGame(",
            "repository.setManualGame("
        )
        val violations = forbiddenConcreteCalls
            .filter(source::contains)
            .map { call -> "GameHubViewModel bypasses GameLibraryStateRepository with $call" }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Persistent Library collections must flow through GameLibraryStateRepository:\n",
                separator = "\n"
            )
        )
    }

    @Test
    fun voiceSelectionPersistenceDoesNotBlockCommandWorkers() {
        val serviceFiles = listOf(
            sourceFile("com/cardenaspiero255/gamehubultra/voice/GameHubVoiceInteractionService.kt"),
            sourceFile("com/cardenaspiero255/gamehubultra/voice/UltraWakeCommandRuntime.kt")
        )

        val violations = serviceFiles.flatMap { file ->
            val source = file.readText()
            buildList {
                if (!source.contains("DurableSelectionMutationQueue.enqueue")) {
                    add("${file.name} must use the process-durable selection mutation queue")
                }
                if (!source.contains("onFailure =")) {
                    add("${file.name} must observe asynchronous selection persistence failures")
                }
                if (source.contains("selectionSaveScope")) {
                    add("${file.name} ties accepted selection writes to service lifetime")
                }
                if (containsBlockingSelectionWrite(source)) {
                    add("${file.name} blocks its command worker during selection persistence")
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Voice selection persistence must stay asynchronous, ordered, and observable:\n",
                separator = "\n"
            )
        )
    }

    @Test
    fun blockingSelectionWriteDetectionHandlesNestedBlocksAndDispatcherArgument() {
        val nestedBlockingWrite = """
            runBlocking(Dispatchers.IO) {
                if (shouldPersist) {
                    println("nested")
                }
                selectionRepository.saveSelectedGame("game.a")
            }
        """.trimIndent()
        val nestedArgumentBlockingWrite = """
            kotlinx.coroutines.runBlocking(context.plus(Dispatchers.IO)) {
                selectionRepository.saveSelectedProfile(PerformanceProfile.X4)
            }
        """.trimIndent()
        val asyncWrite = """
            runBlocking { selectionRepository.selectedGameFlow().first() }
            DurableSelectionMutationQueue.enqueue {
                selectionRepository.saveSelectedGame("game.a")
            }
        """.trimIndent()

        assertTrue(containsBlockingSelectionWrite(nestedBlockingWrite))
        assertTrue(containsBlockingSelectionWrite(nestedArgumentBlockingWrite))
        assertTrue(!containsBlockingSelectionWrite(asyncWrite))
    }

    @Test
    fun embeddedVoiceSelectionUsesProcessDurablePersistence() {
        val app = sourceFile(
            "com/cardenaspiero255/gamehubultra/GameHubUltraApp.kt"
        ).readText()
        val viewModel = sourceFile(
            "com/cardenaspiero255/gamehubultra/ui/GameHubViewModel.kt"
        ).readText()

        listOf(
            "onVoiceSelectedGame = viewModel::persistVoiceSelectedGame",
            "onVoiceSelectedProfile = viewModel::persistVoiceSelectedProfile",
            "onVoiceSelectedGameWithProfile = viewModel::persistVoiceSelectedGameWithProfile"
        ).forEach { expected ->
            assertTrue(
                app.contains(expected),
                "Embedded voice callbacks must use durable persistence: $expected"
            )
        }

        listOf(
            "onVoiceSelectedGame = viewModel::selectGame",
            "onVoiceSelectedProfile = viewModel::selectGlobalProfile",
            "onVoiceSelectedGameWithProfile = viewModel::selectGameWithProfile"
        ).forEach { forbidden ->
            assertTrue(
                !app.contains(forbidden),
                "Embedded voice persistence must not depend on viewModelScope: $forbidden"
            )
        }

        assertTrue(
            viewModel.contains("DurableSelectionMutationQueue.enqueue"),
            "GameHubViewModel voice persistence must use the process-durable queue"
        )
        assertTrue(
            viewModel.contains("onFailure = ::reportVoiceSelectionPersistenceFailure"),
            "GameHubViewModel must observe durable voice persistence failures"
        )
    }

    @Test
    fun productionCompositionDoesNotOwnComposePresentation() {
        val composition = sourceFile(
            "com/cardenaspiero255/gamehubultra/composition/GameHubProductionComposition.kt"
        ).readText()

        val forbiddenPresentationSymbols = listOf(
            "androidx.compose.",
            "GameHubUltraTheme",
            "GameHubUltraApp",
            "@Composable"
        )
        val violations = buildList {
            forbiddenPresentationSymbols
                .filter(composition::contains)
                .mapTo(this) { symbol ->
                    "Production composition reaches presentation symbol $symbol"
                }
            if (Regex("""\\bGameHubViewModel\\b""").containsMatchIn(composition)) {
                add("Production composition reaches presentation symbol GameHubViewModel")
            }
        }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Production composition must build runtime dependencies only; presentation belongs to the UI boundary:\n",
                separator = "\n"
            )
        )
    }

    @Test
    fun gameHubPresentationBoundaryDoesNotConstructBlockNinePersistence() {
        val guardedFiles = listOf(
            sourceFile("com/cardenaspiero255/gamehubultra/GameHubUltraApp.kt"),
            sourceFile("com/cardenaspiero255/gamehubultra/ui/GameHubPresentation.kt"),
            sourceFile("com/cardenaspiero255/gamehubultra/ui/GameHubViewModel.kt")
        )
        val forbiddenConstructions = listOf(
            "GameHubPreferencesRepository(",
            "GameSessionStore(",
            "GameSessionLifecycleCoordinator(",
            "StoreLibraryStore(",
            "GameOptimizationMemoryStore("
        )

        val violations = guardedFiles.flatMap { file ->
            val source = file.readText()
            forbiddenConstructions
                .filter(source::contains)
                .map { construction ->
                    "${file.name} constructs Block 9 production dependency $construction"
                }
        }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Block 9 persistence/runtime construction belongs to production composition:\n",
                separator = "\n"
            )
        )
    }

    @Test
    fun productionCompositionOwnsBlockNinePersistenceConstruction() {
        val composition = sourceFile(
            "com/cardenaspiero255/gamehubultra/composition/GameHubProductionComposition.kt"
        ).readText()

        listOf(
            "GameHubPreferencesRepository(appContext)",
            "GameSessionStore(appContext)",
            "StoreLibraryStore(appContext)",
            "GameOptimizationMemoryStore(appContext)"
        ).forEach { construction ->
            assertTrue(
                composition.contains(construction),
                "Production composition must own $construction"
            )
        }
        assertTrue(
            composition.contains("GameSessionLifecycleCoordinatorFactory"),
            "Production composition must own the session coordinator factory"
        )
    }

    @Test
    fun productionCompositionOwnsConcreteUltraImplementations() {
        val composition = sourceFile(
            "com/cardenaspiero255/gamehubultra/composition/GameHubProductionComposition.kt"
        ).readText()

        listOf(
            "UltraProductionQueryExecutor",
            "GeminiNanoLocalAiModelAdapter",
            "UltraConversationMemoryStore"
        ).forEach { concrete ->
            assertTrue(
                composition.contains(concrete),
                "Production composition root must own $concrete"
            )
        }
    }

    private fun containsBlockingSelectionWrite(source: String): Boolean {
        val runBlockingName = Regex("""\brunBlocking\b""")
        val selectionWrite = Regex(
            """selectionRepository\s*\.\s*saveSelected""" +
                """(?:GameAndProfile|Game|Profile)\s*\("""
        )

        var searchFrom = 0
        while (true) {
            val match = runBlockingName.find(source, searchFrom) ?: return false
            var cursor = match.range.last + 1
            cursor = skipWhitespace(source, cursor)

            if (source.getOrNull(cursor) == '(') {
                val closeParenthesis = matchingDelimiterEnd(
                    source = source,
                    openIndex = cursor,
                    openDelimiter = '(',
                    closeDelimiter = ')'
                )
                if (closeParenthesis == null) {
                    searchFrom = match.range.last + 1
                    continue
                }
                cursor = skipWhitespace(source, closeParenthesis + 1)
            }

            if (source.getOrNull(cursor) != '{') {
                searchFrom = match.range.last + 1
                continue
            }

            val closeBrace = matchingDelimiterEnd(
                source = source,
                openIndex = cursor,
                openDelimiter = '{',
                closeDelimiter = '}'
            )
            if (closeBrace == null) {
                searchFrom = match.range.last + 1
                continue
            }

            val body = source.substring(cursor + 1, closeBrace)
            if (selectionWrite.containsMatchIn(body)) return true
            searchFrom = closeBrace + 1
        }
    }

    private fun skipWhitespace(source: String, start: Int): Int {
        var index = start
        while (index < source.length && source[index].isWhitespace()) {
            index += 1
        }
        return index
    }

    private fun matchingDelimiterEnd(
        source: String,
        openIndex: Int,
        openDelimiter: Char,
        closeDelimiter: Char
    ): Int? {
        var depth = 0
        var index = openIndex
        var inString = false
        var inChar = false
        var inTripleString = false
        var inLineComment = false
        var inBlockComment = false
        var escaped = false

        while (index < source.length) {
            val char = source[index]
            val next = source.getOrNull(index + 1)

            when {
                inLineComment -> {
                    if (char == '\n') inLineComment = false
                    index += 1
                }
                inBlockComment -> {
                    if (char == '*' && next == '/') {
                        inBlockComment = false
                        index += 2
                    } else {
                        index += 1
                    }
                }
                inTripleString -> {
                    if (source.startsWith("\"\"\"", index)) {
                        inTripleString = false
                        index += 3
                    } else {
                        index += 1
                    }
                }
                inString -> {
                    when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == '"' -> inString = false
                    }
                    index += 1
                }
                inChar -> {
                    when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == '\'' -> inChar = false
                    }
                    index += 1
                }
                source.startsWith("//", index) -> {
                    inLineComment = true
                    index += 2
                }
                source.startsWith("/*", index) -> {
                    inBlockComment = true
                    index += 2
                }
                source.startsWith("\"\"\"", index) -> {
                    inTripleString = true
                    index += 3
                }
                char == '"' -> {
                    inString = true
                    index += 1
                }
                char == '\'' -> {
                    inChar = true
                    index += 1
                }
                char == openDelimiter -> {
                    depth += 1
                    index += 1
                }
                char == closeDelimiter -> {
                    depth -= 1
                    if (depth == 0) return index
                    index += 1
                }
                else -> index += 1
            }
        }
        return null
    }

    private fun sourceFile(relativePath: String): File {
        val root = sourceRoot()
        val file = File(root, relativePath)
        assertTrue(file.isFile, "Expected source file does not exist: $relativePath")
        return file
    }

    private fun sourceDirectory(relativePath: String): File {
        val root = sourceRoot()
        val directory = File(root, relativePath)
        assertTrue(directory.isDirectory, "Expected source directory does not exist: $relativePath")
        return directory
    }

    private fun sourceRoot(): File =
        sequenceOf(
            File("src/main/java"),
            File("app/src/main/java")
        ).firstOrNull(File::isDirectory)
            ?: error("Main source root must be available to architecture tests")
    @Test
    fun gameHubUltraAppDoesNotOwnSessionSharingInfrastructure() {
        val source = sourceFile(
            "com/cardenaspiero255/gamehubultra/GameHubUltraApp.kt"
        ).readText()

        val forbiddenSymbols = listOf(
            "fun shareSessionHistory(",
            "fun sharePerformanceTimeline("
        )
        val violations = forbiddenSymbols.filter(source::contains)

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Session sharing infrastructure belongs behind the presentation boundary:\n",
                separator = "\n"
            )
        )
    }

    @Test
    fun gameHubUltraAppDoesNotOwnLibraryScreen() {
        val app = sourceFile(
            "com/cardenaspiero255/gamehubultra/GameHubUltraApp.kt"
        ).readText()
        val libraryScreen = sourceFile(
            "com/cardenaspiero255/gamehubultra/ui/library/LibraryScreen.kt"
        ).readText()

        assertTrue(
            !app.contains("fun LibraryScreen("),
            "LibraryScreen belongs to the ui.library presentation boundary"
        )
        assertTrue(
            libraryScreen.contains("internal fun LibraryScreen("),
            "The Library presentation boundary must own LibraryScreen"
        )
    }

    @Test
    fun gameHubUltraAppDoesNotOwnHomeScreen() {
        val app = sourceFile(
            "com/cardenaspiero255/gamehubultra/GameHubUltraApp.kt"
        ).readText()
        val homeScreen = sourceFile(
            "com/cardenaspiero255/gamehubultra/ui/home/HomeScreen.kt"
        ).readText()

        assertTrue(
            !app.contains("fun HomeScreen("),
            "HomeScreen belongs to the ui.home presentation boundary"
        )
        assertTrue(
            homeScreen.contains("internal fun HomeScreen("),
            "The Home presentation boundary must own HomeScreen"
        )
    }

    @Test
    fun gameHubUltraAppDoesNotOwnVoiceAssistantCard() {
        val app = sourceFile(
            "com/cardenaspiero255/gamehubultra/GameHubUltraApp.kt"
        ).readText()
        val voiceCard = sourceFile(
            "com/cardenaspiero255/gamehubultra/ui/voice/VoiceAssistantCard.kt"
        ).readText()

        assertTrue(
            !app.contains("fun VoiceAssistantCard("),
            "VoiceAssistantCard belongs to the ui.voice presentation boundary"
        )
        assertTrue(
            voiceCard.contains("internal fun VoiceAssistantCard("),
            "The voice presentation boundary must own VoiceAssistantCard"
        )
    }

    @Test
    fun ultraWakeServiceDoesNotOwnForegroundNotificationInfrastructure() {
        val service = sourceFile(
            "com/cardenaspiero255/gamehubultra/voice/UltraWakeService.kt"
        ).readText()
        val foreground = sourceFile(
            "com/cardenaspiero255/gamehubultra/voice/UltraWakeForegroundController.kt"
        ).readText()

        assertTrue(
            !service.contains("NotificationChannel(") &&
                !service.contains("NotificationCompat.Builder("),
            "UltraWakeService must delegate foreground notification infrastructure"
        )
        assertTrue(
            foreground.contains("internal class UltraWakeForegroundController"),
            "The voice infrastructure boundary must own foreground notification setup"
        )
    }


    @Test
    fun ultraWakeServiceDoesNotOwnSpeechPlaybackWatchdog() {
        val service = sourceFile(
            "com/cardenaspiero255/gamehubultra/voice/UltraWakeService.kt"
        ).readText()
        val playback = sourceFile(
            "com/cardenaspiero255/gamehubultra/voice/UltraWakeSpeechPlaybackController.kt"
        ).readText()

        assertTrue(
            !service.contains("COMMAND_SPEECH_TIMEOUT_MS") &&
                !service.contains("checkSpeechWatchdog("),
            "UltraWakeService must delegate TTS playback watchdog infrastructure"
        )
        assertTrue(
            playback.contains("internal class UltraWakeSpeechPlaybackController"),
            "The voice infrastructure boundary must own TTS playback watchdog behavior"
        )
    }


    @Test
    fun ultraWakeServiceDoesNotOwnRecognizerIntentConfiguration() {
        val service = sourceFile(
            "com/cardenaspiero255/gamehubultra/voice/UltraWakeService.kt"
        ).readText()
        val factory = sourceFile(
            "com/cardenaspiero255/gamehubultra/voice/UltraWakeRecognitionIntentFactory.kt"
        ).readText()

        assertTrue(
            !service.contains("private fun baseRecognitionIntent()"),
            "UltraWakeService must delegate Android recognizer intent configuration"
        )
        assertTrue(
            factory.contains("internal object UltraWakeRecognitionIntentFactory"),
            "The voice infrastructure boundary must own recognizer intent configuration"
        )
    }


    @Test
    fun ultraWakeServiceDoesNotOwnCommandRuntime() {
        val service = sourceFile(
            "com/cardenaspiero255/gamehubultra/voice/UltraWakeService.kt"
        ).readText()
        val runtime = sourceFile(
            "com/cardenaspiero255/gamehubultra/voice/UltraWakeCommandRuntime.kt"
        ).readText()

        assertTrue(
            !service.contains("UltraUnifiedAgentRouter.route(") &&
                !service.contains("VoiceCommandEngine.execute("),
            "UltraWakeService must delegate Ultra command execution runtime"
        )
        assertTrue(
            runtime.contains("internal class UltraWakeCommandRuntime"),
            "The voice domain boundary must own Ultra command execution"
        )
    }


    @Test
    fun ownershipContractsDoNotCarryCompletedMigrationPromises() {
        val contracts = sourceFile(
            "com/cardenaspiero255/gamehubultra/data/GameStateOwnershipContracts.kt"
        ).readText()

        assertTrue(
            !contracts.contains("later Block 8 cut"),
            "Completed architecture migrations must not remain documented as future work"
        )
    }


    @Test
    fun ultraImplementationHelpersStayModuleInternal() {
        val declarations = mapOf(
            "com/cardenaspiero255/gamehubultra/UltraAssistantQueryRunner.kt" to listOf(
                "internal sealed interface UltraAssistantQuerySubmission",
                "internal class UltraAssistantQueryRunner"
            ),
            "com/cardenaspiero255/gamehubultra/UltraCommandUiEffectPolicy.kt" to listOf(
                "internal data class UltraScopedProfileRecommendation",
                "internal object UltraCommandUiEffectPolicy"
            ),
            "com/cardenaspiero255/gamehubultra/UltraSessionScopeSelection.kt" to listOf(
                "internal data class UltraVoiceTurnScope",
                "internal fun captureUltraVoiceTurnScope",
                "internal fun isUltraVoiceTurnScopeCurrent"
            ),
            "com/cardenaspiero255/gamehubultra/UltraVoiceResultPublisher.kt" to listOf(
                "internal object UltraVoiceResultPublisher"
            )
        )

        val violations = declarations.flatMap { (path, expectedDeclarations) ->
            val source = sourceFile(path).readText()
            expectedDeclarations
                .filterNot(source::contains)
                .map { declaration -> "$path must keep $declaration module-internal" }
        }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Ultra implementation helpers must not expand the public API:\n",
                separator = "\n"
            )
        )
    }


}
