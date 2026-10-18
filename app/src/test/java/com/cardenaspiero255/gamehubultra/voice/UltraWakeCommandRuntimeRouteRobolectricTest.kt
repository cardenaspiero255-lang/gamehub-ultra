package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.ai.GameHubAiAdvisor
import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoute
import com.cardenaspiero255.gamehubultra.ai.UltraQueryExecutor
import com.cardenaspiero255.gamehubultra.data.GameAliasStateRepository
import com.cardenaspiero255.gamehubultra.data.UltraConversationMemoryStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Executes the real Android voice runtime through its fallback routing path.
 * Earlier pure-policy tests did not execute the runtime's new routing call site.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UltraWakeCommandRuntimeRouteRobolectricTest {
    @Test
    fun generalSpanishQuestionRoutesThroughRuntimeWithoutProfileMutation() {
        val aliases = Mockito.mock(GameAliasStateRepository::class.java)
        Mockito.`when`(aliases.aliases()).thenReturn(emptyMap())
        var routedChat: UltraAgentRoute.Chat? = null
        val executor = object : UltraQueryExecutor {
            override fun answer(
                route: UltraAgentRoute.Chat,
                stableKnowledgeFallback: (() -> String?)?,
                localChat: () -> String
            ): String {
                routedChat = route
                return "Respuesta de prueba en español."
            }
        }
        val runtime = UltraWakeCommandRuntime(
            context = RuntimeEnvironment.getApplication(),
            queryExecutor = executor,
            aiAdvisor = GameHubAiAdvisor(),
            aliasRepository = aliases,
            memoryStore = Mockito.mock(UltraConversationMemoryStore::class.java),
            conversationLedger = UltraVoiceConversationLedger()
        )

        val answer = runtime.execute("Ultra, cuéntame una historia sobre un gato")

        assertEquals("Respuesta de prueba en español.", answer)
        val chat = assertNotNull(routedChat)
        assertTrue(chat.message.contains("gato", ignoreCase = true))
        Mockito.verify(aliases, Mockito.never()).save(
            Mockito.anyString(),
            Mockito.anyString()
        )
    }
}
