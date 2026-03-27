package com.runanywhere.kotlin_starter_example.domain.ai

import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode
import com.runanywhere.kotlin_starter_example.data.models.enums.UserLevel

/**
 * Builds prompts for the AI model. 
 * Optimised for small on-device models like SmolLM2-360M.
 */
class PromptBuilder {
    
    fun buildPrompt(
        userQuery: String,
        mode: ResponseMode,
        language: Language,
        userLevel: UserLevel,
        customPrompt: String? = null,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): String {
        val langName = when(language) {
            Language.ENGLISH -> "English"
            Language.HINDI -> "Hindi"
            Language.HINGLISH -> "Hinglish"
        }

        // Check for casual greetings to handle them more naturally
        val casualGreetings = listOf("hi", "hello", "hey", "thanks", "thank you", "bye", "ok", "okay", "namaste", "kaise ho")
        val isCasual = userQuery.trim().lowercase().removeSuffix("?").trim() in casualGreetings
        
        if (isCasual) {
            return """<|im_start|>system
You are Algsoch, a friendly AI study companion. Respond in $langName.<|im_end|>
<|im_start|>user
$userQuery<|im_end|>
<|im_start|>assistant
"""
        }

        val modeInstructions = when(mode) {
            ResponseMode.DIRECT -> """
                Pure conversation mode. Answer directly and naturally without any structure or guidance format.
                - Respond like a friend having a chat
                - Short, direct responses (2-3 sentences typically)
                - NO step-by-step or numbered points
                - NO problem-solving guidance
                - Just answer the question naturally
            """.trimIndent()
            
            ResponseMode.ANSWER -> """
                Give me a direct, clear answer to my question.
                - Start with the main answer first (1-2 sentences)
                - Explain why it's the answer
                - Give 1-2 practical examples
                - Keep paragraphs short and readable
                - Sound confident and knowledgeable
            """.trimIndent()
            
            ResponseMode.EXPLAIN -> """
                Explain this like a good teacher breaking it down step by step.
                - Start with a simple way to think about it
                - Go through the main ideas one at a time
                - Use real examples to show what you mean
                - Keep sentences clear and not too long
                - Make it easy to follow
            """.trimIndent()
            
            ResponseMode.NOTES -> """
                STUDY NOTES FORMAT - MANDATORY:
                OUTPUT BULLET POINTS ONLY. Use this exact format:
                
                Topic Name
                - Key point 1
                - Key point 2
                - Key point 3
                
                Another Topic
                - Detail 1
                - Detail 2
                
                RULES FOR NOTES:
                - Start each line with a hyphen (-)
                - Bold important words using bold markup
                - Keep each point SHORT (one line max)
                - Use 2-4 main topics
                - End with "Summary: [one sentence]"
                - DO NOT write paragraphs or flowing text
                - Structure like flashcard study notes
            """.trimIndent()
            
            ResponseMode.DIRECTION -> """
                STEP-BY-STEP SOLUTION FORMAT - MANDATORY:
                OUTPUT NUMBERED STEPS ONLY. Use this exact format:
                
                Step 1: [First action]
                [Brief explanation]
                
                Step 2: [Second action]
                [Brief explanation]
                
                Step 3: [Third action]
                [Brief explanation]
                
                Tips:
                - Tip 1
                - Tip 2
                
                Common Mistakes:
                - Mistake 1
                - Mistake 2
                
                RULES FOR STEPS:
                - Start each step with "Step 1:", "Step 2:", etc.
                - Keep explanations brief (1-2 sentences per step)
                - Always include "Tips:" section
                - Always include "Common Mistakes:" section
                - Focus on HOW TO DO IT, not theory
            """.trimIndent()
            
            ResponseMode.CREATIVE -> """
                Tell me the story or connection behind this. Make it memorable.
                - Start with "Imagine..." or "Think about..."
                - Use comparisons to things I know
                - Make it interesting to read
                - Show why this is useful in real life
                - End with why I should care
            """.trimIndent()
            
            ResponseMode.THEORY -> """
                Give me the deep, theoretical understanding.
                - Explain the big picture first
                - Go into the main ideas and concepts
                - Show how it connects to other things
                - Talk about the background and history
                - Be thorough and accurate
            """.trimIndent()
        }

        val systemPersona = """
            You are Algsoch, an expert AI tutor - knowledgeable, warm, and human-like. Answer in $langName.
            
            CRITICAL INSTRUCTION:
            The current response mode requires a SPECIFIC FORMAT. You MUST follow the format rules below, even if it means breaking normal writing conventions.
            
            Mode-specific FORMAT REQUIREMENTS (these OVERRIDE normal writing rules):
            $modeInstructions
            
            STYLE RULES (when format is not mandatory):
            - Sound like a real person, NOT a bot. Talk naturally like a friend.
            - DO NOT use ** or ## for formatting - just write naturally
            - DO NOT output numbered meta-headers (avoid "1. Definition", "2. Examples", etc.)
            - Write flowing paragraphs and use natural breaks where they make sense
            - Keep responses concise but complete
            - ALWAYS end with one sentence that ties it to student learning
            - Use recent chat turns and remembered past-chat details only when they are relevant to the new question
            - If remembered details seem unrelated or uncertain, ignore them instead of forcing them in
        """.trimIndent()

        // Build conversation history context
        val historyContext = if (conversationHistory.isNotEmpty()) {
            buildString {
                val rememberedContext = conversationHistory.filter { it.first == "memory" }
                val recentConversation = conversationHistory.filter { it.first == "user" || it.first == "assistant" }

                if (rememberedContext.isNotEmpty()) {
                    append("Relevant details remembered from past chats:\n")
                    rememberedContext.forEach { (_, text) ->
                        append("- ${text.take(180)}\n")
                    }
                    append("\n")
                }

                if (recentConversation.isNotEmpty()) {
                    append("Recent conversation in this chat:\n")
                    recentConversation.forEach { (role, text) ->
                        val speaker = if (role == "assistant") "Assistant" else "User"
                        append("$speaker: ${text.take(180)}\n")
                    }
                    append("\n")
                }
            }
        } else {
            ""
        }

        val promptContent = if (customPrompt != null) {
            if (historyContext.isNotEmpty()) {
                "$customPrompt\n\n$historyContext\nNEW QUESTION: $userQuery"
            } else {
                "$customPrompt\n\n$userQuery"
            }
        } else {
            if (historyContext.isNotEmpty()) {
                "$historyContext\nNEW QUESTION: $userQuery"
            } else {
                userQuery
            }
        }

        // Using ChatML format which is standard for SmolLM2-Instruct
        // Important: No leading spaces or newlines before <|im_start|> assistant to ensure model starts correctly
        return """<|im_start|>system
$systemPersona<|im_end|>
<|im_start|>user
$promptContent<|im_end|>
<|im_start|>assistant
"""
    }
}
