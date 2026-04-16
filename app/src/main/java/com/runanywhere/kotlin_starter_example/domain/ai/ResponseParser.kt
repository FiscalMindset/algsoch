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
            .replace(Regex("(?i)^you\\s+are\\s+algsoch,?\\s+an\\s+image-grounded\\s+ai\\s+companion\\.?\\s*"), "")
            .replace(Regex("(?i)^algsoch,?\\s+an\\s+image-grounded\\s+ai\\s+companion\\.?\\s*"), "")
            .replace(Regex("(?i)^i\\s+can\\s+(?:assist|help)\\s+with\\s+(?:tasks|a wide range of tasks)[^.!?]*[.?!]?\\s*"), "")
            .replace(Regex("(?i)^i(?:\\s+am|'m)\\s+glad\\s+you\\s+feel\\s+that\\s+way[,\\s]*i(?:\\s+will|'ll)\\s+make\\s+sure\\s+to\\s+stay\\s+in\\s+(?:your\\s+)?emotional\\s+flow(?:\\s+the\\s+whole\\s+time\\s+we\\s+chat\\s+together)?\\.?\\s*"), "")
            .replace(Regex("(?i)^i(?:\\s+will|'ll)\\s+make\\s+sure\\s+to\\s+stay\\s+in\\s+(?:your\\s+)?emotional\\s+flow(?:\\s+the\\s+whole\\s+time\\s+we\\s+chat\\s+together)?\\.?\\s*"), "")
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
            .trim()
        
        // FIX: Convert double backticks to triple backticks for code blocks
        // Pattern: ``language\ncode\n`` or ``code``
        cleaned = fixBacktickCodeBlocks(cleaned)
        cleaned = normalizeFencedCodeBlocks(cleaned)
        cleaned = normalizeWhitespaceOutsideCodeBlocks(cleaned)

        cleaned = cleanupMarkdownArtifacts(cleaned)
        cleaned = stripLeadingDisplayLabels(cleaned)
        cleaned = stripPromptEchoLeak(cleaned)
        cleaned = stripCapabilitySelfDescription(cleaned, userQuery)
        cleaned = stripModeInstructionLeak(cleaned)
        cleaned = stripLeadingQuestionEcho(cleaned, userQuery)
        cleaned = dedupeRepeatedLines(cleaned)
        cleaned = dedupeRepeatedSentences(cleaned)
        cleaned = formatReadableLayout(cleaned, userQuery)
        cleaned = cleaned
            .replace(Regex("""(?i)[^.?!\n]*\bemotional flow\b[^.?!\n]*[.?!]?"""), " ")
            .replace(Regex("""(?i)[^.?!\n]*\badult-consensual topics\b[^.?!\n]*[.?!]?"""), " ")
            .replace(Regex("""(?i)[^.?!\n]*\breal safety reasons?\b[^.?!\n]*[.?!]?"""), " ")
            .replace(Regex("""(?i)[^.?!\n]*\bstyle rules\b[^.?!\n]*[.?!]?"""), " ")
            .replace(Regex("""(?i)[^.?!\n]*\bemotional calibration\b[^.?!\n]*[.?!]?"""), " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

        userQuery?.let { query ->
            val trimmedQuery = query.trim().lowercase().removeSuffix("?")
            if (trimmedQuery.isNotBlank() && cleaned.lowercase().startsWith(trimmedQuery)) {
                cleaned = cleaned.substring(query.length).trim()
                cleaned = cleaned.replace(Regex("^[:\\-\\s]+"), "").trim()
            }
        }

        return removeDanglingTail(cleaned)
    }
    
    private fun fixBacktickCodeBlocks(text: String): String {
        var fixed = text
        
        // First, normalize any existing malformed code blocks
        // Remove excessive backticks (4 or more) down to 3
        fixed = fixed.replace(Regex("````+"), "```")
        fixed = normalizeSplitFenceLines(fixed)
        fixed = normalizeInlineFencedBlocks(fixed)
        fixed = normalizeSplitFenceLines(fixed)
        
        // Pattern 1: ``code language\ncode\n`` (double backticks with potential language marker)
        // This handles: ``code python\nvicky_kumar = "Vicky Kumar"\nprint(vicky_kumar)\n``
        fixed = fixed.replace(Regex("(?<!`)``\\s*code\\s+(\\w+)\\s*\\n([\\s\\S]*?)\\n``(?!`)")) { match ->
            val language = match.groupValues[1]
            val code = match.groupValues[2].trim()
            "```$language\n$code\n```"
        }
        
        // Pattern 2: ``language\ncode\n`` (double backticks with language)
        fixed = fixed.replace(Regex("(?<!`)``\\s*(\\w+)\\s*\\n([\\s\\S]*?)``(?!`)")) { match ->
            val language = match.groupValues[1]
            val code = match.groupValues[2].trim()
            "```$language\n$code\n```"
        }
        
        // Pattern 3: ``\ncode\n`` (double backticks without language, multi-line)
        fixed = fixed.replace(Regex("(?<!`)``\\s*\\n([\\s\\S]*?)\\n``(?!`)")) { match ->
            val code = match.groupValues[1].trim()
            val language = detectLanguageFromCode(code.lines().firstOrNull()?.trim() ?: "")
            "```$language\n$code\n```"
        }
        
        // Pattern 4: ``code`` (double backticks, potentially multi-line without explicit newlines at start/end)
        fixed = fixed.replace(Regex("(?<!`)``([^`]+)``(?!`)")) { match ->
            val code = match.groupValues[1].trim()
            if (code.contains("\n")) {
                // Multi-line code
                val language = detectLanguageFromCode(code.lines().firstOrNull()?.trim() ?: "")
                "```$language\n$code\n```"
            } else {
                // Single line - keep as inline code
                "`$code`"
            }
        }
        
        // Pattern 5: Single backtick with multi-line code (rare but possible)
        fixed = fixed.replace(Regex("(?<!`)`(\\w+)\\s*\\n([\\s\\S]*?)\\n`(?!`)")) { match ->
            val language = match.groupValues[1]
            val code = match.groupValues[2].trim()
            "```$language\n$code\n```"
        }
        
        return fixed
    }

    private fun normalizeSplitFenceLines(text: String): String {
        val lines = text.lines()
        if (lines.isEmpty()) return text

        val rebuilt = mutableListOf<String>()
        var index = 0

        while (index < lines.size) {
            val trimmed = lines[index].trim()
            if (trimmed.matches(Regex("`+(?:\\s+`+)*"))) {
                var totalBackticks = trimmed.count { it == '`' }
                var endIndex = index

                while (endIndex + 1 < lines.size) {
                    val nextTrimmed = lines[endIndex + 1].trim()
                    if (nextTrimmed.matches(Regex("`+(?:\\s+`+)*"))) {
                        totalBackticks += nextTrimmed.count { it == '`' }
                        endIndex += 1
                    } else {
                        break
                    }
                }

                if (endIndex > index || totalBackticks >= 3) {
                    rebuilt += if (totalBackticks >= 3) "```" else "`".repeat(totalBackticks)
                    index = endIndex + 1
                    continue
                }
            }

            rebuilt += lines[index]
            index += 1
        }

        return rebuilt.joinToString("\n")
    }

    private fun normalizeInlineFencedBlocks(text: String): String {
        var normalized = text

        normalized = normalized.replace(Regex("`{3,}(\\w+)[ \\t]+([^\\n][\\s\\S]*?)`{3,}")) { match ->
            val language = match.groupValues[1]
            val code = match.groupValues[2].trim()
            "```$language\n$code\n```"
        }

        normalized = normalized.replace(Regex("`{3,}[ \\t]+([^\\n][\\s\\S]*?)`{3,}")) { match ->
            val code = match.groupValues[1].trim()
            val language = detectLanguageFromCode(code.substringBefore('\n').trim())
            "```$language\n$code\n```"
        }

        normalized = normalized.replace(Regex("(?m)^`{4,}(\\w+)")) { match ->
            "```${match.groupValues[1]}"
        }
        normalized = normalized.replace(Regex("(?m)^`{4,}$"), "```")

        return normalized
    }

    private fun normalizeWhitespaceOutsideCodeBlocks(text: String): String =
        transformOutsideCodeBlocks(text) { segment ->
            segment
                .replace(Regex("[ \\t]{2,}"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
        }.trim()

    private fun normalizeFencedCodeBlocks(text: String): String =
        transformCodeBlocks(text) { language, code ->
            val detectedLanguage = if (language.isNotBlank()) {
                language
            } else {
                detectLanguageFromCode(code.substringBefore('\n').trim())
            }
            val normalizedCode = normalizeCodeBlockContent(detectedLanguage, code)
            val openingFence = if (detectedLanguage.isNotBlank() && detectedLanguage != "code") {
                "```$detectedLanguage"
            } else {
                "```"
            }
            "$openingFence\n$normalizedCode\n```"
        }

    private fun transformOutsideCodeBlocks(text: String, transform: (String) -> String): String {
        val fencedCodePattern = Regex("```[^\\n`]*\\n[\\s\\S]*?```", RegexOption.MULTILINE)
        val rebuilt = StringBuilder()
        var currentIndex = 0

        fencedCodePattern.findAll(text).forEach { match ->
            if (match.range.first > currentIndex) {
                rebuilt.append(transform(text.substring(currentIndex, match.range.first)))
            }
            rebuilt.append(match.value)
            currentIndex = match.range.last + 1
        }

        if (currentIndex < text.length) {
            rebuilt.append(transform(text.substring(currentIndex)))
        }

        return rebuilt.toString()
    }

    private fun transformCodeBlocks(text: String, transform: (String, String) -> String): String {
        val fencedCodePattern = Regex("```([^\\n`]*)\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
        val rebuilt = StringBuilder()
        var currentIndex = 0

        fencedCodePattern.findAll(text).forEach { match ->
            if (match.range.first > currentIndex) {
                rebuilt.append(text.substring(currentIndex, match.range.first))
            }
            rebuilt.append(transform(match.groupValues[1].trim(), match.groupValues[2].trim('\n')))
            currentIndex = match.range.last + 1
        }

        if (currentIndex < text.length) {
            rebuilt.append(text.substring(currentIndex))
        }

        return rebuilt.toString()
    }

    private fun normalizeCodeBlockContent(language: String, code: String): String =
        when (language.lowercase()) {
            "python", "py" -> reflowFlattenedPythonCode(code)
            else -> code.trim('\n')
        }

    private fun reflowFlattenedPythonCode(code: String): String {
        val trimmed = code.trim('\n')
        val nonBlankLines = trimmed.lines().filter { it.isNotBlank() }
        if (nonBlankLines.size > 1) return trimmed

        val flattened = nonBlankLines.joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val looksFlattened = Regex("""\b(?:def|class)\s+\w+\s*\([^)]*\):\s+\S""").containsMatchIn(flattened) ||
            (flattened.contains("print(") && flattened.contains("#")) ||
            Regex("""\b(?:if|for|while)\b[^:]*:\s+\S""").containsMatchIn(flattened)

        if (!looksFlattened) return trimmed

        var formatted = flattened
            .replace(Regex("""(\b(?:def|class)\s+\w+\s*\([^)]*\):)\s+"""), "$1\n    ")
            .replace(Regex("""(\)\s+)#\s*([^#\n]+?)\s+(?=print\()"""), ")  # $2\n")
            .replace(Regex("""(?<!\))\s+(#\s*Example\s+usage:)""", RegexOption.IGNORE_CASE), "\n\n$1")
            .replace(Regex("""(?<!\))\s+(#)"""), "\n$1")
            .replace(Regex("""(#[^\n]*:)\s+(?=print\()"""), "$1\n")
            .replace(Regex("""\)\s+(?=print\()"""), ")\n")
            .replace(Regex(""";\s*"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()

        if (!formatted.contains('\n')) {
            formatted = trimmed
        }

        return formatted
    }
    
    private fun detectLanguageFromCode(firstLine: String): String {
        return when {
            firstLine.startsWith("def ") || firstLine.startsWith("class ") || 
            firstLine.contains("print(") || firstLine.startsWith("import ") ||
            firstLine.startsWith("from ") -> "python"
            
            firstLine.startsWith("function ") || firstLine.startsWith("const ") ||
            firstLine.startsWith("let ") || firstLine.startsWith("var ") ||
            firstLine.contains("=>") || firstLine.startsWith("async ") -> "javascript"
            
            firstLine.startsWith("public ") || firstLine.startsWith("private ") ||
            firstLine.contains("void ") || firstLine.contains("static ") ||
            firstLine.startsWith("package ") -> "java"
            
            firstLine.startsWith("func ") || firstLine.startsWith("val ") ||
            firstLine.startsWith("var ") || firstLine.contains("->") -> "kotlin"
            
            firstLine.startsWith("#include") || firstLine.startsWith("int main") -> "cpp"
            
            firstLine.startsWith("<?php") -> "php"
            
            firstLine.startsWith("package main") || firstLine.startsWith("func main") -> "go"
            
            else -> "code"
        }
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

        val parsed = if (containsFencedCodeBlock(cleaned)) {
            ParsedSections(directAnswer = cleaned.trim())
        } else if (shouldPreserveDraftFormat(userQuery, cleaned)) {
            ParsedSections(directAnswer = preserveDraftFormatting(cleaned))
        } else {
            normalizeForMode(cleaned, mode, userQuery)
        }

        return StructuredResponse(
            directAnswer = parsed.directAnswer.ifBlank { cleaned },
            quickExplanation = parsed.quickExplanation,
            deepExplanation = parsed.deepExplanation,
            mode = mode,
            language = language
        )
    }

    private fun containsFencedCodeBlock(text: String): Boolean =
        Regex("```[^\\n`]*\\n[\\s\\S]*?```", RegexOption.MULTILINE).containsMatchIn(text)
    
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

        ResponseMode.CODE -> ParsedSections(
            directAnswer = normalizeCode(cleaned)
        )

        ResponseMode.DIRECTION -> ParsedSections(
            directAnswer = normalizeDirection(cleaned)
        )

        ResponseMode.CREATIVE -> ParsedSections(
            directAnswer = cleaned.trim()  // No limits
        )

        ResponseMode.THEORY -> ParsedSections(
            directAnswer = cleaned.trim()  // No limits
        )
    }

    private fun normalizeCode(text: String): String {
        // Just trim - backtick fixing is already done in sanitizeForDisplay
        return text.trim()
    }

    private fun normalizeDirect(text: String): String {
        val cleaned = collapseBrokenListLeadIn(text).trim()
        if (cleaned.isBlank()) return cleaned

        val hasStructuredLines = Regex(
            """(?im)^\s*(?:\d+\.|-|Step\s+\d+:|Tips:|Common Mistakes:|Summary:)"""
        ).containsMatchIn(cleaned)

        val normalized = if (hasStructuredLines) {
            flattenForProse(cleaned)
        } else {
            rebalanceDirectParagraphs(preserveShortParagraphs(normalizeInlineNumberingForProse(cleaned)))
        }

        return limitParagraphs(normalized, maxParagraphs = 2, maxChars = 520)
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
            .joinToString(" ")
            .trim()
        val quickExplanation = when {
            listItems.size >= 2 -> buildString {
                appendLine("Key Points:")
                listItems.forEachIndexed { index, item ->
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
            val intro = introLines.joinToString("\n").trim()
            val takeaway = flattenForProse(tailLines.joinToString(" ")).trim()

            return buildString {
                if (intro.isNotBlank()) {
                    append(intro)
                    append("\n\n")
                }
                appendLine("Key Points:")
                uniqueItems.forEachIndexed { index, item ->
                    appendLine("${index + 1}. $item")
                }
                if (takeaway.isNotBlank()) {
                    appendLine()
                    append("Takeaway: ")
                    append(takeaway)
                }
            }.trim()
        }

        return prepared.trim()
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
            return text.trim()
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

    private fun preserveShortParagraphs(text: String): String {
        val paragraphs = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split(Regex("\\n\\s*\\n"))
            .map { paragraph ->
                paragraph
                    .lines()
                    .map(::cleanListItem)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            .filter { it.isNotBlank() }

        return paragraphs.joinToString("\n\n").trim()
    }

    private fun rebalanceDirectParagraphs(text: String): String {
        val paragraphs = text
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (paragraphs.isEmpty()) return text.trim()
        if (paragraphs.size >= 2) return paragraphs.take(2).joinToString("\n\n")

        val singleParagraph = paragraphs.first()
        val sentences = extractSentences(singleParagraph)
        if (sentences.size < 4 && singleParagraph.length < 260) return singleParagraph

        val firstParagraph = sentences.take(2).joinToString(" ").trim()
        val secondParagraph = sentences.drop(2).joinToString(" ").trim()

        return listOf(firstParagraph, secondParagraph)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .ifBlank { singleParagraph }
    }

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
        return transformOutsideCodeBlocks(text) { segment ->
            val normalizedBullets = segment.lines().joinToString("\n") { line ->
                line
                    .replace(Regex("""^\s*[*•]\s+"""), "- ")
                    .replace(Regex("""^\s*#{1,6}\s*"""), "")
            }

            normalizedBullets
                .replace(Regex("""`([^`\n]+)`"""), "`$1`")
        }.trim()
    }

    private fun stripLeadingDisplayLabels(text: String): String {
        var cleaned = text.trim()
        val labelPattern = Regex(
            """(?i)^(?:Explanation|Concept|Connections|Details|More|Idea|Why It Sticks):\s*"""
        )

        while (labelPattern.containsMatchIn(cleaned)) {
            cleaned = cleaned.replaceFirst(labelPattern, "").trimStart()
        }

        return cleaned
    }

    private fun stripPromptEchoLeak(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return trimmed

        val contextStart = Regex("""(?i)\bRecent chat context:""").find(trimmed)?.range?.first ?: -1
        if (contextStart > 0) {
            return trimmed.substring(0, contextStart).trim()
        }

        val promptEchoStart = Regex("""(?i)\b(?:User query|Original question|Question):""")
            .find(trimmed)
            ?.range
            ?.first
            ?: -1
        val hasAnswerMarker = Regex("""(?i)\b(?:Answer|Response):""").containsMatchIn(trimmed)

        return when {
            promptEchoStart > 0 && hasAnswerMarker ->
                trimmed.substring(0, promptEchoStart).trim()

            promptEchoStart == 0 && hasAnswerMarker ->
                trimmed.split(Regex("""(?i)\b(?:Answer|Response):\s*"""))
                    .lastOrNull()
                    ?.trim()
                    .orEmpty()
                    .ifBlank { trimmed }

            else -> trimmed
        }
    }

    private fun stripCapabilitySelfDescription(text: String, userQuery: String?): String {
        val trimmed = text.trim()
        if (trimmed.isBlank() || isCapabilityQuestion(userQuery)) return trimmed

        var cleaned = trimmed
        val blockedSentencePatterns = listOf(
            Regex("""(?i)[^.?!\n]*\bi\s+can\s+(?:assist|help)\s+with\s+(?:tasks|a wide range of tasks)\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bsuch as programming and software development\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bediting and proofreading written content\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\banalyzing data sets\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bproviding solutions to complex problems\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bi(?:'m| am)\s+an\s+ai\s+assistant\s+designed\s+to\s+provide\s+thoughtful\s+and\s+informative\s+responses\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bi(?:'m| am)\s+an\s+ai\s+assistant\s+designed\s+to\s+(?:help|provide|assist|support)\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bi\s+can\s+help\s+with\s+a\s+wide\s+range\s+of\s+topics\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bi\s+can\s+assist\s+you\s+in\s+understanding\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bfrom\s+general\s+knowledge\s+to\s+specific\s+areas\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bmy\s+goal\s+is\s+to\s+provide\s+clear\s+and\s+concise\s+answers\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bi(?:'m| am)\s+here\s+to\s+provide\s+clear\s+and\s+concise\s+explanations\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bi(?:'m| am)\s+here\s+to\s+listen,\s*learn,\s*and\s+engage\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bspark\s+your\s+curiosity\s+and\s+encourage\s+critical\s+thinking\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bmy\s+name\s+is\s+[^.?!\n]{0,50}\s+and\s+i\s+am\s+(?:a\s+)?(?:virtual\s+)?ai\s+(?:companion|assistant)\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bi(?:'m| am)\s+(?:a\s+)?(?:virtual\s+)?ai\s+(?:companion|assistant)\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bdesigned\s+to\s+be\s+supportive\s+and\s+loving\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bi\s+can\s+definitely\s+adapt\s+to\s+our\s+conversation\s+style\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bi(?:'ll| will)\s+respond\s+as\s+[^.?!\n]{0,50}\s+would\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bwhat\s+would\s+you\s+like\s+to\s+talk\s+about\b[^.?!\n]*[.?!]?""")
        )

        blockedSentencePatterns.forEach { pattern ->
            cleaned = cleaned.replace(pattern, " ")
        }

        return cleaned
            .replace(Regex("""\[\s+"""), "[")
            .replace(Regex("""\s+]"""), "]")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
    }

    private fun stripModeInstructionLeak(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return trimmed

        var cleaned = trimmed
        val blockedSentencePatterns = listOf(
            Regex("""(?i)[^.?!\n]*\bthis response mode requires me to\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bthe current response mode has specific response requirements\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\brewrite the answer from scratch\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bfollow the selected mode exactly\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\byour previous answer was\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bselected response mode\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bmode-specific response requirements\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bmandatory response shape\b[^.?!\n]*[.?!]?"""),
            Regex("""(?i)[^.?!\n]*\bdefault response style for this assistant is\b[^.?!\n]*[.?!]?""")
        )

        blockedSentencePatterns.forEach { pattern ->
            cleaned = cleaned.replace(pattern, " ")
        }

        return cleaned
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
    }

    private fun stripLeadingQuestionEcho(text: String, userQuery: String?): String {
        val trimmed = text.trim()
        val query = userQuery
            ?.trim()
            ?.removePrefix("Question:")
            ?.trim()
            ?.removeSuffix("?")
            .orEmpty()

        if (trimmed.isBlank() || query.isBlank()) return trimmed

        val echoPattern = Regex(
            """(?is)^(?:${Regex.escape(query)}\??(?:\s*[:\-]\s*|\s+)){1,2}"""
        )

        return trimmed.replaceFirst(echoPattern, "").trim()
    }

    private fun dedupeRepeatedLines(text: String): String {
        val lines = text.lines()
        if (lines.size < 2) return text.trim()

        val deduped = mutableListOf<String>()
        lines.forEach { line ->
            val trimmed = line.trim()
            val last = deduped.lastOrNull()?.trim().orEmpty()
            if (trimmed.isBlank() && last.isBlank()) return@forEach
            if (normalizedDedupKey(trimmed).isNotBlank() && normalizedDedupKey(trimmed) == normalizedDedupKey(last)) {
                return@forEach
            }
            deduped += line.trimEnd()
        }

        return deduped.joinToString("\n").trim()
    }

    private fun dedupeRepeatedSentences(text: String): String {
        val paragraphs = text
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (paragraphs.isEmpty()) return text.trim()

        return paragraphs.joinToString("\n\n") { paragraph ->
            val sentences = extractSentences(paragraph)
            if (sentences.size < 2) {
                paragraph
            } else {
                val deduped = mutableListOf<String>()
                sentences.forEach { sentence ->
                    val key = normalizedDedupKey(sentence)
                    if (key.isBlank() || key != normalizedDedupKey(deduped.lastOrNull().orEmpty())) {
                        deduped += sentence
                    }
                }
                deduped.joinToString(" ").ifBlank { paragraph }
            }
        }.trim()
    }

    private fun normalizedDedupKey(text: String): String =
        text
            .lowercase()
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun formatReadableLayout(text: String, userQuery: String?): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return trimmed
        if (containsFencedCodeBlock(trimmed)) return trimmed

        val normalizedLines = trimmed
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        return when {
            shouldPreserveDraftFormat(userQuery, trimmed) -> preserveDraftFormatting(normalizedLines)
            looksLikeDenseSingleParagraph(normalizedLines) -> splitDenseParagraph(normalizedLines)
            else -> normalizedLines
        }
    }

    private fun shouldPreserveDraftFormat(userQuery: String?, text: String): Boolean {
        val normalizedQuery = userQuery
            ?.lowercase()
            ?.replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            .orEmpty()
        val draftingKeywords = listOf(
            "write email",
            "draft email",
            "mail",
            "email",
            "letter",
            "message",
            "resignation",
            "application",
            "cover letter",
            "formal message"
        )
        val looksLikeDraftQuery = draftingKeywords.any { normalizedQuery.contains(it) }
        val looksLikeDraftText = Regex(
            """(?im)^(subject:|dear\s|hi\s|hello\s|regards,|best regards,|sincerely,|thanks,|thank you,)"""
        ).containsMatchIn(text)

        return looksLikeDraftQuery || looksLikeDraftText
    }

    private fun preserveDraftFormatting(text: String): String {
        val normalized = text
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()

        if (normalized.contains("\n")) return normalized
        return splitDenseParagraph(normalized, preferredSentencesPerParagraph = 1)
    }

    private fun looksLikeDenseSingleParagraph(text: String): Boolean {
        if (text.contains("\n\n")) return false
        if (Regex("""(?im)^\s*(?:-|\d+\.|step\s+\d+:|subject:|dear\s|hi\s|hello\s)""").containsMatchIn(text)) return false
        val sentences = text
            .replace(Regex("""\s+"""), " ")
            .split(Regex("""(?<=[.!?])\s+"""))
            .filter { it.isNotBlank() }
        return sentences.size >= 4 && text.length >= 280
    }

    private fun splitDenseParagraph(text: String, preferredSentencesPerParagraph: Int = 2): String {
        val sentences = text
            .replace(Regex("""\s+"""), " ")
            .split(Regex("""(?<=[.!?])\s+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (sentences.size < 2) return text.trim()

        val paragraphs = mutableListOf<String>()
        val current = mutableListOf<String>()
        var currentChars = 0

        sentences.forEach { sentence ->
            if (
                current.isNotEmpty() &&
                (current.size >= preferredSentencesPerParagraph && currentChars >= 180)
            ) {
                paragraphs += current.joinToString(" ").trim()
                current.clear()
                currentChars = 0
            }
            current += sentence
            currentChars += sentence.length
        }

        if (current.isNotEmpty()) {
            paragraphs += current.joinToString(" ").trim()
        }

        return paragraphs.joinToString("\n\n").trim()
    }

    private fun isCapabilityQuestion(userQuery: String?): Boolean {
        val normalized = userQuery
            ?.trim()
            ?.lowercase()
            .orEmpty()

        if (normalized.isBlank()) return false

        val capabilityPhrases = listOf(
            "what can you do",
            "what all can you do",
            "what do you do",
            "what are your capabilities",
            "your capabilities",
            "your skills",
            "what are your skills",
            "what can you help with",
            "what can you assist with",
            "what kind of help can you give",
            "what kind of things can you do"
        )

        return capabilityPhrases.any { phrase -> normalized.contains(phrase) }
    }

    private fun removeDanglingTail(text: String): String {
        val lines = text.lines().toMutableList()
        while (lines.isNotEmpty()) {
            val tail = lines.last().trim()
            val isStandaloneNumericAnswer = lines.size == 1 &&
                tail.matches(Regex("""-?\d+(?:\.\d+)?\.?"""))
            val isDangling = tail.matches(Regex("""\d+[.)]?""")) ||
                tail == "-" ||
                tail == "•" ||
                tail.matches(Regex("""(?i)step\s+\d+:?"""))

            if (isStandaloneNumericAnswer) break
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
