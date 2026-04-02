package com.runanywhere.kotlin_starter_example.data.models

import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode
import com.runanywhere.kotlin_starter_example.data.models.enums.UserLevel

// Simple data class (no Room - in-memory only)
data class UserPreferences(
    val id: Int = 1,
    var preferredLanguage: Language = Language.ENGLISH,
    var preferredMode: ResponseMode = ResponseMode.ANSWER,
    var preferredLevel: UserLevel = UserLevel.SMART,
    var totalQuestions: Int = 0,
    var languageUsageMap: String = "{}",
    var modeUsageMap: String = "{}",
    var totalLikes: Int = 0,
    var totalDislikes: Int = 0
)
