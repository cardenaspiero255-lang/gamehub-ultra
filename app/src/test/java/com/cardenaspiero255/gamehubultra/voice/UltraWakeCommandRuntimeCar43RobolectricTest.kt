package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.ai.GameHubAiAdvisor
import com.cardenaspiero255.gamehubultra.ai.UltraQueryExecutor
import com.cardenaspiero255.gamehubultra.composition.GameHubProductionComposition
import com.cardenaspiero255.gamehubultra.data.GameAliasStateRepository
import com.cardenaspiero255.gamehubultra.data.UltraConversationMemoryStore
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UltraWakeCommandRuntimeCar43RobolectricTest {
    @Test
    fun `active session voice metrics use enriched CAR43 AI context path`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val selectionRepository =
            GameHubProductionComposition.selectionRepository(context.applicationContext)
        selectionRepository.saveSelectedGameAndProfile(
            context.packageName,
            PerformanceProfile.BALANCED
        )
        val runtime = UltraWakeCommandRuntime(
            context = context,
            queryExecutor = Mockito.mock(UltraQueryExecutor::class.java),
            aiAdvisor = Mockito.mock(GameHubAiAdvisor::class.java),
            aliasRepository = Mockito.mock(GameAliasStateRepository::class.java),
            memoryStore = Mockito.mock(UltraConversationMemoryStore::class.java),
            conversationLedger = UltraVoiceConversationLedger()
        )

        val answer = runtime.execute("Ultra, batería")

        assertTrue(answer.contains("batería", ignoreCase = true))
    }
}
