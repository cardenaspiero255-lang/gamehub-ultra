package com.cardenaspiero255.gamehubultra.voice

import android.speech.RecognizerIntent
import com.cardenaspiero255.gamehubultra.ai.GameHubAiAdvisor
import com.cardenaspiero255.gamehubultra.ai.UltraQueryExecutor
import com.cardenaspiero255.gamehubultra.data.GameAliasStateRepository
import com.cardenaspiero255.gamehubultra.data.UltraConversationMemoryStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class UltraF9WakeNamePersistenceRobolectricTest {
    @Test fun renamedNamePersistsAcrossNewStoreInstancesAndCanBeResetToUltra() {
        val context = RuntimeEnvironment.getApplication()
        val store = UltraWakeNameStore(context)
        try {
            assertTrue(store.save("Ultra"))
            assertEquals("ultra", store.load())
            assertTrue(store.save("Roberto"))
            assertEquals("roberto", UltraWakeNameStore(context).load())
            assertFalse(store.save("activa"))
            assertEquals("roberto", UltraWakeNameStore(context).load())
            assertTrue(store.save("Ultra"))
            assertEquals("ultra", UltraWakeNameStore(context).load())
        } finally {
            store.save("ultra")
        }
    }

    @Test fun realCommandRuntimeSavesRenameWithoutInvokingAi() {
        val context = RuntimeEnvironment.getApplication()
        val store = UltraWakeNameStore(context)
        try {
            store.save("ultra")
            val runtime = UltraWakeCommandRuntime(
                context = context,
                queryExecutor = Mockito.mock(UltraQueryExecutor::class.java),
                aiAdvisor = Mockito.mock(GameHubAiAdvisor::class.java),
                aliasRepository = Mockito.mock(GameAliasStateRepository::class.java),
                memoryStore = Mockito.mock(UltraConversationMemoryStore::class.java),
                conversationLedger = UltraVoiceConversationLedger()
            )
            val reply = runtime.execute("Ultra desde ahora te llamaras Roberto")
            assertTrue(reply.contains("roberto", ignoreCase = true))
            assertEquals("roberto", UltraWakeNameStore(context).load())

            val reverted = runtime.execute("Ultra desde ahora te llamaras Ultra")
            assertTrue(reverted.contains("ultra", ignoreCase = true))
            assertEquals("ultra", UltraWakeNameStore(context).load())
        } finally {
            store.save("ultra")
        }
    }

    @Test fun detachedServiceCannotCrashWhenVoiceNameIsLookedUp() {
        val service = UltraWakeService()
        val check = UltraWakeService::class.java.getDeclaredMethod(
            "containsWakeWord", String::class.java
        ).apply { isAccessible = true }
        assertTrue(check.invoke(service, "Ultra abre BS") as Boolean)
        assertFalse(check.invoke(service, "esto no es una orden") as Boolean)
    }

    @Test fun wakeServiceRenamesImmediatelyWithoutWaitingForTheAiCommandQueue() {
        val context = RuntimeEnvironment.getApplication()
        val store = UltraWakeNameStore(context)
        val controller = Robolectric.buildService(UltraWakeService::class.java)
        val service = controller.get()
        val executor = Mockito.mock(java.util.concurrent.ExecutorService::class.java)
        UltraWakeService::class.java.getDeclaredField("commandExecutor")
            .apply { isAccessible = true }
            .set(service, executor)
        val coordinator = UltraWakeService::class.java.getDeclaredField("commandCoordinator")
            .apply { isAccessible = true }
            .get(service) as UltraWakeCommandCoordinator
        val handle = UltraWakeService::class.java.getDeclaredMethod(
            "handleCommand", String::class.java
        ).apply { isAccessible = true }
        try {
            store.save("ultra")
            assertTrue(coordinator.tryBeginCommand())
            handle.invoke(service, "Ultra, desde ahora te llamarás Roberto")
            assertEquals("roberto", store.load())
            Mockito.verifyNoInteractions(executor)
        } finally {
            store.save("ultra")
        }
    }

    @Test fun asrBiasContainsCustomNameAndOriginalUltra() {
        val intent = UltraWakeRecognitionIntentFactory.create(
            languageTag = "es-CL",
            mode = UltraWakeRecognitionMode.LEGACY_RESTARTING,
            persistentSource = null,
            wakeName = "roberto"
        )
        val hints = intent.getStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS).orEmpty()
        assertTrue("Ultra" in hints)
        assertTrue("roberto" in hints)
        assertTrue("Ultra desde ahora te llamarás" in hints)
        assertTrue("roberto desde ahora te llamarás" in hints)
        assertTrue("roberto deja de hablar" in hints)
    }
}
