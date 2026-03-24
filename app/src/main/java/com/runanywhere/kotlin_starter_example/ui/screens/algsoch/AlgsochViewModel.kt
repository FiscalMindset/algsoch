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
            analyticsData = generateAnalyticsData()
            showAnalyticsDialog = true
        }
    }
    
    fun dismissAnalyticsDialog() {
        showAnalyticsDialog = false
    }
    
    private suspend fun generateAnalyticsData(): Map<String, Any> {
        val allMessages = messages
        val aiMessages = allMessages.filter { !it.isUser }
        val userMessages = allMessages.filter { it.isUser }
        val globalStats = chatHistoryManager?.getGlobalStats()
        
        // Mode usage statistics
        val modeUsage = mutableMapOf<String, Int>()
        aiMessages.forEach { msg ->
            msg.structuredResponse?.let { response ->
                val modeName = response.mode.displayName()
                modeUsage[modeName] = modeUsage.getOrDefault(modeName, 0) + 1
            }
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
        
        // Response time analysis
        val responseTimes = aiMessages.mapNotNull { it.structuredResponse?.responseTimeMs }
        val avgResponseTime = if (responseTimes.isNotEmpty()) responseTimes.average() else 0.0
        
        // Token usage analysis
        val tokenUsages = aiMessages.mapNotNull { it.structuredResponse?.tokensUsed }
        val totalTokens = tokenUsages.sum()
        val avgTokens = if (tokenUsages.isNotEmpty()) tokenUsages.average() else 0.0
        
        // Writing style analysis (simple keyword analysis)
        val writingStyle = analyzeWritingStyle(userMessages.map { it.text })

        val preferredMode = when {
            modeUsage.isNotEmpty() -> modeUsage.maxByOrNull { it.value }?.key ?: selectedMode.displayName()
            selectedCustomMode != null -> selectedCustomMode?.name ?: selectedMode.displayName()
            else -> selectedMode.displayName()
        }
        
        return mapOf(
            "totalConversations" to (globalStats?.totalSessions ?: chatSessions.size),
            "totalMessages" to (globalStats?.totalMessages ?: allMessages.size),
            "totalQuestions" to (globalStats?.totalQuestions ?: userMessages.size),
            "totalResponses" to aiMessages.size,
            "modeUsage" to modeUsage,
            "feedbackStats" to feedbackStats,
            "avgResponseTime" to avgResponseTime,
            "totalTokens" to totalTokens,
            "avgTokensPerResponse" to avgTokens,
            "writingStyle" to writingStyle,
            "preferredLanguage" to selectedLanguage.displayName(),
            "preferredMode" to preferredMode,
            "preferredLevel" to selectedLevel.displayName()
        )
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
        val allText = queries.joinToString(" ").lowercase()
        
        // Simple analysis - count question types and complexity indicators
        val questionWords = listOf("what", "how", "why", "when", "where", "who", "which", "explain", "describe")
        val complexWords = listOf("algorithm", "complexity", "optimization", "analysis", "implementation", "theory")
        
        val questionCount = questionWords.sumOf { word -> allText.split(word).size - 1 }
        val complexCount = complexWords.sumOf { word -> allText.split(word).size - 1 }
        
        val avgQueryLength = if (queries.isNotEmpty()) queries.sumOf { it.length }.toDouble() / queries.size else 0.0
        
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
