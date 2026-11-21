package com.example.jobly.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.jobly.model.CardAccent

// Surfaces
val SurfaceWhite = Color(0xFFFFFFFF)
val CanvasGray = Color(0xFFF0F0F0)
val CardSurface = Color(0xFFF9F9F9)
val ChatBoxBg = Color(0xFFDBDBDB)
val TopBarDivider = Color(0xFFC9C9C9)
val DrawerSurface = Color(0xFFF7F2FA)
val DrawerScrollbar = Color(0xFF79747E)

// Material 3 & Text Tokens
val OnSurface = Color(0xFF1D1B20)
val OnSurfaceVariant = Color(0xFF49454F)
val OnPrimaryContainer = Color(0xFF4F378A)
val SecondaryContainer = Color(0xFFE8DEF8)
val OnSecondaryContainer = Color(0xFF4A4459)
val Outline = Color(0xFF79747E)
val OutlineVariant = Color(0xFFCAC4D0)
val BadgeError = Color(0xFFB3261E)

// Jobly Brand & Card Accents
val SalaryGreen = Color(0xFF4BC54B)
val AccentYellow = Color(0xFFFFCC00)
val AccentYellowAlt = Color(0xFFFFE137)
val AccentBlue = Color(0xFF8BA9D5)
val AccentPink = Color(0xFFE96F91)
val AccentGreen = Color(0xFF8FD591)

// Chip Gradient Stops
val ChipYellowStart = Color(0xFFFFCF70)
val ChipYellowEnd = Color(0xFFE68649)
val ChipBlueStart = Color(0xFF7CCAF9)
val ChipBlueEnd = Color(0xFF4790DE)
val ChipPinkStart = Color(0xFFFA84B8)
val ChipPinkEnd = Color(0xFFE24A8A)
val ChipGreenStart = Color(0xFFB2E876)
val ChipGreenEnd = Color(0xFF79C450)

data class AccentPalette(
    val borderColor: Color,
    val avatarColor: Color,
    val chipBrush: Brush
)

object CardAccentPalette {
    private val yellowBrush = Brush.verticalGradient(listOf(ChipYellowStart, ChipYellowEnd))
    private val blueBrush = Brush.verticalGradient(listOf(ChipBlueStart, ChipBlueEnd))
    private val pinkBrush = Brush.verticalGradient(listOf(ChipPinkStart, ChipPinkEnd))
    private val greenBrush = Brush.verticalGradient(listOf(ChipGreenStart, ChipGreenEnd))

    private val palettes: Map<CardAccent, AccentPalette> = mapOf(
        CardAccent.YELLOW to AccentPalette(
            borderColor = AccentYellow,
            avatarColor = AccentYellow,
            chipBrush = yellowBrush
        ),
        CardAccent.YELLOW_ALT to AccentPalette(
            borderColor = AccentYellow,
            avatarColor = AccentYellowAlt,
            chipBrush = yellowBrush
        ),
        CardAccent.BLUE to AccentPalette(
            borderColor = AccentBlue,
            avatarColor = AccentBlue,
            chipBrush = blueBrush
        ),
        CardAccent.PINK to AccentPalette(
            borderColor = AccentPink,
            avatarColor = AccentPink,
            chipBrush = pinkBrush
        ),
        CardAccent.GREEN to AccentPalette(
            borderColor = AccentGreen,
            avatarColor = AccentGreen,
            chipBrush = greenBrush
        )
    )

    fun forAccent(accent: CardAccent): AccentPalette = palettes.getValue(accent)
}
