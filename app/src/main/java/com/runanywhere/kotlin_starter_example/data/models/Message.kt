package com.runanywhere.kotlin_starter_example.data.models

import com.runanywhere.kotlin_starter_example.data.models.enums.FeedbackType
import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode

// Simple data class (no Room - no persistence)
// To enable database: uncomment Room annotations and add Room dependency
data class Message(
    val id: Long = System.currentTimeMillis(),
    val conversationId: Long = 0,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val mode: ResponseMode? = null,
    val language: Language? = null,
    val reasoningSteps: String? = null,
    val feedbackType: FeedbackType? = null
)
