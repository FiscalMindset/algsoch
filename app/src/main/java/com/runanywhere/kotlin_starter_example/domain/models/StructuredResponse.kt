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

        ResponseMode.ANSWER -> buildSectionedText(
            "Answer" to directAnswer,
            "Details" to quickExplanation,
            "More" to deepExplanation
        )

        ResponseMode.EXPLAIN -> if (directAnswer.contains("Key Points:") || directAnswer.contains("Takeaway:")) {
            directAnswer.trim()
        } else {
            buildSectionedText(
                "Explanation" to directAnswer,
                "Details" to quickExplanation,
                "More" to deepExplanation
            )
        }

        ResponseMode.NOTES, ResponseMode.DIRECTION -> buildString {
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

        ResponseMode.CREATIVE -> buildSectionedText(
            "Idea" to directAnswer,
            "Why It Sticks" to quickExplanation,
            "More" to deepExplanation
        )

        ResponseMode.THEORY -> buildSectionedText(
            "Concept" to directAnswer,
            "Connections" to quickExplanation,
            "More" to deepExplanation
        )
    }

    private fun buildSectionedText(vararg sections: Pair<String, String?>): String =
        buildString {
            sections.forEachIndexed { index, (label, content) ->
                val value = content?.trim().orEmpty()
                if (value.isBlank()) return@forEachIndexed
                if (isNotBlank()) {
                    appendLine()
                    appendLine()
                }
                append(label)
                append(": ")
                append(value)
            }
        }.trim()
}
