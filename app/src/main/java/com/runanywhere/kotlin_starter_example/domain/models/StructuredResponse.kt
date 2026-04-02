package com.runanywhere.kotlin_starter_example.domain.models

import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode

data class StructuredResponse(
    val directAnswer: String,
    val quickExplanation: String,
    val deepExplanation: String?,
    val mode: ResponseMode,
    val language: Language,
    val modelName: String = "SmolLM2-360M", // Default model name
    val tokensUsed: Int = 0, // Token usage tracking
    val promptTokens: Int = 0,
    val responseTokens: Int = 0,
    val responseTimeMs: Long = 0, // Response generation time
    val timeToFirstTokenMs: Long? = null,
    val generationTrace: List<GenerationTraceEntry> = emptyList()
) {
    fun toDisplayText(): String = when (mode) {
        ResponseMode.DIRECT -> directAnswer.trim()

        ResponseMode.ANSWER,
        ResponseMode.EXPLAIN,
        ResponseMode.CREATIVE,
        ResponseMode.THEORY -> joinSections(directAnswer, quickExplanation, deepExplanation)

        ResponseMode.CODE, ResponseMode.DIRECTION -> buildString {
            append(directAnswer.trim())
            if (!quickExplanation.isBlank()) {
                appendLine()
                appendLine()
                append(quickExplanation.trim())
            }
            if (!deepExplanation.isNullOrBlank()) {
                appendLine()
                appendLine()
                append(deepExplanation.trim())
            }
        }.trim()

    }

    private fun joinSections(vararg sections: String?): String =
        sections
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .joinToString("\n\n")
            .trim()
}
