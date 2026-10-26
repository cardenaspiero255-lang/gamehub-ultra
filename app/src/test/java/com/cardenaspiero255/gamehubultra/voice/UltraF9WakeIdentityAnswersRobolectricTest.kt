package com.cardenaspiero255.gamehubultra.voice

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
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Exercises actual Android SharedPreferences + live voice-runtime early routing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class UltraF9WakeIdentityAnswersRobolectricTest {
    @Test fun theActualVoiceRuntimeAnswersWithoutCallingAIOrOpeningGames() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("ultra_wake_identity_v1", 0)
        prefs.edit().clear().commit()
        val nameStore = UltraWakeNameStore(context)
        try {
            val runtime = UltraWakeCommandRuntime(
                context = context,
                queryExecutor = Mockito.mock(UltraQueryExecutor::class.java),
                aiAdvisor = Mockito.mock(GameHubAiAdvisor::class.java),
                aliasRepository = Mockito.mock(GameAliasStateRepository::class.java),
                memoryStore = Mockito.mock(UltraConversationMemoryStore::class.java),
                conversationLedger = UltraVoiceConversationLedger()
            )
            assertEquals(
                "Me llamo Ultra. Es mi nombre predeterminado.",
                runtime.execute("Ultra como te llamas")
            )
            assertTrue(nameStore.save("Juan"))
            val spoken = runtime.execute(
                UltraWakeNamePolicy.canonicalizeInvocation("Juan, como te llamas", "juan")
            )
            assertEquals(
                "Me llamo Juan, porque ese es el nombre que me pusiste. Antes me llamaba Ultra.",
                spoken
            )
            assertTrue(nameStore.save("Roberto"))
            assertEquals("juan", UltraWakeNameStore(context).previousName())
            assertEquals(
                "Antes me llamaba Juan. Ahora me llamo Roberto.",
                runtime.execute(
                    UltraWakeNamePolicy.canonicalizeInvocation(
                        "Roberto, como te llamabas antes", "roberto"
                    )
                )
            )
            assertEquals("roberto", UltraWakeNameStore(context).load())
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun repeatedNameAndInvalidUpdatesMustNotOverwriteLastDistinctName() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("ultra_wake_identity_v1", 0)
        prefs.edit().clear().commit()
        try {
            val store = UltraWakeNameStore(context)
            assertTrue(store.save("Juan"))
            assertEquals("ultra", store.previousName())
            assertTrue(store.save("Juan"))
            assertEquals("ultra", UltraWakeNameStore(context).previousName())
            assertFalse(store.save("Activa"))
            assertEquals("ultra", UltraWakeNameStore(context).previousName())
            assertTrue(store.save("Roberto"))
            assertEquals("juan", UltraWakeNameStore(context).previousName())
            assertTrue(store.save("Ultra"))
            assertEquals("roberto", UltraWakeNameStore(context).previousName())
        } finally {
            prefs.edit().clear().commit()
        }
    }
    @Test fun inAppVoiceRenameRunsLocallyBeforeConversationalAI() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("ultra_wake_identity_v1", 0)
        prefs.edit().clear().commit()
        try {
            assertEquals(
                "Entendido. Ahora me puedes llamar juana.",
                UltraWakeLocalIdentityActions.handle(context, "Ultra, desde ahora te llamarás Juana")
            )
            assertEquals("juana", UltraWakeNameStore(context).load())
            assertEquals(
                "Me llamo Juana, porque ese es el nombre que me pusiste. Antes me llamaba Ultra.",
                UltraWakeLocalIdentityActions.handle(context, "Juana, cómo te llamas")
            )
        } finally {
            prefs.edit().clear().commit()
        }
    }


    @Test fun nonIdentityTextStillReachesTheCommandRouterWithoutLosingNumbers() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("ultra_wake_identity_v1", 0)
        prefs.edit().clear().commit()
        try {
            assertTrue(UltraWakeNameStore(context).save("Juana"))
            assertEquals(
                "ultra convierte -2.5 kilómetros a metros",
                UltraWakeLocalIdentityActions.canonicalize(
                    context, "Juana, convierte -2.5 kilómetros a metros"
                )
            )
            assertEquals(
                "abre BS",
                UltraWakeLocalIdentityActions.canonicalize(context, "abre BS")
            )
            assertEquals(
                null,
                UltraWakeLocalIdentityActions.handle(context, "Juana, no cambies tu nombre")
            )
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
