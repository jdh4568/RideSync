package com.example.ridemate_api_test.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RideMateLightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = SurfaceWhite,
    primaryContainer = AccentBlue,
    onPrimaryContainer = SurfaceWhite,
    secondary = BrandBlue,
    onSecondary = SurfaceWhite,
    background = SkyBackground,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SoftCard,
    onSurfaceVariant = TextSecondary,
    outline = InputBorder,
    outlineVariant = LightBorder,
    error = DangerRed,
    onError = SurfaceWhite,
)

@Composable
fun RideMate_api_testTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = RideMateLightColorScheme,
        typography = RideMateTypography,
        content = content,
    )
}
