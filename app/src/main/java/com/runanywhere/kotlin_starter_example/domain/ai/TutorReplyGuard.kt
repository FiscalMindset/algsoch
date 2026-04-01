package com.runanywhere.kotlin_starter_example.domain.ai

import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import java.util.Locale

internal object TutorReplyGuard {

    fun shouldRetry(userQuery: String, rawResponse: String): Boolean {
        val normalizedQuery = normalize(userQuery)
        val normalizedResponse = normalize(rawResponse)

        if (normalizedResponse.isBlank()) return true

        return looksLikeGenericReset(normalizedQuery, normalizedResponse) ||
            looksLikePromptEchoLeak(rawResponse) ||
            looksLikeSeriousTopicMisclassification(normalizedQuery, normalizedResponse) ||
            looksLikeSeriousTopicFictionalization(normalizedQuery, normalizedResponse) ||
            looksLikeMalformedIncompleteReply(rawResponse, normalizedResponse) ||
            looksLikeWrongTechDefinition(normalizedQuery, normalizedResponse) ||
            looksLikeQuestionMismatch(normalizedQuery, normalizedResponse) ||
            looksLikeMissingRequestedSubject(normalizedQuery, normalizedResponse)
    }

    fun buildFallbackReply(
        userQuery: String,
        rawResponse: String,
        language: Language,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): String {
        val normalizedQuery = resolveQueryContext(
            normalizedQuery = normalize(userQuery),
            conversationHistory = conversationHistory
        )
        val normalizedResponse = normalize(rawResponse)

        specificFallback(normalizedQuery, language)?.let { return it }
        salvageIncompleteReply(rawResponse)?.let { return it }
        bestEffortFallback(normalizedQuery, language)?.let { return it }

        return when (language) {
            Language.ENGLISH -> {
                if (looksLikeMalformedIncompleteReply(rawResponse, normalizedResponse)) {
                    "The answer cut off, but in short: I should answer this directly instead of starting a broken list."
                } else {
                    "The reply drifted off-topic, but I should answer the current question directly instead of resetting."
                }
            }
            Language.HINDI -> "Mera previous reply track se bahar chala gaya. Kripya apna sawal ek baar phir bhejiye, main seedha jawab dunga."
            Language.HINGLISH -> "Mera previous reply track se bahar chala gaya. Please apna sawal ek baar phir bhejo, main seedha answer dunga."
        }
    }

    private fun looksLikeGenericReset(normalizedQuery: String, normalizedResponse: String): Boolean {
        val queryLooksLikeRealQuestion = normalizedQuery.length > 8 &&
            (normalizedQuery.contains("?") || normalizedQuery.split(" ").size >= 3)

        if (!queryLooksLikeRealQuestion) return false

        val blockedPhrases = listOf(
            "i m ready to assist you",
            "i am ready to assist you",
            "i'm ready to assist you",
            "with your programming questions",
            "with your coding questions",
            "what s the first question",
            "what's the first question",
            "what is the first question",
            "how can i assist you today",
            "how can i help you today"
        )

        return blockedPhrases.any { normalizedResponse.contains(it) }
    }

    private fun looksLikePromptEchoLeak(rawResponse: String): Boolean {
        val lowered = rawResponse.lowercase(Locale.getDefault())
        val queryEchoCount = Regex("""(?i)\b(?:user query|original question|question):""")
            .findAll(rawResponse)
            .count()

        return queryEchoCount >= 2 ||
            lowered.contains("recent chat context:") ||
            ((lowered.contains("user query:") || lowered.contains("original question:") || lowered.contains("question:")) &&
                (lowered.contains("answer:") || lowered.contains("response:")))
    }

    private fun looksLikeSeriousTopicMisclassification(
        normalizedQuery: String,
        normalizedResponse: String
    ): Boolean {
        val seriousTopicKeywords = listOf(
            "rape",
            "sexual assault",
            "sexual violence",
            "abuse",
            "harassment",
            "molestation",
            "violence",
            "trauma"
        )
        val lyricKeywords = listOf(
            "lyric",
            "lyrics",
            "song",
            "verse",
            "chorus",
            "melody",
            "musical"
        )

        return seriousTopicKeywords.any { normalizedQuery.contains(it) } &&
            lyricKeywords.any { normalizedResponse.contains(it) }
    }

    private fun looksLikeMalformedIncompleteReply(rawResponse: String, normalizedResponse: String): Boolean {
        if (normalizedResponse.isBlank()) return true

        val raw = rawResponse.trim()
        val hasBrokenInlineNumberedList = looksLikeBrokenInlineNumberedList(raw)
        val hasDanglingFollowUpItemNumber = hasDanglingFollowUpItemNumber(raw)
        val endsWithDanglingListLead = Regex(
            """(?i)(?:here are|here's how|here is how|some ways|ways to|steps to|examples|options)[^.!?]{0,180}:\s*1\.?\s*$"""
        ).containsMatchIn(raw)
        val endsWithFeatureLead = Regex(
            """(?i)(?:key features|main features|features of|benefits of|common uses of|uses of)[^.!?]{0,120}:\s*1\.?\s*$"""
        ).containsMatchIn(raw)
        val endsWithBareItem = Regex("""(?s).+:\s*\d+\.?\s*$""").containsMatchIn(raw)
        val hasTruncatedTail = looksLikeTruncatedTail(raw)

        return hasBrokenInlineNumberedList ||
            hasDanglingFollowUpItemNumber ||
            endsWithDanglingListLead ||
            endsWithFeatureLead ||
            endsWithBareItem ||
            hasTruncatedTail
    }

    private fun looksLikeSeriousTopicFictionalization(
        normalizedQuery: String,
        normalizedResponse: String
    ): Boolean {
        val seriousTopicKeywords = listOf(
            "rape",
            "rapist",
            "sexual assault",
            "sexual violence",
            "abuse",
            "harassment",
            "molestation",
            "violence",
            "trauma"
        )
        val fictionalizationPhrases = listOf(
            "fictional character",
            "from a story",
            "imaginary character",
            "made up character",
            "not a real person",
            "i don t have any information about him or his actions",
            "i don't have any information about him or his actions"
        )

        return seriousTopicKeywords.any { normalizedQuery.contains(it) } &&
            fictionalizationPhrases.any { normalizedResponse.contains(it) }
    }

    private fun looksLikeWrongTechDefinition(
        normalizedQuery: String,
        normalizedResponse: String
    ): Boolean {
        return when {
            "langchain" in normalizedQuery ->
                normalizedResponse.contains("programming language") ||
                    normalizedResponse.contains("mobile apps") ||
                    normalizedResponse.contains("web applications")

            else -> false
        }
    }

    private fun looksLikeQuestionMismatch(
        normalizedQuery: String,
        normalizedResponse: String
    ): Boolean {
        val asksHowToUsePython = Regex("""\bhow do i use python\b|\bhow to use python\b|\bhow i do use python\b""")
            .containsMatchIn(normalizedQuery)
        val setupOnlyTerms = listOf(
            "install it on your computer",
            "download the latest version",
            "official python website",
            "package manager",
            "pip",
            "conda",
            "python 2",
            "python 3"
        )

        val asksPythonInCrm = "python" in normalizedQuery && "crm" in normalizedQuery

        return (asksHowToUsePython && setupOnlyTerms.any { normalizedResponse.contains(it) }) ||
            (asksPythonInCrm && !normalizedResponse.contains("crm"))
    }

    private fun looksLikeMissingRequestedSubject(
        normalizedQuery: String,
        normalizedResponse: String
    ): Boolean {
        val leadingQuestionPattern = Regex(
            """^(what is|who is|tell me about|thought on|what do you think about|what are your thoughts on|how do i use|how to use|how i do use)\b"""
        )
        if (!leadingQuestionPattern.containsMatchIn(normalizedQuery)) return false

        val subjectTokens = leadingQuestionPattern
            .replaceFirst(normalizedQuery, "")
            .split(" ")
            .map { it.trim() }
            .filter { it.length >= 4 && it !in genericSubjectWords }
            .distinct()

        if (subjectTokens.isEmpty()) return false
        return subjectTokens.none { normalizedResponse.contains(it) }
    }

    private fun specificFallback(normalizedQuery: String, language: Language): String? = when {
        asksForOpinionOnSeriousCrime(normalizedQuery) -> when (language) {
            Language.ENGLISH ->
                "If you mean a real person tied to rape or sexual violence, my view is that it is a serious violent crime and the focus should stay on victims, truth, and accountability."
            Language.HINDI ->
                "Agar aap kisi real vyakti ki baat kar rahe hain jo rape ya sexual violence se juda hai, to mera view hai ki yeh bahut serious violent crime hai aur focus victims, sach aur accountability par hona chahiye."
            Language.HINGLISH ->
                "Agar aap kisi real person ki baat kar rahe ho jo rape ya sexual violence se juda hai, to mera view hai ki yeh bahut serious violent crime hai aur focus victims, sach aur accountability par hona chahiye."
        }

        "langchain" in normalizedQuery -> when (language) {
            Language.ENGLISH ->
                "LangChain is a framework for building applications with large language models. It helps with prompts, tools, retrieval, agents, and workflow chaining."
            Language.HINDI ->
                "LangChain ek framework hai jo large language models ke saath applications banane ke liye use hota hai. Isse prompts, tools, retrieval, agents aur workflows manage kiye jaate hain."
            Language.HINGLISH ->
                "LangChain ek framework hai jo large language models ke saath apps banane ke liye use hota hai. Ye prompts, tools, retrieval, agents aur workflows handle karta hai."
        }

        "python" in normalizedQuery && "crm" in normalizedQuery -> when (language) {
            Language.ENGLISH ->
                "You can use Python in a CRM for automation, API integrations, reporting, and data cleanup. Which CRM are you using?"
            Language.HINDI ->
                "Aap CRM mein Python ko automation, API integration, reporting aur data cleanup ke liye use kar sakte hain. Aap kaunsa CRM use kar rahe hain?"
            Language.HINGLISH ->
                "Aap CRM mein Python ko automation, API integration, reporting aur data cleanup ke liye use kar sakte ho. Aap kaunsa CRM use kar rahe ho?"
        }

        "python" in normalizedQuery && "game" in normalizedQuery -> when (language) {
            Language.ENGLISH ->
                "You can use Python in game development for prototypes, 2D games with Pygame, gameplay scripting, enemy AI, and developer tools like level editors or asset pipelines. For large 3D commercial games, Unity or Unreal are more common, but Python is still very useful for tooling and automation."
            Language.HINDI ->
                "Aap Python ko game development mein prototypes, Pygame ke saath 2D games, gameplay scripting, enemy AI, aur developer tools jaise level editors ya asset pipelines ke liye use kar sakte hain. Bade 3D commercial games ke liye Unity ya Unreal zyada common hote hain, lekin tooling aur automation mein Python bahut useful rehta hai."
            Language.HINGLISH ->
                "Aap Python ko game development mein prototypes, Pygame ke saath 2D games, gameplay scripting, enemy AI, aur developer tools jaise level editors ya asset pipelines ke liye use kar sakte ho. Bade 3D commercial games ke liye Unity ya Unreal zyada common hote hain, lekin tooling aur automation mein Python bahut useful rehta hai."
        }

        Regex("""\bhow do i use python\b|\bhow to use python\b|\bhow i do use python\b""").containsMatchIn(normalizedQuery) -> when (language) {
            Language.ENGLISH ->
                "You can use Python for automation, web backends, data analysis, scripting, and AI. Tell me your use case and I will suggest the best starting path."
            Language.HINDI ->
                "Aap Python ko automation, web backend, data analysis, scripting aur AI ke liye use kar sakte hain. Apna use case batayiye, main best starting path suggest karunga."
            Language.HINGLISH ->
                "Aap Python ko automation, web backend, data analysis, scripting aur AI ke liye use kar sakte ho. Apna use case batao, main best starting path suggest karunga."
        }

        Regex("""\bwhat is python\b""").containsMatchIn(normalizedQuery) -> when (language) {
            Language.ENGLISH ->
                "Python is a general-purpose programming language used for automation, web development, data work, and AI."
            Language.HINDI ->
                "Python ek general-purpose programming language hai jo automation, web development, data work aur AI mein use hoti hai."
            Language.HINGLISH ->
                "Python ek general-purpose programming language hai jo automation, web development, data work aur AI mein use hoti hai."
        }

        else -> null
    }

    private fun bestEffortFallback(normalizedQuery: String, language: Language): String? = when {
        "game" in normalizedQuery && listOf("use", "build", "create", "making", "creating").any { normalizedQuery.contains(it) } -> when (language) {
            Language.ENGLISH ->
                "You can use it in game development for gameplay logic, prototypes, AI behaviors, UI flows, testing tools, and asset pipelines. Tell me the exact language or framework if you want a more specific game stack."
            Language.HINDI ->
                "Aap ise game development mein gameplay logic, prototypes, AI behaviors, UI flows, testing tools aur asset pipelines ke liye use kar sakte hain. Agar aap exact language ya framework batayen, to main zyada specific game stack suggest kar sakta hoon."
            Language.HINGLISH ->
                "Aap ise game development mein gameplay logic, prototypes, AI behaviors, UI flows, testing tools aur asset pipelines ke liye use kar sakte ho. Agar exact language ya framework batao, to main zyada specific game stack suggest kar sakta hoon."
        }

        else -> null
    }

    private fun salvageIncompleteReply(rawResponse: String): String? {
        val collapsed = rawResponse
            .replace(Regex("\\s+"), " ")
            .trim()

        if (collapsed.length < 24) return null

        val stripped = collapsed
            .replace(
                Regex(
                    """(?i)\s*(?:here are|here's how|here is how|some ways|ways to|steps to|examples|options|key features|main features|features of|benefits of|common uses of|uses of)[^.!?]{0,180}:\s*1\.?\s*$"""
                ),
                ""
            )
            .replace(Regex("""\s*:\s*\d+\.?\s*$"""), "")
            .trim()
            .trimEnd(':', ',', '-', ';')

        if (stripped.isBlank()) return null

        val lastSentenceEnd = stripped.lastIndexOfAny(charArrayOf('.', '!', '?'))
        val candidate = when {
            lastSentenceEnd >= 24 -> stripped.substring(0, lastSentenceEnd + 1).trim()
            else -> null
        } ?: return null

        return candidate.takeIf {
            it.split(" ").size >= 6 &&
                !looksLikeMalformedIncompleteReply(it, normalize(it))
        }
    }

    private fun looksLikeBrokenInlineNumberedList(rawResponse: String): Boolean {
        val inlineItemPattern = Regex("""\b\d+\.\s+[A-Za-z]""")
        val inlineItemCount = inlineItemPattern.findAll(rawResponse).count()
        if (inlineItemCount == 0) return false

        val hasLeadIn = Regex(
            """(?i)(?:here are|here's how|here is how|some ways|ways to|steps to|examples|options|key features|main features|features of|benefits of|common uses of|uses of|real[- ]life examples|practical examples|use cases)"""
        ).containsMatchIn(rawResponse)
        val hasOnlyOneItem = inlineItemCount == 1 && !Regex("""\b2\.\s+[A-Za-z]""").containsMatchIn(rawResponse)

        return (hasLeadIn && hasOnlyOneItem) || (hasLeadIn && looksLikeTruncatedTail(rawResponse))
    }

    private fun hasDanglingFollowUpItemNumber(rawResponse: String): Boolean {
        val hasStartedInlineItem = Regex("""\b\d+\.\s+[A-Za-z]""").containsMatchIn(rawResponse)
        val endsWithBareItemNumber = Regex("""\b\d+[.)]?\s*$""").containsMatchIn(rawResponse)
        return hasStartedInlineItem && endsWithBareItemNumber
    }

    private fun looksLikeTruncatedTail(rawResponse: String): Boolean {
        val trimmed = rawResponse.trim()
        if (trimmed.isBlank()) return true
        if (trimmed.last() in ".!?\"'") return false

        if (trimmed.last() in ":,;-(") return true
        if (Regex("""\b\d+[.)]?$""").containsMatchIn(trimmed)) return true
        if (Regex("""(?i)\b(and|or|because|so|then|with|using|for|like|including|such as)\s*$""").containsMatchIn(trimmed)) {
            return true
        }

        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        val lastWord = words.lastOrNull().orEmpty()
        if (lastWord.length in 1..3 && lastWord.any { it.isUpperCase() } && words.size >= 8) {
            return true
        }

        val tailStart = listOf(
            trimmed.lastIndexOf(". "),
            trimmed.lastIndexOf("! "),
            trimmed.lastIndexOf("? "),
            trimmed.lastIndexOf('\n')
        ).maxOrNull() ?: -1
        val tail = if (tailStart >= 0) trimmed.substring(tailStart + 1).trim() else trimmed
        val tailWordCount = tail.split(Regex("\\s+")).count { it.isNotBlank() }
        val hasEnumerationCue = Regex(
            """(?i)\b(for example|examples|ways|uses|features|benefits|including|such as|like|use cases)\b"""
        ).containsMatchIn(tail) || tail.contains(':')

        return tailWordCount >= 14 && hasEnumerationCue
    }

    private fun asksForOpinionOnSeriousCrime(normalizedQuery: String): Boolean {
        val opinionPhrases = listOf(
            "thought on",
            "thoughts on",
            "think about",
            "what do you think about",
            "what are your thoughts on"
        )
        val seriousTopicKeywords = listOf(
            "rape",
            "rapist",
            "sexual assault",
            "sexual violence",
            "abuse",
            "harassment",
            "molestation"
        )

        return opinionPhrases.any { normalizedQuery.contains(it) } &&
            seriousTopicKeywords.any { normalizedQuery.contains(it) }
    }

    private val genericSubjectWords = setOf(
        "about",
        "using",
        "with",
        "from",
        "into",
        "that",
        "this",
        "your",
        "their",
        "what",
        "which"
    )

    private val recentSubjectKeywords = listOf(
        "python",
        "langchain",
        "java",
        "kotlin",
        "javascript",
        "react",
        "django",
        "flask",
        "unity",
        "unreal"
    )

    private fun resolveQueryContext(
        normalizedQuery: String,
        conversationHistory: List<Pair<String, String>>
    ): String {
        if (!Regex("""\bit\b""").containsMatchIn(normalizedQuery)) return normalizedQuery

        val subject = conversationHistory
            .asReversed()
            .map { (_, text) -> normalize(text) }
            .firstNotNullOfOrNull { recentText ->
                recentSubjectKeywords.firstOrNull { keyword -> recentText.contains(keyword) }
            }
            ?: return normalizedQuery

        return normalizedQuery.replace(Regex("""\bit\b"""), subject)
    }

    private fun normalize(text: String): String =
        text
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
