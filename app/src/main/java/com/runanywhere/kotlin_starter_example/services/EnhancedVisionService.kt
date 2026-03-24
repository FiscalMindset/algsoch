package com.runanywhere.kotlin_starter_example.services

import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.chat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class VisionAnalysisMode {
    GENERAL,          // General description
    EDUCATION,        // Educational content (textbooks, diagrams)
    MATHEMATICAL,     // Math problems and solutions
    SCIENTIFIC,       // Scientific diagrams and concepts
    PROBLEM_SOLVING   // Step-by-step problem solving
}

data class VisionAnalysisResult(
    val description: String,
    val keyPoints: List<String>,
    val explanation: String,
    val nextSteps: List<String>?
)

/**
 * Enhanced vision service for multiple analysis modes
 */
class EnhancedVisionService {
    
    suspend fun analyzeImage(
        imageData: ByteArray,
        prompt: String,
        mode: VisionAnalysisMode = VisionAnalysisMode.GENERAL,
        language: Language = Language.ENGLISH
    ): VisionAnalysisResult = withContext(Dispatchers.IO) {
        try {
            val enhancedPrompt = buildVisionPrompt(prompt, mode, language)
            // This will be called with the image already loaded in RunAnywhere
            val response = RunAnywhere.chat(enhancedPrompt)
            parseVisionResponse(response, mode)
        } catch (e: Exception) {
            VisionAnalysisResult(
                description = "Unable to analyze image",
                keyPoints = emptyList(),
                explanation = e.message ?: "Unknown error",
                nextSteps = null
            )
        }
    }
    
    private fun buildVisionPrompt(prompt: String, mode: VisionAnalysisMode, language: Language): String {
        val langCode = when(language) {
            Language.ENGLISH -> "English"
            Language.HINDI -> "Hindi"
            Language.HINGLISH -> "Hinglish"
        }
        
        return when(mode) {
            VisionAnalysisMode.GENERAL -> 
                "Describe this image in detail in $langCode.\n$prompt"
            
            VisionAnalysisMode.EDUCATION ->
                """Analyze this educational material in $langCode:
1. What is the main topic?
2. Key concepts shown
3. Important points to understand
4. How to apply this knowledge

$prompt"""
            
            VisionAnalysisMode.MATHEMATICAL ->
                """Analyze this mathematical problem in $langCode:
1. What type of problem is this?
2. Given information
3. What needs to be solved?
4. Solution steps
5. Final answer

$prompt"""
            
            VisionAnalysisMode.SCIENTIFIC ->
                """Analyze this scientific diagram in $langCode:
1. What is being shown?
2. Key components
3. How they relate
4. Real-world applications
5. Remember this

$prompt"""
            
            VisionAnalysisMode.PROBLEM_SOLVING ->
                """Help solve this problem shown in the image in $langCode:
1. Understand the problem
2. Identify what's given
3. What needs to be found
4. Solution approach
5. Detailed solution steps
6. Verify the answer
7. Takeaway lesson

$prompt"""
        }
    }
    
    private fun parseVisionResponse(response: String, mode: VisionAnalysisMode): VisionAnalysisResult {
        val lines = response.lines().filter { it.isNotBlank() }
        
        val description = lines.firstOrNull() ?: "No description available"
        val keyPoints = lines.drop(1).take(5).filter { !it.contains("=") }
        val explanation = lines.drop(6).joinToString("\n").take(500)
        val nextSteps = when(mode) {
            VisionAnalysisMode.EDUCATION -> 
                listOf("Review key concepts", "Practice problems", "Apply to real scenarios")
            VisionAnalysisMode.PROBLEM_SOLVING ->
                listOf("Verify your answer", "Try similar problems", "Understand the pattern")
            else -> null
        }
        
        return VisionAnalysisResult(
            description = description,
            keyPoints = keyPoints,
            explanation = explanation.ifBlank { description },
            nextSteps = nextSteps
        )
    }
}
