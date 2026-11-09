package com.relay.manager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDeep = Color(0xFF050914)
val BgCard = Color(0xFF0D1526)
val BgCardLight = Color(0xFF16203C)
val BorderDim = Color(0xFF243158)

val Cyan = Color(0xFF00E5FF)
val Mint = Color(0xFF5CFFC0)
val Violet = Color(0xFF8B5CFF)
val Amber = Color(0xFFFFC94D)
val Coral = Color(0xFFFF5C7A)
val Blue = Color(0xFF4D9FFF)
val Pink = Color(0xFFFF5CB8)
val Teal = Color(0xFF2AE7D1)

val TextHi = Color(0xFFF0F4FF)
val TextMid = Color(0xFF97A5CE)
val TextLow = Color(0xFF5C6A93)

val ChartPalette = listOf(Cyan, Mint, Violet, Amber, Blue, Pink, Teal, Coral)

private val Scheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF00333A),
    secondary = Mint,
    tertiary = Violet,
    background = BgDeep,
    onBackground = TextHi,
    surface = BgCard,
    onSurface = TextHi,
    surfaceVariant = BgCardLight,
    onSurfaceVariant = TextMid,
    outline = BorderDim,
    error = Coral,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
