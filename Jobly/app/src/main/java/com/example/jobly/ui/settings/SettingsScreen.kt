package com.example.jobly.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jobly.R
import com.example.jobly.data.remote.dto.UpdateUserSettingsRequestDto
import com.example.jobly.model.DailyDigestStatus
import com.example.jobly.model.UserSettings
import com.example.jobly.ui.notifications.JoblyNotificationHelper
import com.example.jobly.ui.theme.AccentYellow
import com.example.jobly.ui.theme.CanvasGray
import com.example.jobly.ui.theme.CardSurface
import com.example.jobly.ui.theme.JoblyTheme
import com.example.jobly.ui.theme.OnPrimaryContainer
import com.example.jobly.ui.theme.OnSurface
import com.example.jobly.ui.theme.OnSurfaceVariant
import com.example.jobly.ui.theme.OutlineVariant
import com.example.jobly.ui.theme.SecondaryContainer
import com.example.jobly.ui.theme.SurfaceWhite
import com.example.jobly.ui.theme.TopBarDivider

internal data class CityPreset(val name: String, val latitude: Double, val longitude: Double)

internal val JOBLY_CITY_PRESETS = listOf(
    CityPreset("Krakow", 50.0647, 19.9450),
    CityPreset("Warszawa", 52.2297, 21.0122),
    CityPreset("Wroclaw", 51.1079, 17.0385),
    CityPreset("Gdansk", 54.3520, 18.6466),
    CityPreset("Poznan", 52.4064, 16.9252),
    CityPreset("Remote", 50.0647, 19.9450)
)

internal val JOBLY_CURRENCIES = listOf("PLN", "EUR", "USD")

@Composable
fun SecondaryBackTopBar(
    title: String,
    onBack: () -> Unit
) {
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
                text = title,
                color = OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = TopBarDivider)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChoiceChipGroup(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = option.equals(selected, ignoreCase = true)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) SecondaryContainer else CanvasGray)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) AccentYellow else OutlineVariant,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = option,
                    color = OnSurface,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settings: UserSettings,
    digestStatus: DailyDigestStatus?,
    isSavingSettings: Boolean,
    isTriggeringDigest: Boolean,
    onBack: () -> Unit,
    onSaveSettings: (UpdateUserSettingsRequestDto) -> Unit,
    onPushDailyDigestNow: () -> Unit,
    onRerunOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var jobAlertsEnabled by remember(settings.jobAlertsEnabled) {
        mutableStateOf(settings.jobAlertsEnabled)
    }
    var dailyDigestEnabled by remember(settings.dailyDigestEnabled) {
        mutableStateOf(settings.dailyDigestEnabled)
    }
    var selectedCity by remember(settings.city) {
        mutableStateOf(settings.city.ifBlank { "Krakow" })
    }
    var radiusKm by remember(settings.radiusKm) {
        mutableFloatStateOf(settings.radiusKm.toFloat().coerceIn(10f, 150f))
    }
    var selectedCurrency by remember(settings.currency) {
        mutableStateOf(settings.currency.ifBlank { "PLN" })
    }
    var hasNotificationPermission by remember {
        mutableStateOf(JoblyNotificationHelper.hasNotificationPermission(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CanvasGray,
        topBar = { SecondaryBackTopBar(title = "Settings", onBack = onBack) }
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
            // Notifications Section
            SettingsSectionCard(title = "Notifications & Daily Best Match") {
                SettingToggleRow(
                    title = "Job alerts",
                    subtitle = "Notify when newly scraped roles match your profile",
                    checked = jobAlertsEnabled,
                    onCheckedChange = { jobAlertsEnabled = it }
                )

                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f))

                SettingToggleRow(
                    title = "Daily best-match push",
                    subtitle = "Server schedules top CV-matched offer every ${digestStatus?.intervalHours ?: 24}h",
                    checked = dailyDigestEnabled,
                    onCheckedChange = { dailyDigestEnabled = it }
                )

                val statusText = buildString {
                    append("Scheduler: ")
                    append(if (digestStatus?.schedulerEnabled != false) "Active" else "Paused")
                    digestStatus?.nextRunAt?.takeIf { it.isNotBlank() }?.let { nextIso ->
                        append(" • Next: ")
                        append(nextIso.take(16).replace("T", " "))
                        append(" UTC")
                    }
                }
                Text(
                    text = statusText,
                    color = OnSurfaceVariant,
                    fontSize = 12.sp
                )

                Button(
                    onClick = onPushDailyDigestNow,
                    enabled = !isTriggeringDigest,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentYellow,
                        contentColor = OnPrimaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isTriggeringDigest) "Sending match..." else "Push today's best match now",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                    OutlinedButton(
                        onClick = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Allow Android notifications", fontSize = 13.sp, color = OnSurface)
                    }
                }
            }

            // Job Preferences Section
            SettingsSectionCard(title = "Job Preferences") {
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

                Spacer(modifier = Modifier.height(4.dp))

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
                    text = "Preferred currency",
                    color = OnSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                ChoiceChipGroup(
                    options = JOBLY_CURRENCIES,
                    selected = selectedCurrency,
                    onSelect = { selectedCurrency = it }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        val preset = JOBLY_CITY_PRESETS.firstOrNull {
                            it.name.equals(selectedCity, ignoreCase = true)
                        } ?: JOBLY_CITY_PRESETS.first()
                        onSaveSettings(
                            UpdateUserSettingsRequestDto(
                                jobAlertsEnabled = jobAlertsEnabled,
                                dailyDigestEnabled = dailyDigestEnabled,
                                radiusKm = radiusKm.toInt().toDouble(),
                                currency = selectedCurrency,
                                city = preset.name,
                                latitude = preset.latitude,
                                longitude = preset.longitude
                            )
                        )
                    },
                    enabled = !isSavingSettings,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentYellow,
                        contentColor = OnPrimaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isSavingSettings) "Saving..." else "Save settings",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            // Account Setup Section
            SettingsSectionCard(title = "Account") {
                OutlinedButton(
                    onClick = onRerunOnboarding,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Re-run account setup", fontSize = 13.sp, color = OnSurface)
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = OnSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnPrimaryContainer,
                checkedTrackColor = AccentYellow
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    JoblyTheme {
        SettingsScreen(
            settings = UserSettings(),
            digestStatus = DailyDigestStatus(
                schedulerEnabled = true,
                dailyEnabled = true,
                intervalHours = 24,
                nextRunAt = "2026-11-22T11:25:00+00:00"
            ),
            isSavingSettings = false,
            isTriggeringDigest = false,
            onBack = {},
            onSaveSettings = {},
            onPushDailyDigestNow = {},
            onRerunOnboarding = {}
        )
    }
}
