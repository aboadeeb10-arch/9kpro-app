package tv.ninekpro.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import tv.ninekpro.desktop.data.Brand

val SmileFont: FontFamily = FontFamily(
    Font("nunito.ttf", FontWeight.Normal), Font("nunito.ttf", FontWeight.Medium), Font("nunito.ttf", FontWeight.SemiBold), Font("nunito.ttf", FontWeight.Bold), Font("nunito.ttf", FontWeight.ExtraBold))

fun parseColor(hex: String, fallback: Color): Color = try { val h = hex.trim().removePrefix("#"); if (h.length != 6) fallback else Color(("ff$h").toLong(16)) } catch (e: Exception) { fallback }

/** The classic 9K Pro TV look: deep navy gradient, dark-blue glass cards with a thin lighter border, sky-blue accent. */
class SmileColors(val accent: Color, val bg1: Color, val bg2: Color, val bg3: Color, val card: Color, val card2: Color, val line: Color, val text: Color, val muted: Color, val isLight: Boolean) {
    val gradient: Brush get() = Brush.verticalGradient(listOf(bg1, bg2, bg3))
    val cardGradient: Brush get() = Brush.verticalGradient(listOf(card, card2))
    val selected: Color get() = accent.copy(alpha = 0.22f)
}
fun smileColors(brand: Brand, mode: String): SmileColors {
    val accent = parseColor(brand.color, Color(0xFFFFD21F))
    return when (mode) {
        "light" -> SmileColors(accent, Color(0xFFF4F6FA), Color(0xFFEEF1F7), Color(0xFFE6EAF2), Color.White, Color(0xFFF3F5F9), Color(0xFFD5DBE6), Color(0xFF12172A), Color(0xFF5B6478), true)
        "gold" -> SmileColors(accent, Color(0xFF1A1408), Color(0xFF0D0A04), Color(0xFF000000), Color(0xFF1C1609), Color(0xFF120E06), Color(0xFF3A2E12), Color.White, Color(0xFFC9B98A), false)
        "amoled" -> SmileColors(accent, Color.Black, Color.Black, Color.Black, Color(0xFF0E1420), Color(0xFF080C14), Color(0xFF1C2638), Color.White, Color(0xFF9AA3B2), false)
        else -> SmileColors(accent, Color(0xFF10203C), Color(0xFF0A1428), Color(0xFF05091A), Color(0xFF0F2A4C), Color(0xFF0A1B36), Color(0xFF1E3D63), Color.White, Color(0xFF9FB3C8), false)
    }
}
val LocalColors = compositionLocalOf { smileColors(Brand(), "gold") }

@Composable
fun SmileTheme(colors: SmileColors, content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (colors.isLight) lightColorScheme(primary = colors.accent, background = colors.bg1, surface = colors.card, onBackground = colors.text, onSurface = colors.text, onPrimary = Color.White)
    else darkColorScheme(primary = colors.accent, background = colors.bg3, surface = Color(0xFF0F2140), onBackground = colors.text, onSurface = colors.text, onPrimary = Color.White, surfaceVariant = Color(0xFF15305A))
    val base = Typography()
    val t = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = SmileFont), displayMedium = base.displayMedium.copy(fontFamily = SmileFont), displaySmall = base.displaySmall.copy(fontFamily = SmileFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = SmileFont), headlineMedium = base.headlineMedium.copy(fontFamily = SmileFont), headlineSmall = base.headlineSmall.copy(fontFamily = SmileFont),
        titleLarge = base.titleLarge.copy(fontFamily = SmileFont), titleMedium = base.titleMedium.copy(fontFamily = SmileFont), titleSmall = base.titleSmall.copy(fontFamily = SmileFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = SmileFont), bodyMedium = base.bodyMedium.copy(fontFamily = SmileFont), bodySmall = base.bodySmall.copy(fontFamily = SmileFont),
        labelLarge = base.labelLarge.copy(fontFamily = SmileFont), labelMedium = base.labelMedium.copy(fontFamily = SmileFont), labelSmall = base.labelSmall.copy(fontFamily = SmileFont))
    MaterialTheme(colorScheme = scheme, typography = t) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = SmileFont, color = colors.text), LocalColors provides colors, content = content)
    }
}

/** Mouse hover = the TV focus look: slight zoom + accent glow. Put it BEFORE .clickable in the chain. */
@Composable
fun Modifier.hoverGlow(accent: Color, radius: Int = 10, scale: Float = 1.04f): Modifier {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val s by androidx.compose.animation.core.animateFloatAsState(if (hovered) scale else 1f)
    val shape = RoundedCornerShape(radius.dp)
    return this.hoverable(src).graphicsLayer { scaleX = s; scaleY = s }.then(if (hovered) Modifier.border(2.dp, accent, shape).background(accent.copy(alpha = 0.18f), shape) else Modifier)
}

@Composable
fun Modifier.glass(c: SmileColors, radius: Int = 10, selected: Boolean = false): Modifier =
    this.background(if (selected) Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.35f), c.card2)) else c.cardGradient, RoundedCornerShape(radius.dp)).border(1.dp, if (selected) c.accent else c.line, RoundedCornerShape(radius.dp))
