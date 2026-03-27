package com.runanywhere.kotlin_starter_example.domain.ai

import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode
import com.runanywhere.kotlin_starter_example.domain.models.ReasoningStep
import com.runanywhere.kotlin_starter_example.domain.models.StructuredResponse

class ResponseParser {

    fun sanitizeForDisplay(rawResponse: String, userQuery: String? = null): String {
        // Remove common model/control artifacts before anything is shown to the user.
        var cleaned = rawResponse
            .replace(Regex("(?i)^Answer:\\s*"), "")
            .replace(Regex("(?i)^Response:\\s*"), "")
            .replace(Regex("(?i)^Assistant:\\s*"), "")
            .replace("<|im_end|>", "")
            .replace("<|im_start|>", "")
            .replace("<end_of_utterance>", "")
            .replace("</end_of_utterance>", "")
            .replace(Regex("<\\|eot_id\\|>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<\\|end_of_text\\|>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<\\|.*?\\|>"), "")
            .replace(Regex("(?i)end\\.of\\.utterance"), "")
            .replace(Regex("(?i)end_of_utterance"), "")
            .replace(Regex("(?i)model\\s+(?:for\\s+)?vision\\s+is\\s+[\\w\\-]+"), "")
            .replace(Regex("(?i)smolvm[\\w\\-]*"), "")
            .replace(Regex("(?i)qwen2?-?vl[\\w\\-]*"), "")
            .replace(Regex("(?i)lfm2?-?vl[\\w\\-]*"), "")
            .replace(Regex("(?i)<\\|vision_start\\|>.*?<\\|vision_end\\|>"), "")
            .replace(Regex("(?i)<vision>.*?</vision>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        userQuery?.let { query ->
            val trimmedQuery = query.trim().lowercase().removeSuffix("?")
            if (trimmedQuery.isNotBlank() && cleaned.lowercase().startsWith(trimmedQuery)) {
                cleaned = cleaned.substring(query.length).trim()
                cleaned = cleaned.replace(Regex("^[:\\-\\s]+"), "").trim()
            }
        }

        return cleaned
    }
    
    fun parse(rawResponse: String, mode: ResponseMode, language: Language, userQuery: String? = null): StructuredResponse {
        val cleaned = sanitizeForDisplay(rawResponse, userQuery)

        if (cleaned.isBlank()) {
            return StructuredResponse(
                directAnswer = "I'm sorry, I couldn't generate a clear response. Could you try rephrasing your question?",
                quickExplanation = "The offline model returned an empty result.",
                deepExplanation = null,
                mode = mode,
                language = language
            )
        }
        
        // Handle short responses (greetings, etc)
        if (cleaned.length < 120) {
            return StructuredResponse(
                directAnswer = cleaned,
                quickExplanation = "",
                deepExplanation = null,
                mode = mode,
                language = language
            )
        }
        
        val lines = cleaned.lines().filter { it.isNotBlank() }
        
        // For better UX, put the ENTIRE response in directAnswer and leave others empty
        // This way users see the complete response without truncation
        val directAnswer = cleaned.trim()
        
        // Quick and deep explanations are empty - all content goes to directAnswer
        val quickExplanation = ""
        val deepExplanation: String? = null
        
        return StructuredResponse(
            directAnswer = directAnswer,
            quickExplanation = quickExplanation,
            deepExplanation = deepExplanation,
            mode = mode,
            language = language
        )
    }
    
    fun parseReasoningSteps(rawResponse: String): List<ReasoningStep> {
        val steps = mutableListOf<ReasoningStep>()
        val stepPattern = Regex("""STEP\s+(\d+):\s*(.+?)(?=STEP\s+\d+:|$)""", RegexOption.DOT_MATCHES_ALL)
        
        stepPattern.findAll(rawResponse).forEachIndexed { _, match ->
            val stepNumber = match.groupValues[1].toIntOrNull() ?: return@forEachIndexed
            val content = match.groupValues[2].trim()
            val lines = content.lines().filter { it.isNotBlank() }
            
            steps.add(ReasoningStep(
                stepNumber = stepNumber,
                title = lines.firstOrNull() ?: "Thinking Step",
                description = lines.drop(1).joinToString("\n")
            ))
        }
        
        return steps.ifEmpty {
            listOf(ReasoningStep(1, "Educational Analysis", rawResponse.trim()))
        }
    }
}
