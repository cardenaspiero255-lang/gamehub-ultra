package com.cardenaspiero255.gamehubultra.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cardenaspiero255.gamehubultra.composition.GameHubProductionComposition
import com.cardenaspiero255.gamehubultra.data.SessionCoachSessionStore
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.SessionCoachMessage
import com.cardenaspiero255.gamehubultra.domain.SessionCoachPriority
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSignal
import com.cardenaspiero255.gamehubultra.domain.ThermalPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Applied only after the user taps the notification action. The in-app,
 * non-exported receiver also checks a persisted pending proposal, preventing
 * arbitrary notifications from changing a game profile.
 */
/**
 * Persist only after a real pending notification approval.
 * This suspend boundary is exercised directly in Robolectric tests; the
 * BroadcastReceiver separately supplies the asynchronous Android lifecycle.
 * Repeated or forged requests cannot create a new approval.
 */
internal suspend fun applyPendingNightProfile(context: Context, game: String): Boolean {
    val target = game.trim()
    if (target.isBlank()) return false
    val app = context.applicationContext
    val store = SessionCoachSessionStore(app)
    if (!store.isNightProfileProposalPending(target)) return false
    val repository = GameHubProductionComposition.selectionRepository(app)
    val previous = repository.gameProfileConfigFlow(target).first()
        ?: GameProfileConfig()
    val proposed = previous.copy(
        performanceProfile = PerformanceProfile.BALANCED,
        thermalPreference = ThermalPreference.COOLER
    )
    repository.saveGameProfileConfig(target, proposed)
    return store.markNightProfileProposalApplied(target)
}

class NightProfileApprovalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_APPLY) return
        val game = intent.getStringExtra(EXTRA_PACKAGE)?.trim().orEmpty()
        val app = context.applicationContext
        val store = SessionCoachSessionStore(app)
        if (game.isBlank() || !store.isNightProfileProposalPending(game)) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // The user opted in to safer balanced/cooler settings. Do not
                // set a refresh-rate target without device capability proof.
                if (applyPendingNightProfile(app, game)) {
                    SessionCoachNotifications.postAction(
                        app,
                        SessionCoachMessage(
                            signal = SessionCoachSignal.GENERAL,
                            priority = SessionCoachPriority.INFO,
                            title = "Propuesta Noche aplicada",
                            detail = "Perfil equilibrado y preferencia térmica fría guardados para el juego.",
                            action = "Puedes editar los ajustes del juego cuando quieras."
                        )
                    )
                }
            } catch (_: Exception) {
                SessionCoachNotifications.postAction(
                    app,
                    SessionCoachMessage(
                        signal = SessionCoachSignal.GENERAL,
                        priority = SessionCoachPriority.WATCH,
                        title = "No pude guardar la propuesta Noche",
                        detail = "No se guardaron los nuevos ajustes. Prueba desde GameHub Ultra."
                    )
                )
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_APPLY =
            "com.cardenaspiero255.gamehubultra.session.NIGHT_PROFILE_APPLY"
        const val EXTRA_PACKAGE = "night_profile_package"
    }
}
