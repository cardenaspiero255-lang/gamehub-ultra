package com.cardenaspiero255.gamehubultra.voice

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UltraF9AssistantNamePersistenceTest {
    @Test
    fun nameIsUltraByDefaultAndSurvivesRepositoryRecreation() {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("ultra_assistant_identity_v1", 0).edit().clear().commit()
        val first = UltraAssistantNameStore(ctx)
        assertEquals("ultra", first.currentName())
        assertTrue(first.saveName("roberto"))
        val afterProcessRecreation = UltraAssistantNameStore(ctx)
        assertEquals("roberto", afterProcessRecreation.currentName())
        assertTrue(afterProcessRecreation.saveName("ultra"))
        assertEquals("ultra", UltraAssistantNameStore(ctx).currentName())
    }

    @Test
    fun actualContinuousServiceUsesSavedNameAndRejectsOtherSpeakersNames() {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("ultra_assistant_identity_v1", 0).edit().clear().commit()
        val store = UltraAssistantNameStore(ctx)
        assertTrue(store.saveName("roberto"))
        val controller = Robolectric.buildService(UltraWakeService::class.java)
        try {
            val service = controller.get()
            val contains = UltraWakeService::class.java.getDeclaredMethod(
                "containsWakeWord", String::class.java
            ).apply { isAccessible = true }
            assertTrue(contains.invoke(service, "Roberto, abre BS") as Boolean)
            assertTrue(contains.invoke(service, "Ultra, abre BS") as Boolean)
            assertFalse(contains.invoke(service, "Mario, abre BS") as Boolean)
            assertFalse(contains.invoke(service, "Por qué Roberto abre BS") as Boolean)
            assertTrue(store.saveName("ultra"))
            assertFalse(contains.invoke(service, "Roberto, abre BS") as Boolean)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun invalidNamesCannotOverwriteCurrentPersistedName() {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("ultra_assistant_identity_v1", 0).edit().clear().commit()
        val store = UltraAssistantNameStore(ctx)
        assertTrue(store.saveName("roberto"))
        assertFalse(store.saveName("abre"))
        assertFalse(store.saveName("x4"))
        assertFalse(store.saveName(" "))
        assertEquals("roberto", store.currentName())
    }
}
