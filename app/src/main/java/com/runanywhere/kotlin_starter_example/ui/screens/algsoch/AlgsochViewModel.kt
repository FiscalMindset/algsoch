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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    var feedbackType: FeedbackType? = null,
    val structuredResponse: com.runanywhere.kotlin_starter_example.domain.models.StructuredResponse? = null,
    val imageUri: Uri? = null,  // Support for image input in messages
    val assistantLabel: String? = null,
    val isPending: Boolean = false
)

data class TopicInsight(
    val name: String,
    val mentionCount: Int,
    val matchedKeywords: List<String> = emptyList()
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
    
    fun sendMessage(query: String, imageUri: Uri? = null, isVisionReady: Boolean = true) {
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

                val imagePath = if (imageUri != null) resolveImagePath(imageUri) else null
                if (imageUri != null && imagePath == null) {
                    messages = messages + ChatMessage(
                        text = "I could not access the selected image. Please pick the image again.",
                        isUser = false
                    )
                    return@launch
                }

                val finalQuery = visibleUserQuery
                val history = buildConversationHistory(finalQuery)
                val effectiveMode = selectedCustomMode?.let(::preferredResponseModeFor) ?: selectedMode
                val assistantMessageId = System.currentTimeMillis() + 1
                val assistantLabel = selectedCustomMode?.name

                appendPendingAssistantMessage(assistantMessageId, assistantLabel)
                
                val response = aiService.generateAnswer(
                    userQuery = finalQuery,
                    mode = effectiveMode,
                    language = selectedLanguage,
                    userLevel = selectedLevel,
                    customPrompt = buildCustomPrompt(selectedCustomMode),
                    enabledTools = if (imagePath != null) emptyList() else (selectedCustomMode?.enabledTools ?: emptyList()),
                    imagePath = imagePath,
                    conversationHistory = history,
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
        val topicInsights = extractTopics(userMessages.map { it.text })
        val topicsCovered = topicInsights.size
        val timeSpentMinutes = if (allMessages.size >= 2) {
            val duration = (allMessages.maxOfOrNull { it.timestamp } ?: 0L) - (allMessages.minOfOrNull { it.timestamp } ?: 0L)
            val minutes = (duration / 1000.0 / 60.0).toInt()
            if (minutes == 0 && allMessages.isNotEmpty()) 1 else minutes
        } else 0

        val activeDates = userMessages.map { toLocalDate(it.timestamp) }.toSet()
        val activeDays = activeDates.size
        val currentStudyStreak = calculateCurrentStreak(activeDates)
        val longestStudyStreak = calculateLongestStreak(activeDates)
        val today = LocalDate.now(systemZone)
        val questionsThisWeek = userMessages.count { !toLocalDate(it.timestamp).isBefore(today.minusDays(6)) }
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
        
        return mapOf(
            "totalSessions" to totalSessions,
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
            "topicsCovered" to topicsCovered,
            "topicsList" to topicInsights.map { it.name },
            "topicInsights" to topicInsights,
            "topTopic" to (topicInsights.firstOrNull()?.name ?: "General Learning"),
            "timeSpentMinutes" to timeSpentMinutes,
            "activeDays" to activeDays,
            "currentStudyStreak" to currentStudyStreak,
            "longestStudyStreak" to longestStudyStreak,
            "questionsThisWeek" to questionsThisWeek,
            "avgQuestionsPerActiveDay" to avgQuestionsPerActiveDay,
            "avgMessagesPerSession" to avgMessagesPerSession,
            "peakStudyWindow" to peakStudyWindow
        )
    }

    private suspend fun buildConversationHistory(currentQuery: String): List<Pair<String, String>> {
        val historicalMessages = ensureHistoricalMessagesCacheLoaded()
            .filter { it.text.isNotBlank() }
        val currentFingerprints = messages.map(::messageFingerprint).toSet()
        val previousMessages = historicalMessages.filterNot { messageFingerprint(it) in currentFingerprints }

        val recentConversation = messages
            .dropLast(1)
            .filter { it.text.isNotBlank() }
            .takeLast(8)
            .map { message ->
                (if (message.isUser) "user" else "assistant") to message.text.trim().take(240)
            }

        val learnerProfileMemory = buildLearnerProfileMemory(previousMessages)
        val rememberedContext = findRelevantPastMessages(currentQuery, previousMessages)

        return buildList {
            learnerProfileMemory?.let { add("memory" to it) }
            rememberedContext.forEach { add("memory" to it) }
            addAll(recentConversation)
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

    private fun preferredResponseModeFor(mode: CustomMode): ResponseMode = when (mode.id) {
        "study_coach" -> ResponseMode.THEORY
        else -> ResponseMode.EXPLAIN
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
            isPending = true
        )
    }

    private fun updatePendingAssistantMessage(messageId: Long, partialText: String) {
        val visibleText = partialText.ifBlank { "Thinking..." }
        messages = messages.map { message ->
            if (message.id == messageId) {
                message.copy(
                    text = visibleText,
                    isPending = true
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
                    isPending = false
                )
            } else {
                message
            }
        }
    }
}
