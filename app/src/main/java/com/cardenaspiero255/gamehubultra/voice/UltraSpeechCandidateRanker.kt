package com.cardenaspiero255.gamehubultra.voice

/**
 * Selects the most useful speech-recognition candidate without assuming that
 * the first ASR alternative is always the best one.
 *
 * A caller may provide a semantic preference (for example the Ultra wake word).
 * Within the preferred group, Android confidence scores are used when present.
 * Invalid/unknown scores fall back deterministically to recognizer order.
 */
object UltraSpeechCandidateRanker {

    fun select(
        alternatives: List<String>,
        confidenceScores: FloatArray? = null,
        prefer: ((String) -> Boolean)? = null
    ): String? {
        val candidates = alternatives
            .mapIndexedNotNull { index, raw ->
                val text = raw.trim()
                if (text.isBlank()) {
                    null
                } else {
                    Candidate(
                        index = index,
                        text = text,
                        confidence = confidenceScores
                            ?.getOrNull(index)
                            ?.takeIf { it.isFinite() && it >= 0f }
                    )
                }
            }

        if (candidates.isEmpty()) return null

        val preferred = if (prefer == null) {
            emptyList()
        } else {
            candidates.filter { prefer(it.text) }
        }

        val ranked = if (preferred.isNotEmpty()) preferred else candidates
        val commandBearing = if (prefer == null || preferred.isEmpty()) {
            emptyList()
        } else {
            ranked.filter { candidate ->
                candidate.text
                    .replace(Regex("""(?i)^\s*(?:hey\s+)?(?:gamehub\s+)?ultra\b[\s,.:;!?-]*"""), "")
                    .isNotBlank()
            }
        }

        return bestCandidate(
            if (commandBearing.isNotEmpty()) commandBearing else ranked
        ).text
    }

    private fun bestCandidate(candidates: List<Candidate>): Candidate {
        val withConfidence = candidates.filter { it.confidence != null }
        if (withConfidence.isEmpty()) {
            return candidates.minBy { it.index }
        }

        return withConfidence.maxWith(
            compareBy<Candidate> { it.confidence ?: -1f }
                .thenBy { -it.index }
        )
    }

    private data class Candidate(
        val index: Int,
        val text: String,
        val confidence: Float?
    )
}
