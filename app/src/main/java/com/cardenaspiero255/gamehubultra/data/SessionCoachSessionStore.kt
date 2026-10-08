package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import com.cardenaspiero255.gamehubultra.domain.SessionCoachMessage
import com.cardenaspiero255.gamehubultra.domain.SessionCoachPriority
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSignal
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import com.cardenaspiero255.gamehubultra.domain.UltraRecordedGameSession
import com.cardenaspiero255.gamehubultra.domain.UltraSessionHistoryIntelligence
import java.nio.charset.StandardCharsets
import java.util.Base64

data class SessionCoachStoredSession(
    val sessionId: String,
    val packageName: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val samples: List<SessionCoachSnapshot>,
    val preSessionMessage: SessionCoachMessage?,
    val latestObservation: SessionCoachMessage?,
    val gameVersion: String? = null,
    val latestThermalPredictionObservation: SessionCoachMessage? = null
)

internal object SessionCoachSnapshotCodec {
    fun encode(snapshot: SessionCoachSnapshot): String =
        listOf(
            snapshot.timestampMillis.toString(),
            snapshot.batteryPercent?.toString().orEmpty(),
            snapshot.thermalStatus?.toString().orEmpty(),
            snapshot.thermalHeadroom
                ?.takeIf { it.isFinite() }
                ?.toString()
                .orEmpty(),
            snapshot.refreshRateHz
                ?.takeIf { it.isFinite() }
                ?.toString()
                .orEmpty(),
            snapshot.latencyMs?.toString().orEmpty(),
            snapshot.memoryUsedPercent
                ?.takeIf { it in 0..100 }
                ?.toString()
                .orEmpty(),
            snapshot.batteryCharging?.toString().orEmpty(),
            snapshot.powerSaveMode?.toString().orEmpty()
        ).joinToString("|")

    fun decode(raw: String): SessionCoachSnapshot? {
        val fields = raw.split("|")
        if (fields.size !in 6..9) return null

        val timestamp = fields[0].toLongOrNull() ?: return null
        val battery = fields[1].takeIf(String::isNotBlank)
            ?.toIntOrNull()
            ?.takeIf { it in 0..100 }
        val thermal = fields[2].takeIf(String::isNotBlank)?.toIntOrNull()
        val headroom = fields[3].takeIf(String::isNotBlank)
            ?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it >= 0f }
        val refresh = fields[4].takeIf(String::isNotBlank)
            ?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it > 0f }
        val latency = fields[5].takeIf(String::isNotBlank)
            ?.toLongOrNull()
            ?.takeIf { it >= 0L }
        val memoryUsedPercent = fields.getOrNull(6)
            ?.takeIf(String::isNotBlank)
            ?.toIntOrNull()
            ?.takeIf { it in 0..100 }
        val batteryCharging = fields.getOrNull(7)
            ?.takeIf(String::isNotBlank)
            ?.toBooleanStrictOrNull()
        val powerSaveMode = fields.getOrNull(8)
            ?.takeIf(String::isNotBlank)
            ?.toBooleanStrictOrNull()

        return SessionCoachSnapshot(
            timestampMillis = timestamp,
            batteryPercent = battery,
            thermalStatus = thermal,
            thermalHeadroom = headroom,
            refreshRateHz = refresh,
            latencyMs = latency,
            memoryUsedPercent = memoryUsedPercent,
            batteryCharging = batteryCharging,
            powerSaveMode = powerSaveMode
        )
    }
}

internal object SessionCoachMessageCodec {
    fun encode(message: SessionCoachMessage): String =
        listOf(
            message.signal.name,
            message.priority.name,
            encodeText(message.title),
            encodeText(message.detail),
            encodeText(message.action.orEmpty())
        ).joinToString("|")

    fun decode(raw: String?): SessionCoachMessage? {
        if (raw.isNullOrBlank()) return null
        val fields = raw.split("|")
        if (fields.size != 5) return null

        val signal = runCatching { SessionCoachSignal.valueOf(fields[0]) }.getOrNull()
            ?: return null
        val priority = runCatching { SessionCoachPriority.valueOf(fields[1]) }.getOrNull()
            ?: return null
        val title = decodeText(fields[2]) ?: return null
        val detail = decodeText(fields[3]) ?: return null
        val action = decodeText(fields[4])?.takeIf(String::isNotBlank)

        return SessionCoachMessage(
            signal = signal,
            priority = priority,
            title = title,
            detail = detail,
            action = action
        )
    }

    private fun encodeText(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String? = runCatching {
        String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
    }.getOrNull()
}


/**
 * Compact archive: measured summary only, not duplicated raw samples.
 * Each finished session is stored at most once, with a bounded local history.
 */
internal object UltraGameSessionHistoryCodec {
    fun encode(fact: UltraRecordedGameSession): String = listOf(
        Base64.getEncoder().encodeToString(
            fact.packageName.toByteArray(StandardCharsets.UTF_8)
        ),
        fact.startedAtMillis.toString(),
        fact.endedAtMillis.toString(),
        fact.measuredSampleCount.toString(),
        fact.typicalRefreshRateHz?.toString().orEmpty(),
        fact.minimumBatteryPercent?.toString().orEmpty(),
        fact.maximumThermalStatus?.toString().orEmpty(),
        fact.playedAtNight.toString()
    ).joinToString("|")

    fun decode(raw: String): UltraRecordedGameSession? {
        val parts = raw.split("|")
        if (parts.size != 8) return null
        return runCatching {
            val game = String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8)
            val start = parts[1].toLong()
            val end = parts[2].toLong()
            val count = parts[3].toInt()
            if (game.isBlank() || start < 0L || end < start || count <= 0) return null
            UltraRecordedGameSession(
                packageName = game,
                startedAtMillis = start,
                endedAtMillis = end,
                measuredSampleCount = count,
                typicalRefreshRateHz = parts[4].toIntOrNull()?.takeIf { it in 30..240 },
                minimumBatteryPercent = parts[5].toIntOrNull()?.takeIf { it in 0..100 },
                maximumThermalStatus = parts[6].toIntOrNull()?.takeIf { it in 0..7 },
                playedAtNight = parts[7].toBooleanStrict()
            )
        }.getOrNull()
    }
}

class SessionCoachSessionStore(
    context: Context,
    private val maxSamples: Int = DEFAULT_MAX_SAMPLES
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun beginSession(
        sessionId: String,
        packageName: String,
        startedAtMillis: Long,
        gameVersion: String? = null
    ): Boolean {
        val cleanId = sessionId.trim()
        val cleanPackage = packageName.trim()
        if (cleanId.isEmpty() || cleanPackage.isEmpty()) return false

        val editor = preferences.edit()
        ACTIVE_KEYS.forEach(editor::remove)
        editor
            .putString(ACTIVE_ID, cleanId)
            .putString(ACTIVE_PACKAGE, cleanPackage)
            .putString(ACTIVE_VERSION, gameVersion?.trim()?.takeIf(String::isNotEmpty))
            .putLong(ACTIVE_STARTED, startedAtMillis.coerceAtLeast(0L))
            .putString(ACTIVE_SAMPLES, "")
            .apply()
        return true
    }

    @Synchronized
    fun recordPreSession(
        sessionId: String,
        message: SessionCoachMessage
    ): Boolean {
        if (preferences.getString(ACTIVE_ID, null) != sessionId) return false
        preferences.edit()
            .putString(ACTIVE_PRE, SessionCoachMessageCodec.encode(message))
            .apply()
        return true
    }

    @Synchronized
    fun appendSnapshot(
        sessionId: String,
        snapshot: SessionCoachSnapshot,
        observations: List<SessionCoachMessage> = emptyList(),
        thermalPredictionObservation: SessionCoachMessage? = null
    ): SessionCoachSnapshot? {
        if (preferences.getString(ACTIVE_ID, null) != sessionId) return null

        val existing = decodeSamples(preferences.getString(ACTIVE_SAMPLES, null))
        val previous = existing.lastOrNull()
        val boundedLimit = maxSamples.coerceIn(1, ABSOLUTE_MAX_SAMPLES)
        val updated = (existing + snapshot).takeLast(boundedLimit)

        val editor = preferences.edit()
            .putString(
                ACTIVE_SAMPLES,
                updated.joinToString("\n", transform = SessionCoachSnapshotCodec::encode)
            )

        observations.maxByOrNull { it.priority.ordinal }?.let { message ->
            editor.putString(
                ACTIVE_LATEST,
                SessionCoachMessageCodec.encode(message)
            )
        }
        thermalPredictionObservation?.let { message ->
            editor.putString(
                ACTIVE_LATEST_THERMAL_PREDICTION,
                SessionCoachMessageCodec.encode(message)
            )
        }
        editor.apply()
        return previous
    }

    @Synchronized
    fun finishActiveSession(
        endedAtMillis: Long,
        expectedSessionId: String? = null
    ): SessionCoachStoredSession? {
        val active = readSession(ACTIVE_PREFIX) ?: return null
        if (expectedSessionId != null && active.sessionId != expectedSessionId) return null
        val finished = active.copy(
            endedAtMillis = maxOf(endedAtMillis, active.startedAtMillis)
        )
        val editor = preferences.edit()
        LAST_KEYS.forEach(editor::remove)
        writeSession(editor, LAST_PREFIX, finished)
        UltraSessionHistoryIntelligence.fromMeasuredSamples(
            packageName = finished.packageName,
            startedAtMillis = finished.startedAtMillis,
            endedAtMillis = checkNotNull(finished.endedAtMillis),
            samples = finished.samples
        )?.let { newFact ->
            val previousFacts = readRecentGameSessions(MAX_HISTORY)
                .filterNot { it.packageName == newFact.packageName &&
                    it.startedAtMillis == newFact.startedAtMillis }
            editor.putString(
                HISTORY_KEY,
                (previousFacts + newFact).sortedBy { it.endedAtMillis }
                    .takeLast(MAX_HISTORY)
                    .joinToString("\n", transform = UltraGameSessionHistoryCodec::encode)
            )
        }
        ACTIVE_KEYS.forEach(editor::remove)
        editor.apply()
        return finished
    }

    @Synchronized
    fun discardActiveSession(expectedSessionId: String? = null): Boolean {
        val activeId = preferences.getString(ACTIVE_ID, null) ?: return false
        if (expectedSessionId != null && activeId != expectedSessionId) return false
        val editor = preferences.edit()
        ACTIVE_KEYS.forEach(editor::remove)
        editor.apply()
        return true
    }

    fun hasActiveSession(): Boolean =
        !preferences.getString(ACTIVE_ID, null).isNullOrBlank()

    fun readActiveSession(): SessionCoachStoredSession? =
        readSession(ACTIVE_PREFIX)

    fun readLastCompletedSession(): SessionCoachStoredSession? =
        readSession(LAST_PREFIX)?.takeIf { it.endedAtMillis != null }

    /** Historical summaries are sourced only from completed measured sessions. */
    fun readRecentGameSessions(
        limit: Int = 24,
        packageName: String? = null
    ): List<UltraRecordedGameSession> =
        preferences.getString(HISTORY_KEY, null).orEmpty()
            .lineSequence()
            .mapNotNull(UltraGameSessionHistoryCodec::decode)
            .filter { packageName == null || it.packageName == packageName }
            .toList()
            .takeLast(limit.coerceIn(1, MAX_HISTORY))

    private fun readSession(prefix: String): SessionCoachStoredSession? {
        val id = preferences.getString(prefix + KEY_ID, null)?.trim().orEmpty()
        val packageName = preferences.getString(prefix + KEY_PACKAGE, null)?.trim().orEmpty()
        if (id.isEmpty() || packageName.isEmpty()) return null
        if (!preferences.contains(prefix + KEY_STARTED)) return null

        return SessionCoachStoredSession(
            sessionId = id,
            packageName = packageName,
            startedAtMillis = preferences.getLong(prefix + KEY_STARTED, 0L),
            endedAtMillis = if (preferences.contains(prefix + KEY_ENDED)) {
                preferences.getLong(prefix + KEY_ENDED, 0L)
            } else {
                null
            },
            samples = decodeSamples(preferences.getString(prefix + KEY_SAMPLES, null)),
            preSessionMessage = SessionCoachMessageCodec.decode(
                preferences.getString(prefix + KEY_PRE, null)
            ),
            latestObservation = SessionCoachMessageCodec.decode(
                preferences.getString(prefix + KEY_LATEST, null)
            ),
            gameVersion = preferences.getString(prefix + KEY_VERSION, null)
                ?.trim()
                ?.takeIf(String::isNotEmpty),
            latestThermalPredictionObservation = SessionCoachMessageCodec.decode(
                preferences.getString(prefix + KEY_LATEST_THERMAL_PREDICTION, null)
            )
        )
    }

    private fun writeSession(
        editor: android.content.SharedPreferences.Editor,
        prefix: String,
        session: SessionCoachStoredSession
    ) {
        editor
            .putString(prefix + KEY_ID, session.sessionId)
            .putString(prefix + KEY_PACKAGE, session.packageName)
            .putString(prefix + KEY_VERSION, session.gameVersion)
            .putLong(prefix + KEY_STARTED, session.startedAtMillis)
            .putString(
                prefix + KEY_SAMPLES,
                session.samples.joinToString(
                    "\n",
                    transform = SessionCoachSnapshotCodec::encode
                )
            )
        session.endedAtMillis?.let { editor.putLong(prefix + KEY_ENDED, it) }
        session.preSessionMessage?.let {
            editor.putString(prefix + KEY_PRE, SessionCoachMessageCodec.encode(it))
        }
        session.latestObservation?.let {
            editor.putString(prefix + KEY_LATEST, SessionCoachMessageCodec.encode(it))
        }
        session.latestThermalPredictionObservation?.let {
            editor.putString(
                prefix + KEY_LATEST_THERMAL_PREDICTION,
                SessionCoachMessageCodec.encode(it)
            )
        }
    }

    private fun decodeSamples(raw: String?): List<SessionCoachSnapshot> =
        raw.orEmpty()
            .lineSequence()
            .filter(String::isNotBlank)
            .mapNotNull(SessionCoachSnapshotCodec::decode)
            .toList()

    private companion object {
        const val PREFERENCES_NAME = "gamehub_ultra_session_coach"
        const val HISTORY_KEY = "completed_session_history_v1"
        const val MAX_HISTORY = 32
        const val ACTIVE_PREFIX = "active_"
        const val LAST_PREFIX = "last_"
        const val KEY_ID = "id"
        const val KEY_PACKAGE = "package"
        const val KEY_VERSION = "version"
        const val KEY_STARTED = "started"
        const val KEY_ENDED = "ended"
        const val KEY_SAMPLES = "samples"
        const val KEY_PRE = "pre"
        const val KEY_LATEST = "latest"
        const val KEY_LATEST_THERMAL_PREDICTION = "latest_thermal_prediction"

        const val ACTIVE_ID = ACTIVE_PREFIX + KEY_ID
        const val ACTIVE_PACKAGE = ACTIVE_PREFIX + KEY_PACKAGE
        const val ACTIVE_VERSION = ACTIVE_PREFIX + KEY_VERSION
        const val ACTIVE_STARTED = ACTIVE_PREFIX + KEY_STARTED
        const val ACTIVE_ENDED = ACTIVE_PREFIX + KEY_ENDED
        const val ACTIVE_SAMPLES = ACTIVE_PREFIX + KEY_SAMPLES
        const val ACTIVE_PRE = ACTIVE_PREFIX + KEY_PRE
        const val ACTIVE_LATEST = ACTIVE_PREFIX + KEY_LATEST
        const val ACTIVE_LATEST_THERMAL_PREDICTION =
            ACTIVE_PREFIX + KEY_LATEST_THERMAL_PREDICTION

        const val LAST_ID = LAST_PREFIX + KEY_ID
        const val LAST_PACKAGE = LAST_PREFIX + KEY_PACKAGE
        const val LAST_VERSION = LAST_PREFIX + KEY_VERSION
        const val LAST_STARTED = LAST_PREFIX + KEY_STARTED
        const val LAST_ENDED = LAST_PREFIX + KEY_ENDED
        const val LAST_SAMPLES = LAST_PREFIX + KEY_SAMPLES
        const val LAST_PRE = LAST_PREFIX + KEY_PRE
        const val LAST_LATEST = LAST_PREFIX + KEY_LATEST
        const val LAST_LATEST_THERMAL_PREDICTION =
            LAST_PREFIX + KEY_LATEST_THERMAL_PREDICTION

        const val DEFAULT_MAX_SAMPLES = 960
        const val ABSOLUTE_MAX_SAMPLES = 1_440

        val ACTIVE_KEYS = listOf(
            ACTIVE_ID,
            ACTIVE_PACKAGE,
            ACTIVE_VERSION,
            ACTIVE_STARTED,
            ACTIVE_ENDED,
            ACTIVE_SAMPLES,
            ACTIVE_PRE,
            ACTIVE_LATEST,
            ACTIVE_LATEST_THERMAL_PREDICTION
        )
        val LAST_KEYS = listOf(
            LAST_ID,
            LAST_PACKAGE,
            LAST_VERSION,
            LAST_STARTED,
            LAST_ENDED,
            LAST_SAMPLES,
            LAST_PRE,
            LAST_LATEST,
            LAST_LATEST_THERMAL_PREDICTION
        )
    }
}
