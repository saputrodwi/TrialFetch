package com.trialfetch.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.trialfetch.app.R

/**
 * Sistem warna Trial Fetch: satu aksen (#3B5BA9) dan tiga tingkat netral.
 * Semua nilai hex hanya boleh ada di berkas ini; layar memakai
 * `MaterialTheme.colorScheme.*` atau token di [ExtraColors].
 */
private object Palette {
    val Accent = Color(0xFF3B5BA9)
    val OnAccent = Color(0xFFFFFFFF)
    val AccentContainer = Color(0xFFE8EDF7)
    val OnAccentContainer = Color(0xFF22356B)
    val Neutral = Color(0xFF575A60)

    val LightBackground = Color(0xFFF6F6F6)
    val LightSurface = Color(0xFFFEFEFE)
    val LightSurfaceVariant = Color(0xFFECEDEE)
    val LightOutline = Color(0xFFD7D8DA)
    val LightInk = Color(0xFF1A1B1E)
    val LightInkSoft = Color(0xFF575A60)
    val LightError = Color(0xFFB3261E)

    val DarkAccent = Color(0xFF95A9DA)
    val DarkOnAccent = Color(0xFF101418)
    val DarkAccentContainer = Color(0xFF26335C)
    val DarkOnAccentContainer = Color(0xFFD7E2FF)
    val DarkNeutral = Color(0xFFA7A9AE)
    val DarkBackground = Color(0xFF111213)
    val DarkSurface = Color(0xFF19191B)
    val DarkSurfaceVariant = Color(0xFF222325)
    val DarkOutline = Color(0xFF3B3C40)
    val DarkInk = Color(0xFFE7E8E9)
    val DarkInkSoft = Color(0xFFA7A9AE)
    val DarkError = Color(0xFFF2B8B5)
    val DarkOnError = Color(0xFF601410)
}

/**
 * Nama field lama dipertahankan supaya layar yang belum disentuh tetap
 * kompil, tetapi semuanya sekarang mengarah ke satu aksen / netral.
 * `shadow`, `titleShadow`, dan `bgDot` sengaja transparan: dekorasi lama
 * (hard shadow, text-shadow, polkadot) tidak dipakai lagi.
 */
data class ExtraColors(
    val card: Color,
    val cardLight: Color,
    val inkSoft: Color,
    val outline: Color,
    val yellow: Color,
    val yellowDeep: Color,
    val pink: Color,
    val pinkDeep: Color,
    val blue: Color,
    val green: Color,
    val purple: Color,
    val red: Color,
    val shadow: Color,
    val titleShadow: Color,
    val bgDot: Color,
    val onAccent: Color
)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(
        card = Palette.LightSurfaceVariant,
        cardLight = Palette.LightSurface,
        inkSoft = Palette.LightInkSoft,
        outline = Palette.LightOutline,
        yellow = Palette.Accent,
        yellowDeep = Palette.Accent,
        pink = Palette.AccentContainer,
        pinkDeep = Palette.AccentContainer,
        blue = Palette.AccentContainer,
        green = Palette.AccentContainer,
        purple = Palette.AccentContainer,
        red = Palette.LightError,
        shadow = Color.Transparent,
        titleShadow = Color.Transparent,
        bgDot = Color.Transparent,
        onAccent = Palette.OnAccent
    )
}

private val LightColors = lightColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.OnAccent,
    primaryContainer = Palette.AccentContainer,
    onPrimaryContainer = Palette.OnAccentContainer,
    secondary = Palette.Neutral,
    onSecondary = Palette.OnAccent,
    secondaryContainer = Palette.LightSurfaceVariant,
    onSecondaryContainer = Palette.LightInk,
    tertiary = Palette.Neutral,
    onTertiary = Palette.OnAccent,
    tertiaryContainer = Palette.LightSurfaceVariant,
    onTertiaryContainer = Palette.LightInk,
    background = Palette.LightBackground,
    onBackground = Palette.LightInk,
    surface = Palette.LightSurface,
    onSurface = Palette.LightInk,
    surfaceVariant = Palette.LightSurfaceVariant,
    onSurfaceVariant = Palette.LightInkSoft,
    outline = Palette.LightOutline,
    outlineVariant = Palette.LightOutline,
    error = Palette.LightError,
    onError = Palette.OnAccent
)

private val DarkColors = darkColorScheme(
    primary = Palette.DarkAccent,
    onPrimary = Palette.DarkOnAccent,
    primaryContainer = Palette.DarkAccentContainer,
    onPrimaryContainer = Palette.DarkOnAccentContainer,
    secondary = Palette.DarkNeutral,
    onSecondary = Palette.DarkBackground,
    secondaryContainer = Palette.DarkSurfaceVariant,
    onSecondaryContainer = Palette.DarkInk,
    tertiary = Palette.DarkNeutral,
    onTertiary = Palette.DarkBackground,
    tertiaryContainer = Palette.DarkSurfaceVariant,
    onTertiaryContainer = Palette.DarkInk,
    background = Palette.DarkBackground,
    onBackground = Palette.DarkInk,
    surface = Palette.DarkSurface,
    onSurface = Palette.DarkInk,
    surfaceVariant = Palette.DarkSurfaceVariant,
    onSurfaceVariant = Palette.DarkInkSoft,
    outline = Palette.DarkOutline,
    outlineVariant = Palette.DarkOutline,
    error = Palette.DarkError,
    onError = Palette.DarkOnError
)

/** Satu keluarga font, dua weight: Regular untuk isi, SemiBold untuk judul. */
private val Quicksand = FontFamily(
    Font(R.font.quicksand_regular, FontWeight.Normal),
    Font(R.font.quicksand_semibold, FontWeight.SemiBold)
)

private val TypeScale = Typography()

private val AppTypography = Typography(
    displayLarge = TypeScale.displayLarge.copy(fontFamily = Quicksand, fontWeight = FontWeight.SemiBold),
    displayMedium = TypeScale.displayMedium.copy(fontFamily = Quicksand, fontWeight = FontWeight.SemiBold),
    displaySmall = TypeScale.displaySmall.copy(fontFamily = Quicksand, fontWeight = FontWeight.SemiBold),
    headlineLarge = TypeScale.headlineLarge.copy(fontFamily = Quicksand, fontWeight = FontWeight.SemiBold),
    headlineMedium = TypeScale.headlineMedium.copy(fontFamily = Quicksand, fontWeight = FontWeight.SemiBold),
    headlineSmall = TypeScale.headlineSmall.copy(fontFamily = Quicksand, fontWeight = FontWeight.SemiBold),
    titleLarge = TypeScale.titleLarge.copy(fontFamily = Quicksand, fontWeight = FontWeight.SemiBold),
    titleMedium = TypeScale.titleMedium.copy(fontFamily = Quicksand, fontWeight = FontWeight.SemiBold),
    titleSmall = TypeScale.titleSmall.copy(fontFamily = Quicksand, fontWeight = FontWeight.SemiBold),
    bodyLarge = TypeScale.bodyLarge.copy(fontFamily = Quicksand, fontWeight = FontWeight.Normal),
    bodyMedium = TypeScale.bodyMedium.copy(fontFamily = Quicksand, fontWeight = FontWeight.Normal),
    bodySmall = TypeScale.bodySmall.copy(fontFamily = Quicksand, fontWeight = FontWeight.Normal),
    labelLarge = TypeScale.labelLarge.copy(fontFamily = Quicksand, fontWeight = FontWeight.SemiBold),
    labelMedium = TypeScale.labelMedium.copy(fontFamily = Quicksand, fontWeight = FontWeight.SemiBold),
    labelSmall = TypeScale.labelSmall.copy(fontFamily = Quicksand, fontWeight = FontWeight.Normal)
)

@Composable
fun TrialFetchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val extra = if (darkTheme) {
        ExtraColors(
            card = Palette.DarkSurfaceVariant,
            cardLight = Palette.DarkSurface,
            inkSoft = Palette.DarkInkSoft,
            outline = Palette.DarkOutline,
            yellow = Palette.DarkAccent,
            yellowDeep = Palette.DarkAccent,
            pink = Palette.DarkAccentContainer,
            pinkDeep = Palette.DarkAccentContainer,
            blue = Palette.DarkAccentContainer,
            green = Palette.DarkAccentContainer,
            purple = Palette.DarkAccentContainer,
            red = Palette.DarkError,
            shadow = Color.Transparent,
            titleShadow = Color.Transparent,
            bgDot = Color.Transparent,
            onAccent = Palette.DarkOnAccent
        )
    } else {
        ExtraColors(
            card = Palette.LightSurfaceVariant,
            cardLight = Palette.LightSurface,
            inkSoft = Palette.LightInkSoft,
            outline = Palette.LightOutline,
            yellow = Palette.Accent,
            yellowDeep = Palette.Accent,
            pink = Palette.AccentContainer,
            pinkDeep = Palette.AccentContainer,
            blue = Palette.AccentContainer,
            green = Palette.AccentContainer,
            purple = Palette.AccentContainer,
            red = Palette.LightError,
            shadow = Color.Transparent,
            titleShadow = Color.Transparent,
            bgDot = Color.Transparent,
            onAccent = Palette.OnAccent
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            content = content
        )
    }
}
