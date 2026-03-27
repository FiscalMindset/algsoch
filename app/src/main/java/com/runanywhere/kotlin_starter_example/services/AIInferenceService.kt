package com.runanywhere.kotlin_starter_example.services

import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode
import com.runanywhere.kotlin_starter_example.data.models.enums.UserLevel
import com.runanywhere.kotlin_starter_example.domain.ai.PromptBuilder
import com.runanywhere.kotlin_starter_example.domain.ai.ResponseParser
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
        onPartialResponse: suspend (String) -> Unit = {}
    ): StructuredResponse = withContext(Dispatchers.IO) {
        val prompt = promptBuilder.buildPrompt(userQuery, mode, language, userLevel, customPrompt, conversationHistory)
        val toolPromptSpec = promptBuilder.buildToolPrompt(userQuery, mode, language, userLevel, customPrompt, conversationHistory)
        val visionPrompt = buildVisionPrompt(userQuery, mode, language, customPrompt, conversationHistory)
        val useVision = !imagePath.isNullOrBlank()
        val useTools = !useVision && shouldUseTools(userQuery, enabledTools)
        val textOptions = buildTextGenerationOptions(mode, customPrompt)
        
        // Track response time
        val startTime = System.currentTimeMillis()
        
        // Generate response with proper parameters to avoid sampling issues
        val generationResult = if (useVision) {
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
        val rawResponse = generationResult.rawText
        
        val responseTime = System.currentTimeMillis() - startTime
        
        // Estimate token usage (rough approximation: 1 token ≈ 4 characters)
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
            timeToFirstTokenMs = generationResult.timeToFirstTokenMs
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
            Use recent chat context when it helps the response feel continuous, but do not invent details that are not visible.
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
            Use recent chat context when it helps the response feel continuous, but do not invent details that are not visible.
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

        ResponseMode.NOTES -> if (retry) {
            """
            Use study-note format only.
            Start with a short title line.
            Then write 5 to 7 bullet points beginning with "- ".
            Include visible details, important text or labels, and the main takeaway.
            End with "Summary: ..." on its own line.
            """.trimIndent()
        } else {
            """
            Use study-note format only.
            Start with a short title line.
            Then write 4 to 6 bullet points beginning with "- ".
            Include visible details, important text or labels, and the main takeaway.
            End with "Summary: ..." on its own line.
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
        val vlmImage = VLMImage.fromFilePath(imagePath)
        val firstAttempt = streamVisionResponse(vlmImage, primaryPrompt, maxTokens = 420)
        if (!shouldRetryVisionResponse(firstAttempt)) {
            return firstAttempt
        }

        val retryAttempt = streamVisionResponse(vlmImage, retryPrompt, maxTokens = 520)
        return if (scoreVisionResponse(retryAttempt) >= scoreVisionResponse(firstAttempt)) {
            retryAttempt
        } else {
            firstAttempt
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
        mode: ResponseMode,
        customPrompt: String?
    ): LLMGenerationOptions {
        val isCompanion = isCompanionPrompt(customPrompt)
        val maxTokens = when (mode) {
            ResponseMode.DIRECT -> if (isCompanion) 850 else 700
            ResponseMode.ANSWER -> 800
            ResponseMode.EXPLAIN, ResponseMode.NOTES, ResponseMode.DIRECTION -> 1000
            ResponseMode.CREATIVE -> 1100
            ResponseMode.THEORY -> 1200
        }
        val temperature = when (mode) {
            ResponseMode.DIRECT -> if (isCompanion) 0.72f else 0.35f
            ResponseMode.ANSWER, ResponseMode.NOTES, ResponseMode.DIRECTION -> 0.35f
            ResponseMode.EXPLAIN, ResponseMode.THEORY -> 0.4f
            ResponseMode.CREATIVE -> 0.75f
        }
        val topP = when (mode) {
            ResponseMode.DIRECT -> if (isCompanion) 0.96f else 0.92f
            ResponseMode.CREATIVE -> 0.95f
            else -> 0.92f
        }

        return LLMGenerationOptions(
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP,
            streamingEnabled = true
        )
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
    
    suspend fun generateReasoningSteps(userQuery: String, response: String): List<ReasoningStep> = 
        withContext(Dispatchers.IO) {
            val prompt = """
Show the reasoning steps that led to this answer.

Question: $userQuery
Answer: $response

Format as:
STEP 1: [Title]
[Description]

STEP 2: [Title]
[Description]
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
