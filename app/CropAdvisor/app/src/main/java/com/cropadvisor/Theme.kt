@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.cropadvisor

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/** One place for the look of the app: colours, font. Change here and every screen follows. */
object Ds {
    val Forest = Color(0xFF0B3B2A)       // deepest green: headers, strong text on light
    val Brand = Color(0xFF1B8A4B)        // main green: buttons, accents
    val BrandLight = Color(0xFF35B36C)
    val Mint = Color(0xFFE5F4EB)         // soft green surface
    val Page = Color(0xFFF4F6F1)         // screen background
    val Ink = Color(0xFF0F1F18)          // main text
    val Muted = Color(0xFF5F6F67)        // secondary text
    val Line = Color(0xFFE1E8E2)         // hairlines
    val Warn = Color(0xFFF2A516)
    val WarnSoft = Color(0xFFFFF2D3)
    val WarnInk = Color(0xFF7A5200)
    val Danger = Color(0xFFD64545)
    val DangerSoft = Color(0xFFFDE4E4)
    val Sky = Color(0xFFE2F1FB)
    val Sand = Color(0xFFFBEFD9)
    val Lilac = Color(0xFFECE7F8)
    val Rose = Color(0xFFFCE4E4)
}

val AppFont = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.plus_jakarta_sans, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

val AppTypography: Typography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = AppFont), displayMedium = t.displayMedium.copy(fontFamily = AppFont), displaySmall = t.displaySmall.copy(fontFamily = AppFont),
        headlineLarge = t.headlineLarge.copy(fontFamily = AppFont), headlineMedium = t.headlineMedium.copy(fontFamily = AppFont), headlineSmall = t.headlineSmall.copy(fontFamily = AppFont),
        titleLarge = t.titleLarge.copy(fontFamily = AppFont), titleMedium = t.titleMedium.copy(fontFamily = AppFont), titleSmall = t.titleSmall.copy(fontFamily = AppFont),
        bodyLarge = t.bodyLarge.copy(fontFamily = AppFont), bodyMedium = t.bodyMedium.copy(fontFamily = AppFont), bodySmall = t.bodySmall.copy(fontFamily = AppFont),
        labelLarge = t.labelLarge.copy(fontFamily = AppFont), labelMedium = t.labelMedium.copy(fontFamily = AppFont), labelSmall = t.labelSmall.copy(fontFamily = AppFont),
    )
}
