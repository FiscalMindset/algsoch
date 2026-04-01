package com.runanywhere.kotlin_starter_example.domain.ai

import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextResponseSelectorTest {

    @Test
    fun explainMode_prefersCompleteRetryOverShortFirstAttempt() {
        val firstAttempt = "Python is a programming language."
        val retryAttempt = """
            Python is a programming language designed to be readable and practical for many kinds of work.
            It matters because the same language is used in automation, websites, data analysis, and AI.
            Beginners like it because the syntax is clean, so they can focus on concepts instead of punctuation.
            In practice, that means you can start with small scripts and grow into larger real-world projects.
        """.trimIndent()

        val chosen = TextResponseSelector.chooseBetterResponse(
            mode = ResponseMode.EXPLAIN,
            userQuery = "What is Python?",
            firstAttempt = firstAttempt,
            retryAttempt = retryAttempt
        )

        assertEquals(retryAttempt, chosen)
    }

    @Test
    fun explainMode_keepsFirstAttemptWhenRetryLooksTruncated() {
        val firstAttempt = """
            Python is a popular programming language that focuses on readability and developer speed.
            People use it for automation, web development, data work, and machine learning.
            It is considered beginner-friendly because the syntax is clear and there are many learning resources.
            That combination makes it useful both for first projects and for professional software.
        """.trimIndent()
        val retryAttempt = """
            Python is popular because it is easy to read and used in many fields.
            It helps beginners start quickly and professionals build tools for
        """.trimIndent()

        val chosen = TextResponseSelector.chooseBetterResponse(
            mode = ResponseMode.EXPLAIN,
            userQuery = "Why Python?",
            firstAttempt = firstAttempt,
            retryAttempt = retryAttempt
        )

        assertEquals(firstAttempt, chosen)
    }

    @Test
    fun notesMode_rewardsExpectedStructure() {
        val notes = """
            Python Basics
            - Python is a general-purpose programming language.
            - It is known for readable syntax.
            - It is used in automation, web, data, and AI.
            - Beginners often start with Python because setup is simple.
            Summary: Python is versatile and easy to start with.
        """.trimIndent()

        val prose = "Python is flexible and used in many fields, so it is a common first language for beginners."

        val notesScore = TextResponseSelector.score(
            mode = ResponseMode.NOTES,
            userQuery = "Give me notes on Python",
            rawResponse = notes
        )
        val proseScore = TextResponseSelector.score(
            mode = ResponseMode.NOTES,
            userQuery = "Give me notes on Python",
            rawResponse = prose
        )

        assertTrue(notesScore > proseScore)
    }

    @Test
    fun directMode_penalizesDanglingNumberedList() {
        val brokenDirect = "Python can help in a CRM product in a few ways: 1."
        val cleanDirect = "Python can power CRM automation, API integrations, and reporting. Which CRM are you using?"

        val brokenScore = TextResponseSelector.score(
            mode = ResponseMode.DIRECT,
            userQuery = "how to use python in CRM product",
            rawResponse = brokenDirect
        )
        val cleanScore = TextResponseSelector.score(
            mode = ResponseMode.DIRECT,
            userQuery = "how to use python in CRM product",
            rawResponse = cleanDirect
        )

        assertTrue(cleanScore > brokenScore)
    }

    @Test
    fun directMode_penalizesBrokenHeresHowLeadIn() {
        val brokenDirect = "Python is used in CRM for automation and reporting. Here's how you can use Python in a CRM product: 1."
        val cleanDirect = "Python is used in CRM for automation, integrations, and reporting. Which CRM are you using?"

        val brokenScore = TextResponseSelector.score(
            mode = ResponseMode.DIRECT,
            userQuery = "how to use python in CRM product",
            rawResponse = brokenDirect
        )
        val cleanScore = TextResponseSelector.score(
            mode = ResponseMode.DIRECT,
            userQuery = "how to use python in CRM product",
            rawResponse = cleanDirect
        )

        assertTrue(cleanScore > brokenScore)
    }

    @Test
    fun directMode_penalizesGenericAssistantResetReply() {
        val genericReset = "I'm ready to assist you with your programming questions. What's the first question?"
        val cleanDirect = "Python is a programming language used for automation, web apps, data work, and AI."

        val genericScore = TextResponseSelector.score(
            mode = ResponseMode.DIRECT,
            userQuery = "what is python",
            rawResponse = genericReset
        )
        val cleanScore = TextResponseSelector.score(
            mode = ResponseMode.DIRECT,
            userQuery = "what is python",
            rawResponse = cleanDirect
        )

        assertTrue(cleanScore > genericScore)
    }

    @Test
    fun directMode_keepsCompleteFirstAttemptWhenRetryIsBroken() {
        val firstAttempt = "Python is a general-purpose programming language used for automation, web development, data work, and AI."
        val retryAttempt = "Python is a high-level language used for many things. Here are some key features of Python: 1."

        val chosen = TextResponseSelector.chooseBetterResponse(
            mode = ResponseMode.DIRECT,
            userQuery = "what is python",
            firstAttempt = firstAttempt,
            retryAttempt = retryAttempt
        )

        assertEquals(firstAttempt, chosen)
    }

    @Test
    fun directMode_keepsGoodFirstAttemptWhenRetryEndsMidWord() {
        val firstAttempt = "Python can be used in real life for automation, websites, data analysis, testing, and small tools that save time."
        val retryAttempt = "Python is a versatile language that can be used in various real-life scenarios. Here are some examples: 1. Web Development: Python is often used for web development, including building websites and web applications using frameworks like Dja"

        val chosen = TextResponseSelector.chooseBetterResponse(
            mode = ResponseMode.DIRECT,
            userQuery = "how can i use python in real life",
            firstAttempt = firstAttempt,
            retryAttempt = retryAttempt
        )

        assertEquals(firstAttempt, chosen)
    }

    @Test
    fun directMode_doesNotPenalizeCompleteUseCaseSentence() {
        val complete = "Python can be used in real life for automation, websites, reporting, testing, and personal tools."
        val broken = "Python can be used in real life in many ways. Here are some examples: 1. Web Development: Python is used for building websites with frameworks like Dja"

        val completeScore = TextResponseSelector.score(
            mode = ResponseMode.DIRECT,
            userQuery = "how can i use python in real life",
            rawResponse = complete
        )
        val brokenScore = TextResponseSelector.score(
            mode = ResponseMode.DIRECT,
            userQuery = "how can i use python in real life",
            rawResponse = broken
        )

        assertTrue(completeScore > brokenScore)
    }

    @Test
    fun directMode_keepsGoodFirstAttemptWhenRetryEndsWithDanglingNextItem() {
        val firstAttempt = "Python can be used in real life for automation, small tools, data work, and websites."
        val retryAttempt = "Python can be used in real-life applications by: 1. Automating tasks: Python is a great language for automating repetitive tasks, such as data processing or file management. 2."

        val chosen = TextResponseSelector.chooseBetterResponse(
            mode = ResponseMode.DIRECT,
            userQuery = "how can i use python in real life",
            firstAttempt = firstAttempt,
            retryAttempt = retryAttempt
        )

        assertEquals(firstAttempt, chosen)
    }
}
