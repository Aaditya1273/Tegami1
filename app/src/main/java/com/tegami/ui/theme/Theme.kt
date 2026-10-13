package com.tegami.ui.theme

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tegami.R

/**
 * Semantic colour tokens, from the art: crimson wax for actions, cream paper, red-black lacquer for the
 * hero, gold for accents. Screens use these names, never raw hex. The seal colour marks a sealed letter only.
 */
@Immutable
data class TegamiColors(
    val canvas: Color,
    val surface: Color,
    val raised: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val divider: Color,
    val border: Color,
    val subtle: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryTint: Color,
    /** [primary] while pressed: the wax deepens. */
    val primaryPressed: Color,
    /** The one lacquer hero card per screen (a gradient from [hero] to [heroEnd]), and what sits on it. */
    val hero: Color,
    val heroEnd: Color,
    val onHero: Color,
    /** Gold leaf: hero labels, rims and small accents. Decorative or large text only (not body text). */
    val gold: Color,
    val seal: Color,
    val onSeal: Color,
    val positive: Color,
    val positiveTint: Color,
    val warning: Color,
    val warningTint: Color,
    val danger: Color,
    val dangerTint: Color,
    val disabled: Color,
    val isDark: Boolean,
)

private val Crimson = Color(0xFFA3161D)
private val Cream = Color(0xFFFAF4EA)
private val Lacquer = Color(0xFF2A1714)

val LightColors = TegamiColors(
    canvas = Cream, surface = Color(0xFFFFFDF8), raised = Color.White,
    ink = Lacquer, inkSecondary = Lacquer.copy(alpha = 0.74f), inkTertiary = Lacquer.copy(alpha = 0.62f),
    divider = Lacquer.copy(alpha = 0.08f), border = Lacquer.copy(alpha = 0.14f), subtle = Lacquer.copy(alpha = 0.05f),
    primary = Crimson, onPrimary = Color(0xFFFFF8EE), primaryTint = Crimson.copy(alpha = 0.08f),
    primaryPressed = Color(0xFF7E0F15), hero = Color(0xFF2E0A0C), heroEnd = Color(0xFF7A1218), onHero = Color(0xFFFFF4E6),
    gold = Color(0xFFD4A34E), seal = Color(0xFFC0262D), onSeal = Color(0xFFFFF4E6),
    positive = Color(0xFF1E6B47), positiveTint = Color(0xFF1E6B47).copy(alpha = 0.10f),
    warning = Color(0xFF8A5300), warningTint = Color(0xFF8A5300).copy(alpha = 0.10f),
    danger = Color(0xFFB3261E), dangerTint = Color(0xFFB3261E).copy(alpha = 0.09f),
    disabled = Lacquer.copy(alpha = 0.30f), isDark = false,
)

private val Night = Color(0xFF150C0B)
private val Linen = Color(0xFFF6EADB)

val DarkColors = TegamiColors(
    canvas = Night, surface = Color(0xFF1F1513), raised = Color(0xFF291C19),
    ink = Linen, inkSecondary = Linen.copy(alpha = 0.76f), inkTertiary = Linen.copy(alpha = 0.62f),
    divider = Linen.copy(alpha = 0.08f), border = Linen.copy(alpha = 0.16f), subtle = Linen.copy(alpha = 0.06f),
    primary = Color(0xFFF2786E), onPrimary = Color(0xFF2A0B0B), primaryTint = Color(0xFFF2786E).copy(alpha = 0.12f),
    primaryPressed = Color(0xFFD85E55), hero = Color(0xFF2A0A0C), heroEnd = Color(0xFF6A1016), onHero = Color(0xFFFFF4E6),
    gold = Color(0xFFE0B464), seal = Color(0xFFE5574C), onSeal = Color(0xFF2A0B0B),
    positive = Color(0xFF63C493), positiveTint = Color(0xFF63C493).copy(alpha = 0.12f),
    warning = Color(0xFFE6AA4E), warningTint = Color(0xFFE6AA4E).copy(alpha = 0.12f),
    danger = Color(0xFFF2857C), dangerTint = Color(0xFFF2857C).copy(alpha = 0.12f),
    disabled = Linen.copy(alpha = 0.32f), isDark = true,
)

object Spacing {
    val micro = 4.dp
    val compact = 8.dp
    val related = 12.dp
    val standard = 16.dp
    val section = 24.dp
    val major = 32.dp
    val gutter = 20.dp
}

object Shapes {
    val small = RoundedCornerShape(10.dp)
    val standard = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(22.dp)
    val pill = RoundedCornerShape(50)
}

/**
 * Motion levels (ms). micro: control feedback. control: toggles, pills. screen: navigation. spatial: one
 * thing becoming another. seal: the letter opening. Nothing loops while idle; reduced motion collapses all
 * of it to short fades.
 */
object Motion {
    const val micro = 120
    const val control = 180
    const val screen = 240
    const val spatial = 420
    const val seal = 720
    const val stagger = 40

    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Emphasized: Easing = CubicBezierEasing(0.3f, 0f, 0f, 1f)
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
}

/** Haptics confirm real outcomes only: a selection tick, a verified result, a rejection. Never decorative. */
object Haptics {
    fun tick(view: View) = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    fun success(view: View) = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
    fun reject(view: View) = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Manrope for display and headlines, Inter for interface and body. Both OFL, bundled in res/font. */
val Manrope = FontFamily(variable(R.font.manrope, 500), variable(R.font.manrope, 600), variable(R.font.manrope, 700),
    variable(R.font.manrope, 800))
val Inter = FontFamily(variable(R.font.inter, 400), variable(R.font.inter, 500), variable(R.font.inter, 600))
private const val TABULAR = "tnum"

val TegamiTypography = Typography(
    displayMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp,
        letterSpacing = (-1).sp, fontFeatureSettings = TABULAR),
    headlineLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp,
        letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp,
        letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp,
        fontFeatureSettings = TABULAR),
    bodyLarge = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.2.sp,
        fontFeatureSettings = TABULAR),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp),
)

val LocalTegamiColors = staticCompositionLocalOf { LightColors }
/** True when the user wants less motion: Android "remove animations", or Settings → Reduced motion. */
val LocalReducedMotion = staticCompositionLocalOf { false }

object Tegami {
    val colors: TegamiColors
        @Composable @ReadOnlyComposable get() = LocalTegamiColors.current
}

@Composable
fun TegamiTheme(reducedMotion: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) DarkColors else LightColors
    val resolver = LocalContext.current.contentResolver
    val systemReduced = remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val scheme = if (dark) {
        darkColorScheme(primary = c.primary, onPrimary = c.onPrimary, background = c.canvas, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.subtle, onSurfaceVariant = c.inkSecondary,
            outline = c.border, outlineVariant = c.divider, error = c.danger, onError = c.onPrimary,
            surfaceContainerHigh = c.raised, surfaceContainer = c.surface)
    } else {
        lightColorScheme(primary = c.primary, onPrimary = c.onPrimary, background = c.canvas, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.subtle, onSurfaceVariant = c.inkSecondary,
            outline = c.border, outlineVariant = c.divider, error = c.danger, onError = c.onPrimary,
            surfaceContainerHigh = c.raised, surfaceContainer = c.surface)
    }
    CompositionLocalProvider(LocalTegamiColors provides c, LocalReducedMotion provides (systemReduced || reducedMotion)) {
        MaterialTheme(colorScheme = scheme, typography = TegamiTypography, content = content)
    }
}
