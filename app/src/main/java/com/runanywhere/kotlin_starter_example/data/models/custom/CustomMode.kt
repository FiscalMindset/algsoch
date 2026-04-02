package com.runanywhere.kotlin_starter_example.data.models.custom

import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode

/**
 * Represents a user-defined custom mode (CustomGPT) for specialized chatbots.
 */
data class CustomMode(
    val id: String, // Unique identifier
    val name: String, // User-friendly name
    val description: String, // What this mode does
    val basePrompt: String, // System prompt for the model
    val enabledTools: List<String> = emptyList() // Tool/function names enabled for this mode
)
