package com.alabadla.internetcardledger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LedgerLightColors = lightColorScheme(
    primary = Color(0xFF176B3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F5DE),
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF3E6650),
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF171D19),
    surface = Color.White,
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFE0EAE2),
    onSurfaceVariant = Color(0xFF3F4942),
    outline = Color(0xFF6F7971),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val LedgerTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 32.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 28.sp),
    labelLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun InternetCardLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LedgerLightColors,
        typography = LedgerTypography,
        content = content,
    )
}
