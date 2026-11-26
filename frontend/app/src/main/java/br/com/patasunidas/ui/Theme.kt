package br.com.patasunidas.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import br.com.patasunidas.R

/** Cores de Amazônia Contemporânea, alinhadas aos tokens do frontend web. */
object PU {
    val Green = Color(0xFF173D2B)
    val Foliage = Color(0xFF32664B)
    val Cream = Color(0xFFF6F1E6)
    val Paper = Color(0xFFFFFDFA)
    val Earth = Color(0xFFA66A43)
    val Orange = Color(0xFFD7A84B)
    val SegmentTrack = Color(0xFFE2E9DF)
    val Ink = Color(0xFF203126)
    val Muted = Color(0xFF576B5D)
    val Placeholder = Color(0xFF8A8F8A)
    val Border = Color(0xFFD9E0D6)
    val CheckBorder = Color(0xFF4A524B)
    val Divider = Color(0xFFE6E3DB)

    val CardGreen = Color(0xFFD5E0D0)
    val CardBlue = Color(0xFFD5DFF0)
    val CardPeach = Color(0xFFF4D3C3)
}

@OptIn(ExperimentalTextApi::class)
val Manrope = FontFamily(*listOf(400,500,600,700,800).map { weight -> Font(R.font.manrope, weight = FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight))) }.toTypedArray())

private val patasColors = lightColorScheme(
    primary = PU.Green, onPrimary = Color.White,
    secondary = PU.Foliage, onSecondary = Color.White,
    tertiary = PU.Earth, onTertiary = Color.White,
    background = PU.Cream, onBackground = PU.Ink,
    surface = PU.Paper, onSurface = PU.Ink,
    surfaceVariant = Color(0xFFE9EEE5), onSurfaceVariant = PU.Muted,
    outline = PU.Border, error = Color(0xFFA13F34), onError = Color.White
)

private val defaultTypography = Typography()
private val patasTypography = defaultTypography.run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Manrope), displayMedium = displayMedium.copy(fontFamily = Manrope), displaySmall = displaySmall.copy(fontFamily = Manrope),
        headlineLarge = headlineLarge.copy(fontFamily = Manrope), headlineMedium = headlineMedium.copy(fontFamily = Manrope), headlineSmall = headlineSmall.copy(fontFamily = Manrope),
        titleLarge = titleLarge.copy(fontFamily = Manrope), titleMedium = titleMedium.copy(fontFamily = Manrope), titleSmall = titleSmall.copy(fontFamily = Manrope),
        bodyLarge = bodyLarge.copy(fontFamily = Manrope), bodyMedium = bodyMedium.copy(fontFamily = Manrope), bodySmall = bodySmall.copy(fontFamily = Manrope),
        labelLarge = labelLarge.copy(fontFamily = Manrope), labelMedium = labelMedium.copy(fontFamily = Manrope), labelSmall = labelSmall.copy(fontFamily = Manrope)
    )
}

@Composable
fun PatasUnidasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = patasColors,
        typography = patasTypography,
        shapes = MaterialTheme.shapes.copy(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        content = content
    )
}
