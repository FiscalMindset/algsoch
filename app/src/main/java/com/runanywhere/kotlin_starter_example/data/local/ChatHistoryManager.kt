package com.runanywhere.kotlin_starter_example.data.local

import android.content.Context
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
                title = deriveSessionTitle(sessionMessages, file.nameWithoutExtension),
                preview = deriveSessionPreview(sessionMessages)
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
                        val restoredText = text.ifBlank {
                            if (isUser) "" else "This older saved reply has no visible text available."
                        }
                        
                        messages.add(
                            ChatMessage(
                                id = timestamp,
                                text = restoredText,
                                isUser = isUser,
                                timestamp = timestamp
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
        // Find the key and get everything until the closing quote, handling escaped quotes
        val keyPattern = """"$key"\s*:\s*"""".toRegex()
        val keyMatch = keyPattern.find(json) ?: return ""
        
        val startIndex = keyMatch.range.last + 1
        val valueString = json.substring(startIndex)
        
        // Extract the complete quoted string, handling escaped characters
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
        return ""
    }

    private fun messageTextForStorage(message: ChatMessage): String =
        message.text.ifBlank { message.structuredResponse?.toDisplayText().orEmpty() }

    private fun escapeJsonString(text: String): String =
        text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    private fun deriveSessionTitle(messages: List<ChatMessage>, fallbackName: String): String {
        val firstPrompt = messages
            .firstOrNull { it.isUser && it.text.isNotBlank() }
            ?.text
            ?.singleLine()

        return if (!firstPrompt.isNullOrBlank()) {
            firstPrompt.take(52)
        } else {
            humanizeFallbackName(fallbackName)
        }
    }

    private fun deriveSessionPreview(messages: List<ChatMessage>): String {
        val lastAssistantMessage = messages
            .asReversed()
            .firstOrNull { !it.isUser && it.text.isNotBlank() }
            ?.text
            ?.singleLine()

        if (!lastAssistantMessage.isNullOrBlank()) {
            return lastAssistantMessage.take(96)
        }

        val lastMessage = messages
            .asReversed()
            .firstOrNull { it.text.isNotBlank() }
            ?.text
            ?.singleLine()

        return lastMessage?.take(96) ?: "Resume this conversation."
    }

    private fun humanizeFallbackName(rawName: String): String {
        val cleaned = canonicalSessionName(rawName).replace('_', ' ').trim()
        return if (cleaned.matches(Regex("chat\\s*\\d+"))) {
            "Untitled Chat"
        } else {
            cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    private fun String.singleLine(): String =
        replace(Regex("\\s+"), " ").trim()
}

data class ChatSession(
    val name: String,
    val path: String,
    val lastModified: Long,
    val messageCount: Int,
    val questionCount: Int = 0,
    val title: String = name,
    val preview: String = ""
)

data class GlobalChatStats(
    val totalSessions: Int,
    val totalMessages: Int,
    val totalQuestions: Int
)
