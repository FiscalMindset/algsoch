package com.runanywhere.kotlin_starter_example.domain.models

data class GenerationTraceEntry(
    val label: String,
    val text: String,
    val reason: String? = null,
    val wasStreamed: Boolean = false,
    val wasSelected: Boolean = false
)
