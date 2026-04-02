package com.runanywhere.kotlin_starter_example.data.models

// Simple data class (no Room)
data class Conversation(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastMessageAt: Long = System.currentTimeMillis()
)
