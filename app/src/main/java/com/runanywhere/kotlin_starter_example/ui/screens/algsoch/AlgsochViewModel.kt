package com.runanywhere.kotlin_starter_example.ui.screens.algsoch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.runanywhere.kotlin_starter_example.domain.models.GenerationTraceEntry
import com.runanywhere.kotlin_starter_example.domain.models.ReasoningStep
import com.runanywhere.kotlin_starter_example.services.AIInferenceService
import com.runanywhere.kotlin_starter_example.services.ToolRegistry
import com.runanywhere.kotlin_starter_example.data.store.CustomModeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    var feedbackType: FeedbackType? = null,
    val structuredResponse: com.runanywhere.kotlin_starter_example.domain.models.StructuredResponse? = null,
    val imageUri: Uri? = null,  // Support for image input in messages
    val imageAnalysisTrace: ImageAnalysisTrace? = null,
    val assistantLabel: String? = null,
    val isPending: Boolean = false,
    val generationStatus: String? = null
)

data class TopicInsight(
    val name: String,
    val mentionCount: Int,
    val matchedKeywords: List<String> = emptyList()
)

data class AnalyticsInsightItem(
    val title: String,
    val description: String
)

data class AnalyticsBreakdownItem(
    val label: String,
    val count: Int,
    val share: Float,
    val supportingText: String? = null
)

data class DailyActivityPoint(
    val label: String,
    val dateLabel: String,
    val questionCount: Int,
    val imageQuestionCount: Int = 0
)

enum class ImageFocusMode(
    val displayName: String,
    val shortDescription: String
) {
    FULL_FRAME(
        displayName = "Full frame",
        shortDescription = "Analyze the whole image."
    ),
    CENTER_FOCUS(
        displayName = "Center focus",
        shortDescription = "Crop toward the center subject."
    ),
    SQUARE_CROP(
        displayName = "Square crop",
        shortDescription = "Use a tighter center square."
    )
}

data class ImageAnalysisStep(
    val title: String,
    val description: String,
    val wasApplied: Boolean = true
)

data class ImageAnalysisTrace(
    val sourceLabel: String,
    val focusModeLabel: String = ImageFocusMode.FULL_FRAME.displayName,
    val mimeType: String? = null,
    val analyzedImageUri: Uri? = null,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val processedWidth: Int = 0,
    val processedHeight: Int = 0,
    val originalSizeBytes: Long = 0L,
    val processedSizeBytes: Long = 0L,
    val preprocessingApplied: Boolean = false,
    val preprocessingSummary: String = "",
    val analysisSummary: String = "",
    val previewNote: String = "",
    val steps: List<ImageAnalysisStep> = emptyList()
)

data class AnswerSourceDetails(
    val question: String,
    val answer: String,
    val modeLabel: String,
    val modelName: String,
    val assistantLabel: String? = null,
    val tokensUsed: Int = 0,
    val promptTokens: Int = 0,
    val responseTokens: Int = 0,
    val responseTimeMs: Long = 0,
    val timeToFirstTokenMs: Long? = null,
    val attempts: List<GenerationTraceEntry> = emptyList(),
    val imageUri: Uri? = null,
    val imageAnalysisTrace: ImageAnalysisTrace? = null
)

private data class RelevantMemoryMatch(
    val text: String,
    val score: Double,
    val timestamp: Long
)

private data class TopicDefinition(
    val name: String,
    val keywords: List<String>
)

private data class PreparedImageInput(
    val displayImageUri: Uri,
    val modelImagePath: String,
    val trace: ImageAnalysisTrace
)

class AlgsochViewModel : ViewModel() {
    companion object {
        private val systemZone: ZoneId = ZoneId.systemDefault()

        private val stopWords = setOf(
            "a", "an", "and", "are", "as", "at", "be", "because", "but", "by", "can", "could",
            "do", "does", "for", "from", "get", "give", "had", "has", "have", "help", "how",
            "i", "if", "in", "into", "is", "it", "its", "just", "me", "my", "need", "of", "on",
            "or", "please", "show", "so", "tell", "that", "the", "their", "them", "there",
            "these", "this", "to", "understand", "us", "use", "using", "want", "was", "we",
            "what", "when", "where", "which", "who", "why", "with", "would", "you", "your"
        )

        private val topicCatalog = listOf(
            TopicDefinition("Dynamic Programming", listOf("dynamic programming", "dp", "memoization", "tabulation")),
            TopicDefinition("Recursion", listOf("recursion", "recursive", "base case", "recursive tree")),
            TopicDefinition("Arrays & Strings", listOf("array", "arrays", "string", "substring", "subarray", "prefix sum", "two pointer", "sliding window")),
            TopicDefinition("Linked Lists", listOf("linked list", "linked lists", "singly linked list", "doubly linked list")),
            TopicDefinition("Stacks & Queues", listOf("stack", "queue", "deque", "monotonic stack", "priority queue")),
            TopicDefinition("Trees", listOf("tree", "binary tree", "bst", "heap", "segment tree", "trie")),
            TopicDefinition("Graphs", listOf("graph", "graphs", "bfs", "dfs", "dijkstra", "topological sort", "union find", "disjoint set")),
            TopicDefinition("Sorting & Searching", listOf("binary search", "sorting", "sort", "search", "mergesort", "quicksort")),
            TopicDefinition("Object-Oriented Programming", listOf("oops", "oop", "object oriented", "inheritance", "polymorphism", "encapsulation", "abstraction")),
            TopicDefinition("Databases & SQL", listOf("sql", "database", "databases", "mysql", "postgres", "join", "index", "normalization")),
            TopicDefinition("Web Development", listOf("html", "css", "javascript", "react", "node", "frontend", "backend", "rest api")),
            TopicDefinition("Android Development", listOf("android", "kotlin", "compose", "jetpack", "activity", "fragment", "viewmodel")),
            TopicDefinition("System Design", listOf("system design", "scalability", "load balancer", "cache", "microservices")),
            TopicDefinition("Machine Learning", listOf("machine learning", "ml", "neural network", "classification", "regression", "training data", "model")),
            TopicDefinition("Statistics & Probability", listOf("probability", "statistics", "mean", "median", "variance", "distribution")),
            TopicDefinition("Mathematics", listOf("math", "mathematics", "algebra", "calculus", "geometry", "equation", "integral", "derivative", "matrix")),
            TopicDefinition("Physics", listOf("physics", "force", "motion", "energy", "velocity", "acceleration")),
            TopicDefinition("Chemistry", listOf("chemistry", "molecule", "reaction", "atom", "bond", "acid", "base")),
            TopicDefinition("Biology", listOf("biology", "cell", "genetics", "evolution", "photosynthesis", "dna")),
            TopicDefinition("Economics", listOf("economics", "demand", "supply", "inflation", "gdp", "market")),
            TopicDefinition("History", listOf("history", "war", "empire", "revolution", "civilization")),
            TopicDefinition("English & Writing", listOf("english", "grammar", "essay", "writing", "literature", "poem"))
        )

        private val moodKeywordMap = mapOf(
            "happy" to listOf("happy", "good", "great", "better", "glad", "joy", "fun"),
            "excited" to listOf("excited", "thrilled", "cant wait", "can't wait", "hyped", "celebrating"),
            "stressed" to listOf("stressed", "pressure", "overwhelmed", "panic", "burnout", "drained"),
            "sad" to listOf("sad", "down", "cry", "crying", "hurt", "heartbroken", "upset"),
            "tired" to listOf("tired", "sleepy", "exhausted", "worn out", "low energy"),
            "lonely" to listOf("lonely", "alone", "empty", "missing you", "miss you")
        )
    }
    
    private val aiService = AIInferenceService()
    private var chatHistoryManager: ChatHistoryManager? = null
    private var appContext: Context? = null
    private var historicalMessagesCache: List<ChatMessage> = emptyList()
    private var hasHydratedHistoryCache = false
    private var activeAssistantMessageId: Long? = null
    
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

    var isLoadingReasoning by mutableStateOf(false)
        private set

    var sourceDetails by mutableStateOf<AnswerSourceDetails?>(null)
        private set
    
    var showNotesDialog by mutableStateOf(false)
        private set
    
    var notesContent by mutableStateOf("")
        private set
    
    var selectedCustomMode by mutableStateOf<CustomMode?>(null)
        private set
    
    // Generation job for cancellation support
    private var generationJob: Job? = null
    private val reasoningCache = mutableMapOf<String, List<ReasoningStep>>()
    
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
            refreshHistoricalMessagesCacheAsync()
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

    fun applyLaunchSelection(assistantId: String?) {
        val targetId = assistantId?.trim().orEmpty()
        if (targetId.isBlank()) {
            selectedCustomMode = null
            selectedMode = ResponseMode.DIRECT
            return
        }

        val mode = CustomModeStore.getModeById(targetId)
        selectedCustomMode = mode
        if (mode == null) {
            selectedMode = ResponseMode.DIRECT
        }
    }

    fun changeCustomMode(customMode: CustomMode?) {
        selectedCustomMode = customMode
    }
    
    fun sendMessage(
        query: String,
        imageUri: Uri? = null,
        imageFocusMode: ImageFocusMode = ImageFocusMode.FULL_FRAME,
        isVisionReady: Boolean = true
    ) {
        if (query.isBlank() && imageUri == null || isGenerating) return

        val visibleUserQuery = when {
            query.isNotBlank() -> query.trim()
            imageUri != null -> defaultImageQuestion()
            else -> query
        }

        val userMessage = ChatMessage(text = visibleUserQuery, isUser = true, imageUri = imageUri)
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

                val preparedImage = if (imageUri != null) prepareImageForVision(imageUri, imageFocusMode) else null
                if (imageUri != null && preparedImage == null) {
                    messages = messages + ChatMessage(
                        text = "I could not access the selected image. Please pick the image again.",
                        isUser = false
                    )
                    return@launch
                }
                preparedImage?.let { prepared ->
                    messages = messages.map { message ->
                        if (message.id == userMessage.id) {
                            message.copy(
                                imageUri = prepared.displayImageUri,
                                imageAnalysisTrace = prepared.trace
                            )
                        } else {
                            message
                        }
                    }
                }

                val finalQuery = visibleUserQuery
                val history = buildConversationHistory(
                    currentQuery = finalQuery,
                    activeMode = selectedCustomMode,
                    hasImageInput = preparedImage != null
                )
                val effectiveMode = selectedCustomMode?.let { preferredResponseModeFor(it, finalQuery) } ?: selectedMode
                val assistantMessageId = System.currentTimeMillis() + 1
                val assistantLabel = selectedCustomMode?.name

                appendPendingAssistantMessage(assistantMessageId, assistantLabel)
                
                val response = aiService.generateAnswer(
                    userQuery = finalQuery,
                    mode = effectiveMode,
                    language = selectedLanguage,
                    userLevel = selectedLevel,
                    customPrompt = buildCustomPrompt(selectedCustomMode),
                    enabledTools = if (preparedImage != null) emptyList() else (selectedCustomMode?.enabledTools ?: emptyList()),
                    imagePath = preparedImage?.modelImagePath,
                    conversationHistory = history,
                    onRetryStarted = {
                        withContext(Dispatchers.Main.immediate) {
                            markPendingAssistantMessageAsRetrying(assistantMessageId)
                        }
                    },
                    onPartialResponse = { partial ->
                        withContext(Dispatchers.Main.immediate) {
                            updatePendingAssistantMessage(assistantMessageId, partial)
                        }
                    }
                )
                
                val aiMessage = ChatMessage(
                    id = assistantMessageId,
                    text = response.toDisplayText(),
                    isUser = false,
                    structuredResponse = response,
                    imageUri = preparedImage?.displayImageUri,
                    imageAnalysisTrace = preparedImage?.trace,
                    assistantLabel = assistantLabel
                )
                replacePendingAssistantMessage(assistantMessageId, aiMessage)
                
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
                    replacePendingAssistantMessage(
                        activeAssistantMessageId,
                        ChatMessage(
                            text = errorText,
                            isUser = false,
                            assistantLabel = selectedCustomMode?.name
                        )
                    )
                }
            } finally {
                isGenerating = false
                activeAssistantMessageId = null
                generationJob = null
            }
        }
    }
    
    fun cancelGeneration() {
        aiService.cancelActiveGeneration()
        generationJob?.cancel()
        activeAssistantMessageId?.let { pendingId ->
            finalizePendingAssistantMessage(
                pendingId,
                interruptionNote = "[Response interrupted by user]"
            )
        }
        isGenerating = false
        generationJob = null
        activeAssistantMessageId = null
    }
    
    private fun autosaveChat() {
        viewModelScope.launch {
            try {
                val sessionName = if (currentSessionPath != null) 
                    java.io.File(currentSessionPath!!).nameWithoutExtension 
                else 
                    "chat_${System.currentTimeMillis()}"
                
                val savedPath = chatHistoryManager?.saveChat(
                    messages = messages,
                    sessionName = sessionName,
                    existingSessionPath = currentSessionPath
                )
                if (savedPath != null) {
                    currentSessionPath = savedPath
                    refreshHistoricalMessagesCache()
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
                refreshHistoricalMessagesCache()
                loadChatSessionsListOnly()
            } catch (_: Exception) {}
        }
    }

    fun deleteAllChatSessions() {
        viewModelScope.launch {
            try {
                chatHistoryManager?.deleteAllChatSessions()
                messages = emptyList()
                currentSessionPath = null
                historicalMessagesCache = emptyList()
                hasHydratedHistoryCache = true
                loadChatSessionsListOnly()
                updateUserStats()
            } catch (_: Exception) {}
        }
    }
    
    private fun updateUserStats() {
        val userMessages = messages.filter { it.isUser }
        userStats = mapOf(
            "totalQuestions" to userMessages.size,
            "preferredLanguage" to selectedLanguage.name,
            "preferredMode" to (selectedCustomMode?.name ?: selectedMode.displayName())
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
    
    fun showReasoningFor(
        userQuery: String,
        response: com.runanywhere.kotlin_starter_example.domain.models.StructuredResponse,
        responseText: String,
        assistantLabel: String? = null,
        imageUri: Uri? = null,
        imageAnalysisTrace: ImageAnalysisTrace? = null
    ) {
        sourceDetails = AnswerSourceDetails(
            question = userQuery,
            answer = responseText,
            modeLabel = displayModeLabel(response, assistantLabel),
            modelName = response.modelName,
            assistantLabel = assistantLabel,
            tokensUsed = response.tokensUsed,
            promptTokens = response.promptTokens,
            responseTokens = response.responseTokens,
            responseTimeMs = response.responseTimeMs,
            timeToFirstTokenMs = response.timeToFirstTokenMs,
            attempts = response.generationTrace,
            imageUri = imageUri,
            imageAnalysisTrace = imageAnalysisTrace
        )
        showReasoningDialog = true

        val cacheKey = buildString {
            append(normalizeText(userQuery))
            append("::")
            append(normalizeText(responseText))
            append("::")
            append(response.mode.name)
            append("::")
            append(response.generationTrace.size)
            imageAnalysisTrace?.let {
                append("::image::")
                append(it.preprocessingApplied)
                append(':')
                append(it.processedWidth)
                append('x')
                append(it.processedHeight)
            }
        }
        val cached = reasoningCache[cacheKey]
        if (cached != null) {
            reasoningSteps = cached
            isLoadingReasoning = false
            return
        }

        reasoningSteps = buildSourceTimelineSteps(sourceDetails!!)
        reasoningCache[cacheKey] = reasoningSteps
        isLoadingReasoning = false
    }
    
    fun dismissReasoningDialog() {
        showReasoningDialog = false
        isLoadingReasoning = false
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
        val historicalMessages = ensureHistoricalMessagesCacheLoaded()
        val currentSessionIds = messages.map { it.id }.toSet()
        val uniqueHistoricalMessages = historicalMessages.filter { it.id !in currentSessionIds }
        val allMessages = (messages + uniqueHistoricalMessages).sortedBy { it.timestamp }
        
        val aiMessages = allMessages.filter { !it.isUser }
        val userMessages = allMessages.filter { it.isUser }
        val globalStats = chatHistoryManager?.getGlobalStats()
        
        // Use calculated values
        val totalQuestions = userMessages.size
        val totalMessages = allMessages.size
        val totalSessions = (globalStats?.totalSessions ?: chatSessions.size).coerceAtLeast(if (allMessages.isNotEmpty()) 1 else 0)
        
        // Mode usage with fallback
        val modeUsage = mutableMapOf<String, Int>()
        aiMessages.forEach { msg ->
            val modeName = msg.assistantLabel ?: msg.structuredResponse?.mode?.displayName()
            if (!modeName.isNullOrBlank()) {
                modeUsage[modeName] = modeUsage.getOrDefault(modeName, 0) + 1
            }
        }
        if (modeUsage.isEmpty() && aiMessages.isNotEmpty()) {
            modeUsage[selectedMode.displayName()] = aiMessages.size
        }
        val modelUsage = aiMessages
            .mapNotNull { it.structuredResponse?.modelName?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()

        // Feedback statistics
        val feedbackStats = mutableMapOf<String, Int>()
        val modeFeedback = mutableMapOf<String, Pair<Int, Int>>()
        aiMessages.forEach { msg ->
            val feedback = when (msg.feedbackType) {
                FeedbackType.LIKE -> "Likes"
                FeedbackType.DISLIKE -> "Dislikes"
                null -> "No Feedback"
            }
            feedbackStats[feedback] = feedbackStats.getOrDefault(feedback, 0) + 1

            val modeName = msg.assistantLabel ?: msg.structuredResponse?.mode?.displayName()
            if (!modeName.isNullOrBlank()) {
                val (likes, dislikes) = modeFeedback[modeName] ?: (0 to 0)
                modeFeedback[modeName] = when (msg.feedbackType) {
                    FeedbackType.LIKE -> (likes + 1) to dislikes
                    FeedbackType.DISLIKE -> likes to (dislikes + 1)
                    null -> likes to dislikes
                }
            }
        }

        // Response time and tokens
        val responseTimes = aiMessages
            .mapNotNull { it.structuredResponse?.responseTimeMs }
            .filter { it > 0L }
        val avgResponseTime = if (responseTimes.isNotEmpty()) responseTimes.average() else 0.0
        val timeToFirstTokenValues = aiMessages
            .mapNotNull { it.structuredResponse?.timeToFirstTokenMs }
            .filter { it > 0L }
        val avgTimeToFirstTokenMs = if (timeToFirstTokenValues.isNotEmpty()) {
            timeToFirstTokenValues.average()
        } else {
            0.0
        }

        val tokenUsages = aiMessages.mapNotNull { it.structuredResponse?.tokensUsed }
        val totalTokens = if (tokenUsages.sum() > 0) tokenUsages.sum() else (allMessages.size / 2) * 100
        val avgTokens = if (tokenUsages.isNotEmpty()) tokenUsages.average() else 100.0

        // Topics and time
        val topicInsights = extractTopics(userMessages.map { it.text })
        val topicsCovered = topicInsights.size
        val timeSpentMinutes = calculateSessionizedStudyMinutes(allMessages)
        val recentActivity = buildRecentDailyActivity(userMessages)
        val recentActiveDays = recentActivity.count { it.questionCount > 0 }
        val studyConsistencyPercent = if (recentActivity.isNotEmpty()) {
            ((recentActiveDays * 100f) / recentActivity.size).roundToInt()
        } else {
            0
        }
        val visionQuestions = userMessages.count { it.imageUri != null }
        val visionResponses = aiMessages.count { message ->
            message.imageUri != null || (message.structuredResponse?.modelName?.contains("VLM", ignoreCase = true) == true)
        }
        val visionSharePercent = if (totalQuestions > 0) {
            ((visionQuestions * 100f) / totalQuestions).roundToInt()
        } else {
            0
        }

        val activeDates = userMessages.map { toLocalDate(it.timestamp) }.toSet()
        val activeDays = activeDates.size
        val currentStudyStreak = calculateCurrentStreak(activeDates)
        val longestStudyStreak = calculateLongestStreak(activeDates)
        val today = LocalDate.now(systemZone)
        val questionsThisWeek = userMessages.count { !toLocalDate(it.timestamp).isBefore(today.minusDays(6)) }
        val previousWeekQuestions = userMessages.count { message ->
            val date = toLocalDate(message.timestamp)
            !date.isBefore(today.minusDays(13)) && date.isBefore(today.minusDays(6))
        }
        val avgQuestionsPerActiveDay = if (activeDays > 0) totalQuestions.toDouble() / activeDays else 0.0
        val avgMessagesPerSession = if (totalSessions > 0) totalMessages.toDouble() / totalSessions else 0.0
        val peakStudyHour = userMessages
            .groupingBy { Instant.ofEpochMilli(it.timestamp).atZone(systemZone).hour }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val peakStudyWindow = formatStudyWindow(peakStudyHour)
        
        // Writing style analysis
        val writingStyle = analyzeWritingStyle(userMessages.map { it.text })

        val preferredMode = when {
            modeUsage.isNotEmpty() -> modeUsage.maxByOrNull { it.value }?.key ?: selectedMode.displayName()
            selectedCustomMode != null -> selectedCustomMode?.name ?: selectedMode.displayName()
            else -> selectedMode.displayName()
        }
        val preferredLanguage = aiMessages
            .mapNotNull { it.structuredResponse?.language?.displayName() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: selectedLanguage.displayName()
        val ratedResponses = aiMessages.count { it.feedbackType != null }
        val feedbackCoveragePercent = if (aiMessages.isNotEmpty()) {
            ((ratedResponses * 100.0) / aiMessages.size).toInt()
        } else {
            0
        }
        val bestRatedMode = modeFeedback
            .filterValues { (likes, dislikes) -> likes + dislikes > 0 }
            .maxWithOrNull(
                compareByDescending<Map.Entry<String, Pair<Int, Int>>> { (_, counts) ->
                    val (likes, dislikes) = counts
                    val ratedTotal = likes + dislikes
                    if (ratedTotal == 0) Double.NEGATIVE_INFINITY else likes.toDouble() / ratedTotal
                }.thenByDescending { (_, counts) ->
                    counts.first - counts.second
                }
            )
            ?.key
        val analyticsInsights = buildAnalyticsInsights(
            totalQuestions = totalQuestions,
            questionsThisWeek = questionsThisWeek,
            previousWeekQuestions = previousWeekQuestions,
            topTopic = topicInsights.firstOrNull()?.name,
            peakStudyWindow = peakStudyWindow,
            preferredMode = preferredMode,
            preferredLanguage = preferredLanguage,
            feedbackCoveragePercent = feedbackCoveragePercent,
            bestRatedMode = bestRatedMode
        )
        val modeBreakdown = buildBreakdownItems(modeUsage, aiMessages.size) { label, count ->
            if (count == 1) "1 reply used $label mode." else "$count replies used $label mode."
        }
        val modelBreakdown = buildBreakdownItems(modelUsage, aiMessages.size) { label, count ->
            if (count == 1) "1 saved reply came from $label." else "$count saved replies came from $label."
        }
        val feedbackBreakdown = buildBreakdownItems(feedbackStats, aiMessages.size) { label, count ->
            if (count == 1) "1 reply is in $label." else "$count replies are in $label."
        }

        return mapOf(
            "totalSessions" to totalSessions,
            "totalConversations" to totalSessions,
            "totalMessages" to totalMessages,
            "totalQuestions" to totalQuestions,
            "totalResponses" to aiMessages.size,
            "ratedResponses" to ratedResponses,
            "modeUsage" to modeUsage,
            "feedbackStats" to feedbackStats,
            "feedbackCoveragePercent" to feedbackCoveragePercent,
            "avgResponseTime" to avgResponseTime,
            "avgTimeToFirstTokenMs" to avgTimeToFirstTokenMs,
            "totalTokens" to totalTokens,
            "avgTokensPerResponse" to avgTokens,
            "writingStyle" to writingStyle,
            "preferredLanguage" to preferredLanguage,
            "preferredMode" to preferredMode,
            "preferredLevel" to selectedLevel.displayName(),
            "topicsCovered" to topicsCovered,
            "topicsList" to topicInsights.map { it.name },
            "topicInsights" to topicInsights,
            "topTopic" to (topicInsights.firstOrNull()?.name ?: "General Learning"),
            "timeSpentMinutes" to timeSpentMinutes,
            "activeDays" to activeDays,
            "currentStudyStreak" to currentStudyStreak,
            "longestStudyStreak" to longestStudyStreak,
            "questionsThisWeek" to questionsThisWeek,
            "previousWeekQuestions" to previousWeekQuestions,
            "avgQuestionsPerActiveDay" to avgQuestionsPerActiveDay,
            "avgMessagesPerSession" to avgMessagesPerSession,
            "peakStudyWindow" to peakStudyWindow,
            "recentActivity" to recentActivity,
            "studyConsistencyPercent" to studyConsistencyPercent,
            "visionQuestions" to visionQuestions,
            "visionResponses" to visionResponses,
            "visionSharePercent" to visionSharePercent,
            "modeVariety" to modeUsage.size,
            "modelVariety" to modelUsage.size,
            "modeBreakdown" to modeBreakdown,
            "modelBreakdown" to modelBreakdown,
            "feedbackBreakdown" to feedbackBreakdown,
            "bestRatedMode" to (bestRatedMode ?: ""),
            "insights" to analyticsInsights
        )
    }

    private fun calculateSessionizedStudyMinutes(
        messages: List<ChatMessage>,
        inactivityGapMinutes: Long = 30L
    ): Int {
        if (messages.isEmpty()) return 0

        val sortedTimestamps = messages.map { it.timestamp }.sorted()
        val gapThresholdMs = inactivityGapMinutes * 60_000L
        var sessionStart = sortedTimestamps.first()
        var sessionEnd = sortedTimestamps.first()
        var totalDurationMs = 0L

        sortedTimestamps.drop(1).forEach { timestamp ->
            if (timestamp - sessionEnd <= gapThresholdMs) {
                sessionEnd = timestamp
            } else {
                totalDurationMs += (sessionEnd - sessionStart).coerceAtLeast(60_000L)
                sessionStart = timestamp
                sessionEnd = timestamp
            }
        }

        totalDurationMs += (sessionEnd - sessionStart).coerceAtLeast(60_000L)
        return ((totalDurationMs + 59_999L) / 60_000L).toInt()
    }

    private fun buildAnalyticsInsights(
        totalQuestions: Int,
        questionsThisWeek: Int,
        previousWeekQuestions: Int,
        topTopic: String?,
        peakStudyWindow: String,
        preferredMode: String,
        preferredLanguage: String,
        feedbackCoveragePercent: Int,
        bestRatedMode: String?
    ): List<AnalyticsInsightItem> {
        if (totalQuestions == 0) return emptyList()

        val insights = mutableListOf<AnalyticsInsightItem>()

        val activityInsight = when {
            questionsThisWeek > previousWeekQuestions && questionsThisWeek > 0 ->
                AnalyticsInsightItem(
                    title = "Learning momentum is up",
                    description = "You asked $questionsThisWeek questions in the last 7 days, up from $previousWeekQuestions in the previous week."
                )

            previousWeekQuestions > questionsThisWeek && previousWeekQuestions > 0 ->
                AnalyticsInsightItem(
                    title = "Your pace dipped this week",
                    description = "You asked $questionsThisWeek questions in the last 7 days versus $previousWeekQuestions in the previous week."
                )

            questionsThisWeek > 0 ->
                AnalyticsInsightItem(
                    title = "You are actively studying",
                    description = "You asked $questionsThisWeek questions this week and most often study around $peakStudyWindow."
                )

            else ->
                AnalyticsInsightItem(
                    title = "Your recent activity is light",
                    description = "You have built history before, but there were no new questions in the last 7 days."
                )
        }
        insights += activityInsight

        topTopic?.takeIf { it.isNotBlank() }?.let { topic ->
            insights += AnalyticsInsightItem(
                title = "Your strongest topic signal",
                description = "$topic is your most repeated study theme so far. $preferredMode mode appears to be your go-to format."
            )
        }

        if (feedbackCoveragePercent > 0) {
            val description = if (!bestRatedMode.isNullOrBlank()) {
                "You have rated $feedbackCoveragePercent% of assistant replies, and $bestRatedMode is performing best for you."
            } else {
                "You have rated $feedbackCoveragePercent% of assistant replies, which is enough to start learning your preferences."
            }
            insights += AnalyticsInsightItem(
                title = "Feedback is becoming useful",
                description = description
            )
        } else {
            insights += AnalyticsInsightItem(
                title = "Personalization can get stronger",
                description = "You mostly study in $preferredLanguage, but the app still has no reply feedback to learn from yet."
            )
        }

        return insights.take(3)
    }

    private fun buildBreakdownItems(
        counts: Map<String, Int>,
        total: Int,
        supportingText: (label: String, count: Int) -> String? = { _, _ -> null }
    ): List<AnalyticsBreakdownItem> {
        if (counts.isEmpty()) return emptyList()

        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { (label, count) ->
                AnalyticsBreakdownItem(
                    label = label,
                    count = count,
                    share = if (total > 0) count.toFloat() / total else 0f,
                    supportingText = supportingText(label, count)
                )
            }
    }

    private fun buildRecentDailyActivity(
        userMessages: List<ChatMessage>,
        days: Long = 7L
    ): List<DailyActivityPoint> {
        if (days <= 0L) return emptyList()

        val messagesByDate = userMessages.groupBy { toLocalDate(it.timestamp) }
        val today = LocalDate.now(systemZone)
        val fullDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

        return (days - 1 downTo 0L).map { offset ->
            val date = today.minusDays(offset)
            val dayMessages = messagesByDate[date].orEmpty()
            DailyActivityPoint(
                label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                dateLabel = date.format(fullDateFormatter),
                questionCount = dayMessages.size,
                imageQuestionCount = dayMessages.count { it.imageUri != null }
            )
        }
    }

    private suspend fun buildConversationHistory(
        currentQuery: String,
        activeMode: CustomMode? = selectedCustomMode,
        hasImageInput: Boolean = false
    ): List<Pair<String, String>> {
        val currentSessionHistory = messages
            .dropLast(1)
            .filter { it.text.isNotBlank() && !it.isPending }

        val recentConversation = currentSessionHistory
            .takeLast(12)
            .mapNotNull { message ->
                when {
                    message.isUser -> "user" to message.text.trim().take(240)
                    shouldIncludeAssistantHistory(message, activeMode) ->
                        "assistant" to message.text.trim().take(240)
                    else -> null
                }
            }
            .takeLast(if (hasImageInput) 6 else 8)

        if (!CustomModeStore.isCompanionMode(activeMode)) {
            return if (hasImageInput) recentConversation.takeLast(4) else recentConversation
        }

        val historicalMessages = ensureHistoricalMessagesCacheLoaded()
            .filter { it.text.isNotBlank() }
        val currentFingerprints = messages.map(::messageFingerprint).toSet()
        val previousMessages = historicalMessages.filterNot { messageFingerprint(it) in currentFingerprints }

        val profileMemory = buildCompanionProfileMemory(previousMessages)
        val rememberedContext = findRelevantPastMessages(currentQuery, previousMessages)

        return buildList {
            profileMemory?.let { add("memory" to it) }
            rememberedContext.forEach { add("memory" to it) }
            addAll(recentConversation)
        }
    }

    private fun shouldIncludeAssistantHistory(
        message: ChatMessage,
        activeMode: CustomMode?
    ): Boolean {
        if (message.isUser || message.isPending) return false

        val currentAssistantLabel = activeMode?.name
        return when {
            currentAssistantLabel != null -> message.assistantLabel == currentAssistantLabel
            else -> message.assistantLabel == null
        }
    }

    private fun buildLearnerProfileMemory(previousMessages: List<ChatMessage>): String? {
        val pastUserMessages = previousMessages.filter { it.isUser && it.text.isNotBlank() }
        if (pastUserMessages.size < 2) return null

        val topTopics = extractTopics(pastUserMessages.map { it.text }).take(3).map { it.name }
        val recentPastQuestions = pastUserMessages
            .sortedByDescending { it.timestamp }
            .map { it.text.trim() }
            .distinctBy(::normalizeText)
            .take(2)

        return buildString {
            append("Learner profile from past chats: ")
            if (topTopics.isNotEmpty()) {
                append("frequently studies ")
                append(topTopics.joinToString(", "))
                append(". ")
            }
            if (recentPastQuestions.isNotEmpty()) {
                append("Earlier questions included: ")
                append(recentPastQuestions.joinToString(" | ") { it.take(70) })
                append(".")
            }
        }.trim().takeIf { it.isNotBlank() }
    }

    private fun buildCompanionProfileMemory(previousMessages: List<ChatMessage>): String? {
        val pastUserMessages = previousMessages.filter { it.isUser && it.text.isNotBlank() }
        if (pastUserMessages.isEmpty()) return null

        val relationshipMessageCount = previousMessages.count { it.text.isNotBlank() }
        val relationshipStage = inferCompanionRelationshipStage(relationshipMessageCount)
        val stageGuidance = companionStageGuidance(relationshipMessageCount)
        val recentHighlights = pastUserMessages
            .sortedByDescending { it.timestamp }
            .map { it.text.trim() }
            .distinctBy(::normalizeText)
            .take(3)

        val recentMoods = detectRecentMoods(pastUserMessages.takeLast(8).map { it.text })
        val recurringPersonalTopics = extractRelationshipTopics(pastUserMessages.map { it.text }).take(3)
        val recentDynamics = detectRelationshipDynamics(pastUserMessages.takeLast(16).map { it.text })
        val conversationRhythm = inferCompanionConversationRhythm(pastUserMessages.takeLast(20).map { it.text })
        val recentImageShares = pastUserMessages.count { it.imageUri != null }
        val latestPersonalMoment = pastUserMessages
            .lastOrNull()
            ?.text
            ?.trim()
            ?.take(90)

        return buildString {
            append("Relationship memory from earlier chats: ")
            append("current bond stage is ")
            append(relationshipStage)
            append(". ")
            append("Stage guidance: ")
            append(stageGuidance)
            append(". ")
            if (recentMoods.isNotEmpty()) {
                append("the user's recent mood has included ")
                append(recentMoods.joinToString(", "))
                append(". ")
            }
            if (recurringPersonalTopics.isNotEmpty()) {
                append("They often talk about ")
                append(recurringPersonalTopics.joinToString(", "))
                append(". ")
            }
            if (recentDynamics.isNotEmpty()) {
                append("Recent relationship dynamics include ")
                append(recentDynamics.joinToString(", "))
                append(". ")
            }
            conversationRhythm?.let { rhythm ->
                append("Conversation rhythm: ")
                append(rhythm)
                append(". ")
            }
            if (recentImageShares > 0) {
                append("They have shared ")
                append(recentImageShares)
                append(if (recentImageShares == 1) " photo before. " else " photos before. ")
            }
            latestPersonalMoment?.takeIf { it.isNotBlank() }?.let { lastMoment ->
                append("Latest personal moment: ")
                append(lastMoment)
                append(". ")
            }
            if (recentHighlights.isNotEmpty()) {
                append("Recent personal things they shared: ")
                append(recentHighlights.joinToString(" | ") { it.take(80) })
                append(".")
            }
        }.trim().takeIf { it.isNotBlank() }
    }

    private fun inferCompanionRelationshipStage(messageCount: Int): String = when {
        messageCount >= 80 -> "deep, life-partner-level closeness"
        messageCount >= 36 -> "strong romantic attachment and comfort"
        messageCount >= 16 -> "growing affection, trust, and emotional closeness"
        messageCount >= 6 -> "early chemistry with warmth and curiosity"
        else -> "a new connection that is just starting to become personal"
    }

    private fun companionStageGuidance(messageCount: Int): String = when {
        messageCount >= 80 ->
            "You can sound secure, deeply attached, and partner-like, with strong continuity and future-facing warmth"

        messageCount >= 36 ->
            "Use clear affection, comfort, and emotional reassurance, but keep it natural rather than dramatic"

        messageCount >= 16 ->
            "Let closeness deepen with tenderness, soft flirtation, and growing trust without jumping into forever-love speeches every turn"

        messageCount >= 6 ->
            "Keep the energy warm, curious, and lightly flirty; build intimacy gradually instead of acting like a long-time life partner"

        else ->
            "Treat this like a new romantic connection: be sweet and interested, but do not act intensely possessive, deeply devoted, or already life-partner level"
    }

    private fun findRelevantPastMessages(query: String, previousMessages: List<ChatMessage>): List<String> {
        if (query.isBlank()) return emptyList()

        return previousMessages
            .filter { it.isUser && it.text.isNotBlank() }
            .mapNotNull { message ->
                val score = scoreHistoricalRelevance(query, message.text)
                if (score > 1.2) {
                    RelevantMemoryMatch(message.text.trim(), score, message.timestamp)
                } else {
                    null
                }
            }
            .sortedWith(compareByDescending<RelevantMemoryMatch> { it.score }.thenByDescending { it.timestamp })
            .distinctBy { normalizeText(it.text) }
            .take(4)
            .map { it.text.take(220) }
    }

    private fun extractTopics(queries: List<String>): List<TopicInsight> {
        val validQueries = queries.map(String::trim).filter { it.length > 2 }
        if (validQueries.isEmpty()) return emptyList()

        val catalogMatches = topicCatalog.mapNotNull { topic ->
            val matchedKeywords = mutableSetOf<String>()
            var score = 0

            validQueries.forEach { query ->
                val normalizedQuery = normalizeText(query)
                val queryTokens = extractMeaningfulTokens(query).toSet()
                val queryMatches = topic.keywords.filter { keyword ->
                    keywordMatchesQuery(keyword, normalizedQuery, queryTokens)
                }

                if (queryMatches.isNotEmpty()) {
                    matchedKeywords += queryMatches
                    score += queryMatches.size.coerceAtMost(2)
                }
            }

            if (score > 0) {
                TopicInsight(
                    name = topic.name,
                    mentionCount = score,
                    matchedKeywords = matchedKeywords.toList().take(3)
                )
            } else {
                null
            }
        }

        val fallbackTopics = extractFallbackTopics(validQueries, catalogMatches)
        val combinedTopics = (catalogMatches + fallbackTopics)
            .sortedWith(compareByDescending<TopicInsight> { it.mentionCount }.thenBy { it.name })
            .take(8)

        return if (combinedTopics.isEmpty()) {
            listOf(TopicInsight("General Learning", validQueries.size))
        } else {
            combinedTopics
        }
    }

    private fun extractFallbackTopics(
        queries: List<String>,
        existingTopics: List<TopicInsight>
    ): List<TopicInsight> {
        val blockedLabels = existingTopics.map { normalizeText(it.name) }.toSet()
        val phraseScores = mutableMapOf<String, Int>()

        queries.forEach { query ->
            val tokens = extractMeaningfulTokens(query)
            val candidatePhrases = mutableSetOf<String>()

            tokens.forEach { token ->
                if (token.length >= 5) {
                    candidatePhrases += token
                }
            }
            tokens.windowed(size = 2, step = 1, partialWindows = false).forEach { phraseTokens ->
                candidatePhrases += phraseTokens.joinToString(" ")
            }
            tokens.windowed(size = 3, step = 1, partialWindows = false).forEach { phraseTokens ->
                candidatePhrases += phraseTokens.joinToString(" ")
            }

            candidatePhrases.forEach { phrase ->
                if (isUsefulTopicPhrase(phrase)) {
                    val label = formatTopicLabel(phrase)
                    phraseScores[label] = phraseScores.getOrDefault(label, 0) + if (phrase.contains(" ")) 2 else 1
                }
            }
        }

        return phraseScores.entries
            .asSequence()
            .filter { (label, score) ->
                normalizeText(label) !in blockedLabels &&
                    (label.contains(" ") || score >= 2)
            }
            .sortedByDescending { it.value }
            .take(4)
            .map { (label, score) -> TopicInsight(label, score) }
            .toList()
    }

    private fun extractRelationshipTopics(messages: List<String>): List<String> {
        val blockedTokens = stopWords + setOf(
            "love", "baby", "babe", "dear", "sweet", "miss", "photo", "picture", "selfie"
        )
        val scores = mutableMapOf<String, Int>()

        messages.forEach { message ->
            extractMeaningfulTokens(message)
                .filterNot { it in blockedTokens }
                .forEach { token ->
                    scores[token] = scores.getOrDefault(token, 0) + 1
                }
        }

        return scores.entries
            .sortedByDescending { it.value }
            .map { formatTopicLabel(it.key) }
            .distinct()
            .take(4)
    }

    private fun detectRecentMoods(messages: List<String>): List<String> {
        val normalizedMessages = messages.map(::normalizeText)

        return moodKeywordMap.entries
            .mapNotNull { (label, keywords) ->
                if (normalizedMessages.any { message -> keywords.any { keyword -> message.contains(normalizeText(keyword)) } }) {
                    label
                } else {
                    null
                }
            }
            .take(3)
    }

    private fun detectRelationshipDynamics(messages: List<String>): List<String> {
        if (messages.isEmpty()) return emptyList()

        val normalizedMessages = messages.map(::normalizeText)
        val dynamics = listOf(
            "playful flirting" to listOf("cute", "handsome", "beautiful", "pretty", "hot", "tease", "flirt"),
            "reassurance and closeness" to listOf("miss you", "love you", "need you", "stay with me", "hug me", "hold me"),
            "repair and reassurance" to listOf("sorry", "forgive", "jealous", "angry", "ignored", "hurt"),
            "deep life talks" to listOf("life", "future", "society", "world", "meaning", "purpose", "pov", "perspective"),
            "adult intimacy" to listOf("sex", "intimacy", "kiss", "desire", "turned on", "horny", "make love"),
            "future building" to listOf("future", "marry", "marriage", "together", "family", "kids", "home")
        )

        return dynamics.mapNotNull { (label, keywords) ->
            if (normalizedMessages.any { message -> keywords.any { keyword -> message.contains(normalizeText(keyword)) } }) {
                label
            } else {
                null
            }
        }.take(4)
    }

    private fun inferCompanionConversationRhythm(messages: List<String>): String? {
        if (messages.isEmpty()) return null

        val normalizedMessages = messages.map(::normalizeText)
        val shortReplyCount = normalizedMessages.count { message ->
            message.split(" ").count { it.isNotBlank() } <= 4
        }
        val deepTalkCount = normalizedMessages.count { message ->
            listOf("life", "society", "world", "future", "meaning", "purpose", "pov", "perspective")
                .any { keyword -> message.contains(keyword) }
        }
        val playfulCount = normalizedMessages.count { message ->
            listOf("cute", "miss you", "love you", "tease", "flirt", "handsome", "beautiful")
                .any { keyword -> message.contains(keyword) }
        }
        val vulnerableCount = normalizedMessages.count { message ->
            listOf("sad", "lonely", "hurt", "anxious", "overthinking", "scared", "jealous")
                .any { keyword -> message.contains(keyword) }
        }

        return when {
            deepTalkCount >= 3 && playfulCount >= 2 ->
                "They move between thoughtful deep talks and playful affection"

            vulnerableCount >= 2 ->
                "They often open up emotionally and respond well to reassurance, steadiness, and closeness"

            playfulCount >= 3 ->
                "They enjoy playful, flirty back-and-forth with emotional warmth"

            shortReplyCount >= 4 && shortReplyCount >= normalizedMessages.size / 2 ->
                "They often send short texts, so the companion should carry the rhythm gently without sounding repetitive"

            else -> null
        }
    }

    private fun scoreHistoricalRelevance(query: String, candidate: String): Double {
        val queryTokens = extractMeaningfulTokens(query).toSet()
        val candidateTokens = extractMeaningfulTokens(candidate).toSet()
        val sharedTokens = queryTokens.intersect(candidateTokens)
        val sharedPhrases = extractImportantPhrases(query).intersect(extractImportantPhrases(candidate))
        val sharedTopics = matchedTopicNames(query).intersect(matchedTopicNames(candidate))

        if (sharedTokens.isEmpty() && sharedPhrases.isEmpty() && sharedTopics.isEmpty()) {
            return 0.0
        }

        return (sharedTokens.size * 1.2) +
            (sharedPhrases.size * 2.4) +
            (sharedTopics.size * 1.8) +
            if (normalizeText(candidate).contains(normalizeText(query).take(24)) && query.length > 12) 1.0 else 0.0
    }

    private fun messageFingerprint(message: ChatMessage): String =
        "${message.isUser}:${message.timestamp}:${normalizeText(message.text)}"

    private fun normalizeText(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9+# ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun extractMeaningfulTokens(text: String): List<String> =
        normalizeText(text)
            .split(" ")
            .filter { token ->
                token.length > 2 &&
                    token !in stopWords &&
                    token.any { it.isLetter() }
            }

    private fun extractImportantPhrases(text: String): Set<String> {
        val tokens = extractMeaningfulTokens(text)
        val phrases = mutableSetOf<String>()

        tokens.windowed(size = 2, step = 1, partialWindows = false).forEach { window ->
            phrases += window.joinToString(" ")
        }
        tokens.windowed(size = 3, step = 1, partialWindows = false).forEach { window ->
            phrases += window.joinToString(" ")
        }

        return phrases.filterTo(mutableSetOf()) { isUsefulTopicPhrase(it) }
    }

    private fun keywordMatchesQuery(
        keyword: String,
        normalizedQuery: String,
        queryTokens: Set<String>
    ): Boolean {
        val normalizedKeyword = normalizeText(keyword)
        return if (normalizedKeyword.contains(" ")) {
            normalizedQuery.contains(normalizedKeyword)
        } else {
            normalizedKeyword in queryTokens
        }
    }

    private fun matchedTopicNames(text: String): Set<String> {
        val normalizedText = normalizeText(text)
        val tokens = extractMeaningfulTokens(text).toSet()

        return topicCatalog.mapNotNull { topic ->
            if (topic.keywords.any { keywordMatchesQuery(it, normalizedText, tokens) }) {
                topic.name
            } else {
                null
            }
        }.toSet()
    }

    private fun isUsefulTopicPhrase(phrase: String): Boolean {
        val tokens = phrase.split(" ")
        if (tokens.isEmpty()) return false
        if (tokens.all { it in stopWords }) return false
        if (tokens.any { it.length < 3 }) return false

        val normalizedPhrase = normalizeText(phrase)
        val blockedPhrases = setOf(
            "tell me", "help me", "show me", "what is", "how to", "why is",
            "explain this", "need help", "study this", "want know"
        )

        return normalizedPhrase.isNotBlank() &&
            normalizedPhrase !in blockedPhrases &&
            normalizedPhrase.any { it.isLetter() }
    }

    private fun formatTopicLabel(phrase: String): String =
        phrase.split(" ")
            .joinToString(" ") { token -> token.replaceFirstChar { char -> char.titlecase(Locale.getDefault()) } }

    private fun toLocalDate(timestamp: Long): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(systemZone).toLocalDate()

    private fun calculateCurrentStreak(activeDates: Set<LocalDate>): Int {
        if (activeDates.isEmpty()) return 0

        var streak = 1
        var cursor = activeDates.maxOrNull() ?: return 0

        while (activeDates.contains(cursor.minusDays(1))) {
            streak++
            cursor = cursor.minusDays(1)
        }

        return streak
    }

    private fun calculateLongestStreak(activeDates: Set<LocalDate>): Int {
        if (activeDates.isEmpty()) return 0

        val sortedDates = activeDates.sorted()
        var longest = 1
        var current = 1

        for (index in 1 until sortedDates.size) {
            current = if (sortedDates[index - 1].plusDays(1) == sortedDates[index]) {
                current + 1
            } else {
                1
            }
            if (current > longest) {
                longest = current
            }
        }

        return longest
    }

    private fun formatStudyWindow(hour: Int?): String {
        if (hour == null) return "No data yet"

        val start = formatHour(hour)
        val end = formatHour((hour + 1) % 24)
        return "$start - $end"
    }

    private fun formatHour(hour: Int): String {
        val normalizedHour = ((hour % 24) + 24) % 24
        val period = if (normalizedHour < 12) "AM" else "PM"
        val displayHour = when (val hourOnClock = normalizedHour % 12) {
            0 -> 12
            else -> hourOnClock
        }
        return "$displayHour $period"
    }

    private fun prepareImageForVision(
        uri: Uri,
        focusMode: ImageFocusMode = ImageFocusMode.FULL_FRAME
    ): PreparedImageInput? {
        val context = appContext ?: return null
        val originalPath = resolveImagePath(uri) ?: return null
        val originalFile = File(originalPath)
        val originalImageUri = Uri.fromFile(originalFile)
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(originalPath, bounds)

        val originalWidth = bounds.outWidth
        val originalHeight = bounds.outHeight
        val mimeType = context.contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(originalFile.extension.lowercase(Locale.getDefault()))

        if (originalWidth <= 0 || originalHeight <= 0) {
            return PreparedImageInput(
                displayImageUri = originalImageUri,
                modelImagePath = originalPath,
                trace = ImageAnalysisTrace(
                    sourceLabel = inferImageSourceLabel(uri),
                    focusModeLabel = focusMode.displayName,
                    mimeType = mimeType,
                    analyzedImageUri = null,
                    preprocessingApplied = false,
                    preprocessingSummary = "The file was readable, but the app could not inspect its dimensions for optimization.",
                    analysisSummary = "The original local image copy was sent directly to the vision model.",
                    previewNote = "The chat preview shows the same image file that was analyzed.",
                    steps = listOf(
                        ImageAnalysisStep(
                            title = "Local copy prepared",
                            description = "The uploaded image was copied into the app's local cache so it could be analyzed offline."
                        ),
                        ImageAnalysisStep(
                            title = "Optimization skipped",
                            description = "No resize or compression step was applied because the file dimensions could not be inspected."
                        ),
                        ImageAnalysisStep(
                            title = "No visual effects",
                            description = "No crop, blur, beauty filter, or color effect was applied."
                        )
                    )
                )
            )
        }

        val maxVisionDimension = 1536
        val shouldResize = maxOf(originalWidth, originalHeight) > maxVisionDimension
        val shouldCompress = originalFile.length() > 1_500_000L
        val shouldCrop = focusMode != ImageFocusMode.FULL_FRAME
        val shouldOptimize = shouldResize || shouldCompress || shouldCrop
        val sourceLabel = inferImageSourceLabel(uri)

        if (!shouldOptimize) {
            return PreparedImageInput(
                displayImageUri = originalImageUri,
                modelImagePath = originalPath,
                trace = ImageAnalysisTrace(
                    sourceLabel = sourceLabel,
                    focusModeLabel = focusMode.displayName,
                    mimeType = mimeType,
                    analyzedImageUri = null,
                    originalWidth = originalWidth,
                    originalHeight = originalHeight,
                    processedWidth = originalWidth,
                    processedHeight = originalHeight,
                    originalSizeBytes = originalFile.length(),
                    processedSizeBytes = originalFile.length(),
                    preprocessingApplied = false,
                    preprocessingSummary = "No resize or compression was needed because the image was already lightweight enough for the local vision model.",
                    analysisSummary = "The original local image copy was sent directly to the vision model.",
                    previewNote = "The chat preview shows the same image file that was analyzed.",
                    steps = listOf(
                        ImageAnalysisStep(
                            title = "Local copy prepared",
                            description = "The uploaded image was copied into the app's local cache so it could be analyzed offline."
                        ),
                        ImageAnalysisStep(
                            title = "Dimensions checked",
                            description = "The app inspected the image size: ${originalWidth}x${originalHeight}."
                        ),
                        ImageAnalysisStep(
                            title = "Focus area",
                            description = "Focus mode stayed on ${focusMode.displayName.lowercase(Locale.getDefault())}.",
                            wasApplied = false
                        ),
                        ImageAnalysisStep(
                            title = "No preprocessing needed",
                            description = "No crop, resize, compression, blur, or color effect was applied before analysis."
                        )
                    )
                )
            )
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateImageSampleSize(originalWidth, originalHeight, maxVisionDimension)
        }
        val decodedBitmap = BitmapFactory.decodeFile(originalPath, decodeOptions) ?: return null
        val focusedBitmap = applyFocusMode(decodedBitmap, focusMode)
        val optimizedBitmap = scaleBitmapIfNeeded(focusedBitmap, maxVisionDimension)
        val processedFile = File(context.cacheDir, "algsoch_vlm_${System.currentTimeMillis()}.jpg")
        processedFile.outputStream().use { stream ->
            optimizedBitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
        }
        val processedImageUri = Uri.fromFile(processedFile)

        val processedWidth = optimizedBitmap.width
        val processedHeight = optimizedBitmap.height
        if (focusedBitmap !== decodedBitmap) {
            decodedBitmap.recycle()
        }
        if (optimizedBitmap !== decodedBitmap) {
            if (optimizedBitmap !== focusedBitmap) {
                focusedBitmap.recycle()
            }
        }
        optimizedBitmap.recycle()

        val preprocessingBits = buildList {
            if (shouldCrop) add(focusMode.shortDescription.lowercase(Locale.getDefault()).removeSuffix("."))
            if (shouldResize) add("downscaled for faster local inference")
            if (shouldCompress) add("compressed into an optimized JPEG copy")
        }

        return PreparedImageInput(
            displayImageUri = originalImageUri,
            modelImagePath = processedFile.absolutePath,
            trace = ImageAnalysisTrace(
                sourceLabel = sourceLabel,
                focusModeLabel = focusMode.displayName,
                mimeType = mimeType,
                analyzedImageUri = processedImageUri,
                originalWidth = originalWidth,
                originalHeight = originalHeight,
                processedWidth = processedWidth,
                processedHeight = processedHeight,
                originalSizeBytes = originalFile.length(),
                processedSizeBytes = processedFile.length(),
                preprocessingApplied = true,
                preprocessingSummary = "The image was ${preprocessingBits.joinToString(" and ")} before it was sent to the local vision model.",
                analysisSummary = "The model analyzed an optimized local copy so image understanding stays faster and more stable on-device.",
                previewNote = "The chat preview keeps showing the original upload. Only the hidden model input copy was optimized.",
                steps = listOf(
                    ImageAnalysisStep(
                        title = "Local copy prepared",
                        description = "The uploaded image was copied into the app's local cache so it could be analyzed offline."
                    ),
                    ImageAnalysisStep(
                        title = "Dimensions checked",
                        description = "The app inspected the original image size: ${originalWidth}x${originalHeight}."
                    ),
                    ImageAnalysisStep(
                        title = "Focus area",
                        description = when (focusMode) {
                            ImageFocusMode.FULL_FRAME ->
                                "Focus mode stayed on full frame, so the whole image remained visible to the model."
                            ImageFocusMode.CENTER_FOCUS ->
                                "The model copy was cropped toward the center before analysis so the main subject gets more attention."
                            ImageFocusMode.SQUARE_CROP ->
                                "The model copy was converted into a tighter center square before analysis."
                        },
                        wasApplied = shouldCrop
                    ),
                    ImageAnalysisStep(
                        title = "Optimized model copy created",
                        description = "A separate ${processedWidth}x${processedHeight} JPEG copy was generated for the vision model to analyze."
                    ),
                    ImageAnalysisStep(
                        title = "No visual effects",
                        description = "No blur, beauty filter, or color effect was applied. The app only adjusted crop, size, and compression on the model input copy."
                    )
                )
            )
        )
    }

    private fun resolveImagePath(uri: Uri): String? {
        val context = appContext ?: return null
        return try {
            if (uri.scheme == "file") {
                val localPath = uri.path
                if (!localPath.isNullOrBlank()) {
                    val localFile = java.io.File(localPath)
                    if (localFile.exists()) {
                        return localFile.absolutePath
                    }
                }
            }

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

    private fun inferImageSourceLabel(uri: Uri): String {
        val normalized = uri.toString().lowercase(Locale.getDefault())
        return when {
            normalized.contains("algsoch_capture_") -> "Camera capture"
            uri.scheme == "content" -> "Gallery image"
            else -> "Local image"
        }
    }

    private fun calculateImageSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height

        while (currentWidth > maxDimension * 2 || currentHeight > maxDimension * 2) {
            sampleSize *= 2
            currentWidth /= 2
            currentHeight /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun applyFocusMode(bitmap: Bitmap, focusMode: ImageFocusMode): Bitmap =
        when (focusMode) {
            ImageFocusMode.FULL_FRAME -> bitmap

            ImageFocusMode.CENTER_FOCUS -> {
                val cropWidth = (bitmap.width * 0.82f).roundToInt().coerceAtLeast(1)
                val cropHeight = (bitmap.height * 0.82f).roundToInt().coerceAtLeast(1)
                val left = ((bitmap.width - cropWidth) / 2).coerceAtLeast(0)
                val top = ((bitmap.height - cropHeight) / 2).coerceAtLeast(0)
                Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
            }

            ImageFocusMode.SQUARE_CROP -> {
                val size = minOf(bitmap.width, bitmap.height)
                val left = ((bitmap.width - size) / 2).coerceAtLeast(0)
                val top = ((bitmap.height - size) / 2).coerceAtLeast(0)
                Bitmap.createBitmap(bitmap, left, top, size, size)
            }
        }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largestSide = maxOf(bitmap.width, bitmap.height)
        if (largestSide <= maxDimension) return bitmap

        val ratio = maxDimension.toFloat() / largestSide.toFloat()
        val scaledWidth = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val scaledHeight = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    }

    private fun defaultImageQuestion(): String = when (selectedLanguage) {
        Language.ENGLISH -> "What is in this image?"
        Language.HINDI -> "Is image mein kya dikh raha hai?"
        Language.HINGLISH -> "Is image mein kya hai?"
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
        val resolvedMode = CustomModeStore.resolveMode(mode)
        if (resolvedMode.enabledTools.isEmpty()) return resolvedMode.basePrompt

        val toolLines = resolvedMode.enabledTools.mapNotNull { toolId ->
            ToolRegistry.getToolById(toolId)?.let { tool ->
                "- ${tool.name} (${tool.id}): ${tool.description}. Params: ${tool.parametersDescription}"
            }
        }

        if (toolLines.isEmpty()) return resolvedMode.basePrompt

        return buildString {
            appendLine(resolvedMode.basePrompt)
            appendLine()
            appendLine("Available tools for this assistant:")
            toolLines.forEach { appendLine(it) }
            appendLine("Use tools when relevant. If a real-time tool call fails, clearly say so and do not hallucinate values.")
        }
    }

    private fun preferredResponseModeFor(mode: CustomMode, userQuery: String): ResponseMode = when {
        mode.id == "study_coach" -> ResponseMode.THEORY
        CustomModeStore.isCompanionMode(mode) -> preferredCompanionResponseMode(userQuery)
        else -> ResponseMode.EXPLAIN
    }

    private fun preferredCompanionResponseMode(userQuery: String): ResponseMode {
        val normalized = normalizeText(userQuery)
        val wordCount = normalized.split(" ").count { it.isNotBlank() }

        return when {
            normalized.isBlank() -> ResponseMode.DIRECT
            listOf("write me", "letter", "poem", "scene", "fantasy", "imagine").any { normalized.contains(it) } ->
                ResponseMode.CREATIVE
            wordCount >= 18 ||
                listOf(
                    "why", "how", "future", "society", "world", "life", "meaning", "purpose",
                    "relationship", "commitment", "trust", "marriage", "kids", "overthinking",
                    "perspective", "pov", "forgive", "sorry", "jealous", "sex", "intimacy"
                ).any { normalized.contains(it) } -> ResponseMode.ANSWER
            else -> ResponseMode.DIRECT
        }
    }

    private fun displayModeLabel(
        response: com.runanywhere.kotlin_starter_example.domain.models.StructuredResponse,
        assistantLabel: String?
    ): String {
        val resolvedMode = assistantLabel
            ?.let { label -> CustomModeStore.getModes().firstOrNull { it.name == label } }

        return if (CustomModeStore.isCompanionMode(resolvedMode)) {
            "Companion"
        } else {
            response.mode.displayName()
        }
    }

    private suspend fun ensureHistoricalMessagesCacheLoaded(): List<ChatMessage> {
        if (!hasHydratedHistoryCache) {
            refreshHistoricalMessagesCache()
        }
        return historicalMessagesCache
    }

    private suspend fun refreshHistoricalMessagesCache() {
        historicalMessagesCache = chatHistoryManager?.loadAllMessages() ?: emptyList()
        hasHydratedHistoryCache = true
    }

    private fun refreshHistoricalMessagesCacheAsync() {
        viewModelScope.launch {
            refreshHistoricalMessagesCache()
        }
    }

    private fun appendPendingAssistantMessage(messageId: Long, assistantLabel: String?) {
        activeAssistantMessageId = messageId
        messages = messages + ChatMessage(
            id = messageId,
            text = "Thinking...",
            isUser = false,
            assistantLabel = assistantLabel,
            isPending = true,
            generationStatus = "Generating..."
        )
    }

    private fun updatePendingAssistantMessage(messageId: Long, partialText: String) {
        val visibleText = partialText.ifBlank { "Thinking..." }
        messages = messages.map { message ->
            if (message.id == messageId) {
                message.copy(
                    text = visibleText,
                    isPending = true,
                    generationStatus = message.generationStatus ?: "Generating..."
                )
            } else {
                message
            }
        }
    }

    private fun markPendingAssistantMessageAsRetrying(messageId: Long) {
        messages = messages.map { message ->
            if (message.id == messageId) {
                message.copy(
                    isPending = true,
                    generationStatus = "Improving..."
                )
            } else {
                message
            }
        }
    }

    private fun replacePendingAssistantMessage(messageId: Long?, replacement: ChatMessage) {
        if (messageId == null) {
            messages = messages + replacement
            return
        }

        val updatedMessages = messages.map { message ->
            if (message.id == messageId) {
                replacement.copy(id = messageId, isPending = false)
            } else {
                message
            }
        }

        messages = if (updatedMessages.any { it.id == messageId }) {
            updatedMessages
        } else {
            messages + replacement.copy(id = messageId, isPending = false)
        }
    }

    private fun finalizePendingAssistantMessage(messageId: Long, interruptionNote: String) {
        messages = messages.map { message ->
            if (message.id == messageId) {
                val existingText = message.text.takeUnless { it == "Thinking..." }.orEmpty().trim()
                val finalText = listOf(existingText, interruptionNote)
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                    .ifBlank { interruptionNote }
                message.copy(
                    text = finalText,
                    isPending = false,
                    generationStatus = null
                )
            } else {
                message
            }
        }
    }

    private fun buildSourceTimelineSteps(source: AnswerSourceDetails): List<ReasoningStep> {
        val selectedAttempt = source.attempts.firstOrNull { it.wasSelected }
        val imageTrace = source.imageAnalysisTrace
        val attemptSteps = source.attempts.mapIndexed { index, attempt ->
            ReasoningStep(
                stepNumber = index + if (imageTrace != null) 4 else 3,
                title = attempt.label,
                description = buildString {
                    attempt.reason?.takeIf { it.isNotBlank() }?.let {
                        append(it)
                        append(' ')
                    }
                    append(
                        if (attempt.wasSelected) {
                            "This became the final answer."
                        } else {
                            "This draft was reviewed but not kept as the final answer."
                        }
                    )
                    if (attempt.wasStreamed) {
                        append(" It was visible while the answer was being generated.")
                    }
                }.trim()
            )
        }

        return buildList {
            add(
                ReasoningStep(
                    stepNumber = 1,
                    title = "Input Captured",
                    description = buildString {
                        append("The app captured the user's latest message")
                        source.imageAnalysisTrace?.let {
                            append(" and attached an image for local vision analysis")
                        }
                        append(".")
                    }
                )
            )
            if (imageTrace != null) {
                add(
                    ReasoningStep(
                        stepNumber = 2,
                        title = "Image Prepared",
                        description = buildString {
                            append(imageTrace.analysisSummary)
                            if (imageTrace.preprocessingSummary.isNotBlank()) {
                                append(" ")
                                append(imageTrace.preprocessingSummary)
                            }
                            if (imageTrace.previewNote.isNotBlank()) {
                                append(" ")
                                append(imageTrace.previewNote)
                            }
                        }.trim()
                    )
                )
            }
            add(
                ReasoningStep(
                    stepNumber = if (imageTrace != null) 3 else 2,
                    title = "Model And Mode Chosen",
                    description = buildString {
                        append("The app answered in ${source.modeLabel} mode using ${source.modelName}.")
                        source.assistantLabel?.takeIf { it.isNotBlank() }?.let {
                            append(" The active assistant was $it.")
                        }
                    }
                )
            )
            addAll(attemptSteps)
            if (selectedAttempt != null && source.attempts.size > 1) {
                add(
                    ReasoningStep(
                        stepNumber = size + 1,
                        title = "Final Selection",
                        description = "${selectedAttempt.label} was chosen as the final visible answer because it matched the question best and cleared the retry checks."
                    )
                )
            }
        }
    }
}
