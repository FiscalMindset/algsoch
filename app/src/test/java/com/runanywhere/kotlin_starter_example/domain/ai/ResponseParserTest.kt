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
    fun directMode_stripsDanglingInlineFollowUpItem() {
        val rawResponse = """
            Python can be used in real-life applications by: 1. Automating tasks: Python is a great language for automating repetitive tasks, such as data processing or file management. 2.
        """.trimIndent()

        val parsed = parser.parse(
            rawResponse = rawResponse,
            mode = ResponseMode.DIRECT,
            language = Language.ENGLISH
        )

        assertFalse(parsed.directAnswer.contains(" 1. "))
        assertFalse(parsed.directAnswer.trimEnd().endsWith("2."))
        assertTrue(parsed.directAnswer.contains("Automating tasks"))
    }

    @Test
    fun sanitizeForDisplay_removesPromptEchoAndModeLabel() {
        val cleaned = parser.sanitizeForDisplay(
            """
            Explanation: Web Development: Python's web development frameworks are commonly used for creating websites.
            User query: how can i use this in life
            Answer: Python is a versatile language that can be used in many aspects of life.
            """.trimIndent()
        )

        assertFalse(cleaned.contains("Explanation:"))
        assertFalse(cleaned.contains("User query:"))
        assertFalse(cleaned.contains("Answer:"))
        assertTrue(cleaned.startsWith("Web Development:"))
    }

    @Test
    fun sanitizeForDisplay_preserves_python_code_blocks_from_markdown_cleanup() {
        val cleaned = parser.sanitizeForDisplay(
            """
            |Here's a Python code snippet that prints prime numbers from 1 to n:
            |
            |`````python
            |def print_primes(n):
            |    for i in range(2, n + 1):
            |        is_prime = True
            |        for j in range(2, int(i ** 0.5) + 1):
            |            if i % j == 0:
            |                is_prime = False
            |                break
            |        if is_prime:
            |            print(i, end=" ")
            |
            |n = int(input("Enter a natural number: "))
            |print_primes(n)
            |`
            |``
            """.trimMargin()
        )

        assertTrue(cleaned.contains("```python"))
        assertTrue(cleaned.contains("    for i in range(2, n + 1):"))
        assertTrue(cleaned.contains("        is_prime = True"))
        assertTrue(cleaned.contains("i ** 0.5"))
        assertTrue(cleaned.trimEnd().endsWith("```"))
    }

    @Test
    fun sanitizeForDisplay_keeps_double_underscore_names_inside_code_blocks() {
        val cleaned = parser.sanitizeForDisplay(
            """
            |```python
            |if __name__ == "__main__":
            |    print(__file__)
            |```
            """.trimMargin()
        )

        assertTrue(cleaned.contains("__name__"))
        assertTrue(cleaned.contains("\"__main__\""))
        assertTrue(cleaned.contains("__file__"))
    }

    @Test
    fun sanitizeForDisplay_normalizes_inline_fenced_code_blocks() {
        val cleaned = parser.sanitizeForDisplay(
            """
            |``````python def print_even_odd(n): even = [] odd = [] print_even_odd(10) ``` ```
            """.trimMargin()
        )

        assertTrue(cleaned.trimStart().startsWith("```python\n"))
        assertTrue(cleaned.contains("def print_even_odd(n):"))
        assertTrue(cleaned.contains("print_even_odd(10)"))
        assertFalse(cleaned.contains("``` ```"))
        assertTrue(cleaned.trimEnd().endsWith("```"))
    }

    @Test
    fun sanitizeForDisplay_reflows_flattened_inline_python_function() {
        val cleaned = parser.sanitizeForDisplay(
            """
            |Here's a simple Python function that checks if a string has 6 letters or more: ```python def check_string_length(input_str): return len(input_str) >= 6 # Example usage: print(check_string_length("hello")) # True print(check_string_length("world")) # False ```
            """.trimMargin()
        )

        assertTrue(cleaned.contains("```python"))
        assertTrue(cleaned.contains("def check_string_length(input_str):\n    return len(input_str) >= 6"))
        assertTrue(cleaned.contains("# Example usage:\nprint(check_string_length(\"hello\"))"))
        assertTrue(cleaned.contains("\n# True\nprint(check_string_length(\"world\")) # False"))
        assertTrue(cleaned.contains("print(check_string_length(\"world\")) # False"))
    }

    @Test
    fun directMode_preserves_fenced_code_block_after_final_parse() {
        val rawResponse = """
            |Here's a simple Python function that checks if a string has 6 letters or more: ```python def check_string_length(input_str): return len(input_str) >= 6 # Example usage: print(check_string_length("hello")) # True print(check_string_length("world")) # False ```
        """.trimMargin()

        val parsed = parser.parse(
            rawResponse = rawResponse,
            mode = ResponseMode.DIRECT,
            language = Language.ENGLISH
        )

        assertTrue(parsed.directAnswer.contains("```python"))
        assertTrue(parsed.directAnswer.contains("def check_string_length(input_str):"))
        assertTrue(parsed.directAnswer.contains("return len(input_str) >= 6"))
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
