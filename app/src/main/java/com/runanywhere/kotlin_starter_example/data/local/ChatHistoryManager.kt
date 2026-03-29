package com.runanywhere.kotlin_starter_example.data.local

import android.content.Context
import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode
import com.runanywhere.kotlin_starter_example.domain.models.StructuredResponse
import com.runanywhere.kotlin_starter_example.ui.screens.algsoch.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages chat history persistence using JSON file storage.
 */
class ChatHistoryManager(private val context: Context) {
    
    private val chatDir = File(context.filesDir, "chat_history")
    private val preferencesFile = File(context.filesDir, "chat_preferences.json")
    
    init {
        if (!chatDir.exists()) {
            chatDir.mkdirs()
        }
    }
    
    /**
     * Save chat messages to a JSON file with timestamp
     */
    suspend fun saveChat(
        messages: List<ChatMessage>,
        sessionName: String = "default",
        existingSessionPath: String? = null
    ): String =
        withContext(Dispatchers.IO) {
            val chatFile = existingSessionPath
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?: File(chatDir, "${sanitizeSessionName(sessionName)}.json")
            val jsonContent = messagesToJson(messages)
            chatFile.writeText(jsonContent)
            chatFile.absolutePath
        }
    
    /**
     * Load all chat sessions
     */
    suspend fun loadAllChatSessions(): List<ChatSession> = withContext(Dispatchers.IO) {
        listCanonicalSessionFiles().map { file ->
            val sessionMessages = jsonToMessages(file.readText())
            ChatSession(
                name = canonicalSessionName(file.nameWithoutExtension),
                path = file.absolutePath,
                lastModified = file.lastModified(),
                messageCount = sessionMessages.size,
                questionCount = sessionMessages.count { it.isUser },
                title = deriveSessionTitle(sessionMessages, file.nameWithoutExtension, file.lastModified()),
                preview = deriveAssistantPreview(sessionMessages),
                userPreview = deriveUserPreview(sessionMessages),
                assistantPreview = deriveAssistantPreview(sessionMessages),
                assistantName = deriveAssistantName(sessionMessages)
            )
        }.sortedByDescending { it.lastModified }
    }

    /**
     * Aggregate chat statistics across all saved sessions.
     */
    suspend fun getGlobalStats(): GlobalChatStats = withContext(Dispatchers.IO) {
        val files = listCanonicalSessionFiles()
        var totalMessages = 0
        var totalQuestions = 0

        files.forEach { file ->
            val msgs = jsonToMessages(file.readText())
            totalMessages += msgs.size
            totalQuestions += msgs.count { it.isUser }
        }

        GlobalChatStats(
            totalSessions = files.size,
            totalMessages = totalMessages,
            totalQuestions = totalQuestions
        )
    }
    
    /**
     * Load ALL messages from ALL chat sessions for comprehensive analytics
     */
    suspend fun loadAllMessages(): List<ChatMessage> = withContext(Dispatchers.IO) {
        val allMessages = mutableListOf<ChatMessage>()
        val files = listCanonicalSessionFiles()
        
        files.forEach { file ->
            try {
                val messages = jsonToMessages(file.readText())
                allMessages.addAll(messages)
            } catch (e: Exception) {
                // Skip corrupted files
            }
        }
        
        allMessages.sortedBy { it.timestamp }
    }
    
    /**
     * Load specific chat session
     */
    suspend fun loadChatSession(sessionPath: String): List<ChatMessage> = 
        withContext(Dispatchers.IO) {
            val file = File(sessionPath)
            if (file.exists()) {
                jsonToMessages(file.readText())
            } else {
                emptyList()
            }
        }
    
    /**
     * Export chat as CSV for data extraction
     */
    suspend fun exportChatAsCSV(messages: List<ChatMessage>): String = 
        withContext(Dispatchers.IO) {
            val csvFile = File(chatDir, "chat_export_${System.currentTimeMillis()}.csv")
            val csvContent = buildString {
                appendLine("Timestamp,IsUser,Message")
                messages.forEach { msg ->
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(msg.timestamp))
                    val escapedMessage = msg.text.replace("\"", "\"\"")
                    appendLine("\"$timestamp\",${msg.isUser},\"$escapedMessage\"")
                }
            }
            csvFile.writeText(csvContent)
            csvFile.absolutePath
        }
    
    /**
     * Export chat as JSON
     */
    suspend fun exportChatAsJSON(messages: List<ChatMessage>): String = 
        withContext(Dispatchers.IO) {
            val jsonFile = File(chatDir, "chat_export_${System.currentTimeMillis()}.json")
            val jsonContent = messagesToJson(messages)
            jsonFile.writeText(jsonContent)
            jsonFile.absolutePath
        }
    
    /**
     * Delete a chat session
     */
    suspend fun deleteChatSession(sessionPath: String): Boolean = 
        withContext(Dispatchers.IO) {
            val targetFile = File(sessionPath)
            val canonicalName = canonicalSessionName(targetFile.nameWithoutExtension)
            val relatedFiles = listSessionFiles().filter {
                canonicalSessionName(it.nameWithoutExtension) == canonicalName
            }

            relatedFiles.fold(true) { deletedAll, file ->
                file.delete() && deletedAll
            }
        }

    suspend fun deleteAllChatSessions(): Boolean =
        withContext(Dispatchers.IO) {
            listSessionFiles().fold(true) { deletedAll, file ->
                file.delete() && deletedAll
            }
        }
    
    /**
     * Get chat export directory path for file access
     */
    fun getChatDirectoryPath(): String = chatDir.absolutePath

    private fun listSessionFiles(): List<File> =
        chatDir.listFiles()?.filter { it.isFile && it.extension == "json" } ?: emptyList()

    private fun listCanonicalSessionFiles(): List<File> =
        listSessionFiles()
            .groupBy { canonicalSessionName(it.nameWithoutExtension) }
            .values
            .mapNotNull { snapshots -> snapshots.maxByOrNull { it.lastModified() } }

    private fun canonicalSessionName(fileNameWithoutExtension: String): String {
        val parts = fileNameWithoutExtension.split("_")
        var trailingTimestamps = 0

        for (index in parts.indices.reversed()) {
            if (parts[index].matches(Regex("\\d{13}"))) {
                trailingTimestamps++
            } else {
                break
            }
        }

        if (trailingTimestamps <= 1) {
            return fileNameWithoutExtension
        }

        val partsToKeep = parts.size - (trailingTimestamps - 1)
        return parts.take(partsToKeep).joinToString("_")
    }

    private fun sanitizeSessionName(sessionName: String): String =
        sessionName
            .trim()
            .ifBlank { "chat_${System.currentTimeMillis()}" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
    
    private fun messagesToJson(messages: List<ChatMessage>): String {
        return buildString {
            append("[\n")
            messages.forEachIndexed { index, msg ->
                val storedText = messageTextForStorage(msg)
                append("  {\n")
                append("    \"timestamp\": ${msg.timestamp},\n")
                append("    \"isUser\": ${msg.isUser},\n")
                append("    \"text\": \"${escapeJsonString(storedText)}\",\n")
                append("    \"assistantLabel\": ${msg.assistantLabel?.let { "\"${escapeJsonString(it)}\"" } ?: "null"},\n")
                appendStructuredResponseJson(msg.structuredResponse)
                append("    \"feedbackType\": ${msg.feedbackType?.name?.let { "\"$it\"" } ?: "null"}\n")
                append("  }")
                if (index < messages.size - 1) append(",")
                append("\n")
            }
            append("]")
        }
    }
    
    private fun jsonToMessages(json: String): List<ChatMessage> {
        return try {
            val messages = mutableListOf<ChatMessage>()
            val cleaned = json.trim()
            if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
                val content = cleaned.substring(1, cleaned.length - 1)
                val objectStrings = content.split("},\n  {").map { 
                    it.trim().trimStart('{').trimEnd('}').trim()
                }
                
                objectStrings.forEach { objStr ->
                    if (objStr.isNotBlank()) {
                        val timestamp = extractJsonValue(objStr, "timestamp").toLongOrNull() ?: System.currentTimeMillis()
                        val isUser = extractJsonValue(objStr, "isUser").toBoolean()
                        val text = extractJsonValue(objStr, "text")
                        val assistantLabel = extractJsonValue(objStr, "assistantLabel").takeUnless { it == "null" || it.isBlank() }
                        val structuredResponse = parseStructuredResponse(objStr, text)
                        val restoredText = text.ifBlank {
                            if (isUser) "" else "[Older saved reply unavailable]"
                        }
                        
                        messages.add(
                            ChatMessage(
                                id = timestamp,
                                text = restoredText,
                                isUser = isUser,
                                timestamp = timestamp,
                                assistantLabel = assistantLabel,
                                structuredResponse = structuredResponse
                            )
                        )
                    }
                }
            }
            messages
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun extractJsonValue(json: String, key: String): String {
        val keyPattern = """"$key"\s*:\s*"""".toRegex()
        val keyMatch = keyPattern.find(json) ?: return ""

        val startIndex = keyMatch.range.last + 1
        if (startIndex >= json.length) return ""

        val valueString = json.substring(startIndex).trimStart()
        if (valueString.startsWith("\"")) {
            var endIndex = 1
            var isEscaped = false
            while (endIndex < valueString.length) {
                val char = valueString[endIndex]
                if (isEscaped) {
                    isEscaped = false
                } else if (char == '\\') {
                    isEscaped = true
                } else if (char == '"') {
                    // Found the closing quote
                    val rawValue = valueString.substring(1, endIndex)
                    // Unescape JSON string
                    return rawValue
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t")
                }
                endIndex++
            }
        }

        return when {
            valueString.startsWith("true") -> "true"
            valueString.startsWith("false") -> "false"
            valueString.startsWith("null") -> "null"
            else -> valueString
                .takeWhile { it != ',' && it != '\n' && it != '\r' && it != '}' }
                .trim()
        }
    }

    private fun messageTextForStorage(message: ChatMessage): String =
        message.text.ifBlank { message.structuredResponse?.toDisplayText().orEmpty() }

    private fun StringBuilder.appendStructuredResponseJson(structuredResponse: StructuredResponse?) {
        if (structuredResponse == null) return

        append("    \"mode\": \"${structuredResponse.mode.name}\",\n")
        append("    \"language\": \"${structuredResponse.language.name}\",\n")
        append("    \"directAnswer\": \"${escapeJsonString(structuredResponse.directAnswer)}\",\n")
        append("    \"quickExplanation\": \"${escapeJsonString(structuredResponse.quickExplanation)}\",\n")
        append("    \"deepExplanation\": ${structuredResponse.deepExplanation?.let { "\"${escapeJsonString(it)}\"" } ?: "null"},\n")
        append("    \"modelName\": \"${escapeJsonString(structuredResponse.modelName)}\",\n")
        append("    \"tokensUsed\": ${structuredResponse.tokensUsed},\n")
        append("    \"promptTokens\": ${structuredResponse.promptTokens},\n")
        append("    \"responseTokens\": ${structuredResponse.responseTokens},\n")
        append("    \"responseTimeMs\": ${structuredResponse.responseTimeMs},\n")
        append("    \"timeToFirstTokenMs\": ${structuredResponse.timeToFirstTokenMs ?: "null"},\n")
    }

    private fun parseStructuredResponse(jsonObject: String, fallbackText: String): StructuredResponse? {
        val modeRaw = extractJsonValue(jsonObject, "mode").takeUnless { it == "null" || it.isBlank() } ?: return null
        val languageRaw = extractJsonValue(jsonObject, "language").takeUnless { it == "null" || it.isBlank() } ?: return null

        val mode = runCatching { ResponseMode.valueOf(modeRaw) }.getOrNull() ?: return null
        val language = runCatching { Language.valueOf(languageRaw) }.getOrNull() ?: Language.ENGLISH
        val directAnswer = extractJsonValue(jsonObject, "directAnswer").ifBlank { fallbackText }
        val quickExplanation = extractJsonValue(jsonObject, "quickExplanation").takeUnless { it == "null" } ?: ""
        val deepExplanation = extractJsonValue(jsonObject, "deepExplanation").takeUnless { it == "null" || it.isBlank() }
        val modelName = extractJsonValue(jsonObject, "modelName").takeUnless { it == "null" || it.isBlank() } ?: "SmolLM2-360M"
        val tokensUsed = extractJsonValue(jsonObject, "tokensUsed").toIntOrNull() ?: 0
        val promptTokens = extractJsonValue(jsonObject, "promptTokens").toIntOrNull() ?: 0
        val responseTokens = extractJsonValue(jsonObject, "responseTokens").toIntOrNull() ?: 0
        val responseTimeMs = extractJsonValue(jsonObject, "responseTimeMs").toLongOrNull() ?: 0L
        val timeToFirstTokenMs = extractJsonValue(jsonObject, "timeToFirstTokenMs").toLongOrNull()

        return StructuredResponse(
            directAnswer = directAnswer,
            quickExplanation = quickExplanation,
            deepExplanation = deepExplanation,
            mode = mode,
            language = language,
            modelName = modelName,
            tokensUsed = tokensUsed,
            promptTokens = promptTokens,
            responseTokens = responseTokens,
            responseTimeMs = responseTimeMs,
            timeToFirstTokenMs = timeToFirstTokenMs
        )
    }

    private fun escapeJsonString(text: String): String =
        text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    private fun deriveSessionTitle(
        messages: List<ChatMessage>,
        fallbackName: String,
        lastModified: Long
    ): String {
        val firstPrompt = messages
            .firstOrNull { it.isUser && it.text.isNotBlank() }
            ?.text
            ?.singleLine()

        return if (!firstPrompt.isNullOrBlank()) {
            firstPrompt.take(52)
        } else {
            humanizeFallbackName(fallbackName, lastModified)
        }
    }

    private fun deriveUserPreview(messages: List<ChatMessage>): String {
        val lastUserMessage = messages
            .asReversed()
            .firstOrNull { it.isUser && isUsefulSessionText(it.text) }
            ?.text
            ?.singleLine()

        return lastUserMessage?.take(96) ?: "Open this chat to continue learning."
    }

    private fun deriveAssistantPreview(messages: List<ChatMessage>): String {
        val lastAssistantMessage = messages
            .asReversed()
            .firstOrNull { !it.isUser && isUsefulSessionText(it.text) }
            ?.text
            ?.singleLine()

        if (!lastAssistantMessage.isNullOrBlank()) {
            return lastAssistantMessage.take(96)
        }

        return deriveUserPreview(messages)
    }

    private fun deriveAssistantName(messages: List<ChatMessage>): String? =
        messages
            .asReversed()
            .firstOrNull { !it.isUser && !it.assistantLabel.isNullOrBlank() }
            ?.assistantLabel
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun humanizeFallbackName(rawName: String, lastModified: Long): String {
        val cleaned = canonicalSessionName(rawName).replace('_', ' ').trim()
        return if (cleaned.matches(Regex("chat\\s*\\d+"))) {
            "Saved chat · ${formatSessionDate(lastModified)}"
        } else {
            cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    private fun isUsefulSessionText(text: String): Boolean =
        text.isNotBlank() && !isMissingSavedReplyMarker(text)

    private fun isMissingSavedReplyMarker(text: String): Boolean =
        text.contains("[Older saved reply unavailable]", ignoreCase = true) ||
            text.contains("saved reply has no visible text available", ignoreCase = true)

    private fun String.singleLine(): String =
        replace(Regex("\\s+"), " ").trim()

    private fun formatSessionDate(timestamp: Long): String =
        SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(timestamp))
}

data class ChatSession(
    val name: String,
    val path: String,
    val lastModified: Long,
    val messageCount: Int,
    val questionCount: Int = 0,
    val title: String = name,
    val preview: String = "",
    val userPreview: String = "",
    val assistantPreview: String = "",
    val assistantName: String? = null
)

data class GlobalChatStats(
    val totalSessions: Int,
    val totalMessages: Int,
    val totalQuestions: Int
)
