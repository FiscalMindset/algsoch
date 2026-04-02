package com.runanywhere.kotlin_starter_example.data.models.enums

enum class ResponseMode {
    DIRECT,      // Pure conversation, no formatting
    ANSWER,      // Direct output
    EXPLAIN,     // Step-by-step
    CODE,        // Code generation with proper formatting and error fixing
    DIRECTION,   // How to approach
    CREATIVE,    // Analogies/examples
    THEORY;      // Deep conceptual
    
    fun displayName(): String = when(this) {
        DIRECT -> "Direct"
        ANSWER -> "Answer"
        EXPLAIN -> "Explain"
        CODE -> "Code"
        DIRECTION -> "Direction"
        CREATIVE -> "Creative"
        THEORY -> "Theory"
    }
    
    fun description(): String = when(this) {
        DIRECT -> "Just talk to me naturally"
        ANSWER -> "Get direct, concise answers"
        EXPLAIN -> "Step-by-step explanations"
        CODE -> "Generate, fix, and format code"
        DIRECTION -> "Problem-solving approach"
        CREATIVE -> "Analogies and examples"
        THEORY -> "Deep conceptual understanding"
    }
}
