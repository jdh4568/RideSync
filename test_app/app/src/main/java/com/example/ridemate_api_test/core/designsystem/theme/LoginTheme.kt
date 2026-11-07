package com.example.ridemate_api_test.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Figma login palette. Scoped to login; other screens retain their existing theme. */
object LoginDesign {
    val Brand = Color(0xFF276ED3)
    val Ink = Color(0xFF222C35)
    val Outline = Color(0xFFA9A9A9)
    val Hint = Color(0xFF929292)
    val PanelBorder = Color(0xFFE9EDF1)
    val Logo = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 64.sp, lineHeight = 76.sp, letterSpacing = 0.sp)
    val Input = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.sp)
    val Button = Input.copy(fontSize = 25.sp, lineHeight = 32.sp)
    val Divider = Input.copy(fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
fun LoginTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = LoginDesign.Ink, onPrimary = Color.White,
            background = Color.White, onBackground = Color.Black,
            surface = Color.White, onSurface = Color.Black,
            outline = LoginDesign.Outline,
        ),
        typography = Typography.copy(bodyLarge = LoginDesign.Input),
        content = content,
    )
}
