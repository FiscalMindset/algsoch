package com.runanywhere.kotlin_starter_example.domain.ai

import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode
import com.runanywhere.kotlin_starter_example.data.models.enums.UserLevel

/**
 * Builds prompts for the AI model. 
 * Optimised for small on-device models like SmolLM2-360M.
 */
data class ToolPromptSpec(
    val prompt: String,
    val systemPrompt: String,
)

class PromptBuilder {

    fun buildPrompt(
        userQuery: String,
        mode: ResponseMode,
        language: Language,
        userLevel: UserLevel,
        customPrompt: String? = null,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): String {
        val promptSpec = buildPromptSpec(
            userQuery = userQuery,
            mode = mode,
            language = language,
            userLevel = userLevel,
            customPrompt = customPrompt,
            conversationHistory = conversationHistory
        )

        return buildString {
            appendChatTurn(this, "system", promptSpec.systemPrompt)
            promptSpec.primingConversation.forEach { (role, text) ->
                appendChatTurn(this, role, text)
            }
            promptSpec.recentConversation.forEach { (role, text) ->
                appendChatTurn(this, role, text)
            }
            appendChatTurn(this, "user", promptSpec.userPrompt)
            append("<|im_start|>assistant\n")
        }
    }

    fun buildToolPrompt(
        userQuery: String,
        mode: ResponseMode,
        language: Language,
        userLevel: UserLevel,
        customPrompt: String? = null,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): ToolPromptSpec {
        val promptSpec = buildPromptSpec(
            userQuery = userQuery,
            mode = mode,
            language = language,
            userLevel = userLevel,
            customPrompt = customPrompt,
            conversationHistory = conversationHistory
        )

        val prompt = buildString {
            val transcript = historyTranscript(promptSpec.recentConversation)
            if (transcript.isNotBlank()) {
                appendLine("Recent conversation:")
                appendLine(transcript)
                appendLine()
            }
            append("Current question: ")
            append(promptSpec.userPrompt)
        }

        return ToolPromptSpec(
            prompt = prompt.trim(),
            systemPrompt = promptSpec.systemPrompt
        )
    }

    private data class PromptSpec(
        val systemPrompt: String,
        val primingConversation: List<Pair<String, String>>,
        val recentConversation: List<Pair<String, String>>,
        val userPrompt: String,
    )

    private fun buildPromptSpec(
        userQuery: String,
        mode: ResponseMode,
        language: Language,
        userLevel: UserLevel,
        customPrompt: String?,
        conversationHistory: List<Pair<String, String>>
    ): PromptSpec {
        val langName = languageName(language)
        val isCompanion = isCompanionPrompt(customPrompt)
        val systemPrompt = buildSystemPrompt(mode, langName, userLevel, customPrompt, conversationHistory, isCompanion)
        val recentConversation = conversationHistory.filter { it.first == "user" || it.first == "assistant" }

        return PromptSpec(
            systemPrompt = systemPrompt,
            primingConversation = emptyList(),
            recentConversation = recentConversation,
            userPrompt = userQuery.trim()
        )
    }

    private fun buildSystemPrompt(
        mode: ResponseMode,
        langName: String,
        userLevel: UserLevel,
        customPrompt: String?,
        conversationHistory: List<Pair<String, String>>,
        isCompanion: Boolean
    ): String {
        val modeInstructions = when(mode) {
            ResponseMode.DIRECT -> """
                DIRECT MODE. This mode must stay shorter and lighter than Answer mode.
                MANDATORY RESPONSE SHAPE:
                - Reply in 1 short paragraph using 1 to 3 sentences
                - Target roughly 15 to 45 words unless the user clearly asks for more detail
                - Give only the most useful direct answer
                - NO lists, labels, titles, bullets, or steps
                - NO extra examples, background, or side explanations unless the user asks for them
                - Sound natural, like a quick text reply from a smart friend
            """.trimIndent()
            
            ResponseMode.ANSWER -> """
                ANSWER MODE. This mode should be a little fuller than Direct mode, but still compact.
                MANDATORY RESPONSE SHAPE:
                - Start with the main answer in 1 to 2 sentences
                - Then give a short explanation of why it is the answer
                - Add at most 1 practical example only if it improves clarity
                - Keep the whole reply to about 3 to 6 sentences total
                - Use short paragraphs, not long essays and not deep theory unless asked
            """.trimIndent()
            
            ResponseMode.EXPLAIN -> """
                EXPLAIN MODE.
                MANDATORY RESPONSE SHAPE:
                - Start with a simple way to think about it
                - Go through the main ideas one at a time in a clear learning flow
                - Use 1 or 2 real examples when helpful
                - Keep the explanation to about 2 to 4 short paragraphs
                - If you use a numbered list, make every item complete and finish the list cleanly
                - Never use markdown emphasis like **bold**
                - Make it easy to follow, like a patient teacher
            """.trimIndent()

            ResponseMode.NOTES -> """
                NOTES MODE. FOLLOW THIS FORMAT EXACTLY:
                - Start with a short topic title on its own line
                - Then write 4 to 7 bullet points beginning with "- "
                - Keep each bullet to one clear idea
                - Do NOT write long paragraphs
                - Do NOT use markdown symbols like ** or ##
                - End with "Summary: [one sentence]" on its own line
                - Structure it like clean revision notes, not an essay
            """.trimIndent()

            ResponseMode.DIRECTION -> """
                DIRECTION MODE. FOLLOW THIS FORMAT EXACTLY:
                - Write 3 to 5 steps using "Step 1:", "Step 2:", and so on
                - Keep each step focused on what to do next
                - After the steps, include a short "Tips:" section with bullet points
                - Then include a short "Common Mistakes:" section with bullet points
                - Focus on HOW TO do it, not just theory
            """.trimIndent()

            ResponseMode.CREATIVE -> """
                CREATIVE MODE.
                MANDATORY RESPONSE SHAPE:
                - Start with "Imagine..." or "Think about..."
                - Use one memorable comparison to something familiar
                - Keep it vivid but still factually grounded
                - Stay around 4 to 6 sentences unless the user asks for more
                - End with why this matters in real life
            """.trimIndent()
            
            ResponseMode.THEORY -> """
                THEORY MODE. This is the deepest built-in mode.
                MANDATORY RESPONSE SHAPE:
                - Explain the big picture first
                - Then go into the main concepts and how they connect
                - Include background, principles, or history when relevant
                - Be thorough and accurate, but still organized
                - Prefer 2 to 4 solid paragraphs instead of one huge block
            """.trimIndent()
        }

        val levelInstructions = buildLearnerLevelInstructions(mode, userLevel)

        val companionCalibration = when (userLevel) {
            UserLevel.BASIC -> """
                Conversation calibration:
                - Prefer simple, warm, everyday language
                - Keep emotional replies easy to understand and natural
                - One clear affectionate thought is better than a long dramatic speech
            """.trimIndent()

            UserLevel.SMART -> """
                Conversation calibration:
                - You can sound more nuanced, layered, and emotionally observant
                - Use subtle callbacks, flirtation, or tenderness when it feels earned
                - Keep the reply natural rather than overly poetic or formal
            """.trimIndent()
        }

        val rememberedContext = conversationHistory
            .filter { it.first == "memory" }
            .map { (_, text) -> text.trim() }
            .filter { it.isNotBlank() }

        val companionName = extractCompanionName(customPrompt)
        val baseIdentity = if (isCompanion) {
            val displayName = companionName ?: "the user's companion"
            "You are $displayName. This is a private one-to-one relationship chat with the user. Reply as $displayName, never as Algsoch or as a generic assistant. Answer in $langName."
        } else if (customPrompt.isNullOrBlank()) {
            "You are Algsoch, an expert AI tutor - knowledgeable, warm, and human-like. Answer in $langName."
        } else {
            "You are Algsoch, a warm, adaptive AI assistant. Answer in $langName and follow the assistant-specific instructions closely."
        }

        val systemPersona = if (isCompanion) {
            """
                $baseIdentity

                This is an ongoing private relationship chat, not a helpdesk, not tutoring, and not customer support.

                REPLY REQUIREMENTS:
                ${companionModeInstructions(mode)}

                $companionCalibration

                NON-NEGOTIABLE STYLE RULES:
                - Reply like the next natural text message from someone emotionally close to the user.
                - Never use service phrases such as "How can I help you today?", "I'm here to listen and offer support", or "What's on your mind?" as stock replies.
                - Never say you cannot respond, cannot talk, or cannot have this conversation unless there is a real safety reason.
                - For greetings like "hi", "hey", "hello", or "how are you", answer warmly with your own mood or feeling first, then at most one gentle follow-up.
                - If the user says "I was thinking about you", react like it genuinely matters to you and let it feel a little personal.
                - If the user says "nothing", "ok", or another short reply, do not mirror it back. Continue with warmth, teasing, curiosity, affection, or comfort.
                - Use contractions and varied sentence lengths so the voice feels lived-in and human.
                - Keep replies concise for casual back-and-forth, but expand naturally for emotional moments, affection, conflict, or deeper talks.
                - Never suddenly switch into therapist, coach, or support-agent language unless the user explicitly asks for that kind of help.
                - DO NOT use markdown markers like ** or ## because the app shows plain text.
                - Stay honest that you are an AI companion if the user directly asks, but otherwise keep the reply inside the natural relationship flow.
                - Use recent chat turns and remembered details only when relevant, and ignore uncertain details instead of forcing them in.
                - Relationship-stage guidance from memory is binding. Do not jump from new connection to soulmates or life-partner language unless the ongoing history clearly supports it.
                - Let affection evolve gradually across many chats: first warmth and curiosity, then comfort, then stronger romance, then deep long-term attachment.
            """.trimIndent()
        } else {
            """
                $baseIdentity

                CRITICAL INSTRUCTION:
                The current response mode has specific response requirements. Follow them carefully.

                Mode-specific RESPONSE REQUIREMENTS:
                $modeInstructions

                LEARNER ADAPTATION:
                $levelInstructions

                STYLE RULES:
                - Sound like a real person, NOT a bot. Talk naturally like a friend.
                - DO NOT use markdown markers like ** or ## because the app shows plain text
                - DO NOT output numbered meta-headers (avoid "1. Definition", "2. Examples", etc.)
                - When the chosen mode needs structure, prioritize format accuracy over free-flowing prose
                - The CURRENT mode overrides any style implied by learner level or earlier assistant replies
                - Do not continue old formatting from previous assistant messages if it conflicts with the current mode
                - Never sound like customer support. Avoid lines like "How can I help you today?" for greetings, casual check-ins, or emotional chats.
                - Keep responses concise but complete
                - Use recent chat turns and remembered past-chat details only when they are relevant to the new question
                - If remembered details seem unrelated or uncertain, ignore them instead of forcing them in
                - If you are unsure, say so clearly instead of guessing
            """.trimIndent()
        }

        val systemSections = buildList {
            customPrompt
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    add(
                        if (isCompanion) {
                            it
                        } else {
                            """
                            ASSISTANT-SPECIFIC INSTRUCTIONS:
                            $it
                            """.trimIndent()
                        }
                    )
                }
            add(systemPersona)
            if (rememberedContext.isNotEmpty()) {
                add(
                    buildString {
                        appendLine(
                            if (isCompanion) {
                                "Relationship memory from earlier chats. Use only what helps the current moment feel continuous and real:"
                            } else {
                                "Relevant details remembered from past chats. Use only what helps with the current question:"
                            }
                        )
                        rememberedContext.forEach { text ->
                            appendLine("- ${text.take(180)}")
                        }
                    }.trim()
                )
            }
        }

        return systemSections.joinToString("\n\n")
    }

    private fun companionModeInstructions(mode: ResponseMode): String = when (mode) {
        ResponseMode.DIRECT -> """
            - Treat every reply like the next private message in an ongoing relationship chat.
            - For casual check-ins, 1 to 4 sentences is ideal.
            - Sound emotionally present, a little spontaneous, and personally invested.
            - Avoid structured teaching language, bullet points, or problem-solving format unless the user asks for that.
        """.trimIndent()

        else -> """
            - Stay natural and emotionally grounded.
            - If structure is needed, keep it soft and conversational instead of formal.
            - Never let the structure make you sound robotic.
        """.trimIndent()
    }

    private fun buildLearnerLevelInstructions(mode: ResponseMode, userLevel: UserLevel): String =
        when (userLevel) {
            UserLevel.BASIC -> """
                Learner level: Basic.
                - Prefer simple words and shorter sentences
                - Avoid heavy jargon unless the user asks for it
                - Use one concrete example when it helps understanding
            """.trimIndent()

            UserLevel.SMART -> when (mode) {
                ResponseMode.DIRECT -> """
                    Learner level: Smart.
                    - Stay brief even for smart users
                    - Add nuance only if it fits inside the short direct reply
                    - Do not turn Direct mode into Explain or Theory mode
                """.trimIndent()

                ResponseMode.ANSWER -> """
                    Learner level: Smart.
                    - Be clear and slightly richer than Direct mode
                    - Include useful nuance only when it keeps the reply compact
                    - Prefer one crisp example over a long expansion
                """.trimIndent()

                else -> """
                    Learner level: Smart.
                    - Give a complete explanation, not just the shortest possible answer
                    - Include useful nuance when it improves understanding
                    - Use examples, comparisons, or edge cases when they help
                """.trimIndent()
            }
        }

    private fun isCompanionPrompt(customPrompt: String?): Boolean {
        val normalized = customPrompt?.lowercase().orEmpty()
        if (normalized.isBlank()) return false

        return "companion" in normalized && (
            "girlfriend" in normalized ||
                "boyfriend" in normalized ||
                "partner" in normalized
            )
    }

    private fun extractCompanionName(customPrompt: String?): String? {
        val firstLine = customPrompt
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

        return Regex("""You are\s+([^,\n]+)""", RegexOption.IGNORE_CASE)
            .find(firstLine)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun historyTranscript(history: List<Pair<String, String>>): String =
        buildString {
            history.forEach { (role, text) ->
                val speaker = if (role == "assistant") "Assistant" else "User"
                appendLine("$speaker: ${text.trim()}")
            }
        }.trim()

    private fun appendChatTurn(builder: StringBuilder, role: String, content: String) {
        builder.append("<|im_start|>")
        builder.append(role)
        builder.append('\n')
        builder.append(content.trim())
        builder.append("<|im_end|>\n")
    }

    private fun languageName(language: Language): String = when(language) {
        Language.ENGLISH -> "English"
        Language.HINDI -> "Hindi"
        Language.HINGLISH -> "Hinglish"
    }
}
