package com.cardenaspiero255.gamehubultra.voice

import org.junit.Test
import org.junit.runner.RunWith
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
