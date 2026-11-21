package com.example.jobly.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jobly.ui.theme.OnSurface
import com.example.jobly.ui.theme.OnSurfaceVariant
import com.example.jobly.ui.theme.OutlineVariant

private val InlineCodeBg = Color(0xFFEDEDED)
private val CodeBlockBg = Color(0xFFF3F4F6)
private val LinkBlue = Color(0xFF1A73E8)

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class BulletItem(val indentLevel: Int, val text: String) : MarkdownBlock
    data class NumberedItem(val marker: String, val indentLevel: Int, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock
    data object Divider : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = OnSurface,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = 20.sp
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val headingSize = when (block.level) {
                        1 -> 17.sp
                        2 -> 16.sp
                        else -> 15.sp
                    }
                    Text(
                        text = parseInlineMarkdown(block.text, baseColor = color),
                        color = color,
                        fontSize = headingSize,
                        fontWeight = FontWeight.Bold,
                        lineHeight = headingSize * 1.3f
                    )
                }

                is MarkdownBlock.BulletItem -> {
                    val startPad = (block.indentLevel.coerceAtMost(3) * 12).dp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = startPad)
                    ) {
                        Text(
                            text = "•",
                            color = color,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            lineHeight = lineHeight
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = parseInlineMarkdown(block.text, baseColor = color),
                            color = color,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.NumberedItem -> {
                    val startPad = (block.indentLevel.coerceAtMost(3) * 12).dp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = startPad)
                    ) {
                        Text(
                            text = block.marker,
                            color = color,
                            fontSize = fontSize,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = lineHeight
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = parseInlineMarkdown(block.text, baseColor = color),
                            color = color,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.Quote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CodeBlockBg)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(18.dp)
                                .background(OutlineVariant)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseInlineMarkdown(block.text, baseColor = OnSurfaceVariant),
                            color = OnSurfaceVariant,
                            fontSize = fontSize,
                            fontStyle = FontStyle.Italic,
                            lineHeight = lineHeight,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CodeBlockBg)
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp)
                    ) {
                        Text(
                            text = block.code,
                            color = color,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp
                        )
                    }
                }

                is MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = OutlineVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.text, baseColor = color),
                        color = color,
                        fontSize = fontSize,
                        lineHeight = lineHeight
                    )
                }
            }
        }
    }
}

private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.+)$""")
private val BULLET_REGEX = Regex("""^(\s*)[-*+•]\s+(.+)$""")
private val NUMBERED_REGEX = Regex("""^(\s*)(\d+[.)])\s+(.+)$""")
private val QUOTE_REGEX = Regex("""^\s*>\s?(.*)$""")
private val DIVIDER_REGEX = Regex("""^\s*([-*_])\1{2,}\s*$""")

internal fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraphBuffer = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraphBuffer.joinToString("\n"))
            paragraphBuffer.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val rawLine = lines[i]
        val trimmed = rawLine.trim()

        if (trimmed.startsWith("```")) {
            flushParagraph()
            val lang = trimmed.removePrefix("```").trim().ifEmpty { null }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines += lines[i]
                i++
            }
            blocks += MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), lang)
            i++
            continue
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
            i++
            continue
        }

        if (DIVIDER_REGEX.matches(trimmed)) {
            flushParagraph()
            blocks += MarkdownBlock.Divider
            i++
            continue
        }

        val headingMatch = HEADING_REGEX.matchEntire(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            blocks += MarkdownBlock.Heading(
                level = headingMatch.groupValues[1].length,
                text = headingMatch.groupValues[2].trim()
            )
            i++
            continue
        }

        val bulletMatch = BULLET_REGEX.matchEntire(rawLine)
        if (bulletMatch != null) {
            flushParagraph()
            val indentSpaces = bulletMatch.groupValues[1].length
            blocks += MarkdownBlock.BulletItem(
                indentLevel = indentSpaces / 2,
                text = bulletMatch.groupValues[2].trim()
            )
            i++
            continue
        }

        val numberedMatch = NUMBERED_REGEX.matchEntire(rawLine)
        if (numberedMatch != null) {
            flushParagraph()
            val indentSpaces = numberedMatch.groupValues[1].length
            blocks += MarkdownBlock.NumberedItem(
                marker = numberedMatch.groupValues[2],
                indentLevel = indentSpaces / 2,
                text = numberedMatch.groupValues[3].trim()
            )
            i++
            continue
        }

        val quoteMatch = QUOTE_REGEX.matchEntire(rawLine)
        if (quoteMatch != null) {
            flushParagraph()
            val quoteLines = mutableListOf(quoteMatch.groupValues[1].trim())
            while (i + 1 < lines.size) {
                val nextQuote = QUOTE_REGEX.matchEntire(lines[i + 1]) ?: break
                quoteLines += nextQuote.groupValues[1].trim()
                i++
            }
            blocks += MarkdownBlock.Quote(quoteLines.joinToString("\n"))
            i++
            continue
        }

        paragraphBuffer += trimmed
        i++
    }

    flushParagraph()
    return blocks
}

internal fun parseInlineMarkdown(
    text: String,
    baseColor: Color = OnSurface
): AnnotatedString = buildAnnotatedString {
    appendInlineTokens(this, text, baseColor)
}

private fun appendInlineTokens(
    builder: AnnotatedString.Builder,
    text: String,
    baseColor: Color
) {
    var i = 0
    val n = text.length
    while (i < n) {
        // Markdown link: [label](https://...)
        if (text[i] == '[') {
            val closeBracket = text.indexOf(']', startIndex = i + 1)
            if (closeBracket > i + 1 && closeBracket + 1 < n && text[closeBracket + 1] == '(') {
                val closeParen = text.indexOf(')', startIndex = closeBracket + 2)
                if (closeParen > closeBracket + 2) {
                    val label = text.substring(i + 1, closeBracket)
                    val url = text.substring(closeBracket + 2, closeParen).trim()
                    if (url.startsWith("https://", ignoreCase = true) ||
                        url.startsWith("http://", ignoreCase = true)
                    ) {
                        val linkStyle = TextLinkStyles(
                            style = SpanStyle(
                                color = LinkBlue,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                        builder.pushLink(LinkAnnotation.Url(url = url, styles = linkStyle))
                        appendInlineTokens(builder, label, LinkBlue)
                        builder.pop()
                        i = closeParen + 1
                        continue
                    }
                }
            }
        }

        // Inline code: `code`
        if (text[i] == '`') {
            val end = text.indexOf('`', startIndex = i + 1)
            if (end > i + 1) {
                val startOffset = builder.length
                builder.append(text.substring(i + 1, end))
                builder.addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = InlineCodeBg,
                        color = baseColor
                    ),
                    startOffset,
                    builder.length
                )
                i = end + 1
                continue
            }
        }

        // Bold + italic: ***text***
        if (text.startsWith("***", i)) {
            val end = text.indexOf("***", startIndex = i + 3)
            if (end > i + 3) {
                val startOffset = builder.length
                appendInlineTokens(builder, text.substring(i + 3, end), baseColor)
                builder.addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                    startOffset,
                    builder.length
                )
                i = end + 3
                continue
            }
        }

        // Bold: **text** or __text__
        if (text.startsWith("**", i) || text.startsWith("__", i)) {
            val delim = text.substring(i, i + 2)
            val end = text.indexOf(delim, startIndex = i + 2)
            if (end > i + 2) {
                val startOffset = builder.length
                appendInlineTokens(builder, text.substring(i + 2, end), baseColor)
                builder.addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    startOffset,
                    builder.length
                )
                i = end + 2
                continue
            }
        }

        // Strikethrough: ~~text~~
        if (text.startsWith("~~", i)) {
            val end = text.indexOf("~~", startIndex = i + 2)
            if (end > i + 2) {
                val startOffset = builder.length
                appendInlineTokens(builder, text.substring(i + 2, end), baseColor)
                builder.addStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough),
                    startOffset,
                    builder.length
                )
                i = end + 2
                continue
            }
        }

        // Italic: *text* or _text_ (avoid matching snake_case identifiers)
        if (text[i] == '*' || text[i] == '_') {
            val ch = text[i]
            val prevIsWord = i > 0 && text[i - 1].isLetterOrDigit()
            if (!(ch == '_' && prevIsWord)) {
                val end = findClosingSingleDelimiter(text, ch, i + 1)
                if (end > i + 1) {
                    val startOffset = builder.length
                    appendInlineTokens(builder, text.substring(i + 1, end), baseColor)
                    builder.addStyle(
                        SpanStyle(fontStyle = FontStyle.Italic),
                        startOffset,
                        builder.length
                    )
                    i = end + 1
                    continue
                }
            }
        }

        builder.append(text[i])
        i++
    }
}

private fun findClosingSingleDelimiter(text: String, delim: Char, startIndex: Int): Int {
    var idx = startIndex
    while (idx < text.length) {
        if (text[idx] == delim) {
            val nextIsSame = idx + 1 < text.length && text[idx + 1] == delim
            val nextIsWord = idx + 1 < text.length && text[idx + 1].isLetterOrDigit()
            val prevIsSpace = text[idx - 1].isWhitespace()
            if (!nextIsSame && !prevIsSpace && !(delim == '_' && nextIsWord)) {
                return idx
            }
        }
        idx++
    }
    return -1
}
