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
    suspend fun saveChat(messages: List<ChatMessage>, sessionName: String = "default"): String = 
        withContext(Dispatchers.IO) {
            val chatFile = File(chatDir, "${sessionName}_${System.currentTimeMillis()}.json")
            val jsonContent = messagesToJson(messages)
            chatFile.writeText(jsonContent)
            chatFile.absolutePath
        }
    
    /**
     * Load all chat sessions
     */
    suspend fun loadAllChatSessions(): List<ChatSession> = withContext(Dispatchers.IO) {
        chatDir.listFiles()?.map { file ->
            ChatSession(
                name = file.nameWithoutExtension,
                path = file.absolutePath,
                lastModified = file.lastModified(),
                messageCount = jsonToMessages(file.readText()).size
            )
        }?.sortedByDescending { it.lastModified } ?: emptyList()
    }

    /**
     * Aggregate chat statistics across all saved sessions.
     */
    suspend fun getGlobalStats(): GlobalChatStats = withContext(Dispatchers.IO) {
        val files = chatDir.listFiles()?.filter { it.isFile && it.extension == "json" } ?: emptyList()
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
        val files = chatDir.listFiles()?.filter { it.isFile && it.extension == "json" } ?: emptyList()
        
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
            File(sessionPath).delete()
        }
    
    /**
     * Get chat export directory path for file access
     */
    fun getChatDirectoryPath(): String = chatDir.absolutePath
    
    private fun messagesToJson(messages: List<ChatMessage>): String {
        return buildString {
            append("[\n")
            messages.forEachIndexed { index, msg ->
                append("  {\n")
                append("    \"timestamp\": ${msg.timestamp},\n")
                append("    \"isUser\": ${msg.isUser},\n")
                append("    \"text\": \"${msg.text.replace("\"", "\\\"")}\",\n")
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
                        
                        messages.add(
                            ChatMessage(
                                id = timestamp,
                                text = text,
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
                }
                endIndex++
            }
        }
        return ""
    }
}

data class ChatSession(
    val name: String,
    val path: String,
    val lastModified: Long,
    val messageCount: Int
)

data class GlobalChatStats(
    val totalSessions: Int,
    val totalMessages: Int,
    val totalQuestions: Int
)

