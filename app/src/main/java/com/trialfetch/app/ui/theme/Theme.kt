package com.trialfetch.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5E4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F0DC),
    onPrimaryContainer = Color(0xFF002015),
    secondary = Color(0xFF4B635B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE8DD),
    onSecondaryContainer = Color(0xFF072019),
    tertiary = Color(0xFF3F6375),
    tertiaryContainer = Color(0xFFC2E8FD),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD4C0),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513D),
    onPrimaryContainer = Color(0xFFB9F0DC),
    secondary = Color(0xFFB1CCC1),
    onSecondary = Color(0xFF1D352D),
    secondaryContainer = Color(0xFF334B43),
    onSecondaryContainer = Color(0xFFCDE8DD),
    tertiary = Color(0xFFA6CCE0),
    error = Color(0xFFFFB4AB)
)

@Composable
fun TrialFetchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
