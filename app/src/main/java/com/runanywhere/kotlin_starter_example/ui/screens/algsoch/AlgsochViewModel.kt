package com.runanywhere.kotlin_starter_example.ui.screens.algsoch

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runanywhere.kotlin_starter_example.data.local.ChatHistoryManager
import com.runanywhere.kotlin_starter_example.data.local.ChatSession
import com.runanywhere.kotlin_starter_example.data.models.custom.CustomMode
import com.runanywhere.kotlin_starter_example.data.models.enums.FeedbackType
import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode
import com.runanywhere.kotlin_starter_example.data.models.enums.UserLevel
import com.runanywhere.kotlin_starter_example.domain.models.ReasoningStep
import com.runanywhere.kotlin_starter_example.services.AIInferenceService
import com.runanywhere.kotlin_starter_example.services.ToolRegistry
import com.runanywhere.kotlin_starter_example.data.store.CustomModeStore
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    var feedbackType: FeedbackType? = null,
    val structuredResponse: com.runanywhere.kotlin_starter_example.domain.models.StructuredResponse? = null,
    val imageUri: Uri? = null  // Support for image input in messages
)

class AlgsochViewModel : ViewModel() {
    
    private val aiService = AIInferenceService()
    private var chatHistoryManager: ChatHistoryManager? = null
    private var appContext: Context? = null
    
    // Current session
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    
    var selectedMode by mutableStateOf(ResponseMode.DIRECT)
        private set
    
    var selectedLanguage by mutableStateOf(Language.ENGLISH)
        private set
    
    var selectedLevel by mutableStateOf(UserLevel.SMART)
        private set
    
    var isGenerating by mutableStateOf(false)
        private set
    
    var showReasoningDialog by mutableStateOf(false)
        private set
    
    var reasoningSteps by mutableStateOf<List<ReasoningStep>>(emptyList())
        private set
    
    var showNotesDialog by mutableStateOf(false)
        private set
    
    var notesContent by mutableStateOf("")
        private set
    
    var selectedCustomMode by mutableStateOf<CustomMode?>(null)
        private set
    
    // Generation job for cancellation support
    private var generationJob: Job? = null
    
    // Chat history and persistence
    var chatSessions by mutableStateOf<List<ChatSession>>(emptyList())
        private set
    
    var currentSessionPath by mutableStateOf<String?>(null)
        private set
    
    var exportStatus by mutableStateOf("")
        private set
    
    var userStats by mutableStateOf(mapOf<String, Any>())
        private set
    
    var showAnalyticsDialog by mutableStateOf(false)
        private set
    
    var analyticsData by mutableStateOf(mapOf<String, Any>())
        private set
    
    var isLoadingAnalytics by mutableStateOf(false)
        private set
    
    /**
     * Initialize the ViewModel.
     * Changed: No longer auto-loads the last session for a fresh experience.
     */
    fun initialize(context: Context) {
        if (chatHistoryManager == null) {
            appContext = context.applicationContext
            chatHistoryManager = ChatHistoryManager(context.applicationContext)
            CustomModeStore.initialize(context.applicationContext)
            loadChatSessionsListOnly()
        }
    }
    
    /**
     * Just loads the list of sessions without opening any.
     */
    private fun loadChatSessionsListOnly() {
        viewModelScope.launch {
            try {
                chatSessions = chatHistoryManager?.loadAllChatSessions() ?: emptyList()
            } catch (_: Exception) {}
        }
    }
    
    fun loadChatSession(sessionPath: String) {
        viewModelScope.launch {
            try {
                val loadedMessages = chatHistoryManager?.loadChatSession(sessionPath) ?: emptyList()
                messages = loadedMessages
                currentSessionPath = sessionPath
                updateUserStats()
            } catch (_: Exception) {}
        }
    }
    
    fun startNewSession() {
        messages = emptyList()
        currentSessionPath = null
        updateUserStats()
    }
    
    fun changeMode(mode: ResponseMode) {
        selectedMode = mode
        selectedCustomMode = null
    }
    
    fun changeLanguage(language: Language) {
        selectedLanguage = language
    }
    
    fun changeLevel(level: UserLevel) {
        selectedLevel = level
    }
    
    fun changeCustomMode(customMode: CustomMode?) {
        selectedCustomMode = customMode
        if (customMode != null) {
            selectedMode = ResponseMode.DIRECT
        }
    }
    
    fun sendMessage(query: String, imageUri: Uri? = null, isVisionReady: Boolean = true) {
        if (query.isBlank() && imageUri == null || isGenerating) return
        
        val userMessage = ChatMessage(text = query, isUser = true, imageUri = imageUri)
        messages = messages + userMessage
        
        generationJob = viewModelScope.launch {
            isGenerating = true
            try {
                if (imageUri != null && !isVisionReady) {
                    messages = messages + ChatMessage(
                        text = "Vision model is not loaded yet. Please load the Vision model first, then try the image query again.",
                        isUser = false
                    )
                    return@launch
                }

                val imagePath = if (imageUri != null) resolveImagePath(imageUri) else null
                if (imageUri != null && imagePath == null) {
                    messages = messages + ChatMessage(
                        text = "I could not access the selected image. Please pick the image again.",
                        isUser = false
                    )
                    return@launch
                }

                val finalQuery = if (query.isBlank()) "Describe this image in detail." else query
                
                // Build conversation history for context - ONLY include user messages to avoid model echoing
                val history = messages
                    .filter { it.isUser }  // Only user messages
                    .takeLast(3)  // Last 3 user messages for context
                    .map { "user" to it.text }
                
                val response = aiService.generateAnswer(
                    userQuery = finalQuery,
                    mode = selectedMode,
                    language = selectedLanguage,
                    userLevel = selectedLevel,
                    customPrompt = buildCustomPrompt(selectedCustomMode),
                    enabledTools = if (imagePath != null) emptyList() else (selectedCustomMode?.enabledTools ?: emptyList()),
                    imagePath = imagePath,
                    conversationHistory = history
                )
                
                val aiMessage = ChatMessage(
                    text = response.toDisplayText(),
                    isUser = false,
                    structuredResponse = response
                )
                messages = messages + aiMessage
                
                autosaveChat()
                updateUserStats()
            } catch (e: Exception) {
                // Don't show error if cancelled by user
                if (e !is CancellationException) {
                    val errorText = if (e is TimeoutCancellationException) {
                        "The request is taking longer than expected. Please try again or simplify the question."
                    } else {
                        "Error: ${e.message}"
                    }
                    messages = messages + ChatMessage(text = errorText, isUser = false)
                }
            } finally {
                isGenerating = false
                generationJob = null
            }
        }
    }
    
    fun cancelGeneration() {
        generationJob?.cancel()
        isGenerating = false
        generationJob = null
        
        // Add a message indicating generation was stopped by user
        messages = messages + ChatMessage(
            text = "[Response interrupted by user]",
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun autosaveChat() {
        viewModelScope.launch {
            try {
                val sessionName = if (currentSessionPath != null) 
                    java.io.File(currentSessionPath!!).nameWithoutExtension 
                else 
                    "chat_${System.currentTimeMillis()}"
                
                val savedPath = chatHistoryManager?.saveChat(messages, sessionName)
                if (savedPath != null) {
                    currentSessionPath = savedPath
                    loadChatSessionsListOnly()
                }
            } catch (_: Exception) {}
        }
    }
    
    fun exportChatAsCSV() {
        viewModelScope.launch {
            try {
                exportStatus = "Exporting CSV..."
                val csvPath = chatHistoryManager?.exportChatAsCSV(messages)
                exportStatus = if (csvPath != null) {
                    "✅ CSV exported successfully!\n📁 $csvPath"
                } else {
                    "❌ Failed to export CSV"
                }
            } catch (e: Exception) {
                exportStatus = "❌ Error exporting CSV: ${e.message}"
            }
        }
    }
    
    fun exportChatAsJSON() {
        viewModelScope.launch {
            try {
                exportStatus = "Exporting JSON..."
                val jsonPath = chatHistoryManager?.exportChatAsJSON(messages)
                exportStatus = if (jsonPath != null) {
                    "✅ JSON exported successfully!\n📁 $jsonPath"
                } else {
                    "❌ Failed to export JSON"
                }
            } catch (e: Exception) {
                exportStatus = "❌ Error exporting JSON: ${e.message}"
            }
        }
    }
    
    fun deleteChatSession(sessionPath: String) {
        viewModelScope.launch {
            try {
                chatHistoryManager?.deleteChatSession(sessionPath)
                if (currentSessionPath == sessionPath) {
                    startNewSession()
                }
                loadChatSessionsListOnly()
            } catch (_: Exception) {}
        }
    }
    
    private fun updateUserStats() {
        val userMessages = messages.filter { it.isUser }
        userStats = mapOf(
            "totalQuestions" to userMessages.size,
            "preferredLanguage" to selectedLanguage.name,
            "preferredMode" to selectedMode.name
        )
    }
    
    fun provideFeedback(messageId: Long, feedbackType: FeedbackType) {
        messages = messages.map {
            if (it.id == messageId) it.copy().also { msg -> msg.feedbackType = feedbackType }
            else it
        }
        updateUserStats()
        autosaveChat()
    }
    
    fun showReasoningFor(userQuery: String, response: String) {
        viewModelScope.launch {
            try {
                reasoningSteps = aiService.generateReasoningSteps(userQuery, response)
                showReasoningDialog = true
            } catch (_: Exception) {}
        }
    }
    
    fun dismissReasoningDialog() {
        showReasoningDialog = false
    }
    
    fun convertToNotes(response: String) {
        viewModelScope.launch {
            try {
                notesContent = aiService.convertToNotes(response, selectedLanguage)
                showNotesDialog = true
            } catch (_: Exception) {}
        }
    }
    
    fun dismissNotesDialog() {
        showNotesDialog = false
    }
    
    fun showAnalytics() {
        viewModelScope.launch {
            isLoadingAnalytics = true
            try {
                analyticsData = generateAnalyticsData()
                showAnalyticsDialog = true
            } finally {
                isLoadingAnalytics = false
            }
        }
    }
    
    fun dismissAnalyticsDialog() {
        showAnalyticsDialog = false
    }
    
    private suspend fun generateAnalyticsData(): Map<String, Any> {
        // Load ALL historical messages
        val historicalMessages = chatHistoryManager?.loadAllMessages() ?: emptyList()
        val currentSessionIds = messages.map { it.id }.toSet()
        val uniqueHistoricalMessages = historicalMessages.filter { it.id !in currentSessionIds }
        val allMessages = (messages + uniqueHistoricalMessages).sortedBy { it.timestamp }
        
        val aiMessages = allMessages.filter { !it.isUser }
        val userMessages = allMessages.filter { it.isUser }
        val globalStats = chatHistoryManager?.getGlobalStats()
        
        // Use calculated values
        val totalQuestions = userMessages.size
        val totalMessages = allMessages.size
        val totalSessions = chatSessions.size.coerceAtLeast(1)
        
        // Mode usage with fallback
        val modeUsage = mutableMapOf<String, Int>()
        aiMessages.forEach { msg ->
            msg.structuredResponse?.let { response ->
                val modeName = response.mode.displayName()
                modeUsage[modeName] = modeUsage.getOrDefault(modeName, 0) + 1
            }
        }
        if (modeUsage.isEmpty() && aiMessages.isNotEmpty()) {
            modeUsage[selectedMode.displayName()] = aiMessages.size
        }
        
        // Feedback statistics
        val feedbackStats = mutableMapOf<String, Int>()
        aiMessages.forEach { msg ->
            val feedback = when (msg.feedbackType) {
                FeedbackType.LIKE -> "Likes"
                FeedbackType.DISLIKE -> "Dislikes"
                null -> "No Feedback"
            }
            feedbackStats[feedback] = feedbackStats.getOrDefault(feedback, 0) + 1
        }
        
        // Response time and tokens
        val responseTimes = aiMessages.mapNotNull { it.structuredResponse?.responseTimeMs }
        val avgResponseTime = if (responseTimes.isNotEmpty()) responseTimes.average() else 0.0
        
        val tokenUsages = aiMessages.mapNotNull { it.structuredResponse?.tokensUsed }
        val totalTokens = if (tokenUsages.sum() > 0) tokenUsages.sum() else (allMessages.size / 2) * 100
        val avgTokens = if (tokenUsages.isNotEmpty()) tokenUsages.average() else 100.0
        
        // Topics and time
        val topicsCovered = extractTopics(userMessages.map { it.text })
        val timeSpentMinutes = if (allMessages.size >= 2) {
            val duration = (allMessages.maxOfOrNull { it.timestamp } ?: 0L) - (allMessages.minOfOrNull { it.timestamp } ?: 0L)
            val minutes = (duration / 1000.0 / 60.0).toInt()
            if (minutes == 0 && allMessages.isNotEmpty()) 1 else minutes
        } else 0
        
        // Writing style analysis
        val writingStyle = analyzeWritingStyle(userMessages.map { it.text })

        val preferredMode = when {
            modeUsage.isNotEmpty() -> modeUsage.maxByOrNull { it.value }?.key ?: selectedMode.displayName()
            selectedCustomMode != null -> selectedCustomMode?.name ?: selectedMode.displayName()
            else -> selectedMode.displayName()
        }
        
        return mapOf(
            "totalConversations" to totalSessions,
            "totalMessages" to totalMessages,
            "totalQuestions" to totalQuestions,
            "totalResponses" to aiMessages.size,
            "modeUsage" to modeUsage,
            "feedbackStats" to feedbackStats,
            "avgResponseTime" to avgResponseTime,
            "totalTokens" to totalTokens,
            "avgTokensPerResponse" to avgTokens,
            "writingStyle" to writingStyle,
            "preferredLanguage" to selectedLanguage.displayName(),
            "preferredMode" to preferredMode,
            "preferredLevel" to selectedLevel.displayName(),
            "topicsCovered" to topicsCovered.size,
            "topicsList" to topicsCovered.toList(),
            "timeSpentMinutes" to timeSpentMinutes
        )
    }

    private fun extractTopics(queries: List<String>): Set<String> {
        val topics = mutableSetOf<String>()
        if (queries.isEmpty()) return topics
        
        val topicKeywords = mapOf(
            "Algorithms" to listOf("algorithm", "sort", "search", "binary", "recursion", "dp", "dynamic"),
            "Data Structures" to listOf("array", "list", "tree", "graph", "stack", "queue", "hash"),
            "Programming" to listOf("function", "variable", "loop", "class", "object", "code", "program"),
            "Math" to listOf("math", "equation", "number", "calculate", "sum", "multiply"),
            "General" to listOf("what", "how", "why", "explain", "help", "question")
        )
        
        val allText = queries.joinToString(" ").lowercase()
        topicKeywords.forEach { (topic, keywords) ->
            if (keywords.any { allText.contains(it) }) topics.add(topic)
        }
        
        if (topics.isEmpty() && queries.any { it.trim().length > 3 }) {
            topics.add("General")
        }
        return topics
    }

    private fun resolveImagePath(uri: Uri): String? {
        val context = appContext ?: return null
        return try {
            val mimeType = context.contentResolver.getType(uri)
            val extFromMime = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            val extFromUri = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            val extension = when {
                !extFromMime.isNullOrBlank() -> extFromMime
                !extFromUri.isNullOrBlank() -> extFromUri
                else -> "jpg"
            }

            val input = context.contentResolver.openInputStream(uri) ?: return null
            val outFile = java.io.File(context.cacheDir, "algsoch_image_${System.currentTimeMillis()}.$extension")
            input.use { src ->
                outFile.outputStream().use { dst -> src.copyTo(dst) }
            }
            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }
    
    private fun analyzeWritingStyle(queries: List<String>): Map<String, Any> {
        val validQueries = queries.filter { it.isNotBlank() && it.trim().length > 2 }
        if (validQueries.isEmpty()) {
            return mapOf(
                "questionBasedQueries" to 0,
                "technicalQueries" to 0,
                "avgQueryLength" to 0.0,
                "queryStyle" to "No Data"
            )
        }
        
        val allText = validQueries.joinToString(" ").lowercase()
        val questionWords = listOf("what", "how", "why", "when", "where", "who", "which", "explain", "describe")
        val complexWords = listOf("algorithm", "complexity", "optimization", "analysis", "implementation", "theory")
        
        val questionCount = questionWords.sumOf { word -> allText.split(word).size - 1 }
        val complexCount = complexWords.sumOf { word -> allText.split(word).size - 1 }
        val avgQueryLength = validQueries.sumOf { it.length }.toDouble() / validQueries.size
        
        return mapOf(
            "questionBasedQueries" to questionCount,
            "technicalQueries" to complexCount,
            "avgQueryLength" to avgQueryLength,
            "queryStyle" to when {
                questionCount > complexCount -> "Curious/Inquisitive"
                complexCount > questionCount -> "Technical/Advanced"
                else -> "Balanced"
            }
        )
    }

    private fun buildCustomPrompt(mode: CustomMode?): String? {
        mode ?: return null
        if (mode.enabledTools.isEmpty()) return mode.basePrompt

        val toolLines = mode.enabledTools.mapNotNull { toolId ->
            ToolRegistry.getToolById(toolId)?.let { tool ->
                "- ${tool.name} (${tool.id}): ${tool.description}. Params: ${tool.parametersDescription}"
            }
        }

        if (toolLines.isEmpty()) return mode.basePrompt

        return buildString {
            appendLine(mode.basePrompt)
            appendLine()
            appendLine("Available tools for this assistant:")
            toolLines.forEach { appendLine(it) }
            appendLine("Use tools when relevant. If a real-time tool call fails, clearly say so and do not hallucinate values.")
        }
    }
}
