package com.personaltool.app.capture

sealed class OemCorrelationDecision {
    data class Match(val candidate: OemAudioCandidate) : OemCorrelationDecision()
    data class NotFound(val reason: String) : OemCorrelationDecision()
    data class Ambiguous(val reason: String) : OemCorrelationDecision()
}

object OemCorrelationEngine {

    const val BUFFER_PRE_CALL_MS = 15000L
    const val BUFFER_POST_CALL_MS = 25000L

    /**
     * Pure, directly testable correlation decision function.
     * Evaluates candidates against timestamp window, call duration matching, and anti-collision phone number rules.
     */
    fun correlate(
        startTimeMs: Long,
        endTimeMs: Long,
        phoneNumber: String,
        candidates: List<OemAudioCandidate>
    ): OemCorrelationDecision {
        val windowStart = startTimeMs - BUFFER_PRE_CALL_MS
        val windowEnd = endTimeMs + BUFFER_POST_CALL_MS
        val actualCallDurationMs = (endTimeMs - startTimeMs).coerceAtLeast(0L)

        // 1. Filter by timestamp window
        val windowCandidates = candidates.filter { it.dateModifiedEpochMs in windowStart..windowEnd }
        if (windowCandidates.isEmpty()) {
            return OemCorrelationDecision.NotFound(
                "No OEM recording found within timestamp window ($windowStart..$windowEnd)."
            )
        }

        // 2. Duration Correlation
        // If actual call was >= 3 seconds and candidate has duration metadata > 0, verify approximate match
        val durationFilteredCandidates = if (actualCallDurationMs >= 3000L) {
            windowCandidates.filter { candidate ->
                if (candidate.durationMs <= 0L) {
                    true // Unknown duration metadata in candidate: keep for further filtering
                } else {
                    val toleranceMs = (actualCallDurationMs * 0.4).toLong().coerceAtLeast(15000L)
                    val diffMs = kotlin.math.abs(candidate.durationMs - actualCallDurationMs)
                    diffMs <= toleranceMs
                }
            }
        } else {
            windowCandidates
        }

        if (durationFilteredCandidates.isEmpty()) {
            return OemCorrelationDecision.NotFound(
                "Candidate files found in window were rejected due to severe duration mismatch with actual call duration (${actualCallDurationMs}ms)."
            )
        }

        // 3. Collision & Ambiguity Resolution (Fail closed on ambiguity)
        val cleanNumber = phoneNumber.filter { it.isDigit() }
        return if (durationFilteredCandidates.size == 1) {
            OemCorrelationDecision.Match(durationFilteredCandidates.first())
        } else {
            // Multiple candidates in window: require clean number match
            if (cleanNumber.length >= 4) {
                val numberMatches = durationFilteredCandidates.filter { candidate ->
                    candidate.displayName.contains(cleanNumber) ||
                            (candidate.filePath != null && candidate.filePath.contains(cleanNumber))
                }
                when (numberMatches.size) {
                    1 -> OemCorrelationDecision.Match(numberMatches.first())
                    0 -> OemCorrelationDecision.Ambiguous(
                        "Multiple OEM recording files (${durationFilteredCandidates.size}) found in window but none matched phone number '$phoneNumber'; failed closed to prevent wrong-call corruption."
                    )
                    else -> OemCorrelationDecision.Ambiguous(
                        "Multiple OEM recording files (${numberMatches.size}) matched phone number '$phoneNumber' in window; failed closed to prevent wrong-call data corruption."
                    )
                }
            } else {
                OemCorrelationDecision.Ambiguous(
                    "Multiple OEM recording files (${durationFilteredCandidates.size}) found in window for private/unknown number; failed closed to prevent wrong-call corruption."
                )
            }
        }
    }
}
