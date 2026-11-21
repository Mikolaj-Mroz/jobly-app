package com.example.jobly.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val JoblyLightColorScheme = lightColorScheme(
    primary = OnPrimaryContainer,
    onPrimary = SurfaceWhite,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    background = CanvasGray,
    onBackground = OnSurface,
    surface = SurfaceWhite,
    onSurface = OnSurface,
    surfaceVariant = CardSurface,
    onSurfaceVariant = OnSurfaceVariant,
    error = BadgeError,
    onError = SurfaceWhite,
    outline = Outline,
    outlineVariant = OutlineVariant
)

@Composable
fun JoblyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JoblyLightColorScheme,
        content = content
    )
}
