package com.cardenaspiero255.gamehubultra.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RuntimeGameSession(
    val id: String,
    val packageName: String
)

data class SessionEndMetrics(
    val endedAtMillis: Long,
    val endBatteryPercent: Int? = null,
    val endThermalStatus: Int? = null,
    val endRamUsedPercent: Int? = null
)

data class SessionFinishHandle(
    val session: RuntimeGameSession,
    val job: Job
)

/**
 * Owns ordered game-session persistence for a lifecycle scope that outlives UI composition.
 *
 * Runtime state is exposed only after the corresponding start record has been persisted.
 * A private requested session lets an immediate finish safely queue behind a pending start
 * without publishing an unpersisted session to the UI.
 */
class GameSessionLifecycleCoordinator(
    private val store: GameSessionStore,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val queue = SerialMutationQueue(scope, dispatcher)
    private val monitor = Any()
    private var requestedSession: RuntimeGameSession? = null
    private val finishingSessionIds = mutableSetOf<String>()

    private val _runtimeSession = MutableStateFlow<RuntimeGameSession?>(null)
    val runtimeSession: StateFlow<RuntimeGameSession?> = _runtimeSession.asStateFlow()

    fun recoverOrphans(endedAtMillis: Long): Job =
        queue.enqueue {
            store.finishActiveSessions(endedAtMillis)
        }

    fun startSession(record: GameSessionRecord): Job {
        val session = RuntimeGameSession(
            id = record.id,
            packageName = record.packageName
        )
        synchronized(monitor) {
            requestedSession = session
        }

        return queue.enqueue {
            try {
                store.startSession(record)
                val stillRequested = synchronized(monitor) {
                    requestedSession == session
                }
                if (stillRequested) {
                    _runtimeSession.value = session
                }
            } catch (throwable: Throwable) {
                synchronized(monitor) {
                    if (requestedSession == session) {
                        requestedSession = null
                    }
                }
                throw throwable
            }
        }
    }

    fun finishCurrent(metrics: SessionEndMetrics): SessionFinishHandle? {
        val target = synchronized(monitor) {
            val current = requestedSession ?: _runtimeSession.value
            when {
                current == null -> null
                current.id in finishingSessionIds -> null
                else -> {
                    finishingSessionIds += current.id
                    if (requestedSession == current) {
                        requestedSession = null
                    }
                    current
                }
            }
        } ?: return null

        val job = queue.enqueue {
            try {
                store.finishSession(
                    sessionId = target.id,
                    endedAtMillis = metrics.endedAtMillis,
                    endBatteryPercent = metrics.endBatteryPercent,
                    endThermalStatus = metrics.endThermalStatus,
                    endRamUsedPercent = metrics.endRamUsedPercent
                )
                if (_runtimeSession.value?.id == target.id) {
                    _runtimeSession.value = null
                }
            } finally {
                synchronized(monitor) {
                    finishingSessionIds.remove(target.id)
                }
            }
        }
        return SessionFinishHandle(session = target, job = job)
    }

    fun clearSessions(): Job =
        queue.enqueue {
            store.clearSessions()
        }

    suspend fun awaitIdle() {
        queue.awaitIdle()
    }
}
