package com.example.jobly

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.example.jobly.ui.common.MarkdownBlock
import com.example.jobly.ui.common.parseInlineMarkdown
import com.example.jobly.ui.common.parseMarkdownBlocks
import com.example.jobly.ui.offer.formatOfferDescriptionMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun parseMarkdownBlocks_parsesHeadingsListsCodeQuotesAndDividers() {
        val sample = """
            ## Top Matches
            1. **Quality Assurance Engineer** - *IT DEV COMPANY*
              - **Salary:** 16,800 - 22,680 PLN/month
              - **Skills:** Kubernetes, ELK, Linux
            ---
            > Tip: tailor your summary first.
            ```python
            def greet():
                return "hi"
            ```
            Good luck!
        """.trimIndent()

        val blocks = parseMarkdownBlocks(sample)
        assertTrue(blocks[0] is MarkdownBlock.Heading)
        assertEquals(2, (blocks[0] as MarkdownBlock.Heading).level)
        assertEquals("Top Matches", (blocks[0] as MarkdownBlock.Heading).text)

        assertTrue(blocks[1] is MarkdownBlock.NumberedItem)
        assertEquals("1.", (blocks[1] as MarkdownBlock.NumberedItem).marker)

        assertTrue(blocks[2] is MarkdownBlock.BulletItem)
        assertEquals(1, (blocks[2] as MarkdownBlock.BulletItem).indentLevel)

        assertTrue(blocks[3] is MarkdownBlock.BulletItem)
        assertTrue(blocks[4] is MarkdownBlock.Divider)
        assertTrue(blocks[5] is MarkdownBlock.Quote)
        assertEquals("Tip: tailor your summary first.", (blocks[5] as MarkdownBlock.Quote).text)

        assertTrue(blocks[6] is MarkdownBlock.CodeBlock)
        assertEquals("python", (blocks[6] as MarkdownBlock.CodeBlock).language)
        assertTrue((blocks[6] as MarkdownBlock.CodeBlock).code.contains("def greet():"))

        assertTrue(blocks[7] is MarkdownBlock.Paragraph)
        assertEquals("Good luck!", (blocks[7] as MarkdownBlock.Paragraph).text)
    }

    @Test
    fun parseInlineMarkdown_appliesStylesStripsDelimitersAndPreservesSnakeCase() {
        val rendered = parseInlineMarkdown(
            "**Quality Assurance** - *IT DEV* with `update_cv`, ~~old~~, ***top***, my_cv_file, and [JustJoin](https://justjoin.it)"
        )
        val plain = rendered.text
        assertEquals(
            "Quality Assurance - IT DEV with update_cv, old, top, my_cv_file, and JustJoin",
            plain
        )
        assertFalse(plain.contains("**"))
        assertFalse(plain.contains("~~"))
        assertFalse(plain.contains("`"))

        assertTrue(rendered.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(rendered.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(rendered.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
        assertTrue(rendered.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough })
        assertTrue(rendered.getLinkAnnotations(0, plain.length).isNotEmpty())
    }

    @Test
    fun formatOfferDescriptionMarkdown_formatsPipeDelimitedOfferSummaryAndPreservesMarkdown() {
        val raw = "Workplace: Hybrid (Warszawa, -) | Category: devops | Required skills: AWS (4/5), Docker (3/5) | Languages: pl C1, en B2 | Offer URL: https://justjoin.it/job-offer/sample"
        val formatted = formatOfferDescriptionMarkdown(raw, hideOfferUrl = true)

        assertTrue(formatted.contains("**Workplace:** Hybrid (Warszawa)"))
        assertTrue(formatted.contains("**Category:** DevOps"))
        assertTrue(formatted.contains("**Required skills:**\n- AWS (4/5)\n- Docker (3/5)"))
        assertTrue(formatted.contains("**Languages:** PL (C1), EN (B2)"))
        assertFalse(formatted.contains("Offer URL"))

        val plainMarkdown = "### Role overview\n- Build **Android** apps"
        assertEquals(plainMarkdown, formatOfferDescriptionMarkdown(plainMarkdown, hideOfferUrl = true))
    }
}
