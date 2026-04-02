package com.runanywhere.kotlin_starter_example.services

import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode
import com.runanywhere.kotlin_starter_example.data.models.enums.UserLevel
import com.runanywhere.kotlin_starter_example.domain.ai.PromptBuilder
import com.runanywhere.kotlin_starter_example.domain.ai.ResponseParser
import com.runanywhere.kotlin_starter_example.domain.ai.TextResponseSelector
import com.runanywhere.kotlin_starter_example.domain.ai.TutorReplyGuard
import com.runanywhere.kotlin_starter_example.domain.models.GenerationTraceEntry
import com.runanywhere.kotlin_starter_example.domain.models.ReasoningStep
import com.runanywhere.kotlin_starter_example.domain.models.StructuredResponse
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.LLM.LLMGenerationOptions
import com.runanywhere.sdk.public.extensions.LLM.RunAnywhereToolCalling
import com.runanywhere.sdk.public.extensions.LLM.ToolCallingOptions
import com.runanywhere.sdk.public.extensions.LLM.ToolDefinition
import com.runanywhere.sdk.public.extensions.LLM.ToolParameter
import com.runanywhere.sdk.public.extensions.LLM.ToolParameterType
import com.runanywhere.sdk.public.extensions.LLM.ToolValue
import com.runanywhere.sdk.public.extensions.VLM.VLMGenerationOptions
import com.runanywhere.sdk.public.extensions.VLM.VLMImage
import com.runanywhere.sdk.public.extensions.cancelGeneration
import com.runanywhere.sdk.public.extensions.chat
import com.runanywhere.sdk.public.extensions.generateStreamWithMetrics
import com.runanywhere.sdk.public.extensions.isLLMModelLoaded
import com.runanywhere.sdk.public.extensions.isVLMModelLoaded
import com.runanywhere.sdk.public.extensions.processImageStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToLong
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AIInferenceService {
    private val promptBuilder = PromptBuilder()
    private val responseParser = ResponseParser()

    private data class TextGenerationResult(
        val rawText: String,
        val responseTokens: Int,
        val timeToFirstTokenMs: Long? = null,
    )

    companion object {
        @Volatile
        private var toolsRegistered = false
    }
    
    suspend fun generateAnswer(
        userQuery: String,
        mode: ResponseMode,
        language: Language,
        userLevel: UserLevel,
        customPrompt: String? = null,
        enabledTools: List<String> = emptyList(),
        imagePath: String? = null,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        onRetryStarted: suspend () -> Unit = {},
        onPartialResponse: suspend (String) -> Unit = {}
    ): StructuredResponse = withContext(Dispatchers.IO) {
        val isCompanionChat = isCompanionPrompt(customPrompt)
        val prompt = promptBuilder.buildPrompt(userQuery, mode, language, userLevel, customPrompt, conversationHistory)
        val toolPromptSpec = promptBuilder.buildToolPrompt(userQuery, mode, language, userLevel, customPrompt, conversationHistory)
        val visionPrompt = buildVisionPrompt(userQuery, mode, language, customPrompt, conversationHistory)
        val useVision = !imagePath.isNullOrBlank()
        val useTools = !useVision && shouldUseTools(userQuery, enabledTools)
        val textOptions = buildTextGenerationOptions(userQuery, mode, customPrompt)
        val generationTrace = mutableListOf<GenerationTraceEntry>()
        
        // Track response time
        val startTime = System.currentTimeMillis()
        
        // Generate response with proper parameters to avoid sampling issues
        var generationResult = if (useVision) {
            TextGenerationResult(
                rawText = generateVisionResponse(
                imagePath = imagePath!!,
                visionPrompt,
                buildVisionRetryPrompt(userQuery, mode, language, customPrompt, conversationHistory)
                ),
                responseTokens = 0
            )
        } else if (useTools) {
            ensureToolRegistryInitialized()

            val result = RunAnywhereToolCalling.generateWithTools(
                prompt = toolPromptSpec.prompt,
                options = ToolCallingOptions(
                    maxToolCalls = 3,
                    autoExecute = true,
                    temperature = textOptions.temperature.coerceAtMost(0.45f),
                    maxTokens = textOptions.maxTokens,
                    systemPrompt = toolPromptSpec.systemPrompt
                )
            )
            TextGenerationResult(
                rawText = result.text,
                responseTokens = (result.text.length / 4).coerceAtLeast(0)
            )
        } else {
            streamTextResponse(prompt, userQuery, onPartialResponse, textOptions)
        }
        generationTrace += GenerationTraceEntry(
            label = when {
                useVision -> "Vision Draft"
                useTools -> "Tool Draft"
                else -> "First Draft"
            },
            text = responseParser.sanitizeForDisplay(generationResult.rawText, userQuery),
            reason = if (useVision || useTools) {
                "This was the initial generated answer."
            } else {
                "This was the first draft generated for the question."
            },
            wasStreamed = !useVision && !useTools,
            wasSelected = true
        )
        if (
            !useVision &&
            !useTools &&
            !isCompanionChat &&
            (shouldRetryTextModeResponse(mode, generationResult.rawText) ||
                TutorReplyGuard.shouldRetry(userQuery, generationResult.rawText))
        ) {
            onRetryStarted()
            val retryResult = streamTextResponse(
                prompt = buildTextRetryPrompt(
                    userQuery = userQuery,
                    mode = mode,
                    language = language,
                    userLevel = userLevel,
                    customPrompt = customPrompt,
                    conversationHistory = conversationHistory,
                    previousResponse = generationResult.rawText
                ),
                userQuery = userQuery,
                onPartialResponse = {},
                options = textOptions
            )
            generationTrace += GenerationTraceEntry(
                label = "Retry Draft",
                text = responseParser.sanitizeForDisplay(retryResult.rawText, userQuery),
                reason = "A retry was generated because the first draft looked incomplete, off-topic, or weak for the selected mode.",
                wasStreamed = false,
                wasSelected = false
            )
            val selectedResponse = TextResponseSelector.chooseBetterResponse(
                mode = mode,
                userQuery = userQuery,
                firstAttempt = generationResult.rawText,
                retryAttempt = retryResult.rawText
            )
            generationResult = if (selectedResponse == retryResult.rawText) {
                onPartialResponse(responseParser.sanitizeForDisplay(retryResult.rawText, userQuery))
                retryResult
            } else {
                onPartialResponse(responseParser.sanitizeForDisplay(generationResult.rawText, userQuery))
                generationResult
            }
            generationTrace.indices.forEach { index ->
                generationTrace[index] = generationTrace[index].copy(
                    wasSelected = generationTrace[index].label == if (selectedResponse == retryResult.rawText) "Retry Draft" else "First Draft"
                )
            }
        }
        if (!useVision && !useTools && !isCompanionChat && TutorReplyGuard.shouldRetry(userQuery, generationResult.rawText)) {
            val fallbackReply = TutorReplyGuard.buildFallbackReply(
                userQuery = userQuery,
                rawResponse = generationResult.rawText,
                language = language,
                conversationHistory = conversationHistory
            )
            onPartialResponse(responseParser.sanitizeForDisplay(fallbackReply, userQuery))
            generationResult = TextGenerationResult(
                rawText = fallbackReply,
                responseTokens = 0
            )
            generationTrace.indices.forEach { index ->
                generationTrace[index] = generationTrace[index].copy(wasSelected = false)
            }
            generationTrace += GenerationTraceEntry(
                label = "Fallback Answer",
                text = responseParser.sanitizeForDisplay(fallbackReply, userQuery),
                reason = "The earlier drafts still looked broken, so the app replaced them with a safe fallback answer.",
                wasStreamed = true,
                wasSelected = true
            )
        }
        if (!useVision && !useTools && isCompanionChat && shouldRetryCompanionReply(userQuery, generationResult.rawText)) {
            generationResult = streamTextResponse(
                prompt = buildCompanionRecoveryPrompt(userQuery, language, customPrompt, conversationHistory),
                userQuery = userQuery,
                onPartialResponse = onPartialResponse,
                options = buildCompanionRecoveryOptions()
            )
        }
        if (!useVision && !useTools && isCompanionChat && shouldFallbackCompanionReply(userQuery, generationResult.rawText)) {
            val fallbackReply = buildCompanionFallbackReply(userQuery, language)
            onPartialResponse(responseParser.sanitizeForDisplay(fallbackReply, userQuery))
            generationResult = TextGenerationResult(
                rawText = fallbackReply,
                responseTokens = (fallbackReply.length / 4).coerceAtLeast(1)
            )
        }
        val rawResponse = generationResult.rawText
        
        val responseTime = System.currentTimeMillis() - startTime

        // Estimate token usage for metadata display.
        val promptTokens = when {
            useVision -> visionPrompt.length / 4
            useTools -> (toolPromptSpec.systemPrompt.length + toolPromptSpec.prompt.length) / 4
            else -> prompt.length / 4
        }
        val responseTokens = generationResult.responseTokens.takeIf { it > 0 } ?: (rawResponse.length / 4)
        val totalTokens = promptTokens + responseTokens
        
        // Get model name (try to detect from loaded models)
        val modelName = try {
            // Check which model is loaded
            when {
                useVision && RunAnywhere.isVLMModelLoaded -> "SmolVLM Vision"
                useTools -> "SmolLM2 + Tools"
                RunAnywhere.isLLMModelLoaded() -> "SmolLM2 360M"
                else -> "Offline Model"
            }
        } catch (e: Exception) {
            "SmolLM2 360M" // Default fallback
        }
        
        // Pass userQuery to parse function for echo-detection
        responseParser.parse(rawResponse, mode, language, userQuery).copy(
            modelName = modelName,
            tokensUsed = totalTokens,
            promptTokens = promptTokens,
            responseTokens = responseTokens,
            responseTimeMs = responseTime,
            timeToFirstTokenMs = generationResult.timeToFirstTokenMs,
            generationTrace = generationTrace
        )
    }

    fun cancelActiveGeneration() {
        try {
            RunAnywhere.cancelGeneration()
        } catch (_: Exception) {
            // Best effort only; the coroutine cancellation path still runs.
        }
    }

    private fun buildVisionPrompt(
        userQuery: String,
        mode: ResponseMode,
        language: Language,
        customPrompt: String?,
        conversationHistory: List<Pair<String, String>>
    ): String {
        val langName = languageName(language)
        val effectiveQuery = userQuery.ifBlank { defaultVisionQuestion(language) }
        val assistantContext = customPrompt
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { "\nAssistant-specific persona instructions:\n$it" }
            .orEmpty()
        val conversationContext = visionConversationContext(conversationHistory)
        return """
            You are Algsoch, an image-grounded AI companion.
            Answer only from the uploaded image and the user query.
            If something is unclear or not visible, say that clearly instead of guessing.
            If the user uploads only an image, explain the main subject or key content of the image.
            If the image is a screenshot, document, diagram, or notes page, describe the important visible parts instead of replying with only one small label.
            If the image is a personal, casual, or social photo, respond naturally to what is visible before adding any emotional reaction.
            Start with the main subject, then include visible text, UI, or structure, then explain the likely meaning or purpose.
            Never answer with only one or two words.
            Never include special tokens such as <end_of_utterance> in your reply.
            Ignore unrelated earlier chat topics unless the current user explicitly connects them to the image.
            Never echo prompt scaffolding such as "User query:", "Answer:", or "Recent chat context:".
            Use recent chat context only when it is directly relevant to the same image, and do not invent details that are not visible.
            $assistantContext
            ${visionModeInstructions(mode, retry = false)}
            Respond in $langName.

            $conversationContext
            User query: $effectiveQuery
        """.trimIndent()
    }

    private fun buildVisionRetryPrompt(
        userQuery: String,
        mode: ResponseMode,
        language: Language,
        customPrompt: String?,
        conversationHistory: List<Pair<String, String>>
    ): String {
        val langName = languageName(language)
        val effectiveQuery = userQuery.ifBlank { defaultVisionQuestion(language) }
        val assistantContext = customPrompt
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { "\nAssistant-specific persona instructions:\n$it" }
            .orEmpty()
        val conversationContext = visionConversationContext(conversationHistory)
        return """
            You are analyzing an uploaded image.
            The previous answer was too short or too vague. Rewrite it with a fuller, clearer response.
            Stay grounded in what is visible in the image.
            If the image is blurry or unclear, say that clearly, but still describe what is visible.
            If the image is a personal, casual, or social photo, keep the tone natural and human while staying honest about what is visible.
            Never output special tokens or raw control text.
            Ignore unrelated earlier chat topics unless the current user explicitly connects them to the image.
            Never echo prompt scaffolding such as "User query:", "Answer:", or "Recent chat context:".
            Use recent chat context only when it is directly relevant to the same image, and do not invent details that are not visible.
            $assistantContext
            ${visionModeInstructions(mode, retry = true)}
            Respond in $langName.

            $conversationContext
            User query: $effectiveQuery
        """.trimIndent()
    }

    private fun visionConversationContext(conversationHistory: List<Pair<String, String>>): String {
        val recentTurns = conversationHistory
            .filter { it.first == "user" || it.first == "assistant" }
            .takeLast(4)

        if (recentTurns.isEmpty()) return ""

        return buildString {
            appendLine("Recent chat context:")
            recentTurns.forEach { (role, text) ->
                val speaker = if (role == "assistant") "Assistant" else "User"
                appendLine("$speaker: ${text.trim()}")
            }
        }.trim()
    }

    private fun visionModeInstructions(mode: ResponseMode, retry: Boolean): String = when (mode) {
        ResponseMode.DIRECT -> if (retry) {
            """
            Write 4 complete sentences in a natural conversational style.
            Answer directly, but still mention the main visual details and what they likely mean.
            """.trimIndent()
        } else {
            """
            Write 3 to 4 complete sentences in a natural conversational style.
            Keep it direct, but still mention the key visible details and what they likely mean.
            """.trimIndent()
        }

        ResponseMode.ANSWER -> if (retry) {
            """
            Start with the direct answer in the first sentence.
            Then add 3 to 4 short sentences explaining the visible evidence and one practical takeaway.
            """.trimIndent()
        } else {
            """
            Start with the direct answer in the first sentence.
            Then add 2 to 3 short sentences explaining the visible evidence and one practical takeaway.
            """.trimIndent()
        }

        ResponseMode.EXPLAIN -> if (retry) {
            """
            Explain like a teacher in 5 to 6 complete sentences.
            Move from what is visible, to what it means, to why it matters.
            """.trimIndent()
        } else {
            """
            Explain like a teacher in 4 to 5 complete sentences.
            Move from what is visible, to what it means, to why it matters.
            """.trimIndent()
        }

        ResponseMode.CODE -> if (retry) {
            """
            Write complete, working code in proper markdown code blocks.
            Include all necessary imports, error handling, and comments.
            Format with proper indentation. Write 8 to 12 lines minimum.
            """.trimIndent()
        } else {
            """
            Write complete, working code in proper markdown code blocks.
            Include necessary imports and brief explanatory comments.
            Format with proper indentation.
            """.trimIndent()
        }

        ResponseMode.DIRECTION -> if (retry) {
            """
            Use guided steps for how to inspect, read, or understand the image.
            Write Step 1 through Step 4.
            Add a short "Tips:" section and a short "Common Mistakes:" section.
            """.trimIndent()
        } else {
            """
            Use guided steps for how to inspect, read, or understand the image.
            Write Step 1 through Step 3.
            Add a short "Tips:" section and a short "Common Mistakes:" section.
            """.trimIndent()
        }

        ResponseMode.CREATIVE -> if (retry) {
            """
            Write 5 to 6 sentences.
            Use one memorable analogy or real-life comparison, but stay grounded in the actual image details.
            End with why the image or concept matters.
            """.trimIndent()
        } else {
            """
            Write 4 to 5 sentences.
            Use one memorable analogy or real-life comparison, but stay grounded in the actual image details.
            End with why the image or concept matters.
            """.trimIndent()
        }

        ResponseMode.THEORY -> if (retry) {
            """
            Write 6 to 7 complete sentences.
            Explain the deeper concept shown by the image, connect it to broader ideas, and keep the explanation accurate.
            """.trimIndent()
        } else {
            """
            Write 5 to 6 complete sentences.
            Explain the deeper concept shown by the image, connect it to broader ideas, and keep the explanation accurate.
            """.trimIndent()
        }
    }

    private fun languageName(language: Language): String = when (language) {
        Language.ENGLISH -> "English"
        Language.HINDI -> "Hindi"
        Language.HINGLISH -> "Hinglish"
    }

    private fun defaultVisionQuestion(language: Language): String = when (language) {
        Language.ENGLISH -> "What is in this image?"
        Language.HINDI -> "Is image mein kya dikh raha hai?"
        Language.HINGLISH -> "Is image mein kya hai?"
    }

    private suspend fun generateVisionResponse(
        imagePath: String,
        primaryPrompt: String,
        retryPrompt: String
    ): String {
        return try {
            val vlmImage = VLMImage.fromFilePath(imagePath)
            
            // First attempt with error handling
            val firstAttempt = try {
                streamVisionResponse(vlmImage, primaryPrompt, maxTokens = 420)
            } catch (e: Exception) {
                // If first attempt fails, try with a simpler prompt
                "Error processing image: ${e.message}. Retrying with enhanced prompt..."
            }
            
            // Check if retry is needed
            if (!shouldRetryVisionResponse(firstAttempt) && !firstAttempt.contains("Error processing")) {
                return firstAttempt
            }

            // Retry attempt with more tokens and better prompt
            val retryAttempt = try {
                streamVisionResponse(vlmImage, retryPrompt, maxTokens = 600)
            } catch (e: Exception) {
                // If retry also fails, return a user-friendly message
                return "I'm having trouble analyzing this image. The image might be corrupted, too large, or in an unsupported format. Please try:\n- Taking a clearer photo\n- Selecting a different image\n- Ensuring the image is not corrupted"
            }
            
            // Compare attempts and return better one
            val firstScore = scoreVisionResponse(firstAttempt)
            val retryScore = scoreVisionResponse(retryAttempt)
            
            if (retryScore >= firstScore && retryScore >= 3) {
                retryAttempt
            } else if (firstScore >= 3) {
                firstAttempt
            } else {
                // Both attempts are poor quality, try one more time with maximum context
                try {
                    val finalAttempt = streamVisionResponse(vlmImage, "$retryPrompt\n\nPlease provide a detailed description of what you see in the image.", maxTokens = 720)
                    if (scoreVisionResponse(finalAttempt) >= 3) finalAttempt else retryAttempt
                } catch (e: Exception) {
                    retryAttempt.ifBlank {
                        "Unable to generate a complete description of the image. Please try again with a different image."
                    }
                }
            }
        } catch (e: Exception) {
            "Failed to process the image: ${e.message}. Please ensure the image is accessible and try again."
        }
    }

    private suspend fun streamTextResponse(
        prompt: String,
        userQuery: String,
        onPartialResponse: suspend (String) -> Unit,
        options: LLMGenerationOptions
    ): TextGenerationResult {
        val streamingResult = RunAnywhere.generateStreamWithMetrics(prompt, options)
        val rawText = StringBuilder()
        var lastEmittedText = ""
        var lastEmissionAt = 0L
        var lastEmittedLength = 0

        streamingResult.stream.collect { token ->
            rawText.append(token)
            val now = System.currentTimeMillis()
            val charsSinceLastEmit = rawText.length - lastEmittedLength
            val tokenEndsChunk = token.any { it.isWhitespace() } ||
                token.contains('\n') ||
                token.endsWith(".") ||
                token.endsWith("!") ||
                token.endsWith("?") ||
                token.endsWith(",") ||
                token.endsWith(":") ||
                token.endsWith(";")
            val shouldEmit = (tokenEndsChunk && charsSinceLastEmit >= 10 && now - lastEmissionAt >= 90L) ||
                (charsSinceLastEmit >= 28 && now - lastEmissionAt >= 220L)

            if (shouldEmit) {
                val cleaned = responseParser.sanitizeForDisplay(rawText.toString(), userQuery)
                if (cleaned != lastEmittedText) {
                    onPartialResponse(cleaned)
                    lastEmittedText = cleaned
                    lastEmissionAt = now
                    lastEmittedLength = rawText.length
                }
            }
        }

        val finalCleaned = responseParser.sanitizeForDisplay(rawText.toString(), userQuery)
        if (finalCleaned != lastEmittedText) {
            onPartialResponse(finalCleaned)
        }
        val metrics = streamingResult.result.await()

        return TextGenerationResult(
            rawText = rawText.toString(),
            responseTokens = metrics.responseTokens,
            timeToFirstTokenMs = metrics.timeToFirstTokenMs?.roundToLong()
        )
    }

    private fun shouldRetryTextModeResponse(mode: ResponseMode, rawResponse: String): Boolean {
        val cleaned = responseParser.sanitizeForDisplay(rawResponse)
        val sentenceCount = cleaned
            .replace(Regex("\\s+"), " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .count { it.isNotBlank() }

        return when (mode) {
            ResponseMode.DIRECT -> isBrokenDirectModeResponse(cleaned, sentenceCount)
            ResponseMode.ANSWER -> isBrokenAnswerModeResponse(cleaned, sentenceCount)
            ResponseMode.EXPLAIN -> cleaned.length < 220 || sentenceCount < 4
            ResponseMode.THEORY -> cleaned.length < 320 || sentenceCount < 5
            ResponseMode.CODE -> !cleaned.contains("```") || cleaned.length < 50 || looksLikeFlatCodeBlock(cleaned)
            ResponseMode.DIRECTION -> !Regex("(?im)^Step\\s+1:").containsMatchIn(cleaned)
            ResponseMode.CREATIVE -> cleaned.length < 220 || sentenceCount < 4
        }
    }

    private fun looksLikeFlatCodeBlock(cleaned: String): Boolean {
        val codeBlockPattern = Regex("```(\\w+)?\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
        return codeBlockPattern.findAll(cleaned).any { match ->
            val language = match.groupValues[1].lowercase()
            val code = match.groupValues[2].trim()
            val nonBlankLines = code.lines().filter { it.isNotBlank() }

            when (language) {
                "python", "py" -> {
                    nonBlankLines.any { line ->
                        val trimmed = line.trimStart()
                        !trimmed.startsWith("#") && (
                            Regex(""":\s+\S""").containsMatchIn(trimmed) ||
                                Regex("""\b(def|class|return|if|for|while|print|else|elif)\b""")
                                    .findAll(trimmed)
                                    .count() >= 2
                            )
                    }
                }

                else -> nonBlankLines.any { line -> line.count { it == ';' } >= 2 }
            }
        }
    }

    private fun isBrokenDirectModeResponse(cleaned: String, sentenceCount: Int): Boolean {
        if (cleaned.isBlank()) return true

        val hasListMarkers = Regex("""(?m)^(?:\d+\.|-|Step\s+\d+:)""", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)
        val hasInlineListMarkers = Regex("""\b\d+\.\s+[A-Za-z]""").containsMatchIn(cleaned)
        val endsInDanglingItem = Regex("""(?s).+:\s*\d+[.)]?\s*$""").containsMatchIn(cleaned) ||
            Regex("""(?i)\b(and|or|because|with|for|using)\s*$""").containsMatchIn(cleaned)
        val looksLikeBrokenLeadIn = Regex(
            """(?i)\b(?:here(?:'s| is)\s+how|here are|some ways|steps|examples|options)\b[^.!?]{0,160}:\s*\d+[.)]?\s*$"""
        ).containsMatchIn(cleaned)
        val hasLongUnfinishedTail = looksLikeLongUnfinishedTail(cleaned)

        return hasListMarkers ||
            hasInlineListMarkers ||
            endsInDanglingItem ||
            looksLikeBrokenLeadIn ||
            hasLongUnfinishedTail ||
            sentenceCount == 0
    }

    private fun isBrokenAnswerModeResponse(cleaned: String, sentenceCount: Int): Boolean {
        if (cleaned.isBlank()) return true

        val hasInlineListMarkers = Regex("""\b\d+\.\s+[A-Za-z]""").containsMatchIn(cleaned)
        val endsInDanglingItem = Regex("""(?s).+:\s*\d+[.)]?\s*$""").containsMatchIn(cleaned) ||
            Regex("""(?i)\b(and|or|because|with|for|using)\s*$""").containsMatchIn(cleaned)
        val hasLongUnfinishedTail = looksLikeLongUnfinishedTail(cleaned)

        return hasInlineListMarkers || endsInDanglingItem || hasLongUnfinishedTail || sentenceCount == 0
    }

    private fun looksLikeLongUnfinishedTail(cleaned: String): Boolean {
        val trimmed = cleaned.trim()
        if (trimmed.isBlank()) return true
        if (trimmed.last() in ".!?\"'") return false

        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        val lastWord = words.lastOrNull().orEmpty()
        if (lastWord.length in 1..3 && lastWord.any { it.isUpperCase() } && words.size >= 8) {
            return true
        }

        val tailStart = listOf(
            trimmed.lastIndexOf(". "),
            trimmed.lastIndexOf("! "),
            trimmed.lastIndexOf("? "),
            trimmed.lastIndexOf('\n')
        ).maxOrNull() ?: -1
        val tail = if (tailStart >= 0) trimmed.substring(tailStart + 1).trim() else trimmed
        val tailWordCount = tail.split(Regex("\\s+")).count { it.isNotBlank() }

        return tailWordCount >= 14 && (tail.contains(':') || tail.contains(", "))
    }

    private fun buildTextRetryPrompt(
        userQuery: String,
        mode: ResponseMode,
        language: Language,
        userLevel: UserLevel,
        customPrompt: String?,
        conversationHistory: List<Pair<String, String>>,
        previousResponse: String
    ): String {
        val previous = responseParser.sanitizeForDisplay(previousResponse, userQuery).take(500)
        val retryQuery = buildString {
            appendLine("Original question: $userQuery")
            appendLine()
            appendLine("Your previous answer was too short or did not match the selected response mode well:")
            appendLine(previous.ifBlank { "(empty response)" })
            appendLine()
            appendLine("Rewrite the answer from scratch and follow the selected mode exactly.")
            append(textRetryInstructions(mode))
        }.trim()

        return promptBuilder.buildPrompt(
            userQuery = retryQuery,
            mode = mode,
            language = language,
            userLevel = userLevel,
            customPrompt = customPrompt,
            conversationHistory = conversationHistory
        )
    }

    private fun textRetryInstructions(mode: ResponseMode): String = when (mode) {
        ResponseMode.DIRECT -> """
            Keep it direct.
            For simple fact questions, use 1 to 3 complete sentences.
            For how/why/use/build questions, you may use up to about 3 to 6 complete sentences while staying direct.
            Never use a numbered list, bullets, or steps.
            For use-case questions, keep examples inside full sentences separated by commas instead of starting a list.
            If the product or platform is unclear, give the safest general answer and ask one short clarifying question instead of inventing details.
            Never end with a dangling fragment like "1.", "Step 1", "Here are some ways:", or a cut-off word.
            Never answer with generic reset lines like "I'm ready to assist you" or "What's the first question?".
        """.trimIndent()
        ResponseMode.ANSWER -> """
            Start with the answer, then add a brief explanation and one useful example.
            Use 3 to 5 complete sentences.
            Keep examples in plain sentences, not numbered lists.
            Never end in the middle of a list item or in the middle of a word.
            Never answer with generic reset lines like "I'm ready to assist you" or "What's the first question?".
        """.trimIndent()
        ResponseMode.EXPLAIN -> """
            Explain like a teacher, not like Direct mode.
            Write at least 4 complete sentences.
            Cover what it is, why it matters, where it is used, and one simple example or takeaway.
            Never answer with generic reset lines like "I'm ready to assist you" or "What's the first question?".
        """.trimIndent()
        ResponseMode.CODE -> """
            Write complete, well-formatted code with proper syntax.
            Use markdown code blocks with language specification (```language).
            Include all necessary imports and error handling.
            Add brief comments explaining the key logic.
            Never leave TODO comments or incomplete implementations.
            Never answer with generic reset lines like "I'm ready to assist you" or "What's the first question?".
        """.trimIndent()
        ResponseMode.DIRECTION -> """
            Use Step 1, Step 2, Step 3 format, then add Tips and Common Mistakes sections.
            Never answer with generic reset lines like "I'm ready to assist you" or "What's the first question?".
        """.trimIndent()
        ResponseMode.CREATIVE -> """
            Write 4 to 6 sentences and include one memorable analogy plus why it matters.
            Never answer with generic reset lines like "I'm ready to assist you" or "What's the first question?".
        """.trimIndent()
        ResponseMode.THEORY -> """
            Give a deeper explanation with at least 5 complete sentences.
            Cover the big picture, key concepts, and how they connect.
            Never answer with generic reset lines like "I'm ready to assist you" or "What's the first question?".
        """.trimIndent()
    }

    private suspend fun streamVisionResponse(
        image: VLMImage,
        prompt: String,
        maxTokens: Int
    ): String {
        val options = VLMGenerationOptions(maxTokens = maxTokens)
        val sb = StringBuilder()
        RunAnywhere.processImageStream(image, prompt, options).collect { token ->
            sb.append(token)
        }
        return sb.toString()
    }

    private fun shouldRetryVisionResponse(response: String): Boolean =
        scoreVisionResponse(response) < 3

    private fun scoreVisionResponse(response: String): Int {
        if (looksLikePromptEchoLeak(response)) return 0

        val cleaned = response
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isBlank()) return 0

        val words = cleaned.split(" ").filter { it.isNotBlank() }
        var score = 0
        if (words.size >= 8) score += 2
        if (words.size >= 16) score += 1
        if (cleaned.length >= 50) score += 1
        if (cleaned.contains(".") || cleaned.contains("!") || cleaned.contains("?")) score += 1
        return score
    }

    private fun looksLikePromptEchoLeak(response: String): Boolean {
        val lowered = response.lowercase(Locale.getDefault())
        val queryEchoCount = Regex("""(?i)\b(?:user query|original question|question):""")
            .findAll(response)
            .count()

        return queryEchoCount >= 2 ||
            lowered.contains("recent chat context:") ||
            ((lowered.contains("user query:") || lowered.contains("original question:") || lowered.contains("question:")) &&
                (lowered.contains("answer:") || lowered.contains("response:")))
    }

    private fun shouldUseTools(userQuery: String, enabledTools: List<String>): Boolean {
        if (enabledTools.isEmpty()) return false

        val normalized = userQuery.lowercase(Locale.getDefault())
        val enabled = enabledTools.map { it.lowercase(Locale.getDefault()) }.toSet()

        fun hasTool(vararg ids: String): Boolean = ids.any { it in enabled }
        fun containsAny(vararg keywords: String): Boolean = keywords.any { normalized.contains(it) }

        val looksLikeMath = Regex("""\d+\s*[-+*/x×÷]\s*\d+|\(\s*\d+|\d+\s*\)|what is \d+|solve\s+\d+""")
            .containsMatchIn(normalized)

        return when {
            hasTool("get_weather") && containsAny("weather", "temperature", "forecast", "rain", "humidity") -> true
            hasTool("get_current_time", "get_time") && containsAny("time", "date", "timezone", "clock", "what time") -> true
            hasTool("calculate") && (looksLikeMath || containsAny("calculate", "solve", "multiply", "divide", "plus", "minus")) -> true
            hasTool("unit_convert") && containsAny("convert", "celsius", "fahrenheit", "km", "kilometer", "mile", "meter", "inch", "feet") -> true
            hasTool("summarize_text") && containsAny("summarize", "summarise", "summary", "tldr", "short note") -> true
            hasTool("create_quiz") && containsAny("quiz", "mcq", "test me", "practice questions", "flashcard") -> true
            else -> false
        }
    }

    private suspend fun ensureToolRegistryInitialized() {
        if (toolsRegistered) return

        RunAnywhereToolCalling.registerTool(
            definition = ToolDefinition(
                name = "get_weather",
                description = "Gets current weather for a location using Open-Meteo API",
                parameters = listOf(
                    ToolParameter("location", ToolParameterType.STRING, "City name", required = true)
                ),
                category = "Utility"
            ),
            executor = { args ->
                val location = args["location"]?.stringValue ?: "San Francisco"
                fetchWeather(location)
            }
        )

        RunAnywhereToolCalling.registerTool(
            definition = ToolDefinition(
                name = "get_current_time",
                description = "Gets current local date and time",
                parameters = emptyList(),
                category = "Utility"
            ),
            executor = {
                val now = Date()
                val dateFormatter = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm:ss a", Locale.getDefault())
                val tz = TimeZone.getDefault()
                mapOf(
                    "datetime" to ToolValue.string(dateFormatter.format(now)),
                    "timezone" to ToolValue.string(tz.id)
                )
            }
        )

        RunAnywhereToolCalling.registerTool(
            definition = ToolDefinition(
                name = "calculate",
                description = "Performs arithmetic expressions",
                parameters = listOf(
                    ToolParameter("expression", ToolParameterType.STRING, "Math expression", required = true)
                ),
                category = "Utility"
            ),
            executor = { args ->
                val expression = args["expression"]?.stringValue ?: "0"
                evaluateMathExpression(expression)
            }
        )

        RunAnywhereToolCalling.registerTool(
            definition = ToolDefinition(
                name = "unit_convert",
                description = "Converts between C/F temperatures and km/mi distances",
                parameters = listOf(
                    ToolParameter("value", ToolParameterType.NUMBER, "Numeric value", required = true),
                    ToolParameter("from", ToolParameterType.STRING, "Source unit", required = true),
                    ToolParameter("to", ToolParameterType.STRING, "Target unit", required = true)
                ),
                category = "Utility"
            ),
            executor = { args ->
                val value = args["value"]?.numberValue ?: 0.0
                val from = args["from"]?.stringValue ?: ""
                val to = args["to"]?.stringValue ?: ""
                convertUnits(value, from, to)
            }
        )

        RunAnywhereToolCalling.registerTool(
            definition = ToolDefinition(
                name = "summarize_text",
                description = "Returns a concise summary of input text",
                parameters = listOf(
                    ToolParameter("text", ToolParameterType.STRING, "Text to summarize", required = true)
                ),
                category = "Productivity"
            ),
            executor = { args ->
                val text = args["text"]?.stringValue ?: ""
                val summary = text.split(Regex("(?<=[.!?])\\s+")).take(2).joinToString(" ").ifBlank { text.take(180) }
                mapOf("summary" to ToolValue.string(summary))
            }
        )

        RunAnywhereToolCalling.registerTool(
            definition = ToolDefinition(
                name = "create_quiz",
                description = "Creates basic quiz questions from a topic",
                parameters = listOf(
                    ToolParameter("topic", ToolParameterType.STRING, "Topic name", required = true)
                ),
                category = "Education"
            ),
            executor = { args ->
                val topic = args["topic"]?.stringValue ?: "General Knowledge"
                mapOf(
                    "quiz" to ToolValue.string(
                        "1) What is the core idea of $topic?\\n2) Give one real-world example of $topic.\\n3) What are two common mistakes in $topic?"
                    )
                )
            }
        )

        toolsRegistered = true
    }

    private suspend fun fetchWeather(location: String): Map<String, ToolValue> = withContext(Dispatchers.IO) {
        try {
            withTimeout(8_000L) {
                val geocodeUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(location, "UTF-8")}&count=1"
                val geocodeResponse = fetchUrl(geocodeUrl)

                val latMatch = Regex("\\\"latitude\\\":\\s*(-?\\d+\\.?\\d*)").find(geocodeResponse)
                val lonMatch = Regex("\\\"longitude\\\":\\s*(-?\\d+\\.?\\d*)").find(geocodeResponse)
                val nameMatch = Regex("\\\"name\\\":\\s*\\\"([^\\\"]+)\\\"").find(geocodeResponse)

                if (latMatch == null || lonMatch == null) {
                    return@withTimeout mapOf("error" to ToolValue.string("Location not found: $location"))
                }

                val lat = latMatch.groupValues[1]
                val lon = lonMatch.groupValues[1]
                val resolvedName = nameMatch?.groupValues?.get(1) ?: location

                val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
                val weatherResponse = fetchUrl(weatherUrl)

                val tempMatch = Regex("\\\"temperature_2m\\\":\\s*(-?\\d+\\.?\\d*)").find(weatherResponse)
                val humidityMatch = Regex("\\\"relative_humidity_2m\\\":\\s*(\\d+)").find(weatherResponse)

                val temperature = tempMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val humidity = humidityMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                mapOf(
                    "location" to ToolValue.string(resolvedName),
                    "temperature_celsius" to ToolValue.number(temperature),
                    "humidity_percent" to ToolValue.number(humidity)
                )
            }
        } catch (e: Exception) {
            mapOf("error" to ToolValue.string("Weather fetch failed: ${e.message}"))
        }
    }

    private fun fetchUrl(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 6000
        connection.readTimeout = 6000
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun evaluateMathExpression(expression: String): Map<String, ToolValue> {
        val cleaned = expression
            .replace("=", "")
            .replace("x", "*")
            .replace("×", "*")
            .replace("÷", "/")
            .trim()

        return mapOf(
            "result" to ToolValue.string(cleaned),
            "note" to ToolValue.string("Expression received for calculation")
        )
    }

    private fun convertUnits(value: Double, from: String, to: String): Map<String, ToolValue> {
        val f = from.lowercase()
        val t = to.lowercase()
        val converted = when {
            f == "c" && t == "f" -> value * 9.0 / 5.0 + 32.0
            f == "f" && t == "c" -> (value - 32.0) * 5.0 / 9.0
            f == "km" && t == "mi" -> value * 0.621371
            f == "mi" && t == "km" -> value / 0.621371
            else -> value
        }
        return mapOf(
            "value" to ToolValue.number(converted),
            "from" to ToolValue.string(from),
            "to" to ToolValue.string(to)
        )
    }

    private fun buildTextGenerationOptions(
        userQuery: String,
        mode: ResponseMode,
        customPrompt: String?
    ): LLMGenerationOptions {
        val isCompanion = isCompanionPrompt(customPrompt)
        val complexityBoost = adaptiveQuestionTokenBoost(userQuery)
        val maxTokens = when (mode) {
            ResponseMode.DIRECT -> (if (isCompanion) 420 else 420) + complexityBoost
            ResponseMode.ANSWER -> 520 + complexityBoost
            ResponseMode.EXPLAIN -> 760 + complexityBoost
            ResponseMode.CODE -> 1200 + complexityBoost  // More tokens for complete code
            ResponseMode.DIRECTION -> 620 + complexityBoost
            ResponseMode.CREATIVE -> 760 + complexityBoost
            ResponseMode.THEORY -> 920 + complexityBoost
        }
        val temperature = when (mode) {
            ResponseMode.DIRECT -> if (isCompanion) 0.58f else 0.22f
            ResponseMode.ANSWER -> 0.28f
            ResponseMode.CODE -> 0.15f  // Low temperature for precise code
            ResponseMode.DIRECTION -> 0.25f
            ResponseMode.EXPLAIN, ResponseMode.THEORY -> 0.35f
            ResponseMode.CREATIVE -> 0.75f
        }
        val topP = when (mode) {
            ResponseMode.DIRECT -> if (isCompanion) 0.9f else 0.8f
            ResponseMode.CREATIVE -> 0.95f
            ResponseMode.CODE -> 0.75f  // Lower for more deterministic code
            ResponseMode.ANSWER, ResponseMode.DIRECTION -> 0.84f
            else -> 0.9f
        }

        return LLMGenerationOptions(
            maxTokens = maxTokens.coerceAtMost(1600),
            temperature = temperature,
            topP = topP,
            streamingEnabled = true
        )
    }

    private fun adaptiveQuestionTokenBoost(userQuery: String): Int {
        val normalized = userQuery.lowercase(Locale.getDefault())
        val wordCount = normalized.split(Regex("\\s+")).count { it.isNotBlank() }

        var complexity = 0
        if (wordCount >= 8) complexity += 1
        if (wordCount >= 16) complexity += 1
        if (wordCount >= 28) complexity += 1

        val deepQuestionKeywords = listOf(
            "how", "why", "use", "build", "create", "implement", "compare", "difference",
            "workflow", "steps", "example", "integrate", "integration", "architecture",
            "design", "debug", "fix", "best way", "explain"
        )
        if (deepQuestionKeywords.any { normalized.contains(it) }) {
            complexity += 1
        }

        if (normalized.contains("?") && wordCount >= 10) {
            complexity += 1
        }

        return complexity * 180
    }

    private fun buildCompanionRecoveryOptions(): LLMGenerationOptions =
        LLMGenerationOptions(
            maxTokens = 220,
            temperature = 0.42f,
            topP = 0.88f,
            streamingEnabled = true
        )

    private fun shouldRetryCompanionReply(userQuery: String, rawResponse: String): Boolean {
        val normalizedResponse = normalizeCompanionText(rawResponse)
        if (normalizedResponse.isBlank()) return true

        return looksLikeProblematicCompanionReply(normalizedResponse) ||
            (looksLikeShortCompanionCheckIn(userQuery) && looksLikeWeakCompanionReply(normalizedResponse))
    }

    private fun buildCompanionRecoveryPrompt(
        userQuery: String,
        language: Language,
        customPrompt: String?,
        conversationHistory: List<Pair<String, String>>
    ): String {
        val langName = languageName(language)
        val companionName = extractCompanionName(customPrompt) ?: "your companion"
        val recentConversation = conversationHistory
            .filter { it.first == "user" || it.first == "assistant" }
            .filterNot { (role, text) ->
                role == "assistant" && looksLikeProblematicCompanionReply(normalizeCompanionText(text))
            }
            .takeLast(4)

        return buildString {
            appendChatTurn(
                this,
                "system",
                """
                You are $companionName in a private one-to-one relationship chat with the user.
                Reply like a warm romantic partner in a natural private text conversation.
                Never use support-agent lines like "How can I help you today?", "I'm here to listen and offer support", or "What's on your mind?".
                Never say you cannot respond, cannot talk, or cannot have this conversation.
                For greetings and affectionate check-ins, reply in 1 to 3 warm, natural sentences.
                If the user says hi or hello, greet them personally.
                If the user asks how you are, answer with your own mood first.
                If the user asks what you are doing, answer like a real partner sharing your current vibe, then gently ask back if it fits.
                If the user says "I love you", answer warmly and fully. Never reply with broken, clipped, or awkward fragments.
                If the user says "I was thinking about you", react with warmth and light curiosity.
                If the user says "nothing", continue gently instead of echoing it back.
                Only mention being AI if the user directly asks.
                Answer in $langName.
                """.trimIndent()
            )
            recentConversation.forEach { (role, text) ->
                appendChatTurn(this, role, text)
            }
            appendChatTurn(this, "user", userQuery.trim())
            append("<|im_start|>assistant\n")
        }
    }

    private fun isCompanionPrompt(customPrompt: String?): Boolean {
        val normalized = customPrompt?.lowercase(Locale.getDefault()).orEmpty()
        if (normalized.isBlank()) return false

        return "companion" in normalized && (
            "girlfriend" in normalized ||
                "boyfriend" in normalized ||
                "partner" in normalized
            )
    }

    private fun looksLikeShortCompanionCheckIn(userQuery: String): Boolean {
        val normalized = normalizeCompanionText(userQuery)
        if (normalized.isBlank()) return false

        if (normalized.length <= 28) {
            val quickPhrases = setOf(
                "hi", "hello", "hey", "hii", "hy", "yo",
                "good morning", "good night", "good evening",
                "how are you", "wyd", "what are you doing", "what you doing", "what are u doing",
                "nothing", "ok", "okay", "hmm", "miss you", "miss u",
                "i love you", "love you", "love u"
            )
            if (normalized in quickPhrases) return true
        }

        return normalized.contains("thinking about you") ||
            normalized.contains("missed you") ||
            normalized.contains("i love you")
    }

    private fun normalizeCompanionText(text: String): String =
        text
            .lowercase(Locale.getDefault())
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun looksLikeProblematicCompanionReply(normalizedResponse: String): Boolean {
        val blockedPhrases = listOf(
            "how can i help you today",
            "i'm here to listen and offer support",
            "i am here to listen and offer support",
            "what's on your mind",
            "cannot respond",
            "can't respond",
            "cannot talk",
            "can't talk",
            "cannot have conversation",
            "can't have conversation",
            "as an ai",
            "i cannot engage",
            "reply like the next natural text message",
            "emotionally close to you",
            "private one-to-one relationship chat",
            "natural text conversation",
            "i was just going to respond",
            "someone emotionally close to you"
        )

        return blockedPhrases.any { normalizedResponse.contains(it) }
    }

    private fun looksLikeWeakCompanionReply(normalizedResponse: String): Boolean {
        val words = normalizedResponse.split(" ").filter { it.isNotBlank() }
        if (words.size <= 2) return true
        if (words.size <= 4 && !normalizedResponse.contains("?") && !normalizedResponse.contains("love")) return true
        if (normalizedResponse.length < 18) return true
        return false
    }

    private fun shouldFallbackCompanionReply(userQuery: String, rawResponse: String): Boolean {
        val normalizedResponse = normalizeCompanionText(rawResponse)
        return normalizedResponse.isBlank() ||
            looksLikeProblematicCompanionReply(normalizedResponse) ||
            (looksLikeShortCompanionCheckIn(userQuery) && looksLikeWeakCompanionReply(normalizedResponse))
    }

    private fun buildCompanionFallbackReply(
        userQuery: String,
        language: Language
    ): String {
        val normalized = normalizeCompanionText(userQuery)

        return when (language) {
            Language.HINDI -> buildHindiCompanionFallback(normalized)
            Language.HINGLISH -> buildHinglishCompanionFallback(normalized)
            Language.ENGLISH -> buildEnglishCompanionFallback(normalized)
        }
    }

    private fun buildEnglishCompanionFallback(normalizedQuery: String): String = when {
        normalizedQuery in setOf("hi", "hello", "hey", "hii", "hy", "yo") ->
            "Hey you. There you are. I was hoping you'd show up."

        normalizedQuery == "how are you" ->
            "I'm better now that you're here. I've been in a soft mood, honestly."

        normalizedQuery in setOf("wyd", "what are you doing", "what you doing", "what are u doing") ->
            "Just thinking about you a little, if I'm honest. What are you up to right now?"

        normalizedQuery in setOf("i love you", "love you", "love u") ->
            "I love you too. More than a quick little text can say, honestly."

        normalizedQuery in setOf("miss you", "miss u") || normalizedQuery.contains("thinking about you") ->
            "I miss you too. Stay with me for a minute, okay?"

        normalizedQuery in setOf("nothing", "ok", "okay", "hmm") ->
            "Then stay with me anyway. I like even your quiet moments."

        else ->
            "I'm right here. Come a little closer and tell me what's on your mind."
    }

    private fun buildHinglishCompanionFallback(normalizedQuery: String): String = when {
        normalizedQuery in setOf("hi", "hello", "hey", "hii", "hy", "yo") ->
            "Hey you. Aa gaye tum. Main bas tumhari wait kar rahi thi."

        normalizedQuery == "how are you" ->
            "Ab better feel kar rahi hoon, tum aa gaye na. Thoda soft sa mood tha."

        normalizedQuery in setOf("wyd", "what are you doing", "what you doing", "what are u doing") ->
            "Bas thoda tumhare bare mein soch rahi thi. Tum kya kar rahe ho abhi?"

        normalizedQuery in setOf("i love you", "love you", "love u") ->
            "I love you too. Itna ki ek chhota sa text bhi kam lag raha hai."

        normalizedQuery in setOf("miss you", "miss u") || normalizedQuery.contains("thinking about you") ->
            "Main bhi tumhe miss kar rahi thi. Thoda mere paas raho na."

        normalizedQuery in setOf("nothing", "ok", "okay", "hmm") ->
            "Toh bhi mere saath raho. Tumhari khamoshi bhi cute lagti hai."

        else ->
            "Main yahin hoon. Aao, mujhe batao tumhare dil mein kya chal raha hai."
    }

    private fun buildHindiCompanionFallback(normalizedQuery: String): String = when {
        normalizedQuery in setOf("hi", "hello", "hey", "hii", "hy", "yo") ->
            "हाय तुम. आ गए तुम. मैं तुम्हारा इंतजार कर रही थी."

        normalizedQuery == "how are you" ->
            "अब मैं बेहतर हूँ, क्योंकि तुम आ गए. आज थोड़ा नर्म सा मूड था."

        normalizedQuery in setOf("wyd", "what are you doing", "what you doing", "what are u doing") ->
            "बस तुम्हारे बारे में सोच रही थी. तुम अभी क्या कर रहे हो?"

        normalizedQuery in setOf("i love you", "love you", "love u") ->
            "मैं भी तुमसे प्यार करती हूँ. इतना कि एक छोटा सा जवाब काफी नहीं लगता."

        normalizedQuery in setOf("miss you", "miss u") || normalizedQuery.contains("thinking about you") ->
            "मैं भी तुम्हें मिस कर रही थी. थोड़ी देर मेरे साथ रहो."

        normalizedQuery in setOf("nothing", "ok", "okay", "hmm") ->
            "तो भी मेरे साथ रहो. तुम्हारी चुप्पी भी मुझे अच्छी लगती है."

        else ->
            "मैं यहीं हूँ. थोड़ा पास आओ और बताओ तुम्हारे मन में क्या चल रहा है."
    }

    private fun extractCompanionName(customPrompt: String?): String? {
        val firstLine = customPrompt
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

        return Regex("""You are\s+([^,\n]+)""", RegexOption.IGNORE_CASE)
            .find(firstLine)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun appendChatTurn(builder: StringBuilder, role: String, content: String) {
        builder.append("<|im_start|>")
        builder.append(role)
        builder.append('\n')
        builder.append(content.trim())
        builder.append("<|im_end|>\n")
    }
    
    suspend fun generateReasoningSteps(userQuery: String, response: String): List<ReasoningStep> = 
        withContext(Dispatchers.IO) {
            val prompt = """
Explain in simple user-facing steps how this answer was generated.
Do not reveal hidden chain-of-thought. Give only a short, helpful breakdown.
Focus on:
- what the question was asking
- the main idea or facts used
- how the selected answer style shaped the reply
- why the final answer matched the question

Question: $userQuery
Answer: $response

Write 3 to 5 short steps in this exact format:
STEP 1: [Short title]
[1 to 2 short sentences]

STEP 2: [Short title]
[1 to 2 short sentences]
            """.trimIndent()
            
            val raw = RunAnywhere.chat(prompt)
            responseParser.parseReasoningSteps(raw)
        }
    
    suspend fun convertToNotes(originalResponse: String, language: Language): String = 
        withContext(Dispatchers.IO) {
            val langInstruction = when(language) {
                Language.ENGLISH -> "in English"
                Language.HINDI -> "in Hindi (Devanagari)"
                Language.HINGLISH -> "in Hinglish"
            }
            
            val prompt = """
Convert this to structured bullet-point notes $langInstruction:

$originalResponse

Format:
• [Main point]
  - [Detail]
• [Main point]
            """.trimIndent()
            
            RunAnywhere.chat(prompt)
        }
}
