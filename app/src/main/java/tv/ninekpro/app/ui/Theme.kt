@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
package tv.ninekpro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import tv.ninekpro.app.data.Brand
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import tv.ninekpro.app.R

/** Rounded friendly font matching the smile TV logo (variable Nunito). */
/** Nunito is a VARIABLE font. Android 8.0+ handles it (weight axes); Android 6.x/7.x boxes throw "Could not load font" at the first draw even for a
 *  plain load (crash on open, seen on Amlogic AOSP 7.1.2) — so below API 26 the app uses the system font. */
val SmileFont: FontFamily = if (android.os.Build.VERSION.SDK_INT >= 26) FontFamily(
    Font(R.font.nunito, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.nunito, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.nunito, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.nunito, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.nunito, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
) else FontFamily.Default   // 6.x/7.x boxes cannot parse the variable TTF at all (still "Could not load font" when loaded plainly) → system font there

fun parseColor(hex: String, fallback: Color): Color = try {
    val h = hex.trim().removePrefix("#"); if (h.length != 6) fallback else Color(("ff$h").toLong(16))
} catch (e: Exception) { fallback }

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
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides TextStyle(fontFamily = SmileFont), content = content)
    }
}

/** TV remote focus: the item zooms ~6% with a glowing accent border (IPTV Smarters style). Phones never show it (no focus). */
@Composable
fun Modifier.tvFocus(accent: Color, radius: Int = 10): Modifier {
    // NOTE: must sit BEFORE .clickable/.combinedClickable in the chain — onFocusChanged only sees focus targets that come after it.
    var focused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (focused) 1.08f else 1f, label = "focus")
    val shape = RoundedCornerShape(radius.dp)
    return this.onFocusChanged { focused = it.isFocused }
        .graphicsLayer { scaleX = scale; scaleY = scale; if (focused) { shadowElevation = 18f; this.shape = shape; clip = false; ambientShadowColor = accent; spotShadowColor = accent } }
        .then(if (focused) Modifier.border(3.dp, Color.White, shape).background(accent.copy(alpha = 0.32f), shape) else Modifier)
}

/** Glass card background used everywhere (settings tiles, playlist cards, panes). */
@Composable
fun Modifier.glass(c: SmileColors, radius: Int = 10, selected: Boolean = false): Modifier =
    this.background(if (selected) Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.35f), c.card2)) else c.cardGradient, RoundedCornerShape(radius.dp))
        .border(1.dp, if (selected) c.accent else c.line, RoundedCornerShape(radius.dp))
