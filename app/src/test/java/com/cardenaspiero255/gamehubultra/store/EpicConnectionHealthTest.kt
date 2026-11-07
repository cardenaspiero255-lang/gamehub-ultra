package com.cardenaspiero255.gamehubultra.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Epic cache age is a prompt to refresh, never a claim of live session authentication. */
class EpicConnectionHealthTest {
    private val now = 2_000_000_000_000L
    private val month = 30L * 24 * 60 * 60 * 1000

    @Test fun noCachedMetadataMeansNoLocalEpicLibrary() {
        assertEquals(
            "Epic: sin biblioteca local; inicia sesión para sincronizar.",
            EpicConnectionHealth.statusText(null, false, now)
        )
    }

    @Test fun recentCacheIsNotProofOfLiveEpicAuthentication() {
        assertEquals(
            "Epic: biblioteca local en caché; sesión no verificada.",
            EpicConnectionHealth.statusText(now - 1000, true, now)
        )
    }

    @Test fun staleCachePromptsReauthenticationAndPreservesMetadata() {
        assertEquals(
            "Epic: caché local antigua; vuelve a iniciar sesión para actualizar.",
            EpicConnectionHealth.statusText(now - month - 1, true, now)
        )
    }

    @Test fun staleCacheBoundaryIsNotMisclassifiedEarly() {
        assertEquals(
            "Epic: biblioteca local en caché; sesión no verificada.",
            EpicConnectionHealth.statusText(now - month, true, now)
        )
    }

    @Test fun invalidOrFutureSyncDatesNeverPretendEpicIsConnected() {
        for (time in listOf(null, -1L, 0L, now + 1000)) {
            val status = EpicConnectionHealth.statusText(time, true, now)
            assertEquals("Epic: biblioteca local en caché; sesión no verificada.", status)
            assertFalse(status.contains("CONECTADO"))
        }
    }
}
