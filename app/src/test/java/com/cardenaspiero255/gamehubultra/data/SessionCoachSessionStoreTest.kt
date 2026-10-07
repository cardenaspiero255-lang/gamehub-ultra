package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.domain.SessionCoachMessage
import com.cardenaspiero255.gamehubultra.domain.SessionCoachPriority
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSignal
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionCoachSessionStoreTest {
    private val context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences(
            "gamehub_ultra_session_coach",
            android.content.Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @Test
    fun snapshotCodecRoundTripsAndSanitizesInvalidOptionalMetrics() {
        val valid = SessionCoachSnapshot(
            timestampMillis = 1_234L,
            batteryPercent = 72,
            thermalStatus = 3,
            thermalHeadroom = 0.82f,
            refreshRateHz = 120f,
            latencyMs = 42L,
            memoryUsedPercent = 67,
            batteryCharging = true,
            powerSaveMode = true
        )

        assertEquals(valid, SessionCoachSnapshotCodec.decode(SessionCoachSnapshotCodec.encode(valid)))

        val invalid = SessionCoachSnapshotCodec.decode(
            "99|150|2|NaN|-1|-5|150"
        )
        assertEquals(99L, invalid?.timestampMillis)
        assertNull(invalid?.batteryPercent)
        assertEquals(2, invalid?.thermalStatus)
        assertNull(invalid?.thermalHeadroom)
        assertNull(invalid?.refreshRateHz)
        assertNull(invalid?.latencyMs)
        assertNull(invalid?.memoryUsedPercent)

        val legacy = SessionCoachSnapshotCodec.decode("100|80|1|0.2|120|30")
        assertEquals(100L, legacy?.timestampMillis)
        assertEquals(80, legacy?.batteryPercent)
        assertNull(legacy?.memoryUsedPercent)
        assertNull(legacy?.batteryCharging)
        assertNull(legacy?.powerSaveMode)

        assertNull(SessionCoachSnapshotCodec.decode("broken"))
        assertNull(SessionCoachSnapshotCodec.decode("bad|||||"))
    }

    @Test
    fun messageCodecRoundTripsActionAndNullableAction() {
        val withAction = SessionCoachMessage(
            signal = SessionCoachSignal.THERMAL,
            priority = SessionCoachPriority.ACTION,
            title = "Cambio térmico",
            detail = "Presión térmica elevada.",
            action = "Usa un perfil menos exigente."
        )
        assertEquals(
            withAction,
            SessionCoachMessageCodec.decode(SessionCoachMessageCodec.encode(withAction))
        )

        val withoutAction = withAction.copy(
            signal = SessionCoachSignal.GENERAL,
            priority = SessionCoachPriority.INFO,
            action = null
        )
        assertEquals(
            withoutAction,
            SessionCoachMessageCodec.decode(SessionCoachMessageCodec.encode(withoutAction))
        )

        assertNull(SessionCoachMessageCodec.decode(null))
        assertNull(SessionCoachMessageCodec.decode(""))
        assertNull(SessionCoachMessageCodec.decode("BAD|INFO|a|b|c"))
        assertNull(SessionCoachMessageCodec.decode("GENERAL|BAD|a|b|c"))
        assertNull(SessionCoachMessageCodec.decode("GENERAL|INFO|%%%|%%%|%%%"))
    }

    @Test
    fun sessionLifecyclePersistsPreMidAndPostData() {
        val store = SessionCoachSessionStore(context, maxSamples = 4)
        assertFalse(store.hasActiveSession())
        assertFalse(store.beginSession("", "game.a", 1L))
        assertFalse(store.beginSession("id", "", 1L))

        assertTrue(store.beginSession("session-1", "game.a", 1_000L, gameVersion = "1.2.3"))
        assertTrue(store.hasActiveSession())

        val pre = SessionCoachMessage(
            signal = SessionCoachSignal.GENERAL,
            priority = SessionCoachPriority.INFO,
            title = "Preparación 90/100",
            detail = "Listo",
            action = null
        )
        val mid = SessionCoachMessage(
            signal = SessionCoachSignal.LATENCY,
            priority = SessionCoachPriority.ACTION,
            title = "Pico de latencia",
            detail = "30 ms a 150 ms",
            action = "Revisa la red."
        )
        val lowPriority = mid.copy(
            signal = SessionCoachSignal.BATTERY,
            priority = SessionCoachPriority.WATCH,
            title = "Batería"
        )

        assertFalse(store.recordPreSession("other", pre))
        assertTrue(store.recordPreSession("session-1", pre))

        val first = snapshot(1_000L, 90)
        val second = snapshot(2_000L, 85)
        assertNull(store.appendSnapshot("session-1", first))
        assertEquals(first, store.appendSnapshot("session-1", second, listOf(lowPriority, mid)))
        assertNull(store.appendSnapshot("other", snapshot(3_000L, 80)))

        val active = store.readActiveSession()
        assertEquals("session-1", active?.sessionId)
        assertEquals("game.a", active?.packageName)
        assertEquals("1.2.3", active?.gameVersion)
        assertEquals(pre, active?.preSessionMessage)
        assertEquals(mid, active?.latestObservation)
        assertEquals(listOf(first, second), active?.samples)

        val finished = store.finishActiveSession(500L)
        assertEquals(1_000L, finished?.endedAtMillis)
        assertFalse(store.hasActiveSession())
        assertNull(store.readActiveSession())

        val completed = store.readLastCompletedSession()
        assertEquals("session-1", completed?.sessionId)
        assertEquals("1.2.3", completed?.gameVersion)
        assertEquals(1_000L, completed?.endedAtMillis)
        assertEquals(mid, completed?.latestObservation)
        assertEquals(2, completed?.samples?.size)
        assertNull(store.finishActiveSession(9_999L))
        assertFalse(store.discardActiveSession())
    }

    @Test
    fun sampleHistoryIsBoundedAndNewSessionReplacesOldActiveSession() {
        val store = SessionCoachSessionStore(context, maxSamples = 2)
        assertTrue(store.beginSession("a", "game.a", 1L))
        store.appendSnapshot("a", snapshot(1L, 90))
        store.appendSnapshot("a", snapshot(2L, 80))
        store.appendSnapshot("a", snapshot(3L, 70))

        assertEquals(
            listOf(2L, 3L),
            store.readActiveSession()?.samples?.map { it.timestampMillis }
        )

        assertTrue(store.beginSession("b", "game.b", 10L))
        val active = store.readActiveSession()
        assertEquals("b", active?.sessionId)
        assertEquals("game.b", active?.packageName)
        assertTrue(active?.samples.orEmpty().isEmpty())
        assertNull(active?.preSessionMessage)
        assertNull(active?.latestObservation)

        assertTrue(store.discardActiveSession())
        assertFalse(store.hasActiveSession())
    }

    private fun snapshot(timestamp: Long, battery: Int) =
        SessionCoachSnapshot(
            timestampMillis = timestamp,
            batteryPercent = battery,
            thermalStatus = 1,
            thermalHeadroom = 0.25f,
            refreshRateHz = 120f,
            latencyMs = 30L,
            memoryUsedPercent = 55
        )
    @Test
    fun predictiveThermalObservationSurvivesUnrelatedLatestObservation() {
        val store = SessionCoachSessionStore(context)
        assertTrue(store.beginSession("thermal-state", "game.test", 0L))

        val warning = SessionCoachMessage(
            signal = SessionCoachSignal.THERMAL,
            priority = SessionCoachPriority.ACTION,
            title = "Riesgo térmico previsto",
            detail = "Predicción preventiva.",
            action = "Considera un perfil menos exigente."
        )
        val latency = SessionCoachMessage(
            signal = SessionCoachSignal.LATENCY,
            priority = SessionCoachPriority.INFO,
            title = "Latencia estable",
            detail = "La red se mantiene estable.",
            action = null
        )

        store.appendSnapshot(
            sessionId = "thermal-state",
            snapshot = snapshot(10_000L, 80),
            observations = listOf(warning),
            thermalPredictionObservation = warning
        )
        store.appendSnapshot(
            sessionId = "thermal-state",
            snapshot = snapshot(20_000L, 80),
            observations = listOf(latency)
        )

        val active = assertNotNull(store.readActiveSession())
        assertEquals(latency, active.latestObservation)
        assertEquals(warning, active.latestThermalPredictionObservation)

        val finished = assertNotNull(
            store.finishActiveSession(
                endedAtMillis = 30_000L,
                expectedSessionId = "thermal-state"
            )
        )
        assertEquals(warning, finished.latestThermalPredictionObservation)
        assertEquals(
            warning,
            assertNotNull(store.readLastCompletedSession()).latestThermalPredictionObservation
        )
    }

}
