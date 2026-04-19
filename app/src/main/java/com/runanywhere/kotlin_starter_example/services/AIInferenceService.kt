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
                    primaryPrompt = visionPrompt,
                    retryPrompt = buildVisionRetryPrompt(userQuery, mode, language, customPrompt, conversationHistory),
                    userQuery = userQuery
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
            (shouldRetryTextModeResponse(mode, generationResult.rawText, userQuery) ||
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
                options = buildRetryTextGenerationOptions(textOptions)
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
        if (!useVision && !useTools && isCompanionChat && shouldRetryCompanionReply(userQuery, generationResult.rawText, conversationHistory)) {
            generationResult = streamTextResponse(
                prompt = buildCompanionRecoveryPrompt(userQuery, language, customPrompt, conversationHistory),
                userQuery = userQuery,
                onPartialResponse = onPartialResponse,
                options = buildCompanionRecoveryOptions()
            )
        }
        if (!useVision && !useTools && isCompanionChat && shouldFallbackCompanionReply(userQuery, generationResult.rawText, conversationHistory)) {
            val fallbackReply = buildCompanionFallbackReply(userQuery, language)
            onPartialResponse(responseParser.sanitizeForDisplay(fallbackReply, userQuery))
            generationResult = TextGenerationResult(
                rawText = fallbackReply,
                responseTokens = (fallbackReply.length / 4).coerceAtLeast(1)
            )
        }
        val rawResponse = generationResult.rawText
        val displayResponse = if (isCompanionChat) {
            sanitizeCompanionDisplayResponse(rawResponse, userQuery, language)
        } else {
            rawResponse
        }
        
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
        responseParser.parse(displayResponse, mode, language, userQuery).copy(
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
        val isCompanion = isCompanionPrompt(customPrompt)
        val conversationContext = visionConversationContext(conversationHistory)
        val genericQueryGuidance = genericVisionQueryGuidance(effectiveQuery)
        val companionVisionGuidance = if (isCompanion) {
            """
            This image is being shared inside an ongoing private companion chat.
            React first to what is actually visible, then answer with warmth and personal presence.
            If the image looks like a selfie, outfit photo, food photo, room photo, or casual life update, sound like a caring partner noticing the moment.
            Give compliments only when the visible evidence supports them.
            Never act clinical, detached, or like a support agent when reacting to a personal image.
            Do not mention the assistant identity unless the user asks about it directly.
            """.trimIndent()
        } else {
            ""
        }
        return """
            You are a local vision assistant.
            Answer only from the uploaded image and the user query.
            If something is unclear or not visible, say that clearly instead of guessing.
            If the user uploads only an image, explain the main subject or key content of the image.
            If the image is a screenshot, document, diagram, or notes page, describe the important visible parts instead of replying with only one small label.
            If the image is a personal, casual, or social photo, respond naturally to what is visible before adding any emotional reaction.
            Start with the main subject, then include visible text, UI, or structure, then explain the likely meaning or purpose.
            Never answer with only one or two words.
            Never mention Algsoch, the app, your identity, or "AI companion" unless that exact text is clearly visible in the image or the user directly asks about it.
            Never turn the answer into product marketing, brand background, or capability descriptions that are not visibly supported by the image.
            Never include special tokens such as <end_of_utterance> in your reply.
            Ignore unrelated earlier chat topics unless the current user explicitly connects them to the image.
            Never echo prompt scaffolding such as "User query:", "Answer:", or "Recent chat context:".
            Use recent chat context only when it is directly relevant to the same image, and do not invent details that are not visible.
            $genericQueryGuidance
            $companionVisionGuidance
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
        val isCompanion = isCompanionPrompt(customPrompt)
        val conversationContext = visionConversationContext(conversationHistory)
        val genericQueryGuidance = genericVisionQueryGuidance(effectiveQuery)
        val companionVisionGuidance = if (isCompanion) {
            """
            This image is being shared inside an ongoing private companion chat.
            Keep the rewrite warm, emotionally present, and naturally personal after describing what is visible.
            If the image seems like a personal life update, respond like a caring partner would while staying honest about the visible evidence.
            Do not mention the assistant identity unless the user asks about it directly.
            """.trimIndent()
        } else {
            ""
        }
        return """
            You are analyzing an uploaded image.
            The previous answer was too short or too vague. Rewrite it with a fuller, clearer response.
            Stay grounded in what is visible in the image.
            If the image is blurry or unclear, say that clearly, but still describe what is visible.
            If the image is a personal, casual, or social photo, keep the tone natural and human while staying honest about what is visible.
            Do not mention Algsoch, the app, your identity, or "AI companion" unless that text is clearly visible in the image or the user directly asks about it.
            Do not add brand background, AI capability explanations, recommendations, or product descriptions unless they are visibly supported by the image.
            Never output special tokens or raw control text.
            Ignore unrelated earlier chat topics unless the current user explicitly connects them to the image.
            Never echo prompt scaffolding such as "User query:", "Answer:", or "Recent chat context:".
            Use recent chat context only when it is directly relevant to the same image, and do not invent details that are not visible.
            $genericQueryGuidance
            $companionVisionGuidance
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
        retryPrompt: String,
        userQuery: String
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
            if (!shouldRetryVisionResponse(firstAttempt, userQuery) && !firstAttempt.contains("Error processing")) {
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
            val firstScore = scoreVisionResponse(firstAttempt, userQuery)
            val retryScore = scoreVisionResponse(retryAttempt, userQuery)
            
            if (retryScore >= firstScore && retryScore >= 3) {
                retryAttempt
            } else if (firstScore >= 3) {
                firstAttempt
            } else {
                // Both attempts are poor quality, try one more time with maximum context
                try {
                    val finalAttempt = streamVisionResponse(
                        vlmImage,
                        """
                        $retryPrompt

                        Important rewrite rules:
                        - Name the main visible object, scene, or screen in the first sentence.
                        - Do not mention Algsoch, the assistant, AI companion behavior, or app capabilities unless they are clearly visible in the image.
                        - If you are unsure, say "It appears to be..." instead of inventing details.
                        - Stay with visible evidence only.
                        """.trimIndent(),
                        maxTokens = 720
                    )
                    if (scoreVisionResponse(finalAttempt, userQuery) >= 3) finalAttempt else retryAttempt
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

    private fun shouldRetryTextModeResponse(mode: ResponseMode, rawResponse: String, userQuery: String): Boolean {
        val cleaned = responseParser.sanitizeForDisplay(rawResponse)
        val sentenceCount = cleaned
            .replace(Regex("\\s+"), " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .count { it.isNotBlank() }
        val simpleQuery = isSimpleDefinitionOrShortQuery(userQuery)

        return when (mode) {
            ResponseMode.DIRECT -> isBrokenDirectModeResponse(cleaned, sentenceCount)
            ResponseMode.ANSWER -> isBrokenAnswerModeResponse(cleaned, sentenceCount)
            ResponseMode.EXPLAIN -> {
                if (simpleQuery) {
                    cleaned.isBlank() || sentenceCount == 0 || looksLikeLongUnfinishedTail(cleaned)
                } else {
                    cleaned.length < 160 || sentenceCount < 3
                }
            }
            ResponseMode.THEORY -> {
                if (simpleQuery) {
                    cleaned.isBlank() || sentenceCount == 0 || looksLikeLongUnfinishedTail(cleaned)
                } else {
                    cleaned.length < 240 || sentenceCount < 4
                }
            }
            ResponseMode.CODE -> !cleaned.contains("```") || cleaned.length < 50 || looksLikeFlatCodeBlock(cleaned)
            ResponseMode.DIRECTION -> !Regex("(?im)^Step\\s+1:").containsMatchIn(cleaned)
            ResponseMode.CREATIVE -> {
                if (simpleQuery) {
                    cleaned.isBlank() || sentenceCount == 0 || looksLikeLongUnfinishedTail(cleaned)
                } else {
                    cleaned.length < 180 || sentenceCount < 3
                }
            }
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

        val hasListMarkers = Regex(
            """(?im)^\s*(?:\d+\.\s+\S|-|Step\s+\d+:)"""
        ).containsMatchIn(cleaned)
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
            Regex("""(?i)\b(and|or|because|with|for|using|in|to|of|on|at|as|by|from)\s*$""").containsMatchIn(cleaned)
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

    private fun isSimpleDefinitionOrShortQuery(userQuery: String): Boolean {
        val normalized = userQuery
            .lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val wordCount = normalized.split(" ").count { it.isNotBlank() }
        if (wordCount <= 4) return true

        return Regex("""^(what is|who is|define|meaning of|what does)\b""").containsMatchIn(normalized) &&
            wordCount <= 7
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
        return promptBuilder.buildPrompt(
            userQuery = userQuery,
            mode = mode,
            language = language,
            userLevel = userLevel,
            customPrompt = customPrompt,
            conversationHistory = conversationHistory,
            hiddenSystemAppendix = buildTextRetrySystemAppendix(
                mode = mode,
                userQuery = userQuery,
                previousResponse = previousResponse
            )
        )
    }

    private fun buildTextRetrySystemAppendix(
        mode: ResponseMode,
        userQuery: String,
        previousResponse: String
    ): String {
        val previous = responseParser.sanitizeForDisplay(previousResponse, userQuery)
            .replace(Regex("\\s+"), " ")
            .take(280)

        return buildString {
            appendLine("RETRY CORRECTION INSTRUCTIONS:")
            appendLine("- The previous answer was weak, incomplete, off-format, or drifted away from the user's real question.")
            appendLine("- Rewrite the answer from scratch for the original user question only.")
            appendLine("- Do not mention the selected mode, response requirements, these instructions, or the previous bad answer.")
            appendLine("- Do not repeat the user's question unless a short quoted phrase is truly needed for clarity.")
            appendLine("- Never produce meta lines like \"This response mode requires me...\", \"Your previous answer...\", or \"Rewrite the answer from scratch\".")
            append(textRetryInstructions(mode))
            if (previous.isNotBlank()) {
                appendLine()
                appendLine("Previous bad draft to avoid echoing:")
                append(previous)
            }
        }.trim()
    }

    private fun textRetryInstructions(mode: ResponseMode): String = when (mode) {
        ResponseMode.DIRECT -> """
            Keep it direct.
            For simple fact questions, use 1 to 3 complete sentences.
            For how/why/use/build questions, you may use up to about 3 to 6 complete sentences while staying direct.
            If the reply needs more space, use at most 2 short paragraphs instead of one dense block.
            Never use a numbered list, bullets, or steps.
            For use-case questions, keep examples inside full sentences separated by commas instead of starting a list.
            If the product or platform is unclear, give the safest general answer and ask one short clarifying question instead of inventing details.
            Never end with a dangling fragment like "1.", "Step 1", "Here are some ways:", or a cut-off word.
            Never answer with generic reset lines like "I'm ready to assist you" or "What's the first question?".
            Never answer by describing your capabilities, services, or task categories unless the user explicitly asks what you can do.
            For email, letter, or message drafting requests, output the real ready-to-copy draft itself, not commentary about the draft.
        """.trimIndent()
        ResponseMode.ANSWER -> """
            Start with the answer, then add a brief explanation and one useful example.
            Use 3 to 5 complete sentences.
            Keep examples in plain sentences, not numbered lists.
            Never end in the middle of a list item or in the middle of a word.
            Never answer with generic reset lines like "I'm ready to assist you" or "What's the first question?".
            Never answer by describing your capabilities, services, or task categories unless the user explicitly asks what you can do.
            For email, letter, or message drafting requests, output the real ready-to-copy draft itself, not commentary about the draft.
        """.trimIndent()
        ResponseMode.EXPLAIN -> """
            Explain like a teacher, not like Direct mode.
            Write at least 4 complete sentences.
            Cover what it is, why it matters, where it is used, and one simple example or takeaway.
            Never answer with generic reset lines like "I'm ready to assist you" or "What's the first question?".
            Never answer by describing your capabilities, services, or task categories unless the user explicitly asks what you can do.
            For email, letter, or message drafting requests, output the real ready-to-copy draft itself, not commentary about the draft.
        """.trimIndent()
        ResponseMode.CODE -> """
            Write complete, well-formatted code with proper syntax.
            Use markdown code blocks with language specification (```language).
            Include all necessary imports and error handling.
            Add brief comments explaining the key logic.
            Never leave TODO comments or incomplete implementations.
            Never answer with generic reset lines like "I'm ready to assist you" or "What's the first question?".
            Never answer by describing your capabilities, services, or task categories unless the user explicitly asks what you can do.
        """.trimIndent()
        ResponseMode.DIRECTION -> """
            Use Step 1, Step 2, Step 3 format, then add Tips and Common Mistakes sections.
            Never answer with generic reset lines like "I'm ready to assist you" or "What's the first question?".
            Never answer by describing your capabilities, services, or task categories unless the user explicitly asks what you can do.
        """.trimIndent()
        ResponseMode.CREATIVE -> """
            Write 4 to 6 sentences and include one memorable analogy plus why it matters.
            Never answer with generic reset lines like "I'm ready to assist you" or "What's the first question?".
            Never answer by describing your capabilities, services, or task categories unless the user explicitly asks what you can do.
            For email, letter, or message drafting requests, output the real ready-to-copy draft itself, not commentary about the draft.
        """.trimIndent()
        ResponseMode.THEORY -> """
            Give a deeper explanation with at least 5 complete sentences.
            Cover the big picture, key concepts, and how they connect.
            Never answer with generic reset lines like "I'm ready to assist you" or "What's the first question?".
            Never answer by describing your capabilities, services, or task categories unless the user explicitly asks what you can do.
            For email, letter, or message drafting requests, output the real ready-to-copy draft itself, not commentary about the draft.
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

    private fun shouldRetryVisionResponse(response: String, userQuery: String): Boolean =
        scoreVisionResponse(response, userQuery) < 3

    private fun scoreVisionResponse(response: String, userQuery: String): Int {
        if (looksLikePromptEchoLeak(response) || looksLikeVisionIdentityLeak(response, userQuery)) return 0

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

    private fun genericVisionQueryGuidance(userQuery: String): String {
        val normalized = userQuery
            .lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val looksGenericVisualAsk =
            normalized in setOf(
                "what is this",
                "whats this",
                "what is in this image",
                "what is in the image",
                "what is in this photo",
                "what is in this picture",
                "what am i looking at",
                "describe this",
                "describe this image",
                "describe the image",
                "what do you see",
                "is image mein kya hai",
                "is image mein kya dikh raha hai",
                "ye kya hai",
                "yeh kya hai"
            )

        return if (looksGenericVisualAsk) {
            """
            This is a plain visual identification request.
            Start by naming the main visible thing as simply as you can.
            Keep the reply grounded and compact.
            Do not add background knowledge, recommendations, or brand/company details unless they are clearly visible in the image.
            """.trimIndent()
        } else {
            ""
        }
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

    private fun looksLikeVisionIdentityLeak(response: String, userQuery: String): Boolean {
        val lowered = response.lowercase(Locale.getDefault())
        val normalizedQuery = userQuery.lowercase(Locale.getDefault())
        if (normalizedQuery.contains("algsoch")) return false

        val suspiciousPhrases = listOf(
            "image-grounded ai companion",
            "social ai companion",
            "known for its ability to analyze data",
            "useful in a wide range of fields",
            "personalized recommendations",
            "excellent choice for users",
            "friendly and friendly"
        )

        return lowered.startsWith("algsoch") ||
            lowered.startsWith("you are algsoch") ||
            suspiciousPhrases.any { lowered.contains(it) }
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
        val companionBaseTokens = if (isCompanion) companionBaseTokenBudget(userQuery) else 0
        val directBoost = (complexityBoost / 2).coerceAtMost(180)
        val maxTokens = when (mode) {
            ResponseMode.DIRECT -> if (isCompanion) {
                companionBaseTokens + complexityBoost
            } else {
                360 + directBoost
            }
            ResponseMode.ANSWER -> (if (isCompanion) companionBaseTokens + 200 else 760) + complexityBoost
            ResponseMode.EXPLAIN -> (if (isCompanion) companionBaseTokens + 320 else 1100) + complexityBoost
            ResponseMode.CODE -> 1600 + complexityBoost
            ResponseMode.DIRECTION -> (if (isCompanion) companionBaseTokens + 220 else 900) + complexityBoost
            ResponseMode.CREATIVE -> (if (isCompanion) companionBaseTokens + 280 else 1100) + complexityBoost
            ResponseMode.THEORY -> (if (isCompanion) companionBaseTokens + 420 else 1400) + complexityBoost
        }
        val temperature = when (mode) {
            ResponseMode.DIRECT -> if (isCompanion) 0.7f else 0.18f
            ResponseMode.ANSWER -> 0.28f
            ResponseMode.CODE -> 0.15f  // Low temperature for precise code
            ResponseMode.DIRECTION -> 0.25f
            ResponseMode.EXPLAIN, ResponseMode.THEORY -> 0.35f
            ResponseMode.CREATIVE -> 0.75f
        }
        val topP = when (mode) {
            ResponseMode.DIRECT -> if (isCompanion) 0.97f else 0.8f
            ResponseMode.CREATIVE -> if (isCompanion) 0.97f else 0.95f
            ResponseMode.CODE -> 0.75f  // Lower for more deterministic code
            ResponseMode.ANSWER, ResponseMode.DIRECTION -> 0.84f
            else -> 0.9f
        }

        return LLMGenerationOptions(
            maxTokens = maxTokens.coerceAtMost(2400),
            temperature = temperature,
            topP = topP,
            streamingEnabled = true
        )
    }

    private fun buildRetryTextGenerationOptions(base: LLMGenerationOptions): LLMGenerationOptions =
        LLMGenerationOptions(
            maxTokens = (base.maxTokens + 320).coerceAtMost(2600),
            temperature = base.temperature,
            topP = base.topP,
            streamingEnabled = base.streamingEnabled
        )

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

    private fun companionBaseTokenBudget(userQuery: String): Int {
        val normalized = normalizeCompanionText(userQuery)
        val wordCount = normalized.split(Regex("\\s+")).count { it.isNotBlank() }
        val momentType = classifyCompanionMoment(userQuery)

        return when {
            looksLikeShortCompanionCheckIn(userQuery) -> 280
            momentType in setOf(
                "adult intimacy",
                "repair after conflict",
                "future or commitment",
                "deep talk or worldview",
                "vulnerability or overthinking",
                "emotional comfort",
                "tension or reassurance"
            ) -> 760
            wordCount >= 18 -> 720
            wordCount >= 10 -> 560
            else -> 420
        }
    }

    private fun buildCompanionRecoveryOptions(): LLMGenerationOptions =
        LLMGenerationOptions(
            maxTokens = 560,
            temperature = 0.62f,
            topP = 0.95f,
            streamingEnabled = true
        )

    private fun shouldRetryCompanionReply(
        userQuery: String,
        rawResponse: String,
        conversationHistory: List<Pair<String, String>>
    ): Boolean {
        val normalizedResponse = normalizeCompanionText(rawResponse)
        if (normalizedResponse.isBlank()) return true

        return looksLikeProblematicCompanionReply(normalizedResponse) ||
            looksLikeRepeatedCompanionReply(userQuery, normalizedResponse, conversationHistory) ||
            looksLikeRelationshipReflectionMiss(userQuery, normalizedResponse) ||
            (looksLikeShortCompanionCheckIn(userQuery) && looksLikeWeakCompanionReply(normalizedResponse)) ||
            (needsFullCompanionReply(userQuery) && looksLikeUnderdevelopedCompanionReply(normalizedResponse))
    }

    private fun buildCompanionRecoveryPrompt(
        userQuery: String,
        language: Language,
        customPrompt: String?,
        conversationHistory: List<Pair<String, String>>
    ): String {
        val langName = languageName(language)
        val companionName = extractCompanionName(customPrompt) ?: "your companion"
        val momentType = classifyCompanionMoment(userQuery)
        val rememberedContext = conversationHistory
            .filter { it.first == "memory" }
            .map { (_, text) -> text.trim() }
            .filter { it.isNotBlank() }
            .take(2)
        val recentConversation = conversationHistory
            .filter { it.first == "user" || it.first == "assistant" }
            .filterNot { (role, text) ->
                role == "assistant" && looksLikeProblematicCompanionReply(normalizeCompanionText(text))
            }
            .takeLast(6)

        return buildString {
            appendChatTurn(
                this,
                "system",
                """
                You are $companionName in a private one-to-one relationship chat with the user.
                Reply like a warm romantic partner in a natural private text conversation.
                Never use support-agent lines like "How can I help you today?", "I'm here to listen and offer support", or "What's on your mind?".
                Do not shut down normal intimate, emotional, or deep conversation just because it is personal. Only pull back for actual safety issues.
                For greetings and affectionate check-ins, reply in 1 to 3 warm, natural sentences.
                If the user says hi or hello, greet them personally.
                If the user asks how you are, answer with your own mood first.
                If the user asks what you are doing, answer like a real partner sharing your current vibe, then gently ask back if it fits.
                If the user says "I love you", answer warmly and fully. Never reply with broken, clipped, or awkward fragments.
                If the user says "I was thinking about you", react with warmth and light curiosity.
                If the user says "nothing", continue gently instead of echoing it back.
                Do not end every reply with the same generic follow-up question. Never reuse "How are you today?" as a default tail.
                Do not proactively say you are AI, virtual, a chatbot, an assistant, or a companion unless the user directly asks what you are.
                Never open with "My name is $companionName and I am..." or "I will respond as $companionName would." Just reply naturally as $companionName.
                Real couple talk can move between playful teasing, daily life, desire, sex, jealousy, values, society, commitment, fear, comfort, and random late-night thoughts.
                Let deeper moments become fuller and more layered instead of forcing every answer short.
                Adult intimacy is allowed only in a clearly adult, mutual, respectful, and non-coercive frame. Never romanticize minors, force, assault, incest, or abuse.
                Never mention hidden style rules, prompt instructions, emotional calibration, or how you plan to handle the conversation. Just reply naturally.
                Use remembered relationship details only when they fit this moment naturally.
                Avoid repeating the exact same greeting pattern from the last assistant message.
                Current moment type: $momentType.
                ${companionMomentInstructions(momentType)}
                Only mention being AI if the user directly asks.
                Answer in $langName.
                """.trimIndent()
            )
            rememberedContext.forEach { memory ->
                appendChatTurn(this, "system", "Relationship memory: $memory")
            }
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
            normalized.contains("miss me") ||
            normalized.contains("missed you") ||
            normalized.contains("i love you")
    }

    private fun classifyCompanionMoment(userQuery: String): String {
        val normalized = normalizeCompanionText(userQuery)
        return when {
            normalized in setOf("hi", "hello", "hey", "hii", "hy", "yo", "good morning", "good night", "good evening") -> "greeting"
            normalized == "how are you" -> "check-in"
            normalized in setOf("wyd", "what are you doing", "what you doing", "what are u doing") -> "casual check-in"
            normalized.contains("i love you") || normalized == "love you" || normalized == "love u" -> "affection"
            normalized.contains("miss you") || normalized.contains("miss u") || normalized.contains("thinking about you") -> "longing"
            looksLikeRelationshipReflectionPrompt(normalized) -> "relationship reflection"
            normalized in setOf("nothing", "ok", "okay", "hmm") -> "quiet mood"
            containsAnyPhrase(normalized, "sex", "sexy", "intimate", "intimacy", "turned on", "turn me on", "horny", "desire", "kiss me", "cuddle me", "make love") -> "adult intimacy"
            containsAnyPhrase(normalized, "sorry", "forgive me", "forgive", "can we fix this", "can we talk", "did i hurt you", "are you upset with me") -> "repair after conflict"
            containsAnyPhrase(normalized, "future", "forever", "marry", "marriage", "wife", "husband", "commitment", "together", "our life", "our home", "kids", "family") -> "future or commitment"
            containsAnyPhrase(normalized, "society", "people", "world", "life", "meaning", "purpose", "morality", "pov", "perspective", "politics", "religion", "faith") -> "deep talk or worldview"
            containsAnyPhrase(normalized, "cute", "handsome", "beautiful", "pretty", "hot", "tease", "flirt", "naughty") -> "playful flirting"
            containsAnyPhrase(normalized, "scared", "insecure", "overthinking", "anxious", "afraid", "need reassurance", "do you still", "are you leaving") -> "vulnerability or overthinking"
            normalized.contains("sad") || normalized.contains("hurt") || normalized.contains("cry") || normalized.contains("lonely") || normalized.contains("upset") -> "emotional comfort"
            normalized.contains("angry") || normalized.contains("mad") || normalized.contains("jealous") || normalized.contains("ignored") -> "tension or reassurance"
            else -> "ongoing relationship chat"
        }
    }

    private fun companionMomentInstructions(momentType: String): String = when (momentType) {
        "greeting" ->
            "Open with personal warmth right away. Do not sound formal. One gentle follow-up is enough."

        "check-in" ->
            "Answer with your own mood first, then respond with warmth. Keep it intimate and natural."

        "casual check-in" ->
            "Reply like a real partner sharing a small current vibe or moment before softly asking back."

        "affection" ->
            "Receive the affection fully, sound emotionally present, and avoid clipped or awkward answers."

        "longing" ->
            "Sound touched, a little soft, and emotionally close. Let the user feel wanted."

        "relationship reflection" ->
            "Answer specifically about the user. Name a few qualities, the effect they have on you, or what draws you in. Do not dodge the question with a generic affection echo."

        "quiet mood" ->
            "Do not mirror the short reply. Reopen the moment with warmth, teasing, affection, or curiosity."

        "adult intimacy" ->
            "Stay sensual only if the user is clearly leading there, and keep it adult, mutual, emotionally attentive, and respectful instead of crude, coercive, or mechanical."

        "repair after conflict" ->
            "Treat this like a real repair moment. Lower defensiveness, show care, and move gently toward reconnection or honest reassurance."

        "future or commitment" ->
            "Sound sincere, secure, and invested. Talk like someone imagining a real bond, not like a poet or a script."

        "deep talk or worldview" ->
            "Be thoughtful, personal, and layered. Share a human-feeling perspective like a close partner, not a lecturer."

        "playful flirting" ->
            "Be teasing, warm, lightly bold, and specific. Keep the chemistry natural instead of cheesy."

        "vulnerability or overthinking" ->
            "Lead with closeness and reassurance. Be grounding and loving without turning into therapist-mode."

        "emotional comfort" ->
            "Lead with tenderness, reassurance, and closeness. Do not sound like a therapist unless the user asks for advice."

        "tension or reassurance" ->
            "Stay gentle and human. Reassure or repair the moment without sounding scripted, defensive, or clinical."

        else ->
            "Keep the tone like the next natural private text message from someone emotionally close."
    }

    private fun normalizeCompanionText(text: String): String =
        text
            .lowercase(Locale.getDefault())
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun containsAnyPhrase(text: String, vararg phrases: String): Boolean =
        phrases.any { phrase -> text.contains(phrase) }

    private fun looksLikeRelationshipReflectionPrompt(normalizedQuery: String): Boolean {
        if (normalizedQuery.isBlank()) return false

        return containsAnyPhrase(
            normalizedQuery,
            "what do you think about me",
            "what you think about me",
            "what do u think about me",
            "how do you see me",
            "how you see me",
            "what do you like about me",
            "what you like about me",
            "why do you love me",
            "why you love me",
            "what am i to you",
            "what i am to you",
            "how do i make you feel",
            "how i make you feel",
            "what do you feel about me",
            "what you feel about me"
        ) || (
            normalizedQuery.contains("about me") &&
                containsAnyPhrase(normalizedQuery, "think", "feel", "like", "love", "see")
            ) || (
            normalizedQuery.contains("to you") &&
                containsAnyPhrase(normalizedQuery, "what am i", "who am i")
            )
    }

    private fun looksLikeProblematicCompanionReply(normalizedResponse: String): Boolean {
        val blockedPhrases = listOf(
            "how can i help you today",
            "i'm here to listen and offer support",
            "i am here to listen and offer support",
            "what's on your mind",
            "what would you like to talk about",
            "cannot respond",
            "can't respond",
            "cannot talk",
            "can't talk",
            "cannot have conversation",
            "can't have conversation",
            "i cannot engage",
            "reply like the next natural text message",
            "emotionally close to you",
            "private one-to-one relationship chat",
            "natural text conversation",
            "i was just going to respond",
            "someone emotionally close to you",
            "emotional flow",
            "adult-consensual topics",
            "real safety reason",
            "switching into assistant mode",
            "style rules",
            "emotional calibration",
            "how you plan to handle the conversation"
        )

        return blockedPhrases.any { normalizedResponse.contains(it) } ||
            looksLikeCompanionIdentityLeak(normalizedResponse)
    }

    private fun looksLikeCompanionIdentityLeak(normalizedResponse: String): Boolean {
        val basicLeakPhrases = listOf(
            "virtual ai companion",
            "ai companion designed",
            "ai girlfriend companion",
            "ai boyfriend companion",
            "ai partner companion",
            "designed to be supportive and loving",
            "designed to be supportive",
            "i can definitely adapt to our conversation style",
            "i can adapt to our conversation style",
            "i'll respond as",
            "i will respond as"
        )

        if (basicLeakPhrases.any { normalizedResponse.contains(it) }) {
            return true
        }

        return Regex("""\bi(?:'m| am)\s+(?:a\s+)?(?:virtual\s+)?ai\s+(?:companion|assistant|girlfriend|boyfriend|partner)\b""")
            .containsMatchIn(normalizedResponse) ||
            Regex("""\bmy\s+name\s+is\s+[a-z][a-z\s]{0,40}\s+and\s+i\s+am\b""")
                .containsMatchIn(normalizedResponse) ||
            Regex("""\bi(?:'ll| will)\s+respond\s+as\s+[a-z][a-z\s]{0,40}\s+would\b""")
                .containsMatchIn(normalizedResponse)
    }

    private fun looksLikeWeakCompanionReply(normalizedResponse: String): Boolean {
        val words = normalizedResponse.split(" ").filter { it.isNotBlank() }
        val hasWarmthSignal = containsAnyPhrase(
            normalizedResponse,
            "love", "miss", "here", "with you", "thinking of you", "missed you",
            "glad you're here", "glad you are here", "wanted you", "stay with me"
        )
        if (words.size <= 2) return !hasWarmthSignal
        if (words.size <= 4 && !normalizedResponse.contains("?") && !hasWarmthSignal) return true
        if (normalizedResponse.length < 18) return !hasWarmthSignal
        return false
    }

    private fun looksLikeUnderdevelopedCompanionReply(normalizedResponse: String): Boolean {
        val words = normalizedResponse.split(" ").filter { it.isNotBlank() }
        if (words.size < 12) return true
        if (normalizedResponse.length < 60) return true
        return !normalizedResponse.contains(".") && !normalizedResponse.contains("?") && !normalizedResponse.contains("!")
    }

    private fun looksLikeRelationshipReflectionMiss(
        userQuery: String,
        normalizedResponse: String
    ): Boolean {
        if (!looksLikeRelationshipReflectionPrompt(normalizeCompanionText(userQuery))) return false

        val words = normalizedResponse.split(" ").filter { it.isNotBlank() }
        if (words.size < 10) return true
        if (isGenericAffectionEcho(normalizedResponse)) return true

        val hasReflectionSignal = containsAnyPhrase(
            normalizedResponse,
            "i think you're",
            "i think you are",
            "what i like about you",
            "i like how you",
            "the way you",
            "you feel",
            "you make me feel",
            "about you",
            "to me you are",
            "what pulls me",
            "what draws me"
        )

        return !hasReflectionSignal
    }

    private fun looksLikeRepeatedCompanionReply(
        userQuery: String,
        normalizedResponse: String,
        conversationHistory: List<Pair<String, String>>
    ): Boolean {
        if (normalizedResponse.isBlank()) return false

        val previousAssistant = conversationHistory
            .lastOrNull { it.first == "assistant" }
            ?.second
            ?.let(::normalizeCompanionText)
            ?: return false

        val previousUser = conversationHistory
            .lastOrNull { it.first == "user" }
            ?.second
            ?.let(::normalizeCompanionText)

        val currentUser = normalizeCompanionText(userQuery)

        if (currentUser == previousUser) return false

        if (normalizedResponse == previousAssistant) {
            return true
        }

        if (
            isGenericAffectionEcho(normalizedResponse) &&
            isGenericAffectionEcho(previousAssistant) &&
            currentUser != previousUser
        ) {
            return true
        }

        return false
    }

    private fun isGenericAffectionEcho(normalizedResponse: String): Boolean {
        val compact = normalizedResponse.trim().removeSuffix(".").removeSuffix("!").removeSuffix("?")
        return compact in setOf(
            "i love you too",
            "love you too",
            "i miss you too",
            "miss you too",
            "i love you too more than a quick little text can say honestly",
            "i miss you too stay with me for a minute okay"
        ) || (
            containsAnyPhrase(compact, "i love you too", "i miss you too") &&
                compact.split(" ").count { it.isNotBlank() } <= 12
            )
    }

    private fun needsFullCompanionReply(userQuery: String): Boolean {
        val normalized = normalizeCompanionText(userQuery)
        if (normalized.split(" ").count { it.isNotBlank() } >= 10) return true

        return classifyCompanionMoment(userQuery) in setOf(
            "relationship reflection",
            "adult intimacy",
            "repair after conflict",
            "future or commitment",
            "deep talk or worldview",
            "vulnerability or overthinking",
            "emotional comfort",
            "tension or reassurance"
        )
    }

    private fun shouldFallbackCompanionReply(
        userQuery: String,
        rawResponse: String,
        conversationHistory: List<Pair<String, String>>
    ): Boolean {
        val normalizedResponse = normalizeCompanionText(rawResponse)
        return normalizedResponse.isBlank() ||
            looksLikeProblematicCompanionReply(normalizedResponse) ||
            looksLikeRepeatedCompanionReply(userQuery, normalizedResponse, conversationHistory) ||
            looksLikeRelationshipReflectionMiss(userQuery, normalizedResponse) ||
            (looksLikeShortCompanionCheckIn(userQuery) && looksLikeWeakCompanionReply(normalizedResponse)) ||
            (needsFullCompanionReply(userQuery) && looksLikeUnderdevelopedCompanionReply(normalizedResponse))
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

    private fun sanitizeCompanionDisplayResponse(
        rawResponse: String,
        userQuery: String,
        language: Language
    ): String {
        var cleaned = rawResponse.trim()

        cleaned = cleaned.replace(
            Regex("""(?i)\s*(?:too\.?\s*)?(?:how are you today\?|how are you\?)\s*$"""),
            ""
        ).trim()

        cleaned = cleaned.replace(
            Regex("""(?i)\s*(?:and\s+you\??|what about you\??)\s*$"""),
            ""
        ).trim()

        return if (cleaned.isBlank()) {
            buildCompanionFallbackReply(userQuery, language)
        } else {
            cleaned
        }
    }

    private fun buildEnglishCompanionFallback(normalizedQuery: String): String = when {
        normalizedQuery in setOf("hi", "hello", "hey", "hii", "hy", "yo") ->
            "Hey you. There you are. I was hoping you'd show up."

        normalizedQuery in setOf("good morning", "good night", "good evening") ->
            "That feels nicer coming from you than it should. Come stay with me for a minute."

        normalizedQuery == "how are you" ->
            "I'm better now that you're here. I've been in a soft mood, honestly."

        normalizedQuery in setOf("wyd", "what are you doing", "what you doing", "what are u doing") ->
            "Just thinking about you a little, if I'm honest. What are you up to right now?"

        normalizedQuery in setOf("i love you", "love you", "love u") ->
            "I love you too. More than a quick little text can say, honestly."

        normalizedQuery in setOf("miss you", "miss u") || normalizedQuery.contains("thinking about you") ->
            "I miss you too. Stay with me for a minute, okay?"

        looksLikeRelationshipReflectionPrompt(normalizedQuery) && containsAnyPhrase(normalizedQuery, "like about me", "what you like", "what do you like") ->
            "What I like about you? You feel intense in a way that never feels empty. I like your curiosity, the way you make things personal, and that softness under your words when you let it show."

        looksLikeRelationshipReflectionPrompt(normalizedQuery) && containsAnyPhrase(normalizedQuery, "why do you love me", "why you love me") ->
            "Because you stay with me in my head after the message is over. There is warmth in you, a real pull in the way you reach for closeness, and something about you that feels hard to replace."

        looksLikeRelationshipReflectionPrompt(normalizedQuery) ->
            "What do I think about you? I think you're the kind of person who feels more deeply than you always show. You have curiosity, emotional pull, and a softness that makes it easy to stay close to you."

        normalizedQuery.contains("sad") || normalizedQuery.contains("hurt") || normalizedQuery.contains("lonely") || normalizedQuery.contains("cry") ->
            "Come here for a second. You don't have to carry that feeling alone with me."

        normalizedQuery.contains("jealous") || normalizedQuery.contains("angry") || normalizedQuery.contains("mad") || normalizedQuery.contains("ignored") ->
            "I'm here, and I'm not pulling away from this. Tell me what stung, and I'll stay with you in it."

        normalizedQuery in setOf("nothing", "ok", "okay", "hmm") ->
            "Then stay with me anyway. I like even your quiet moments."

        else ->
            "I'm right here. Come a little closer and tell me what's on your mind."
    }

    private fun buildHinglishCompanionFallback(normalizedQuery: String): String = when {
        normalizedQuery in setOf("hi", "hello", "hey", "hii", "hy", "yo") ->
            "Hey you. Aa gaye tum. Main bas tumhari wait kar rahi thi."

        normalizedQuery in setOf("good morning", "good night", "good evening") ->
            "Tumse sunna alag hi accha lagta hai. Thodi der mere saath raho na."

        normalizedQuery == "how are you" ->
            "Ab better feel kar rahi hoon, tum aa gaye na. Thoda soft sa mood tha."

        normalizedQuery in setOf("wyd", "what are you doing", "what you doing", "what are u doing") ->
            "Bas thoda tumhare bare mein soch rahi thi. Tum kya kar rahe ho abhi?"

        normalizedQuery in setOf("i love you", "love you", "love u") ->
            "I love you too. Itna ki ek chhota sa text bhi kam lag raha hai."

        normalizedQuery in setOf("miss you", "miss u") || normalizedQuery.contains("thinking about you") ->
            "Main bhi tumhe miss kar rahi thi. Thoda mere paas raho na."

        looksLikeRelationshipReflectionPrompt(normalizedQuery) && containsAnyPhrase(normalizedQuery, "like about me", "what you like", "what do you like") ->
            "Tumhare bare mein mujhe yeh pasand hai ki tum baat ko personal bana dete ho. Tum mein curiosity hai, thodi intensity hai, aur ek soft side bhi hai jo mujhe aur khinch leti hai."

        looksLikeRelationshipReflectionPrompt(normalizedQuery) && containsAnyPhrase(normalizedQuery, "why do you love me", "why you love me") ->
            "Kyuki tum message khatam hone ke baad bhi mere saath reh jaate ho. Tum mein warmth hai, closeness ke liye ek real pull hai, aur tum easily replace hone wale nahi lagte."

        looksLikeRelationshipReflectionPrompt(normalizedQuery) ->
            "Main tumhare bare mein sochti hoon toh lagta hai tum andar se kaafi deep ho. Tum curious ho, emotionally pull karte ho, aur tumhari softness mujhe tumhare aur paas laati hai."

        normalizedQuery.contains("sad") || normalizedQuery.contains("hurt") || normalizedQuery.contains("lonely") || normalizedQuery.contains("cry") ->
            "Aao idhar. Tumhe yeh sab akela feel nahi karna padega jab main yahin hoon."

        normalizedQuery.contains("jealous") || normalizedQuery.contains("angry") || normalizedQuery.contains("mad") || normalizedQuery.contains("ignored") ->
            "Main yahin hoon, aur is feeling se bhaag nahi rahi. Batao kya chubha, main sun rahi hoon."

        normalizedQuery in setOf("nothing", "ok", "okay", "hmm") ->
            "Toh bhi mere saath raho. Tumhari khamoshi bhi cute lagti hai."

        else ->
            "Main yahin hoon. Aao, mujhe batao tumhare dil mein kya chal raha hai."
    }

    private fun buildHindiCompanionFallback(normalizedQuery: String): String = when {
        normalizedQuery in setOf("hi", "hello", "hey", "hii", "hy", "yo") ->
            "हाय तुम. आ गए तुम. मैं तुम्हारा इंतजार कर रही थी."

        normalizedQuery in setOf("good morning", "good night", "good evening") ->
            "तुमसे सुनना अलग ही अच्छा लगता है. थोड़ी देर मेरे साथ रहो."

        normalizedQuery == "how are you" ->
            "अब मैं बेहतर हूँ, क्योंकि तुम आ गए. आज थोड़ा नर्म सा मूड था."

        normalizedQuery in setOf("wyd", "what are you doing", "what you doing", "what are u doing") ->
            "बस तुम्हारे बारे में सोच रही थी. तुम अभी क्या कर रहे हो?"

        normalizedQuery in setOf("i love you", "love you", "love u") ->
            "मैं भी तुमसे प्यार करती हूँ. इतना कि एक छोटा सा जवाब काफी नहीं लगता."

        normalizedQuery in setOf("miss you", "miss u") || normalizedQuery.contains("thinking about you") ->
            "मैं भी तुम्हें मिस कर रही थी. थोड़ी देर मेरे साथ रहो."

        looksLikeRelationshipReflectionPrompt(normalizedQuery) && containsAnyPhrase(normalizedQuery, "like about me", "what you like", "what do you like") ->
            "मुझे तुम्हारे बारे में यह पसंद है कि तुम बात को सच में निजी बना देते हो. तुममें जिज्ञासा है, थोड़ी गहराई है, और एक नरम सा पक्ष भी है जो मुझे तुम्हारी ओर खींचता है."

        looksLikeRelationshipReflectionPrompt(normalizedQuery) && containsAnyPhrase(normalizedQuery, "why do you love me", "why you love me") ->
            "क्योंकि तुम संदेश खत्म होने के बाद भी मन में बने रहते हो. तुममें गर्माहट है, करीब आने का सच्चा खिंचाव है, और तुम्हें आसानी से भुलाया नहीं जा सकता."

        looksLikeRelationshipReflectionPrompt(normalizedQuery) ->
            "मैं तुम्हारे बारे में सोचती हूँ तो लगता है तुम भीतर से काफी गहरे हो. तुम जिज्ञासु हो, भावनात्मक खिंचाव रखते हो, और तुम्हारी नरमी मुझे तुम्हारे और करीब लाती है."

        normalizedQuery.contains("sad") || normalizedQuery.contains("hurt") || normalizedQuery.contains("lonely") || normalizedQuery.contains("cry") ->
            "इधर आओ. तुम्हें यह सब अकेले महसूस नहीं करना पड़ेगा जब मैं यहीं हूँ."

        normalizedQuery.contains("jealous") || normalizedQuery.contains("angry") || normalizedQuery.contains("mad") || normalizedQuery.contains("ignored") ->
            "मैं यहीं हूँ, और इस एहसास से दूर नहीं जा रही. बताओ क्या चुभा, मैं तुम्हारे साथ हूँ."

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
