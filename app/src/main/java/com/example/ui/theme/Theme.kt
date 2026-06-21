package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = CleanPrimaryContainer,
    secondary = CleanSecondaryContainer,
    tertiary = CleanHighlight,
    background = CleanTextPrimary,
    surface = CleanTextPrimary,
    onPrimary = CleanTextPrimary,
    onSecondary = CleanTextPrimary,
    onTertiary = CleanTextPrimary,
    onBackground = CleanBackground,
    onSurface = CleanBackground,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = CleanPrimary,
    onPrimary = CleanOnPrimary,
    primaryContainer = CleanPrimaryContainer,
    secondaryContainer = CleanSecondaryContainer,
    background = CleanBackground,
    onBackground = CleanOnBackground,
    surface = CleanSurface,
    onSurface = CleanOnSurface,
    surfaceVariant = CleanHighlight,
    onSurfaceVariant = CleanTextSecondary,
    outline = CleanBorder,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Set to false to enforce the pristine Clean Minimalism theme design uniformly
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
