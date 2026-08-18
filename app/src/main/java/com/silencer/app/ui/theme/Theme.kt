package com.silencer.app.ui.theme

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
    primary = Blue40,
    secondary = Green40,
    tertiary = Amber40,
    surface = Color(0xFFFAFAFA)
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    secondary = Green80,
    tertiary = Amber80
)

@Composable
fun SilencerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 关闭动态取色：华为等 ROM 对 dynamicColorScheme 实现有兼容坑，易导致启动崩溃
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
