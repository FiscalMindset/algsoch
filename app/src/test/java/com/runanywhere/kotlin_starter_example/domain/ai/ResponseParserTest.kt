package com.runanywhere.kotlin_starter_example.domain.ai

import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseParserTest {

    private val parser = ResponseParser()

    @Test
    fun directMode_trimsReplyToShortResponse() {
        val rawResponse = """
            Python is a beginner-friendly programming language. It is used for web apps, automation, data science, and AI. Its syntax is readable, so new learners pick it up quickly. It also has a huge ecosystem of libraries.
        """.trimIndent()

        val parsed = parser.parse(
            rawResponse = rawResponse,
            mode = ResponseMode.DIRECT,
            language = Language.ENGLISH
        )

        assertFalse(parsed.directAnswer.contains("huge ecosystem"))
        assertTrue(parsed.quickExplanation.isBlank())
    }

    @Test
    fun directMode_removesBrokenNumberedListLeadIn() {
        val rawResponse = """
            Python is a popular programming language used in various applications, including CRM products. Here are some ways to use Python in a CRM product: 1.
        """.trimIndent()

        val parsed = parser.parse(
            rawResponse = rawResponse,
            mode = ResponseMode.DIRECT,
            language = Language.ENGLISH
        )

        assertEquals(
            "Python is a popular programming language used in various applications, including CRM products.",
            parsed.directAnswer
        )
    }

    @Test
    fun directMode_removesBrokenHeresHowSentence() {
        val rawResponse = """
            Python is a popular language used in many areas of CRM, including data analysis, reporting, and automation. Here's how you can use Python in a CRM product: 1.
        """.trimIndent()

        val parsed = parser.parse(
            rawResponse = rawResponse,
            mode = ResponseMode.DIRECT,
            language = Language.ENGLISH
        )

        assertEquals(
            "Python is a popular language used in many areas of CRM, including data analysis, reporting, and automation.",
            parsed.directAnswer
        )
    }

    @Test
    fun answerMode_keepsAnswerFirstAndMovesRestToExplanation() {
        val rawResponse = """
            A variable is a named place to store a value. You use it so the program can reuse and update data easily. For example, age = 20 stores a number. That makes code easier to read. It also avoids repeating the same value everywhere.
        """.trimIndent()

        val parsed = parser.parse(
            rawResponse = rawResponse,
            mode = ResponseMode.ANSWER,
            language = Language.ENGLISH
        )

        assertTrue(parsed.directAnswer.startsWith("A variable is a named place"))
        assertTrue(parsed.quickExplanation.contains("age = 20"))
    }

    @Test
    fun answerMode_formatsInlineNumberedListIntoKeyPoints() {
        val rawResponse = """
            Python is a high-level programming language used for many tasks.
            Python is often used in: 1. Web development with Django. 2. Data analysis with pandas. 3. Automation for repetitive work.
        """.trimIndent()

        val parsed = parser.parse(
            rawResponse = rawResponse,
            mode = ResponseMode.ANSWER,
            language = Language.ENGLISH
        )

        assertTrue(parsed.quickExplanation.contains("Key Points:"))
        assertTrue(parsed.quickExplanation.contains("1. Web development with Django"))
    }

    @Test
    fun notesMode_formatsFallbackNotesShape() {
        val rawResponse = """
            Photosynthesis lets plants make food from sunlight. Chlorophyll captures light energy. Water and carbon dioxide are used to form glucose. Oxygen is released as a by-product.
        """.trimIndent()

        val parsed = parser.parse(
            rawResponse = rawResponse,
            mode = ResponseMode.NOTES,
            language = Language.ENGLISH,
            userQuery = "Explain photosynthesis"
        )

        assertTrue(parsed.directAnswer.contains("\n- "))
        assertTrue(parsed.directAnswer.contains("Summary:"))
    }

    @Test
    fun explainMode_removesMarkdownArtifactsAndDanglingListTail() {
        val rawResponse = """
            Python is a high-level programming language that's easy to learn and use.

            1. **Easy to learn**: Python has simple syntax.
            2. **Extensive libraries**: It has tools for many tasks.
            7.
        """.trimIndent()

        val parsed = parser.parse(
            rawResponse = rawResponse,
            mode = ResponseMode.EXPLAIN,
            language = Language.ENGLISH
        )

        assertFalse(parsed.directAnswer.contains("**"))
        assertFalse(parsed.directAnswer.trimEnd().endsWith("7."))
        assertTrue(parsed.directAnswer.contains("Key Points:"))
        assertTrue(parsed.directAnswer.contains("1. Easy to learn:"))
    }

    @Test
    fun directionMode_buildsStructuredFallback() {
        val rawResponse = """
            First identify what the question is asking. Then choose the formula or method that fits. Substitute the values carefully. Finally, check whether the result makes sense.
        """.trimIndent()

        val parsed = parser.parse(
            rawResponse = rawResponse,
            mode = ResponseMode.DIRECTION,
            language = Language.ENGLISH
        )

        assertTrue(parsed.directAnswer.contains("Step 1:"))
        assertTrue(parsed.directAnswer.contains("Tips:"))
        assertTrue(parsed.directAnswer.contains("Common Mistakes:"))
    }
}
