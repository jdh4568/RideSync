package com.example.ridemate_api_test.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object HomeDesign {
    val Brand = LoginDesign.Brand
    val Outline = Color(0xFFDEDEDE)
    val Tile = Color(0xFFF5F5F5)
    val SecondaryText = Color(0xFF686868)
    val GradientStart = Color(0xFF75ABFC)
    val GradientEnd = Color(0xFF376CF4)
}

/** Scoped to home: system dynamic colors do not alter the Figma palette. */
@Composable
fun HomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = HomeDesign.Brand, onPrimary = Color.White,
            background = Color.White, onBackground = Color.Black,
            surface = Color.White, onSurface = Color.Black,
            outline = HomeDesign.Outline,
        ),
        typography = Typography,
        content = content,
    )
}
