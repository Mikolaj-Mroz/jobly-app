package com.example.jobly.ui.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jobly.R
import com.example.jobly.ui.settings.ChoiceChipGroup
import com.example.jobly.ui.settings.SecondaryBackTopBar
import com.example.jobly.ui.theme.AccentYellow
import com.example.jobly.ui.theme.BadgeError
import com.example.jobly.ui.theme.CanvasGray
import com.example.jobly.ui.theme.CardSurface
import com.example.jobly.ui.theme.JoblyTheme
import com.example.jobly.ui.theme.OnPrimaryContainer
import com.example.jobly.ui.theme.OnSurface
import com.example.jobly.ui.theme.OnSurfaceVariant
import com.example.jobly.ui.theme.OutlineVariant
import com.example.jobly.ui.theme.SalaryGreen

private data class FaqEntry(val question: String, val answer: String)

private val HELP_FAQS = listOf(
    FaqEntry(
        question = "What limits apply when uploading a PDF CV?",
        answer = "You can upload valid, unencrypted PDF files up to 5 MB and 10 pages, and store up to 10 CVs at once."
    ),
    FaqEntry(
        question = "Does the AI Assistant really edit my PDF?",
        answer = "Yes. Asking the Assistant to add skills, update experience, or rewrite your summary edits your CV data and regenerates the real PDF file on the server."
    ),
    FaqEntry(
        question = "How does Best for you rank jobs?",
        answer = "The recommendation engine combines skill matches (including aliases like K8s and Kubernetes), TF-IDF similarity, seniority alignment, distance/remote fit, and your application history."
    ),
    FaqEntry(
        question = "What happens when I tap Apply on an offer?",
        answer = "Jobly saves the application to your My applications history and opens the original job listing URL in your browser."
    )
)

@Composable
fun HelpFeedbackScreen(
    defaultEmail: String,
    onBack: () -> Unit,
    onOpenAssistant: () -> Unit,
    onSubmitFeedback: (
        category: String,
        message: String,
        contactEmail: String?,
        onDone: (Result<String>) -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedFaqIdx by remember { mutableStateOf<Int?>(0) }
    var category by remember { mutableStateOf("General") }
    var feedbackMessage by remember { mutableStateOf("") }
    var contactEmail by remember(defaultEmail) { mutableStateOf(defaultEmail) }
    var isSubmitting by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CanvasGray,
        topBar = { SecondaryBackTopBar(title = "Help & feedback", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // FAQ Card
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
                    text = "Frequently Asked Questions",
                    color = OnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                HELP_FAQS.forEachIndexed { idx, faq ->
                    val expanded = expandedFaqIdx == idx
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { expandedFaqIdx = if (expanded) null else idx }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = faq.question,
                                color = OnSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Image(
                                painter = painterResource(R.drawable.ic_profile_arrow_right),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(14.dp)
                                    .rotate(if (expanded) 90f else 0f)
                            )
                        }
                        AnimatedVisibility(visible = expanded) {
                            Text(
                                text = faq.answer,
                                color = OnSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                    if (idx < HELP_FAQS.lastIndex) {
                        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f))
                    }
                }
            }

            // Send Feedback Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardSurface)
                    .border(1.dp, OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Send Feedback",
                    color = OnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                ChoiceChipGroup(
                    options = listOf("General", "Bug", "Feature"),
                    selected = category,
                    onSelect = { category = it }
                )

                OutlinedTextField(
                    value = feedbackMessage,
                    onValueChange = {
                        feedbackMessage = it
                        statusBanner = null
                    },
                    label = { Text("Tell us what's working or what to improve") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = contactEmail,
                    onValueChange = { contactEmail = it },
                    label = { Text("Reply email (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                statusBanner?.let { (isOk, msg) ->
                    Text(
                        text = msg,
                        color = if (isOk) SalaryGreen else BadgeError,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isOk) Color(0xFFE6F4EA) else Color(0xFFFCE8E6))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }

                Button(
                    onClick = {
                        val trimmed = feedbackMessage.trim()
                        if (trimmed.isEmpty()) {
                            statusBanner = false to "Please write a feedback message first."
                            return@Button
                        }
                        isSubmitting = true
                        onSubmitFeedback(category, trimmed, contactEmail.trim()) { result ->
                            isSubmitting = false
                            result.onSuccess { receipt ->
                                feedbackMessage = ""
                                statusBanner = true to "Thanks! Feedback ($receipt) sent to server."
                            }.onFailure { err ->
                                statusBanner = false to (err.message ?: "Failed to submit feedback")
                            }
                        }
                    },
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentYellow,
                        contentColor = OnPrimaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isSubmitting) "Sending..." else "Send feedback",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            // Quick Assistant Shortcut
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
                    text = "Need CV or job search advice?",
                    color = OnSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(
                    onClick = onOpenAssistant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ask Jobly AI Assistant", fontSize = 13.sp, color = OnSurface)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HelpFeedbackScreenPreview() {
    JoblyTheme {
        HelpFeedbackScreen(
            defaultEmail = "jworkman@mail.com",
            onBack = {},
            onOpenAssistant = {},
            onSubmitFeedback = { _, _, _, _ -> }
        )
    }
}
