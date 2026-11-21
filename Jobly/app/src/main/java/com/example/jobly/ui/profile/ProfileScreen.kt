package com.example.jobly.ui.profile

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jobly.R
import com.example.jobly.data.repository.SampleSeedData
import com.example.jobly.model.CvItem
import com.example.jobly.model.JobOffer
import com.example.jobly.model.UserProfile
import com.example.jobly.ui.common.BottomAccentCard
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

@Composable
fun ProfileScreen(
    profile: UserProfile,
    appliedOffers: List<JobOffer>,
    cvItems: List<CvItem> = emptyList(),
    isUploadingCv: Boolean = false,
    onUpdateProfile: (name: String, phone: String, email: String) -> Unit,
    onOfferClick: (JobOffer) -> Unit = {},
    onCvClick: (CvItem) -> Unit = {},
    onPreviewCv: (CvItem) -> Unit = {},
    onDeleteCv: (CvItem) -> Unit = {},
    onUploadPdfClick: () -> Unit = {},
    onCreateBlankCvClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var applicationsExpanded by rememberSaveable { mutableStateOf(false) }
    var cvsExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasGray)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(92.dp)) {
                Image(
                    painter = painterResource(R.drawable.img_profile_avatar),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(89.dp)
                        .clip(CircleShape)
                )

                Box(
                    modifier = Modifier
                        .size(33.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .clickable {
                            Toast.makeText(context, "Profile photo updated", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_profile_camera_bg),
                        contentDescription = stringResource(R.string.cd_change_avatar),
                        modifier = Modifier.fillMaxSize()
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_profile_camera),
                        contentDescription = null,
                        modifier = Modifier.size(width = 20.dp, height = 18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column {
                Text(
                    text = profile.name,
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp
                )
                Text(
                    text = profile.phone,
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp
                )
                Text(
                    text = profile.email,
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            IconButton(
                onClick = { showEditDialog = true },
                modifier = Modifier.size(36.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_profile_edit),
                    contentDescription = stringResource(R.string.cd_edit_profile),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // My applications expandable section
        ProfileActionCard(
            iconRes = R.drawable.ic_profile_applications,
            iconWidth = 40.dp,
            iconHeight = 34.dp,
            horizontalPadding = 20.dp,
            title = "${stringResource(R.string.profile_my_applications)} (${appliedOffers.size})",
            subtitle = stringResource(R.string.profile_check_history),
            expanded = applicationsExpanded,
            onClick = { applicationsExpanded = !applicationsExpanded }
        )

        AnimatedVisibility(visible = applicationsExpanded) {
            InlineApplicationsList(
                appliedOffers = appliedOffers,
                onOfferClick = onOfferClick
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // My CVs expandable section
        ProfileActionCard(
            iconRes = R.drawable.ic_profile_cvs,
            iconWidth = 32.dp,
            iconHeight = 40.dp,
            horizontalPadding = 24.dp,
            title = "${stringResource(R.string.profile_my_cvs)} (${cvItems.size} / 10)",
            subtitle = stringResource(R.string.profile_manage_cvs),
            expanded = cvsExpanded,
            onClick = { cvsExpanded = !cvsExpanded }
        )

        AnimatedVisibility(visible = cvsExpanded) {
            InlineCvsList(
                cvItems = cvItems,
                isUploadingCv = isUploadingCv,
                onCvClick = onCvClick,
                onPreviewCv = onPreviewCv,
                onDeleteCv = onDeleteCv,
                onUploadPdfClick = onUploadPdfClick,
                onCreateBlankCvClick = onCreateBlankCvClick
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showEditDialog) {
        EditProfileDialog(
            profile = profile,
            onDismiss = { showEditDialog = false },
            onSave = { name, phone, email ->
                onUpdateProfile(name, phone, email)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun InlineApplicationsList(
    appliedOffers: List<JobOffer>,
    onOfferClick: (JobOffer) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, start = 4.dp, end = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface)
            .border(1.dp, OutlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (appliedOffers.isEmpty()) {
            Text(
                text = "No applications sent yet.",
                color = OnSurfaceVariant,
                fontSize = 13.sp
            )
        } else {
            appliedOffers.forEachIndexed { index, offer ->
                val formattedSalary = listOf(offer.salaryRange, offer.salaryUnit)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOfferClick(offer) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = offer.title,
                            color = OnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = offer.companyName,
                            color = OnSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (formattedSalary.isNotBlank()) {
                            Text(
                                text = formattedSalary,
                                color = SalaryGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Image(
                        painter = painterResource(R.drawable.ic_profile_arrow_right),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (index < appliedOffers.lastIndex) {
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun InlineCvsList(
    cvItems: List<CvItem>,
    isUploadingCv: Boolean,
    onCvClick: (CvItem) -> Unit,
    onPreviewCv: (CvItem) -> Unit,
    onDeleteCv: (CvItem) -> Unit,
    onUploadPdfClick: () -> Unit,
    onCreateBlankCvClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, start = 4.dp, end = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface)
            .border(1.dp, OutlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isUploadingCv) {
            Text(
                text = "Uploading & validating PDF...",
                color = OnSurfaceVariant,
                fontSize = 12.sp
            )
        }

        if (cvItems.isEmpty()) {
            Text(
                text = "No CVs uploaded yet. Upload a PDF (max 5 MB, 10 pages).",
                color = OnSurfaceVariant,
                fontSize = 13.sp
            )
        } else {
            cvItems.forEachIndexed { index, cv ->
                val meta = buildList {
                    add(cv.updatedLabel)
                    cv.pageCount?.let { add("${it}p") }
                    cv.fileSizeBytes?.let { add("${(it / 1024).coerceAtLeast(1)} KB") }
                }.joinToString(" • ")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCvClick(cv) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cv.fileName,
                            color = OnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = meta,
                            color = OnSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "View",
                        color = OnSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CanvasGray)
                            .clickable { onPreviewCv(cv) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Delete",
                        color = BadgeError,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onDeleteCv(cv) }
                            .padding(horizontal = 6.dp, vertical = 5.dp)
                    )
                }

                if (index < cvItems.lastIndex) {
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f))
                }
            }
        }

        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.6f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentYellow)
                    .clickable(enabled = !isUploadingCv, onClick = onUploadPdfClick)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Upload PDF",
                    color = OnPrimaryContainer,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, OutlineVariant, RoundedCornerShape(8.dp))
                    .clickable(onClick = onCreateBlankCvClick)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ Blank CV",
                    color = OnSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ProfileActionCard(
    iconRes: Int,
    iconWidth: Dp,
    iconHeight: Dp,
    horizontalPadding: Dp,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "profile_arrow_rotation"
    )

    BottomAccentCard(
        accentColor = AccentYellow,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(width = iconWidth, height = iconHeight)
            )
            Spacer(modifier = Modifier.width(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp
                )
                Text(
                    text = subtitle,
                    color = Color.Black,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Image(
                painter = painterResource(R.drawable.ic_profile_arrow_right),
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(arrowRotation)
            )
        }
    }
}

@Composable
private fun EditProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var phone by remember { mutableStateOf(profile.phone) }
    var email by remember { mutableStateOf(profile.email) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, phone, email) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    JoblyTheme {
        ProfileScreen(
            profile = SampleSeedData.defaultProfile,
            appliedOffers = SampleSeedData.sampleAppliedOffers,
            cvItems = SampleSeedData.sampleCvItems,
            onUpdateProfile = { _, _, _ -> }
        )
    }
}
