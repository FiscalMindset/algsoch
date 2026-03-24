package com.runanywhere.kotlin_starter_example.services

import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode
import com.runanywhere.kotlin_starter_example.data.models.enums.UserLevel
import com.runanywhere.kotlin_starter_example.domain.ai.PromptBuilder
import com.runanywhere.kotlin_starter_example.domain.ai.ResponseParser
import com.runanywhere.kotlin_starter_example.domain.models.ReasoningStep
import com.runanywhere.kotlin_starter_example.domain.models.StructuredResponse
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.LLM.RunAnywhereToolCalling
import com.runanywhere.sdk.public.extensions.LLM.ToolCallingOptions
import com.runanywhere.sdk.public.extensions.LLM.ToolDefinition
import com.runanywhere.sdk.public.extensions.LLM.ToolParameter
import com.runanywhere.sdk.public.extensions.LLM.ToolParameterType
import com.runanywhere.sdk.public.extensions.LLM.ToolValue
import com.runanywhere.sdk.public.extensions.VLM.VLMGenerationOptions
import com.runanywhere.sdk.public.extensions.VLM.VLMImage
import com.runanywhere.sdk.public.extensions.chat
import com.runanywhere.sdk.public.extensions.isLLMModelLoaded
import com.runanywhere.sdk.public.extensions.isVLMModelLoaded
import com.runanywhere.sdk.public.extensions.processImageStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): StructuredResponse = withContext(Dispatchers.IO) {
        val prompt = promptBuilder.buildPrompt(userQuery, mode, language, userLevel, customPrompt, conversationHistory)
        val visionPrompt = buildVisionPrompt(userQuery, language)
        
        // Track response time
        val startTime = System.currentTimeMillis()
        
        // Generate response with proper parameters to avoid sampling issues
        val rawResponse = if (!imagePath.isNullOrBlank()) {
            val vlmImage = VLMImage.fromFilePath(imagePath)
            val options = VLMGenerationOptions(maxTokens = 300)  // Concise image analysis
            val sb = StringBuilder()
            RunAnywhere.processImageStream(vlmImage, visionPrompt, options).collect { token ->
                sb.append(token)
            }
            sb.toString()
        } else if (enabledTools.isNotEmpty()) {
            ensureToolRegistryInitialized()
            val toolContext = "Available tools: ${enabledTools.joinToString(", ")}. Use tools when needed and avoid guessing."
            // Use the mode-specific prompt from PromptBuilder as the base!
            val toolPrompt = prompt.replace("<|im_start|>assistant\n", "") + "\n$toolContext\n\nUse tools if beneficial, but prioritize direct educational explanation."

            val result = RunAnywhereToolCalling.generateWithTools(
                prompt = toolPrompt,
                options = ToolCallingOptions(
                    maxToolCalls = 3,
                    autoExecute = true,
                    temperature = 0.2f,
                    maxTokens = 600  // Moderate tokens for concise educational responses
                )
            )
            result.text
        } else {
            RunAnywhere.chat(prompt)
        }
        
        val responseTime = System.currentTimeMillis() - startTime
        
        // Estimate token usage (rough approximation: 1 token ≈ 4 characters)
        val promptTokens = prompt.length / 4
        val responseTokens = rawResponse.length / 4
        val totalTokens = promptTokens + responseTokens
        
        // Get model name (try to detect from loaded models)
        val modelName = try {
            // Check which model is loaded
            when {
                !imagePath.isNullOrBlank() && RunAnywhere.isVLMModelLoaded -> "SmolVLM-256M-Instruct"
                enabledTools.isNotEmpty() -> "SmolLM2-360M-Instruct + Tools"
                RunAnywhere.isLLMModelLoaded() -> "SmolLM2-360M-Instruct"
                else -> "Unknown Model"
            }
        } catch (e: Exception) {
            "SmolLM2-360M-Instruct" // Default fallback
        }
        
        // Pass userQuery to parse function for echo-detection
        responseParser.parse(rawResponse, mode, language, userQuery).copy(
            modelName = modelName,
            tokensUsed = totalTokens,
            responseTimeMs = responseTime
        )
    }

    private fun buildVisionPrompt(userQuery: String, language: Language): String {
        val langName = when (language) {
            Language.ENGLISH -> "English"
            Language.HINDI -> "Hindi"
            Language.HINGLISH -> "Hinglish"
        }

        val effectiveQuery = userQuery.ifBlank { "Describe the uploaded image in detail." }
        return """
            You are an image-grounded assistant.
            Answer only based on the uploaded image and the user query.
            If something is not visible in the image, say it clearly instead of guessing.
            Respond in $langName.

            User query: $effectiveQuery
        """.trimIndent()
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
