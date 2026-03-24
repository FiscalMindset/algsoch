package com.runanywhere.kotlin_starter_example.services

import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.chat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class QuizQuestion(
    val id: Int,
    val question: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String
)

/**
 * Service to generate quiz questions from study content
 * Helps students test their understanding
 */
class QuizGeneratorService {
    
    suspend fun generateQuiz(
        content: String,
        numberOfQuestions: Int = 5,
        language: Language = Language.ENGLISH
    ): List<QuizQuestion> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildQuizPrompt(content, numberOfQuestions, language)
            val rawResponse = RunAnywhere.chat(prompt)
            parseQuizResponse(rawResponse)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun buildQuizPrompt(content: String, count: Int, language: Language): String {
        val langInstruction = when(language) {
            Language.ENGLISH -> "in English"
            Language.HINDI -> "in Hindi (Devanagari)"
            Language.HINGLISH -> "in Hinglish"
        }
        
        return """
Generate exactly $count multiple choice quiz questions $langInstruction based on this content:

$content

For each question, provide:
QUESTION [number]: [question text]
A) [option A]
B) [option B]
C) [option C]
D) [option D]
ANSWER: [correct option]
EXPLANATION: [brief explanation why this is correct]

---

Generate questions that test comprehension and critical thinking.
        """.trimIndent()
    }
    
    private fun parseQuizResponse(response: String): List<QuizQuestion> {
        val questions = mutableListOf<QuizQuestion>()
        val questionPattern = Regex("""QUESTION\s+(\d+):\s*(.+?)(?=A\))""", RegexOption.DOT_MATCHES_ALL)
        
        questionPattern.findAll(response).forEach { match ->
            try {
                val questionNum = match.groupValues[1].toInt()
                val questionText = match.groupValues[2].trim()
                
                // Find options
                val optionsStart = match.range.last
                val nextQuestionStart = questionPattern.find(response, optionsStart + 1)?.range?.first ?: response.length
                val optionsSection = response.substring(optionsStart, nextQuestionStart)
                
                val optionPattern = Regex("""([A-D]\))\s*(.+?)(?=[A-D\)]|ANSWER:|$)""")
                val options = mutableListOf<String>()
                optionPattern.findAll(optionsSection).forEach { optMatch ->
                    options.add(optMatch.groupValues[2].trim())
                }
                
                // Find answer
                val answerPattern = Regex("""ANSWER:\s*([A-D])""")
                val answerMatch = answerPattern.find(optionsSection)
                val correctOption = answerMatch?.groupValues?.get(1) ?: "A"
                
                // Find explanation
                val explanationPattern = Regex("""EXPLANATION:\s*(.+?)(?=QUESTION|\Z)""", RegexOption.DOT_MATCHES_ALL)
                val explanationMatch = explanationPattern.find(optionsSection)
                val explanation = explanationMatch?.groupValues?.get(1)?.trim() ?: ""
                
                if (options.size >= 4) {
                    questions.add(QuizQuestion(
                        id = questionNum,
                        question = questionText,
                        options = options.take(4),
                        correctAnswer = correctOption,
                        explanation = explanation
                    ))
                }
            } catch (e: Exception) {
                // Skip malformed questions
            }
        }
        
        return questions
    }
}
