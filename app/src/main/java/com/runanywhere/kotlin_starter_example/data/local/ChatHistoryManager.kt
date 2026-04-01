package com.runanywhere.kotlin_starter_example.data.local

import android.content.Context
import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import com.runanywhere.kotlin_starter_example.data.models.enums.FeedbackType
import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode
import com.runanywhere.kotlin_starter_example.domain.models.StructuredResponse
import com.runanywhere.kotlin_starter_example.ui.screens.algsoch.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
            val jsonContent = ChatHistoryJsonCodec.messagesToJson(messages)
            chatFile.writeText(jsonContent)
            chatFile.absolutePath
        }
    
    /**
     * Load all chat sessions
     */
    suspend fun loadAllChatSessions(): List<ChatSession> = withContext(Dispatchers.IO) {
        listCanonicalSessionFiles().map { file ->
            val sessionMessages = ChatHistoryJsonCodec.jsonToMessages(file.readText())
            val latestAssistantResponse = sessionMessages
                .asReversed()
                .firstNotNullOfOrNull { message ->
                    if (!message.isUser) message.structuredResponse else null
                }
            ChatSession(
                name = canonicalSessionName(file.nameWithoutExtension),
                path = file.absolutePath,
                lastModified = file.lastModified(),
                messageCount = sessionMessages.size,
                questionCount = sessionMessages.count { it.isUser && it.text.isNotBlank() },
                title = deriveSessionTitle(sessionMessages, file.nameWithoutExtension, file.lastModified()),
                preview = deriveAssistantPreview(sessionMessages),
                userPreview = deriveUserPreview(sessionMessages),
                assistantPreview = deriveAssistantPreview(sessionMessages),
                assistantName = deriveAssistantName(sessionMessages),
                modeLabel = latestAssistantResponse?.mode?.displayName(),
                modelName = latestAssistantResponse?.modelName?.takeIf { it.isNotBlank() },
                responseTimeMs = latestAssistantResponse?.responseTimeMs ?: 0L
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
            val msgs = ChatHistoryJsonCodec.jsonToMessages(file.readText())
            totalMessages += msgs.size
            totalQuestions += msgs.count { it.isUser && it.text.isNotBlank() }
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
                val messages = ChatHistoryJsonCodec.jsonToMessages(file.readText())
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
                ChatHistoryJsonCodec.jsonToMessages(file.readText())
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
            val jsonContent = ChatHistoryJsonCodec.messagesToJson(messages)
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
    val assistantName: String? = null,
    val modeLabel: String? = null,
    val modelName: String? = null,
    val responseTimeMs: Long = 0L
)

data class GlobalChatStats(
    val totalSessions: Int,
    val totalMessages: Int,
    val totalQuestions: Int
)

internal object ChatHistoryJsonCodec {

    fun messagesToJson(messages: List<ChatMessage>): String {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject().apply {
                    put("timestamp", message.timestamp)
                    put("isUser", message.isUser)
                    put("text", messageTextForStorage(message))
                    put("assistantLabel", message.assistantLabel ?: JSONObject.NULL)
                    put("feedbackType", message.feedbackType?.name ?: JSONObject.NULL)
                    message.structuredResponse?.let { response ->
                        put("mode", response.mode.name)
                        put("language", response.language.name)
                        put("directAnswer", response.directAnswer)
                        put("quickExplanation", response.quickExplanation)
                        put("deepExplanation", response.deepExplanation ?: JSONObject.NULL)
                        put("modelName", response.modelName)
                        put("tokensUsed", response.tokensUsed)
                        put("promptTokens", response.promptTokens)
                        put("responseTokens", response.responseTokens)
                        put("responseTimeMs", response.responseTimeMs)
                        put("timeToFirstTokenMs", response.timeToFirstTokenMs ?: JSONObject.NULL)
                    }
                }
            )
        }
        return array.toString(2)
    }

    fun jsonToMessages(json: String): List<ChatMessage> = runCatching {
        val cleaned = json.trim()
        if (cleaned.isBlank()) {
            emptyList()
        } else {
            val jsonArray = JSONArray(cleaned)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.optJSONObject(index) ?: continue
                    val timestamp = jsonObject.optLong("timestamp", System.currentTimeMillis())
                    val isUser = jsonObject.optBoolean("isUser", false)
                    val text = jsonObject.optString("text")
                    val structuredResponse = parseStructuredResponse(jsonObject, text)
                    val restoredText = text.ifBlank {
                        if (isUser) "" else "[Older saved reply unavailable]"
                    }

                    add(
                        ChatMessage(
                            id = timestamp,
                            text = restoredText,
                            isUser = isUser,
                            timestamp = timestamp,
                            feedbackType = parseFeedbackType(jsonObject.optNullableString("feedbackType")),
                            assistantLabel = jsonObject.optNullableString("assistantLabel"),
                            structuredResponse = structuredResponse
                        )
                    )
                }
            }
        }
    }.getOrElse { emptyList() }

    private fun parseStructuredResponse(jsonObject: JSONObject, fallbackText: String): StructuredResponse? {
        val modeRaw = jsonObject.optNullableString("mode") ?: return null
        val languageRaw = jsonObject.optNullableString("language") ?: return null

        val mode = runCatching { ResponseMode.valueOf(modeRaw) }.getOrNull() ?: return null
        val language = runCatching { Language.valueOf(languageRaw) }.getOrNull() ?: Language.ENGLISH

        return StructuredResponse(
            directAnswer = jsonObject.optString("directAnswer").ifBlank { fallbackText },
            quickExplanation = jsonObject.optString("quickExplanation"),
            deepExplanation = jsonObject.optNullableString("deepExplanation"),
            mode = mode,
            language = language,
            modelName = jsonObject.optNullableString("modelName") ?: "SmolLM2-360M",
            tokensUsed = jsonObject.optInt("tokensUsed", 0),
            promptTokens = jsonObject.optInt("promptTokens", 0),
            responseTokens = jsonObject.optInt("responseTokens", 0),
            responseTimeMs = jsonObject.optLong("responseTimeMs", 0L),
            timeToFirstTokenMs = jsonObject.optNullableLong("timeToFirstTokenMs")
        )
    }

    private fun messageTextForStorage(message: ChatMessage): String =
        message.text.ifBlank { message.structuredResponse?.toDisplayText().orEmpty() }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private fun parseFeedbackType(rawValue: String?): FeedbackType? =
        rawValue?.let { value -> runCatching { FeedbackType.valueOf(value) }.getOrNull() }
}
