package com.cardenaspiero255.gamehubultra.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureBoundaryGuardTest {

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
    fun gameHubUltraAppMonolithHasStrictSizeCeiling() {
        val app = sourceFile("com/cardenaspiero255/gamehubultra/GameHubUltraApp.kt")
        val lineCount = app.readLines().size

        assertTrue(
            lineCount <= 2600,
            "GameHubUltraApp.kt must keep shrinking during weakness block 6; " +
                "current line count: $lineCount, ceiling: 2600"
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
            "GameHubViewModel",
            "@Composable"
        )
        val violations = forbiddenPresentationSymbols
            .filter(composition::contains)
            .map { symbol -> "Production composition reaches presentation symbol $symbol" }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Production composition must build runtime dependencies only; presentation belongs to the UI boundary:\n",
                separator = "\n"
            )
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
}
