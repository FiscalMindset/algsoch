package com.runanywhere.kotlin_starter_example.domain.ai

import com.runanywhere.kotlin_starter_example.data.models.enums.Language
import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode
import com.runanywhere.kotlin_starter_example.domain.models.ReasoningStep
import com.runanywhere.kotlin_starter_example.domain.models.StructuredResponse

class ResponseParser {

    private data class ParsedSections(
        val directAnswer: String,
        val quickExplanation: String = "",
        val deepExplanation: String? = null
    )

    fun sanitizeForDisplay(rawResponse: String, userQuery: String? = null): String {
        // Remove common model/control artifacts before anything is shown to the user.
        var cleaned = rawResponse
            .replace(Regex("(?i)^Answer:\\s*"), "")
            .replace(Regex("(?i)^Response:\\s*"), "")
            .replace(Regex("(?i)^Assistant:\\s*"), "")
            .replace("<|im_end|>", "")
            .replace("<|im_start|>", "")
            .replace("<end_of_utterance>", "")
            .replace("</end_of_utterance>", "")
            .replace(Regex("<\\|eot_id\\|>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<\\|end_of_text\\|>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<\\|.*?\\|>"), "")
            .replace(Regex("(?i)end\\.of\\.utterance"), "")
            .replace(Regex("(?i)end_of_utterance"), "")
            .replace(Regex("(?i)model\\s+(?:for\\s+)?vision\\s+is\\s+[\\w\\-]+"), "")
            .replace(Regex("(?i)smolvm[\\w\\-]*"), "")
            .replace(Regex("(?i)qwen2?-?vl[\\w\\-]*"), "")
            .replace(Regex("(?i)lfm2?-?vl[\\w\\-]*"), "")
            .replace(Regex("(?i)<\\|vision_start\\|>.*?<\\|vision_end\\|>"), "")
            .replace(Regex("(?i)<vision>.*?</vision>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        cleaned = cleanupMarkdownArtifacts(cleaned)

        userQuery?.let { query ->
            val trimmedQuery = query.trim().lowercase().removeSuffix("?")
            if (trimmedQuery.isNotBlank() && cleaned.lowercase().startsWith(trimmedQuery)) {
                cleaned = cleaned.substring(query.length).trim()
                cleaned = cleaned.replace(Regex("^[:\\-\\s]+"), "").trim()
            }
        }

        return removeDanglingTail(cleaned)
    }
    
    fun parse(rawResponse: String, mode: ResponseMode, language: Language, userQuery: String? = null): StructuredResponse {
        val cleaned = sanitizeForDisplay(rawResponse, userQuery)

        if (cleaned.isBlank()) {
            return StructuredResponse(
                directAnswer = "I'm sorry, I couldn't generate a clear response. Could you try rephrasing your question?",
                quickExplanation = "The offline model returned an empty result.",
                deepExplanation = null,
                mode = mode,
                language = language
            )
        }

        val parsed = normalizeForMode(cleaned, mode, userQuery)

        return StructuredResponse(
            directAnswer = parsed.directAnswer.ifBlank { cleaned },
            quickExplanation = parsed.quickExplanation,
            deepExplanation = parsed.deepExplanation,
            mode = mode,
            language = language
        )
    }
    
    fun parseReasoningSteps(rawResponse: String): List<ReasoningStep> {
        val steps = mutableListOf<ReasoningStep>()
        val stepPattern = Regex("""STEP\s+(\d+):\s*(.+?)(?=STEP\s+\d+:|$)""", RegexOption.DOT_MATCHES_ALL)
        
        stepPattern.findAll(rawResponse).forEachIndexed { _, match ->
            val stepNumber = match.groupValues[1].toIntOrNull() ?: return@forEachIndexed
            val content = match.groupValues[2].trim()
            val lines = content.lines().filter { it.isNotBlank() }
            
            steps.add(ReasoningStep(
                stepNumber = stepNumber,
                title = lines.firstOrNull() ?: "Thinking Step",
                description = lines.drop(1).joinToString("\n")
            ))
        }
        
        return steps.ifEmpty {
            listOf(ReasoningStep(1, "Educational Analysis", rawResponse.trim()))
        }
    }

    private fun normalizeForMode(
        cleaned: String,
        mode: ResponseMode,
        userQuery: String?
    ): ParsedSections = when (mode) {
        ResponseMode.DIRECT -> ParsedSections(
            directAnswer = normalizeDirect(cleaned)
        )

        ResponseMode.ANSWER -> normalizeAnswer(cleaned)

        ResponseMode.EXPLAIN -> ParsedSections(
            directAnswer = normalizeExplain(cleaned)
        )

        ResponseMode.NOTES -> ParsedSections(
            directAnswer = normalizeNotes(cleaned, userQuery)
        )

        ResponseMode.DIRECTION -> ParsedSections(
            directAnswer = normalizeDirection(cleaned)
        )

        ResponseMode.CREATIVE -> ParsedSections(
            directAnswer = limitSentences(cleaned, maxSentences = 6, maxChars = 760)
        )

        ResponseMode.THEORY -> ParsedSections(
            directAnswer = limitParagraphs(cleaned, maxParagraphs = 4, maxChars = 1400)
        )
    }

    private fun normalizeDirect(text: String): String {
        val flattened = collapseBrokenListLeadIn(flattenForProse(text))
        return limitSentences(flattened, maxSentences = 3, maxChars = 240)
    }

    private fun normalizeAnswer(text: String): ParsedSections {
        val prepared = prepareExplainLayout(text)
        val lines = prepared.lines().map(String::trim).filter { it.isNotBlank() }
        val listItems = lines
            .filter(::isExplainListLine)
            .mapNotNull(::normalizeExplainListItem)
            .distinctBy { it.lowercase() }

        val introText = lines
            .filterNot(::isExplainListLine)
            .joinToString(" ")
            .trim()

        val flattened = flattenForProse(if (introText.isNotBlank()) introText else text)
        val sentences = extractSentences(flattened)

        if (sentences.isEmpty()) {
            return ParsedSections(directAnswer = flattened)
        }

        val directAnswer = sentences.take(2).joinToString(" ").trim()
        val proseExplanation = sentences
            .drop(2)
            .take(4)
            .joinToString(" ")
            .trim()
        val quickExplanation = when {
            listItems.size >= 2 -> buildString {
                appendLine("Key Points:")
                listItems.take(4).forEachIndexed { index, item ->
                    appendLine("${index + 1}. $item")
                }
                if (proseExplanation.isNotBlank()) {
                    appendLine()
                    append("Takeaway: ")
                    append(proseExplanation)
                }
            }.trim()
            else -> proseExplanation
        }

        return ParsedSections(
            directAnswer = directAnswer.ifBlank { flattened },
            quickExplanation = quickExplanation
        )
    }

    private fun normalizeExplain(text: String): String {
        val prepared = prepareExplainLayout(text)
        val lines = prepared.lines()
        val introLines = mutableListOf<String>()
        val listItems = mutableListOf<String>()
        val tailLines = mutableListOf<String>()
        var inList = false

        lines.forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isBlank() && !inList -> {
                    if (introLines.lastOrNull()?.isNotBlank() == true) introLines += ""
                }

                isExplainListLine(line) -> {
                    normalizeExplainListItem(line)?.let(listItems::add)
                    inList = true
                }

                inList -> tailLines += line
                else -> introLines += line
            }
        }

        val uniqueItems = listItems
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        if (uniqueItems.size >= 2) {
            val intro = limitParagraphs(introLines.joinToString("\n").trim(), maxParagraphs = 2, maxChars = 420)
            val takeaway = limitSentences(flattenForProse(tailLines.joinToString(" ")), maxSentences = 2, maxChars = 260)

            return buildString {
                if (intro.isNotBlank()) {
                    append(intro)
                    append("\n\n")
                }
                appendLine("Key Points:")
                uniqueItems.take(6).forEachIndexed { index, item ->
                    appendLine("${index + 1}. $item")
                }
                if (takeaway.isNotBlank()) {
                    appendLine()
                    append("Takeaway: ")
                    append(takeaway)
                }
            }.trim()
        }

        return limitParagraphs(prepared, maxParagraphs = 5, maxChars = 1300)
    }

    private fun normalizeNotes(text: String, userQuery: String?): String {
        val rawLines = text.lines().map(String::trim).filter { it.isNotBlank() }
        val hasBulletShape = rawLines.count { it.startsWith("- ") } >= 2
        val hasSummary = rawLines.any { it.startsWith("Summary:", ignoreCase = true) }

        if (hasBulletShape && hasSummary) {
            return rawLines.joinToString("\n")
        }

        val title = when {
            rawLines.firstOrNull()?.startsWith("- ") == false &&
                rawLines.firstOrNull()?.startsWith("Summary:", ignoreCase = true) == false &&
                (rawLines.firstOrNull()?.length ?: 0) <= 70 -> rawLines.first()
            !userQuery.isNullOrBlank() -> userQuery.trim().removeSuffix("?").take(70)
            else -> "Study Notes"
        }

        val bullets = extractSentences(flattenForProse(text))
            .map(::cleanListItem)
            .filter { it.isNotBlank() }
            .take(5)
            .ifEmpty { listOf(cleanListItem(text).take(140)) }

        val summary = bullets.lastOrNull()?.take(140).orEmpty()

        return buildString {
            appendLine(title)
            bullets.forEach { appendLine("- $it") }
            append("Summary: ")
            append(summary.ifBlank { "Review the key points above." })
        }.trim()
    }

    private fun normalizeDirection(text: String): String {
        if (Regex("(?im)^Step\\s+1:").containsMatchIn(text)) {
            return limitParagraphs(text, maxParagraphs = 8, maxChars = 1200)
        }

        val items = extractSentences(flattenForProse(text))
            .map(::cleanListItem)
            .filter { it.isNotBlank() }

        if (items.isEmpty()) return text

        val steps = items.take(4)
        val tips = items.drop(steps.size).take(2).ifEmpty {
            listOf("Check each step before moving on.")
        }
        val mistakes = items.drop(steps.size + tips.size).take(2).ifEmpty {
            listOf("Skipping the setup and jumping straight to the final answer.")
        }

        return buildString {
            steps.forEachIndexed { index, step ->
                appendLine("Step ${index + 1}: $step")
            }
            appendLine("Tips:")
            tips.forEach { appendLine("- $it") }
            appendLine("Common Mistakes:")
            mistakes.forEach { appendLine("- $it") }
        }.trim()
    }

    private fun flattenForProse(text: String): String =
        normalizeInlineNumberingForProse(text)
            .lines()
            .map(::cleanListItem)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun ensureListSpacing(text: String): String {
        val lines = text.lines()
        if (lines.isEmpty()) return text

        val rebuilt = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            val isListLine = trimmed.matches(Regex("""(?:\d+\.|-|Step\s+\d+:).*""", RegexOption.IGNORE_CASE))
            val previous = rebuilt.lastOrNull().orEmpty().trim()
            val previousIsText = previous.isNotBlank() &&
                !previous.matches(Regex("""(?:\d+\.|-|Step\s+\d+:|Tips:|Common Mistakes:|Summary:).*""", RegexOption.IGNORE_CASE))

            if (isListLine && previousIsText && rebuilt.lastOrNull()?.isNotBlank() == true) {
                rebuilt += ""
            }

            rebuilt += if (index == lines.lastIndex) trimmed else line.trimEnd()
        }

        return rebuilt.joinToString("\n").replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    private fun prepareExplainLayout(text: String): String {
        val splitInlineLists = text.replace(
            Regex("""(?<=\S)\s+(\d+\.\s+[A-Z])"""),
            "\n$1"
        )
        return ensureListSpacing(splitInlineLists)
    }

    private fun extractSentences(text: String): List<String> =
        text
            .replace(Regex("\\s+"), " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun limitSentences(text: String, maxSentences: Int, maxChars: Int): String {
        val sentences = extractSentences(text)
        val limited = if (sentences.isNotEmpty()) {
            sentences.take(maxSentences).joinToString(" ")
        } else {
            text.trim()
        }

        return limitChars(limited, maxChars)
    }

    private fun limitParagraphs(text: String, maxParagraphs: Int, maxChars: Int): String {
        val paragraphs = text
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(maxParagraphs)

        val joined = if (paragraphs.isNotEmpty()) {
            paragraphs.joinToString("\n\n")
        } else {
            text.trim()
        }

        return limitChars(joined, maxChars)
    }

    private fun limitChars(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text.trim()

        val clipped = text.take(maxChars).trimEnd()
        val lastBoundary = listOf(
            clipped.lastIndexOf(". "),
            clipped.lastIndexOf("! "),
            clipped.lastIndexOf("? "),
            clipped.lastIndexOf('\n')
        ).maxOrNull() ?: -1

        return when {
            lastBoundary > maxChars / 2 -> clipped.take(lastBoundary + 1).trim()
            else -> clipped.trim()
        }
    }

    private fun cleanupMarkdownArtifacts(text: String): String {
        val normalizedBullets = text.lines().joinToString("\n") { line ->
            line
                .replace(Regex("""^\s*[*•]\s+"""), "- ")
                .replace(Regex("""^\s*#{1,6}\s*"""), "")
        }

        return normalizedBullets
            .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
            .replace(Regex("""__(.+?)__"""), "$1")
            .replace(Regex("""`([^`]+)`"""), "$1")
            .replace("**", "")
            .replace("__", "")
            .trim()
    }

    private fun removeDanglingTail(text: String): String {
        val lines = text.lines().toMutableList()
        while (lines.isNotEmpty()) {
            val tail = lines.last().trim()
            val isDangling = tail.matches(Regex("""\d+[.)]?""")) ||
                tail == "-" ||
                tail == "•" ||
                tail.matches(Regex("""(?i)step\s+\d+:?"""))

            if (!isDangling) break
            lines.removeAt(lines.lastIndex)
        }

        var cleaned = lines.joinToString("\n").trim()
        if (
            Regex("""\b\d+\.\s+[A-Za-z]""").containsMatchIn(cleaned) &&
            Regex("""\b\d+[.)]?\s*$""").containsMatchIn(cleaned)
        ) {
            cleaned = cleaned.replace(Regex("""\s+\d+[.)]?\s*$"""), "").trim()
        }

        return cleaned
    }

    private fun collapseBrokenListLeadIn(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return trimmed

        val brokenSentence = Regex(
            """(?is)^(?:here(?:'s| is)\s+how|here are|these are|some ways|ways to|steps to|examples of|options for)[^.!?]{0,180}:\s*\d+[.)]?\s*$"""
        )

        val sentences = trimmed
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()

        while (sentences.isNotEmpty() && brokenSentence.matches(sentences.last())) {
            sentences.removeAt(sentences.lastIndex)
        }

        val withoutBrokenSentence = if (sentences.isNotEmpty()) {
            sentences.joinToString(" ")
        } else {
            trimmed
        }

        return withoutBrokenSentence
            .replace(Regex("""(?is):\s*\d+[.)]?\s*$"""), ".")
            .replace(Regex("""\s+\.$"""), ".")
            .trim()
    }

    private fun isExplainListLine(line: String): Boolean =
        line.matches(Regex("""(?:\d+\.|-)\s+.+""", RegexOption.IGNORE_CASE))

    private fun normalizeExplainListItem(line: String): String? {
        val cleaned = line
            .replace(Regex("""^\d+\.\s*"""), "")
            .replace(Regex("""^-\s*"""), "")
            .trim()
            .removeSuffix(":")
            .trim()

        if (cleaned.isBlank()) return null
        if (cleaned.matches(Regex("""\d+[.)]?"""))) return null

        return removeDanglingTail(cleaned)
            .trim()
            .takeIf { it.isNotBlank() && !it.matches(Regex("""\d+[.)]?""")) }
    }

    private fun cleanListItem(text: String): String =
        text
            .replace(Regex("^(?:[-*•]+\\s*)"), "")
            .replace(Regex("(?i)^step\\s+\\d+:\\s*"), "")
            .replace(Regex("(?i)^tips:\\s*"), "")
            .replace(Regex("(?i)^common mistakes:\\s*"), "")
            .replace(Regex("(?i)^summary:\\s*"), "")
            .trim()

    private fun normalizeInlineNumberingForProse(text: String): String =
        text
            .replace(Regex("""(?<=[:.;])\s*\d+\.\s+"""), " ")
            .replace(Regex("""\s{2,}"""), " ")
}
