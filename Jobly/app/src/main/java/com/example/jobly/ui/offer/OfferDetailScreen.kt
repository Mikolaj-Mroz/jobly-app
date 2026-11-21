package com.example.jobly.ui.offer

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.example.jobly.R
import com.example.jobly.data.repository.SampleSeedData
import com.example.jobly.model.JobOffer
import com.example.jobly.ui.common.MarkdownText
import com.example.jobly.ui.common.OfferAvatar
import com.example.jobly.ui.theme.AccentYellow
import com.example.jobly.ui.theme.CanvasGray
import com.example.jobly.ui.theme.CardAccentPalette
import com.example.jobly.ui.theme.CardSurface
import com.example.jobly.ui.theme.JoblyTheme
import com.example.jobly.ui.theme.OnPrimaryContainer
import com.example.jobly.ui.theme.OnSurface
import com.example.jobly.ui.theme.OnSurfaceVariant
import com.example.jobly.ui.theme.OutlineVariant
import com.example.jobly.ui.theme.SalaryGreen
import com.example.jobly.ui.theme.SurfaceWhite
import com.example.jobly.ui.theme.TopBarDivider

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OfferDetailScreen(
    offer: JobOffer,
    alreadyApplied: Boolean,
    hasWebsiteUrl: Boolean,
    onBack: () -> Unit,
    onApplyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = CardAccentPalette.forAccent(offer.accent)
    val formattedSalary = listOf(offer.salaryRange, offer.salaryUnit)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    val sourceHost = offer.externalUrl?.let { url ->
        runCatching { Uri.parse(url).host }.getOrNull()
    }.orEmpty()
    val buttonLabel = when {
        hasWebsiteUrl && alreadyApplied -> "Open original website"
        hasWebsiteUrl -> "Apply on website"
        alreadyApplied -> "Already applied"
        else -> "Apply now"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CanvasGray,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceWhite)
                    .statusBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onBack)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_profile_arrow_right),
                            contentDescription = "Back",
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(180f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Back",
                            color = OnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = "Job Offer",
                        color = OnSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = TopBarDivider)
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceWhite)
                    .navigationBarsPadding()
            ) {
                HorizontalDivider(thickness = 0.5.dp, color = TopBarDivider)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = onApplyClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentYellow,
                            contentColor = OnPrimaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = buttonLabel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.borderColor)
                    .padding(top = 5.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardSurface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OfferAvatar(initial = offer.initial, backgroundColor = palette.avatarColor)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = offer.title,
                                color = OnSurface,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 24.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = offer.companyName,
                                color = OnSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (formattedSalary.isNotBlank()) {
                        Text(
                            text = formattedSalary,
                            color = SalaryGreen,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetaBadge(offer.level)
                        MetaBadge(offer.contractType)
                        MetaBadge(offer.workSchedule)
                        if (alreadyApplied) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFE6F4EA))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Applied",
                                    color = SalaryGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (sourceHost.isNotBlank()) {
                        Text(
                            text = "Original listing: $sourceHost",
                            color = OnSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Match explanation card
            if (offer.matchScore != null || offer.matchReasons.isNotEmpty()) {
                DetailSectionCard(title = "Why this matches you") {
                    offer.matchScore?.let { score ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE6F4EA))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$score% match",
                                color = SalaryGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    offer.matchReasons.forEach { reason ->
                        Text(
                            text = "• $reason",
                            color = OnSurface,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }

            // Required skills card
            if (offer.skills.isNotEmpty()) {
                DetailSectionCard(title = "Skills") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        offer.skills.forEach { skill ->
                            Box(
                                modifier = Modifier
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.chipBrush)
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = skill,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Full description card
            if (offer.description.isNotBlank()) {
                val formattedDescription = formatOfferDescriptionMarkdown(
                    description = offer.description,
                    hideOfferUrl = hasWebsiteUrl
                )
                if (formattedDescription.isNotBlank()) {
                    DetailSectionCard(title = "About this role") {
                        MarkdownText(
                            markdown = formattedDescription,
                            color = OnSurface,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }
                }
            }
        }
    }
}

private val CATEGORY_DISPLAY_NAMES = mapOf(
    "pm" to "PM",
    "qa" to "QA",
    "ai" to "AI",
    "devops" to "DevOps",
    "ux" to "UX",
    "ui" to "UI",
    "ui/ux" to "UI/UX",
    "net" to ".NET",
    "erp" to "ERP",
    "bi" to "BI",
    "c" to "C/C++",
    "js" to "JavaScript",
    "javascript" to "JavaScript",
    "typescript" to "TypeScript",
    "php" to "PHP",
    "html" to "HTML",
    "ios" to "iOS",
    "sap" to "SAP"
)

internal fun formatOfferDescriptionMarkdown(
    description: String,
    hideOfferUrl: Boolean = false
): String {
    val trimmed = description.trim()
    if (trimmed.isEmpty()) return ""

    val rawSegments = trimmed.split(Regex("\\s*\\|\\s*")).filter { it.isNotBlank() }
    val parsedPairs = rawSegments.mapNotNull { segment ->
        val colonIdx = segment.indexOf(':')
        if (colonIdx <= 0) {
            null
        } else {
            val key = segment.substring(0, colonIdx).trim()
            val value = segment.substring(colonIdx + 1).trim()
            if (key.isNotEmpty() && key.length <= 32 && value.isNotEmpty()) key to value else null
        }
    }

    // If not a pipe-delimited Key: Value summary, render as regular Markdown directly
    if (rawSegments.size < 2 || parsedPairs.size < 2) {
        return trimmed
    }

    val lines = mutableListOf<String>()
    for ((key, rawValue) in parsedPairs) {
        when (key.lowercase()) {
            "offer url", "url" -> {
                if (!hideOfferUrl) {
                    lines += "**Offer URL:** [$rawValue]($rawValue)"
                }
            }
            "workplace" -> {
                val cleaned = rawValue
                    .replace(Regex(",\\s*-\\s*\\)"), ")")
                    .replace(Regex("\\(\\s*-\\s*\\)"), "")
                    .trim()
                lines += "**Workplace:** $cleaned"
            }
            "category" -> {
                val formatted = CATEGORY_DISPLAY_NAMES[rawValue.lowercase()]
                    ?: rawValue.replaceFirstChar { it.uppercase() }
                lines += "**Category:** $formatted"
            }
            "languages" -> {
                val formattedLangs = rawValue
                    .split(",")
                    .mapNotNull { token ->
                        val parts = token.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                        when {
                            parts.isEmpty() -> null
                            parts.size == 1 -> parts[0].uppercase()
                            else -> "${parts[0].uppercase()} (${parts.drop(1).joinToString(" ").uppercase()})"
                        }
                    }
                    .joinToString(", ")
                if (formattedLangs.isNotBlank()) {
                    lines += "**Languages:** $formattedLangs"
                }
            }
            "required skills", "skills" -> {
                val skills = rawValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (skills.isNotEmpty()) {
                    if (lines.isNotEmpty()) lines += ""
                    lines += "**Required skills:**"
                    skills.forEach { skill ->
                        lines += "- $skill"
                    }
                }
            }
            else -> {
                lines += "**$key:** $rawValue"
            }
        }
    }

    return lines.joinToString("\n").trim()
}

@Composable
private fun DetailSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface)
            .border(1.dp, OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

@Composable
private fun MetaBadge(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CanvasGray)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = OnSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OfferDetailScreenPreview() {
    JoblyTheme {
        OfferDetailScreen(
            offer = SampleSeedData.sampleBestOffers.first().copy(
                externalUrl = "https://justjoin.it/job-offer/sample-qa",
                matchScore = 88,
                matchReasons = listOf("Skills: Kubernetes, Elk, Linux", "Mid role alignment", "Remote-friendly")
            ),
            alreadyApplied = false,
            hasWebsiteUrl = true,
            onBack = {},
            onApplyClick = {}
        )
    }
}
