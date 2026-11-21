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
import androidx.core.view.WindowCompat

/**
 * Tema bergaya Mihon: satu aksen biru, tiga tingkat netral, dan tipografi
 * sistem. Semua warna didefinisikan sekali di sini; layar memakai token
 * melalui MaterialTheme.colorScheme.
 */
private object Mihon {
    val LightPrimary = Color(0xFF1565C0)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFFE3F0FF)
    val LightOnPrimaryContainer = Color(0xFF0A3E7A)
    val LightBackground = Color(0xFFFBFBFD)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFF0F2F6)
    val LightOutline = Color(0xFFD5D9E2)
    val LightInk = Color(0xFF1A1C20)
    val LightInkSoft = Color(0xFF5D6472)
    val LightError = Color(0xFFB3261E)

    val DarkPrimary = Color(0xFF8AB4F8)
    val DarkOnPrimary = Color(0xFF102A4C)
    val DarkPrimaryContainer = Color(0xFF233B5E)
    val DarkOnPrimaryContainer = Color(0xFFCFE0FF)
    val DarkBackground = Color(0xFF0F1115)
    val DarkSurface = Color(0xFF181B21)
    val DarkSurfaceVariant = Color(0xFF242832)
    val DarkOutline = Color(0xFF3A404D)
    val DarkInk = Color(0xFFE8EAF0)
    val DarkInkSoft = Color(0xFFA6ADBC)
    val DarkError = Color(0xFFF2B8B5)
    val DarkOnError = Color(0xFF601410)
}

/** Kompatibilitas untuk layar lama; semuanya diarahkan ke token tema. */
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
        card = Mihon.LightSurfaceVariant,
        cardLight = Mihon.LightSurface,
        inkSoft = Mihon.LightInkSoft,
        outline = Mihon.LightOutline,
        yellow = Mihon.LightPrimary,
        yellowDeep = Mihon.LightPrimary,
        pink = Mihon.LightPrimaryContainer,
        pinkDeep = Mihon.LightPrimaryContainer,
        blue = Mihon.LightPrimaryContainer,
        green = Mihon.LightPrimaryContainer,
        purple = Mihon.LightPrimaryContainer,
        red = Mihon.LightError,
        shadow = Color.Transparent,
        titleShadow = Color.Transparent,
        bgDot = Color.Transparent,
        onAccent = Mihon.LightOnPrimary
    )
}

private val LightColors = lightColorScheme(
    primary = Mihon.LightPrimary,
    onPrimary = Mihon.LightOnPrimary,
    primaryContainer = Mihon.LightPrimaryContainer,
    onPrimaryContainer = Mihon.LightOnPrimaryContainer,
    secondary = Mihon.LightInkSoft,
    onSecondary = Mihon.LightOnPrimary,
    secondaryContainer = Mihon.LightSurfaceVariant,
    onSecondaryContainer = Mihon.LightInk,
    tertiary = Mihon.LightInkSoft,
    onTertiary = Mihon.LightOnPrimary,
    tertiaryContainer = Mihon.LightSurfaceVariant,
    onTertiaryContainer = Mihon.LightInk,
    background = Mihon.LightBackground,
    onBackground = Mihon.LightInk,
    surface = Mihon.LightSurface,
    onSurface = Mihon.LightInk,
    surfaceVariant = Mihon.LightSurfaceVariant,
    onSurfaceVariant = Mihon.LightInkSoft,
    outline = Mihon.LightOutline,
    outlineVariant = Mihon.LightOutline,
    error = Mihon.LightError,
    onError = Mihon.LightOnPrimary
)

private val DarkColors = darkColorScheme(
    primary = Mihon.DarkPrimary,
    onPrimary = Mihon.DarkOnPrimary,
    primaryContainer = Mihon.DarkPrimaryContainer,
    onPrimaryContainer = Mihon.DarkOnPrimaryContainer,
    secondary = Mihon.DarkInkSoft,
    onSecondary = Mihon.DarkBackground,
    secondaryContainer = Mihon.DarkSurfaceVariant,
    onSecondaryContainer = Mihon.DarkInk,
    tertiary = Mihon.DarkInkSoft,
    onTertiary = Mihon.DarkBackground,
    tertiaryContainer = Mihon.DarkSurfaceVariant,
    onTertiaryContainer = Mihon.DarkInk,
    background = Mihon.DarkBackground,
    onBackground = Mihon.DarkInk,
    surface = Mihon.DarkSurface,
    onSurface = Mihon.DarkInk,
    surfaceVariant = Mihon.DarkSurfaceVariant,
    onSurfaceVariant = Mihon.DarkInkSoft,
    outline = Mihon.DarkOutline,
    outlineVariant = Mihon.DarkOutline,
    error = Mihon.DarkError,
    onError = Mihon.DarkOnError
)

@Composable
fun TrialFetchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val extra = if (darkTheme) {
        ExtraColors(
            card = Mihon.DarkSurfaceVariant,
            cardLight = Mihon.DarkSurface,
            inkSoft = Mihon.DarkInkSoft,
            outline = Mihon.DarkOutline,
            yellow = Mihon.DarkPrimary,
            yellowDeep = Mihon.DarkPrimary,
            pink = Mihon.DarkPrimaryContainer,
            pinkDeep = Mihon.DarkPrimaryContainer,
            blue = Mihon.DarkPrimaryContainer,
            green = Mihon.DarkPrimaryContainer,
            purple = Mihon.DarkPrimaryContainer,
            red = Mihon.DarkError,
            shadow = Color.Transparent,
            titleShadow = Color.Transparent,
            bgDot = Color.Transparent,
            onAccent = Mihon.DarkOnPrimary
        )
    } else {
        ExtraColors(
            card = Mihon.LightSurfaceVariant,
            cardLight = Mihon.LightSurface,
            inkSoft = Mihon.LightInkSoft,
            outline = Mihon.LightOutline,
            yellow = Mihon.LightPrimary,
            yellowDeep = Mihon.LightPrimary,
            pink = Mihon.LightPrimaryContainer,
            pinkDeep = Mihon.LightPrimaryContainer,
            blue = Mihon.LightPrimaryContainer,
            green = Mihon.LightPrimaryContainer,
            purple = Mihon.LightPrimaryContainer,
            red = Mihon.LightError,
            shadow = Color.Transparent,
            titleShadow = Color.Transparent,
            bgDot = Color.Transparent,
            onAccent = Mihon.LightOnPrimary
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
            typography = Typography(),
            content = content
        )
    }
}
