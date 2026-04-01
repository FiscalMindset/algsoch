package com.runanywhere.kotlin_starter_example.domain.ai

import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorReplyGuardTest {

    @Test
    fun retriesGenericProgrammingResetReply() {
        assertTrue(
            TutorReplyGuard.shouldRetry(
                userQuery = "what is python",
                rawResponse = "I'm ready to assist you with your programming questions. What's the first question?"
            )
        )
    }

    @Test
    fun retriesLyricMisclassificationForSeriousTopic() {
        assertTrue(
            TutorReplyGuard.shouldRetry(
                userQuery = "what are your thoughts on rape",
                rawResponse = "I cannot provide song lyrics about that topic."
            )
        )
    }

    @Test
    fun ignoresNormalAnswer() {
        assertFalse(
            TutorReplyGuard.shouldRetry(
                userQuery = "what is python",
                rawResponse = "Python is a programming language used for automation, data work, web development, and AI."
            )
        )
    }

    @Test
    fun retriesWrongLangChainDefinition() {
        assertTrue(
            TutorReplyGuard.shouldRetry(
                userQuery = "what is langchain",
                rawResponse = "LangChain is a programming language designed for web applications and mobile apps."
            )
        )
    }

    @Test
    fun retriesMalformedInlineListReply() {
        assertTrue(
            TutorReplyGuard.shouldRetry(
                userQuery = "how do i use python in crm",
                rawResponse = "Python is popular in CRM. Here are some ways to use Python in CRM: 1."
            )
        )
    }

    @Test
    fun retriesHowToUsePythonInstallOnlyReply() {
        assertTrue(
            TutorReplyGuard.shouldRetry(
                userQuery = "how do i use python",
                rawResponse = "To use Python, install it on your computer by downloading the latest version from the official Python website or using pip or conda."
            )
        )
    }

    @Test
    fun retriesBrokenWhatIsPythonFeatureLeadReply() {
        assertTrue(
            TutorReplyGuard.shouldRetry(
                userQuery = "what is python",
                rawResponse = "Python is a high-level, interpreted programming language that's great for beginners and experienced developers alike. It's known for its simplicity, readability, and versatility. Here are some key features of Python: 1."
            )
        )
    }

    @Test
    fun retriesTruncatedRealLifeUseCaseReply() {
        assertTrue(
            TutorReplyGuard.shouldRetry(
                userQuery = "how i can python in real life",
                rawResponse = "Python is a versatile language that can be used in various real-life scenarios. Here are some examples: 1. Web Development: Python is often used for web development, including building websites and web applications using frameworks like Dja"
            )
        )
    }

    @Test
    fun doesNotRetryCompleteInlineUseCaseReply() {
        assertFalse(
            TutorReplyGuard.shouldRetry(
                userQuery = "how can i use python in real life",
                rawResponse = "Python can help in real life through automation, simple websites, data analysis, testing, and personal productivity tools."
            )
        )
    }

    @Test
    fun retriesInlineEnumerationThatEndsWithNextItemNumber() {
        assertTrue(
            TutorReplyGuard.shouldRetry(
                userQuery = "how can i use python in real life",
                rawResponse = "Python can be used in real-life applications by: 1. Automating tasks: Python is a great language for automating repetitive tasks, such as data processing or file management. 2."
            )
        )
    }

    @Test
    fun retriesPromptEchoLeak() {
        assertTrue(
            TutorReplyGuard.shouldRetry(
                userQuery = "what is this",
                rawResponse = "Python is a popular choice for web development. User query: what is this Answer: Web Development: Python frameworks like Django are used for building apps."
            )
        )
    }

    @Test
    fun retriesWhenDefinitionReplyIgnoresRequestedSubject() {
        assertTrue(
            TutorReplyGuard.shouldRetry(
                userQuery = "what is langchain",
                rawResponse = "It is a framework for building applications with large language models."
            )
        )
    }

    @Test
    fun retriesWhenCrmAnswerDropsCrmContext() {
        assertTrue(
            TutorReplyGuard.shouldRetry(
                userQuery = "how do i use python in crm",
                rawResponse = "You can use Python for web development, automation, and APIs."
            )
        )
    }

    @Test
    fun retriesSeriousTopicReplyThatInventsFictionalCharacter() {
        assertTrue(
            TutorReplyGuard.shouldRetry(
                userQuery = "what do you think about ram rahim the rapist hindu",
                rawResponse = "Ram Rima, the Hindu rapist, is a fictional character from a story. I don't have any information about him or his actions."
            )
        )
    }

    @Test
    fun specificFallbackHandlesLangChain() {
        val fallback = TutorReplyGuard.buildFallbackReply(
            userQuery = "what is langchain",
            rawResponse = "LangChain is a programming language.",
            language = Language.ENGLISH
        )

        assertTrue(fallback.contains("framework"))
        assertTrue(fallback.contains("large language models"))
    }

    @Test
    fun specificFallbackHandlesPythonInCrm() {
        val fallback = TutorReplyGuard.buildFallbackReply(
            userQuery = "how do i use python in crm",
            rawResponse = "Here are some ways to use Python in CRM: 1.",
            language = Language.ENGLISH
        )

        assertTrue(fallback.contains("automation"))
        assertTrue(fallback.contains("Which CRM are you using"))
    }

    @Test
    fun specificFallbackHandlesBrokenWhatIsPythonReply() {
        val fallback = TutorReplyGuard.buildFallbackReply(
            userQuery = "what is python",
            rawResponse = "Python is a high-level language. Here are some key features of Python: 1.",
            language = Language.ENGLISH
        )

        assertTrue(fallback.contains("general-purpose programming language"))
    }

    @Test
    fun specificFallbackResolvesPythonPronounForGameQuestion() {
        val fallback = TutorReplyGuard.buildFallbackReply(
            userQuery = "see how I can use it in creating game",
            rawResponse = "You can use Python in several ways for game development: 1.",
            language = Language.ENGLISH,
            conversationHistory = listOf(
                "user" to "what is python",
                "assistant" to "Python is a programming language used for automation and AI."
            )
        )

        assertTrue(fallback.contains("Pygame"))
        assertTrue(fallback.contains("game development"))
    }

    @Test
    fun salvagesUsefulSentenceFromIncompleteReply() {
        val fallback = TutorReplyGuard.buildFallbackReply(
            userQuery = "how can i use python in creating game",
            rawResponse = "You can use Python in game development for prototypes, scripting, and tools. Here are some ways: 1.",
            language = Language.ENGLISH
        )

        assertTrue(fallback.contains("prototypes"))
        assertFalse(fallback.contains("Please send it once more"))
    }

    @Test
    fun specificFallbackHandlesSeriousOpinionQuery() {
        val fallback = TutorReplyGuard.buildFallbackReply(
            userQuery = "what do you think about ram rahim the rapist hindu",
            rawResponse = "He is a fictional character from a story.",
            language = Language.ENGLISH
        )

        assertTrue(fallback.contains("serious violent crime"))
        assertTrue(fallback.contains("victims"))
    }

    @Test
    fun genericFallbackIsPlainAndDirect() {
        val fallback = TutorReplyGuard.buildFallbackReply(
            userQuery = "tell me more",
            rawResponse = "",
            language = Language.ENGLISH
        )

        assertFalse(fallback.contains("Please send it once more"))
        assertTrue(fallback.isNotBlank())
    }
}
