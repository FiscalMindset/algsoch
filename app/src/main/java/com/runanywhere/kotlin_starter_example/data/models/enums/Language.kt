package com.runanywhere.kotlin_starter_example.data.models.enums

enum class Language {
    ENGLISH,
    HINDI,
    HINGLISH;
    
    fun displayName(): String = when(this) {
        ENGLISH -> "English"
        HINDI -> "हिंदी"
        HINGLISH -> "Hinglish"
    }
    
    fun code(): String = when(this) {
        ENGLISH -> "en"
        HINDI -> "hi"
        HINGLISH -> "hi-en"
    }
}
