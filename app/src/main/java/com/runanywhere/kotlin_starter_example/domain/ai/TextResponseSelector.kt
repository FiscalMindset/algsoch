package com.runanywhere.kotlin_starter_example.domain.ai

import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode
import kotlin.math.min

internal object TextResponseSelector {

    private val parser = ResponseParser()

    fun chooseBetterResponse(
        mode: ResponseMode,
        userQuery: String,
        firstAttempt: String,
        retryAttempt: String
    ): String {
        val firstScore = score(mode, userQuery, firstAttempt)
        val retryScore = score(mode, userQuery, retryAttempt)
        val firstNeedsRetry = TutorReplyGuard.shouldRetry(userQuery, firstAttempt)
        val retryNeedsRetry = TutorReplyGuard.shouldRetry(userQuery, retryAttempt)

        return when {
            !firstNeedsRetry && retryNeedsRetry -> firstAttempt
            firstNeedsRetry && !retryNeedsRetry -> retryAttempt
            retryScore >= firstScore + 1.25 -> retryAttempt
            else -> firstAttempt
        }
    }

    fun score(
        mode: ResponseMode,
        userQuery: String,
        rawResponse: String
    ): Double {
        val cleaned = parser.sanitizeForDisplay(rawResponse, userQuery)
        if (cleaned.isBlank()) return Double.NEGATIVE_INFINITY

        val normalized = cleaned.replace(Regex("\\s+"), " ").trim()
        val sentenceCount = normalized
            .split(Regex("(?<=[.!?])\\s+"))
            .map(String::trim)
            .count { it.isNotBlank() }
            .coerceAtLeast(if (normalized.isNotBlank()) 1 else 0)
        val bulletCount = Regex("(?m)^- ").findAll(cleaned).count()
        val stepCount = Regex("(?im)^Step\\s+\\d+:").findAll(cleaned).count()
        val hasSummary = Regex("(?im)^Summary:").containsMatchIn(cleaned)
        val hasTips = Regex("(?im)^Tips:").containsMatchIn(cleaned)
        val hasCommonMistakes = Regex("(?im)^Common Mistakes:").containsMatchIn(cleaned)

        var score = 0.0
        score += min(normalized.length, 600) / 60.0
        score += min(sentenceCount, 8) * 0.9
        if (TutorReplyGuard.shouldRetry(userQuery, rawResponse)) {
            score -= 8.0
        }

        when (mode) {
            ResponseMode.DIRECT -> {
                if (normalized.length in 15..240) score += 2.0
                if (sentenceCount in 1..3) score += 2.0
                if (sentenceCount > 4) score -= 1.5
                if (Regex("""\b\d+\.\s+[A-Za-z]""").containsMatchIn(cleaned)) score -= 3.0
            }

            ResponseMode.ANSWER -> {
                if (normalized.length in 40..420) score += 2.0
                if (sentenceCount in 2..6) score += 2.5
                if (Regex("""\b\d+\.\s+[A-Za-z]""").containsMatchIn(cleaned)) score -= 1.5
            }

            ResponseMode.EXPLAIN -> {
                if (normalized.length >= 220) score += 3.0 else score -= 2.0
                if (sentenceCount >= 4) score += 3.0 else score -= 2.0
            }

            ResponseMode.CODE -> {
                // Check for code blocks
                val hasCodeBlock = cleaned.contains("```")
                if (hasCodeBlock) score += 4.0 else score -= 3.0
                if (normalized.length >= 50) score += 2.0
            }

            ResponseMode.DIRECTION -> {
                score += stepCount * 1.4
                if (stepCount >= 3) score += 3.0 else score -= 2.5
                if (hasTips) score += 1.8 else score -= 1.0
                if (hasCommonMistakes) score += 1.8 else score -= 1.0
            }

            ResponseMode.CREATIVE -> {
                if (normalized.length >= 220) score += 3.0 else score -= 2.0
                if (sentenceCount >= 4) score += 2.5 else score -= 1.5
            }

            ResponseMode.THEORY -> {
                if (normalized.length >= 320) score += 3.5 else score -= 2.5
                if (sentenceCount >= 5) score += 3.0 else score -= 2.0
            }
        }

        score -= truncationPenalty(normalized)
        return score
    }

    private fun truncationPenalty(text: String): Double {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return 6.0

        var penalty = 0.0
        val lowercase = trimmed.lowercase()

        if (trimmed.last() !in ".!?") {
            penalty += 1.0
        }
        if (trimmed.endsWith(":") || trimmed.endsWith(",") || trimmed.endsWith("-")) {
            penalty += 2.0
        }
        if (Regex("""\b\d+[.)]?$""").containsMatchIn(trimmed)) {
            penalty += 2.0
        }
        if (Regex("""\b(and|or|because|so|then|with|using|for)$""").containsMatchIn(lowercase)) {
            penalty += 1.8
        }

        return penalty
    }
}
