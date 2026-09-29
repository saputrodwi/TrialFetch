package com.trialfetch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Palet diambil apa adanya dari versi web (Trial Fetch), supaya kedua
 * aplikasi terlihat sebagai produk yang sama. Nilai diambil dari blok
 * `:root` dan `[data-theme="dark"]` di index.html versi web.
 */
object WebPalette {
    // accent
    val Pink = Color(0xFFF6C9DB)
    val PinkDeep = Color(0xFFE8A9C6)
    val Blue = Color(0xFFC3E4F5)
    val BlueStrong = Color(0xFF8ECDEA)
    val Yellow = Color(0xFFFFEEA8)
    val YellowDeep = Color(0xFFF0D97E)
    val Green = Color(0xFFBDE5C8)
    val GreenDeep = Color(0xFF7DBE90)
    val GreenInk = Color(0xFF2F6B40)
    val Purple = Color(0xFFDBCBF0)
    val PurpleDeep = Color(0xFFC0A8E0)
    val Red = Color(0xFFF2A0A0)
    val RedDeep = Color(0xFFE85D5D)
    val RedInk = Color(0xFFB03636)

    // terang
    val LightBg = Color(0xFFF3EFE9)
    val LightCard = Color(0xFFE4DFD6)
    val LightCardLight = Color(0xFFFBF9F5)
    val LightInk = Color(0xFF3A3733)
    val LightInkSoft = Color(0xFF8A857E)
    val LightOutline = Color(0xFFB3AEA6)

    // gelap
    val DarkBg = Color(0xFF262322)
    val DarkCard = Color(0xFF3C3835)
    val DarkCardLight = Color(0xFF2F2C2A)
    val DarkInk = Color(0xFFD8D2C8)
    val DarkInkSoft = Color(0xFF9A938A)
    val DarkOutline = Color(0xFF57524C)
}

/** Warna non-Material3 yang dipakai khusus di beberapa komponen. */
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
    val red: Color
)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(
        card = WebPalette.LightCard,
        cardLight = WebPalette.LightCardLight,
        inkSoft = WebPalette.LightInkSoft,
        outline = WebPalette.LightOutline,
        yellow = WebPalette.Yellow,
        yellowDeep = WebPalette.YellowDeep,
        pink = WebPalette.Pink,
        pinkDeep = WebPalette.PinkDeep,
        blue = WebPalette.Blue,
        green = WebPalette.Green,
        purple = WebPalette.Purple,
        red = WebPalette.Red
    )
}

private val LightColors = lightColorScheme(
    primary = WebPalette.BlueStrong,
    onPrimary = Color(0xFF07354A),
    primaryContainer = WebPalette.Blue,
    onPrimaryContainer = Color(0xFF07354A),
    secondary = WebPalette.PinkDeep,
    onSecondary = Color(0xFF3F2130),
    secondaryContainer = WebPalette.Pink,
    onSecondaryContainer = Color(0xFF3F2130),
    tertiary = WebPalette.PurpleDeep,
    onTertiary = Color(0xFF2B1A3D),
    tertiaryContainer = WebPalette.Purple,
    onTertiaryContainer = Color(0xFF2B1A3D),
    background = WebPalette.LightBg,
    onBackground = WebPalette.LightInk,
    surface = WebPalette.LightCardLight,
    onSurface = WebPalette.LightInk,
    surfaceVariant = WebPalette.LightCard,
    onSurfaceVariant = WebPalette.LightInkSoft,
    outline = WebPalette.LightOutline,
    error = WebPalette.RedDeep,
    onError = Color.White,
    errorContainer = WebPalette.Red,
    onErrorContainer = WebPalette.RedInk
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FBEE3),
    onPrimary = Color(0xFF06323F),
    primaryContainer = Color(0xFF1B4A5C),
    onPrimaryContainer = WebPalette.Blue,
    secondary = WebPalette.PinkDeep,
    onSecondary = Color(0xFF3A1B2A),
    secondaryContainer = Color(0xFF5A3350),
    onSecondaryContainer = WebPalette.Pink,
    tertiary = Color(0xFFB49BDD),
    onTertiary = Color(0xFF27163A),
    tertiaryContainer = Color(0xFF453260),
    onTertiaryContainer = WebPalette.Purple,
    background = WebPalette.DarkBg,
    onBackground = WebPalette.DarkInk,
    surface = WebPalette.DarkCardLight,
    onSurface = WebPalette.DarkInk,
    surfaceVariant = WebPalette.DarkCard,
    onSurfaceVariant = WebPalette.DarkInkSoft,
    outline = WebPalette.DarkOutline,
    error = Color(0xFFE86A6A),
    onError = Color(0xFF3B0E0E),
    errorContainer = Color(0xFF5C2222),
    onErrorContainer = Color(0xFFFFB0B0)
)

private val AppTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

@Composable
fun TrialFetchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val extra = if (darkTheme) {
        ExtraColors(
            card = WebPalette.DarkCard,
            cardLight = WebPalette.DarkCardLight,
            inkSoft = WebPalette.DarkInkSoft,
            outline = WebPalette.DarkOutline,
            yellow = Color(0xFFE8D38A),
            yellowDeep = Color(0xFFC9AF5C),
            pink = Color(0xFFE9A8C4),
            pinkDeep = Color(0xFFC77FA2),
            blue = Color(0xFF8FC8E4),
            green = Color(0xFF6FA87F),
            purple = Color(0xFFA88FC9),
            red = Color(0xFFC96A6A)
        )
    } else {
        ExtraColors(
            card = WebPalette.LightCard,
            cardLight = WebPalette.LightCardLight,
            inkSoft = WebPalette.LightInkSoft,
            outline = WebPalette.LightOutline,
            yellow = WebPalette.Yellow,
            yellowDeep = WebPalette.YellowDeep,
            pink = WebPalette.Pink,
            pinkDeep = WebPalette.PinkDeep,
            blue = WebPalette.Blue,
            green = WebPalette.Green,
            purple = WebPalette.Purple,
            red = WebPalette.Red
        )
    }

    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            content = content
        )
    }
}
