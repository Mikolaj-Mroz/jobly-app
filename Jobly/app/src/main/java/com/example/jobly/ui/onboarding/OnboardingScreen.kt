package com.example.jobly.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jobly.data.remote.dto.UpdateUserSettingsRequestDto
import com.example.jobly.model.CvItem
import com.example.jobly.model.UserProfile
import com.example.jobly.model.UserSettings
import com.example.jobly.ui.settings.ChoiceChipGroup
import com.example.jobly.ui.settings.JOBLY_CITY_PRESETS
import com.example.jobly.ui.settings.JOBLY_CURRENCIES
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
fun OnboardingScreen(
    initialProfile: UserProfile,
    initialSettings: UserSettings,
    cvItems: List<CvItem>,
    isUploadingCv: Boolean,
    isSaving: Boolean,
    onUploadPdfClick: () -> Unit,
    onComplete: (
        name: String,
        phone: String,
        email: String,
        settings: UpdateUserSettingsRequestDto
    ) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    var name by rememberSaveable { mutableStateOf(initialProfile.name) }
    var email by rememberSaveable { mutableStateOf(initialProfile.email) }
    var phone by rememberSaveable { mutableStateOf(initialProfile.phone) }
    var selectedCity by rememberSaveable { mutableStateOf(initialSettings.city.ifBlank { "Krakow" }) }
    var radiusKm by rememberSaveable { mutableFloatStateOf(initialSettings.radiusKm.toFloat().coerceIn(10f, 150f)) }
    var selectedCurrency by rememberSaveable { mutableStateOf(initialSettings.currency.ifBlank { "PLN" }) }
    var dailyDigestEnabled by rememberSaveable { mutableStateOf(initialSettings.dailyDigestEnabled) }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun buildSettingsRequest(): UpdateUserSettingsRequestDto {
        val preset = JOBLY_CITY_PRESETS.firstOrNull {
            it.name.equals(selectedCity, ignoreCase = true)
        } ?: JOBLY_CITY_PRESETS.first()
        return UpdateUserSettingsRequestDto(
            jobAlertsEnabled = true,
            dailyDigestEnabled = dailyDigestEnabled,
            radiusKm = radiusKm.toInt().toDouble(),
            currency = selectedCurrency,
            city = preset.name,
            latitude = preset.latitude,
            longitude = preset.longitude,
            onboardingCompleted = true
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CanvasGray,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CanvasGray)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (step > 1) {
                    OutlinedButton(
                        onClick = {
                            validationError = null
                            step--
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Back", color = OnSurface)
                    }
                }

                Button(
                    onClick = {
                        when (step) {
                            1 -> {
                                if (name.trim().isEmpty() || email.trim().isEmpty()) {
                                    validationError = "Please enter your name and email."
                                } else {
                                    validationError = null
                                    step = 2
                                }
                            }
                            2 -> {
                                validationError = null
                                step = 3
                            }
                            else -> {
                                onComplete(
                                    name.trim(),
                                    phone.trim(),
                                    email.trim(),
                                    buildSettingsRequest()
                                )
                            }
                        }
                    },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentYellow,
                        contentColor = OnPrimaryContainer
                    ),
                    modifier = Modifier
                        .weight(2f)
                        .height(50.dp)
                ) {
                    val label = when {
                        isSaving -> "Saving..."
                        step < 3 -> "Continue"
                        else -> "Finish & Explore Jobs"
                    }
                    Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Welcome to Jobly",
                        color = OnSurface,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Step $step of 3",
                        color = OnSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = "Skip",
                    color = OnSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isSaving, onClick = onSkip)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            // Step progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(OutlineVariant.copy(alpha = 0.4f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(step / 3f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(AccentYellow)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardSurface)
                    .border(1.dp, OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (step) {
                    1 -> {
                        Text(
                            text = "Set up your account",
                            color = OnSurface,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "This personalizes your CV header and application profile.",
                            color = OnSurfaceVariant,
                            fontSize = 13.sp
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                validationError = null
                            },
                            label = { Text("Full name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                validationError = null
                            },
                            label = { Text("Email address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    2 -> {
                        Text(
                            text = "Job location & daily digest",
                            color = OnSurface,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Preferred city / work mode",
                            color = OnSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        ChoiceChipGroup(
                            options = JOBLY_CITY_PRESETS.map { it.name },
                            selected = selectedCity,
                            onSelect = { selectedCity = it }
                        )
                        Text(
                            text = "Search radius: ${radiusKm.toInt()} km",
                            color = OnSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = radiusKm,
                            onValueChange = { radiusKm = it },
                            valueRange = 10f..150f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentYellow,
                                activeTrackColor = AccentYellow
                            )
                        )
                        Text(
                            text = "Preferred salary currency",
                            color = OnSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        ChoiceChipGroup(
                            options = JOBLY_CURRENCIES,
                            selected = selectedCurrency,
                            onSelect = { selectedCurrency = it }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Daily best-match notification",
                                    color = OnSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Receive one top-matched job every 24 hours",
                                    color = OnSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = dailyDigestEnabled,
                                onCheckedChange = { dailyDigestEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = OnPrimaryContainer,
                                    checkedTrackColor = AccentYellow
                                )
                            )
                        }
                    }

                    else -> {
                        Text(
                            text = "Upload your CV (Optional)",
                            color = OnSurface,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Upload your real PDF resume (up to 5 MB, 10 pages) so Jobly can rank best matches and let AI tailor it for you.",
                            color = OnSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )

                        cvItems.firstOrNull()?.let { latest ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE6F4EA))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Active CV: ${latest.fileName} (${latest.updatedLabel})",
                                    color = SalaryGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onUploadPdfClick,
                            enabled = !isUploadingCv,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isUploadingCv) "Uploading PDF..." else "Upload PDF CV",
                                color = OnSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                validationError?.let { msg ->
                    Text(
                        text = msg,
                        color = BadgeError,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    JoblyTheme {
        OnboardingScreen(
            initialProfile = UserProfile("John Workman", "+1 123 456 789", "jworkman@mail.com"),
            initialSettings = UserSettings(),
            cvItems = emptyList(),
            isUploadingCv = false,
            isSaving = false,
            onUploadPdfClick = {},
            onComplete = { _, _, _, _ -> },
            onSkip = {}
        )
    }
}
