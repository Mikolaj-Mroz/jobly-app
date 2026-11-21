package com.example.jobly.ui.assistant

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jobly.R
import com.example.jobly.model.ChatMessage
import com.example.jobly.model.CvItem
import com.example.jobly.model.JobOffer
import com.example.jobly.ui.common.MarkdownText
import com.example.jobly.ui.theme.AccentYellow
import com.example.jobly.ui.theme.BadgeError
import com.example.jobly.ui.theme.CanvasGray
import com.example.jobly.ui.theme.ChatBoxBg
import com.example.jobly.ui.theme.JoblyTheme
import com.example.jobly.ui.theme.OnSurface
import com.example.jobly.ui.theme.OnSurfaceVariant
import com.example.jobly.ui.theme.OutlineVariant
import com.example.jobly.ui.theme.SalaryGreen
import com.example.jobly.ui.theme.SecondaryContainer
import com.example.jobly.ui.theme.SurfaceWhite

private val ErrorBubbleBg = Color(0xFFFCE8E6)

@Composable
fun AssistantScreen(
    messages: List<ChatMessage>,
    isSending: Boolean,
    onSendPrompt: (String) -> Unit,
    activeCvName: String,
    cvItems: List<CvItem> = emptyList(),
    activeCvId: String? = null,
    onSelectCv: (CvItem) -> Unit = {},
    onCreateEmptyCvClick: () -> Unit = {},
    onUploadPdfClick: () -> Unit = {},
    onPreviewCvClick: (CvItem) -> Unit = {},
    onOfferClick: (JobOffer) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var promptText by rememberSaveable { mutableStateOf("") }
    var showCvMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isSending) {
        val lastIndex = messages.size + (if (isSending) 1 else 0) - 1
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    val submitInput = {
        val trimmed = promptText.trim()
        if (trimmed.isNotEmpty() && !isSending) {
            onSendPrompt(trimmed)
            promptText = ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasGray)
            .imePadding()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && !isSending) {
                Text(
                    text = stringResource(R.string.assistant_greeting),
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubble(
                            message = msg,
                            onPreviewCvClick = onPreviewCvClick,
                            onOfferClick = onOfferClick
                        )
                    }
                    if (isSending) {
                        item(key = "assistant_thinking") {
                            ThinkingBubble()
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
        ) {
            QuickSuggestionRow(
                iconRes = R.drawable.ic_assistant_cv,
                iconWidth = 12.dp,
                iconHeight = 16.dp,
                gapStart = 14.dp,
                label = stringResource(R.string.assistant_edit_cv),
                enabled = !isSending,
                onClick = { onSendPrompt("Edit my CV") }
            )

            Spacer(modifier = Modifier.height(4.dp))

            QuickSuggestionRow(
                iconRes = R.drawable.ic_assistant_search,
                iconWidth = 18.dp,
                iconHeight = 18.dp,
                gapStart = 11.dp,
                label = stringResource(R.string.assistant_find_job),
                enabled = !isSending,
                onClick = { onSendPrompt("Find me a job") }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ChatBoxBg)
                .defaultMinSize(minHeight = 77.dp)
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp)
        ) {
            BasicTextField(
                value = promptText,
                onValueChange = { promptText = it },
                textStyle = TextStyle(
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(Color.Black),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { submitInput() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                decorationBox = { innerTextField ->
                    if (promptText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.assistant_ask_hint),
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(
                        onClick = { showCvMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_chat_add),
                            contentDescription = stringResource(R.string.cd_attach_file),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showCvMenu,
                        onDismissRequest = { showCvMenu = false },
                        containerColor = SurfaceWhite
                    ) {
                        if (cvItems.isNotEmpty()) {
                            cvItems.forEach { cv ->
                                val isSelected = cv.id == activeCvId ||
                                    (activeCvId == null && cv.fileName == activeCvName)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isSelected) "✓ ${cv.fileName}" else cv.fileName,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = OnSurface,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    onClick = {
                                        showCvMenu = false
                                        onSelectCv(cv)
                                        Toast.makeText(
                                            context,
                                            "Active CV: ${cv.fileName}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.6f))
                        }

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "+ New empty CV",
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnSurface,
                                    fontSize = 13.sp
                                )
                            },
                            onClick = {
                                showCvMenu = false
                                onCreateEmptyCvClick()
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Upload PDF CV...",
                                    color = OnSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            },
                            onClick = {
                                showCvMenu = false
                                onUploadPdfClick()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = activeCvName,
                    color = OnSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        Toast.makeText(context, "Listening for voice prompt...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_chat_mic),
                        contentDescription = stringResource(R.string.cd_voice_input),
                        modifier = Modifier.size(width = 14.dp, height = 19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (isSending) return@IconButton
                        if (promptText.trim().isNotEmpty()) {
                            submitInput()
                        } else {
                            Toast.makeText(context, "Type a message or pick an offer action", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isSending,
                    modifier = Modifier
                        .size(28.dp)
                        .alpha(if (isSending) 0.5f else 1f)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_chat_voice),
                        contentDescription = stringResource(R.string.cd_send_or_voice),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSuggestionRow(
    iconRes: Int,
    iconWidth: Dp,
    iconHeight: Dp,
    gapStart: Dp,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(width = iconWidth, height = iconHeight)
        )
        Spacer(modifier = Modifier.width(gapStart))
        Text(
            text = label,
            color = Color.Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onPreviewCvClick: (CvItem) -> Unit,
    onOfferClick: (JobOffer) -> Unit
) {
    val bgColor = when {
        message.isUser -> SecondaryContainer
        message.isError -> ErrorBubbleBg
        else -> SurfaceWhite
    }
    val textColor = if (message.isError) BadgeError else OnSurface

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = 310.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (message.isUser || message.isError) {
                    Text(
                        text = message.text,
                        color = textColor,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                } else {
                    MarkdownText(
                        markdown = message.text,
                        color = textColor,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            message.updatedCv?.let { updatedCv ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceWhite)
                        .border(1.dp, AccentYellow, RoundedCornerShape(10.dp))
                        .clickable { onPreviewCvClick(updatedCv) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_assistant_cv),
                        contentDescription = null,
                        modifier = Modifier.size(width = 12.dp, height = 16.dp)
                    )
                    Text(
                        text = "PDF updated: ${updatedCv.fileName} • View PDF",
                        color = OnSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (message.suggestedOffers.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    message.suggestedOffers.take(3).forEach { offer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceWhite)
                                .border(1.dp, OutlineVariant, RoundedCornerShape(10.dp))
                                .clickable { onOfferClick(offer) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = offer.title,
                                    color = OnSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sub = buildString {
                                    append(offer.companyName)
                                    if (offer.salaryRange.isNotBlank()) {
                                        append(" • ")
                                        append(offer.salaryRange)
                                    }
                                }
                                Text(
                                    text = sub,
                                    color = OnSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            offer.matchScore?.let { score ->
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$score%",
                                    color = SalaryGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceWhite)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Thinking...",
                color = OnSurfaceVariant,
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AssistantScreenPreview() {
    JoblyTheme {
        AssistantScreen(
            messages = emptyList(),
            isSending = false,
            onSendPrompt = {},
            activeCvName = "My-new-cv.pdf"
        )
    }
}
