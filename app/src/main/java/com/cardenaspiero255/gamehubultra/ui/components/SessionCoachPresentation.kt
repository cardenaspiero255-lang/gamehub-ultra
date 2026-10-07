package com.cardenaspiero255.gamehubultra.ui.components

import com.cardenaspiero255.gamehubultra.domain.SessionCoachMessage
import com.cardenaspiero255.gamehubultra.domain.SessionCoachPostSessionReport
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot

internal object SessionCoachPresentation {
    fun lines(
        preSession: SessionCoachMessage?,
        liveSamples: List<SessionCoachSnapshot>,
        observations: List<SessionCoachMessage>,
        postSession: SessionCoachPostSessionReport?,
        sessionActive: Boolean,
        activeSessionText: String,
        stableText: String
    ): List<String> = buildList {
        if (sessionActive) {
            add(activeSessionText)
            if (observations.isEmpty()) {
                add(stableText)
            } else {
                observations.takeLast(3).forEach { observation ->
                    add("• ${observation.title}: ${observation.detail}")
                    observation.action?.let(::add)
                }
            }
        } else {
            preSession?.let { message ->
                add(message.title)
                add(message.detail)
                message.action?.let(::add)
            }
            observations.takeLast(1).forEach { observation ->
                add("• ${observation.title}: ${observation.detail}")
                observation.action?.let(::add)
            }
        }

        postSession?.let { report ->
            add(report.summary)
            report.nextSteps.take(3).forEach { add("• $it") }
        }
    }
}
