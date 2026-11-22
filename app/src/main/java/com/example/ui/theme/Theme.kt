package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme =
  darkColorScheme(
    primary = StudioCoral,
    onPrimary = StudioObsidian,
    primaryContainer = StudioElevated,
    onPrimaryContainer = StudioCoralLight,
    secondary = StudioCoralLight,
    onSecondary = StudioObsidian,
    secondaryContainer = StudioCard,
    onSecondaryContainer = StudioTextPrimary,
    tertiary = StudioInfo,
    onTertiary = StudioObsidian,
    background = StudioObsidian,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioCard,
    onSurfaceVariant = StudioTextSecondary,
    outline = StudioBorder,
    outlineVariant = StudioElevated,
    error = StudioError,
    onError = CaptionWhite,
  )

private val StudioLightColorScheme =
  lightColorScheme(
    primary = StudioCoralDeep,
    onPrimary = CaptionWhite,
    secondary = StudioCoral,
    onSecondary = CaptionWhite,
    tertiary = StudioInfo,
    onTertiary = CaptionWhite,
    background = StudioLightBackground,
    onBackground = StudioLightText,
    surface = StudioLightSurface,
    onSurface = StudioLightText,
    surfaceVariant = StudioLightCard,
    onSurfaceVariant = StudioTextMuted,
    outline = StudioBorder,
    error = StudioError,
    onError = CaptionWhite,
  )

val StudioShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
  )

@Composable
fun CaptionStudioTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = StudioShapes,
    content = content,
  )
}
