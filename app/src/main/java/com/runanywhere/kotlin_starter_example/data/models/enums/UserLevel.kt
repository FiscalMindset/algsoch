package com.runanywhere.kotlin_starter_example.data.models.enums

enum class UserLevel {
    BASIC,   // Simple answers, minimal explanation
    SMART;   // Full adaptive system with comprehensive explanations
    
    fun displayName(): String = when(this) {
        BASIC -> "Basic"
        SMART -> "Smart"
    }
}
