package com.runanywhere.kotlin_starter_example.data.local

import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode
import com.runanywhere.kotlin_starter_example.domain.models.StructuredResponse
import com.runanywhere.kotlin_starter_example.ui.screens.algsoch.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryJsonCodecTest {

    @Test
    fun roundTripsUserMessagesAndAssistantMetadata() {
        val originalMessages = listOf(
            ChatMessage(
                id = 101L,
                text = "what is langchain",
                isUser = true,
                timestamp = 101L
            ),
            ChatMessage(
                id = 102L,
                text = "LangChain is a framework for building applications with large language models.",
                isUser = false,
                timestamp = 102L,
                structuredResponse = StructuredResponse(
                    directAnswer = "LangChain is a framework for building applications with large language models.",
                    quickExplanation = "",
                    deepExplanation = null,
                    mode = ResponseMode.DIRECT,
                    language = Language.ENGLISH,
                    modelName = "SmolLM2 360M",
                    tokensUsed = 965,
                    promptTokens = 712,
                    responseTokens = 253,
                    responseTimeMs = 15_300L,
                    timeToFirstTokenMs = 740L
                )
            )
        )

        val restoredMessages = ChatHistoryJsonCodec.jsonToMessages(
            ChatHistoryJsonCodec.messagesToJson(originalMessages)
        )

        assertEquals(2, restoredMessages.size)
        assertTrue(restoredMessages.first().isUser)
        assertEquals("what is langchain", restoredMessages.first().text)

        val restoredAssistant = restoredMessages.last()
        assertFalse(restoredAssistant.isUser)
        assertNotNull(restoredAssistant.structuredResponse)
        assertEquals("SmolLM2 360M", restoredAssistant.structuredResponse?.modelName)
        assertEquals(965, restoredAssistant.structuredResponse?.tokensUsed)
        assertEquals(15_300L, restoredAssistant.structuredResponse?.responseTimeMs)
        assertEquals(740L, restoredAssistant.structuredResponse?.timeToFirstTokenMs)
    }

    @Test
    fun preservesEscapedTextAndAssistantLabel() {
        val originalMessages = listOf(
            ChatMessage(
                id = 201L,
                text = "Explain \"LangChain\"\nwith an example",
                isUser = true,
                timestamp = 201L
            ),
            ChatMessage(
                id = 202L,
                text = "Sure, here is a simple example.",
                isUser = false,
                timestamp = 202L,
                assistantLabel = "Smart"
            )
        )

        val restoredMessages = ChatHistoryJsonCodec.jsonToMessages(
            ChatHistoryJsonCodec.messagesToJson(originalMessages)
        )

        assertEquals("Explain \"LangChain\"\nwith an example", restoredMessages.first().text)
        assertEquals("Smart", restoredMessages.last().assistantLabel)
    }
}
