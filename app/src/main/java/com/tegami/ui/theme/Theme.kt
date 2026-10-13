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
 * Semantic colour tokens, from the art: crimson wax for actions, cream paper, ink black, red-black lacquer for the
 * hero, gold for accents. Screens use these names, never raw hex. The seal colour marks a sealed letter only.
 *
 * Two crimsons on purpose: [action] fills buttons and selected controls and is the same deep crimson in both
 * themes; [primary] is crimson used as text or line, lightened in dark mode so it stays readable. [danger] is a
 * vermilion-orange, never the brand crimson, so an error can't be mistaken for a button. All text pairs ≥ 4.5:1.
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
    /** [action] while pressed: the wax deepens. */
    val primaryPressed: Color,
    /** Fill for primary buttons, selected tiles and progress: deep crimson in both themes. */
    val action: Color,
    val onAction: Color,
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

private val Crimson = Color(0xFFB52635)     // action fill; cream text on it is 6.0:1
private val CrimsonInk = Color(0xFFA51E2D)  // crimson as text on cream: 6.6:1
private val Cream = Color(0xFFF8F1E7)
private val Ink = Color(0xFF1C1517)
private val Vermilion = Color(0xFFC4320A)   // danger: orange-red, distinct from the crimson

val LightColors = TegamiColors(
    canvas = Cream, surface = Color(0xFFFFFCF6), raised = Color.White,
    ink = Ink, inkSecondary = Ink.copy(alpha = 0.74f), inkTertiary = Ink.copy(alpha = 0.62f),
    divider = Ink.copy(alpha = 0.08f), border = Ink.copy(alpha = 0.14f), subtle = Ink.copy(alpha = 0.05f),
    primary = CrimsonInk, onPrimary = Color(0xFFFFF8EE), primaryTint = Crimson.copy(alpha = 0.08f),
    primaryPressed = Color(0xFF8F1A27), action = Crimson, onAction = Color(0xFFFFF8EE),
    hero = Color(0xFF2E0A0C), heroEnd = Color(0xFF7A1218), onHero = Color(0xFFFFF4E6),
    gold = Color(0xFFD4A34E), seal = Color(0xFFC0262D), onSeal = Color(0xFFFFF4E6),
    positive = Color(0xFF1E6B47), positiveTint = Color(0xFF1E6B47).copy(alpha = 0.10f),
    warning = Color(0xFF8A5300), warningTint = Color(0xFF8A5300).copy(alpha = 0.10f),
    danger = Vermilion, dangerTint = Vermilion.copy(alpha = 0.09f),
    disabled = Ink.copy(alpha = 0.30f), isDark = false,
)

private val Night = Color(0xFF120D0E)
private val Linen = Color(0xFFF6EADB)

val DarkColors = TegamiColors(
    canvas = Night, surface = Color(0xFF1D1617), raised = Color(0xFF261D1F),
    ink = Linen, inkSecondary = Linen.copy(alpha = 0.76f), inkTertiary = Linen.copy(alpha = 0.62f),
    divider = Linen.copy(alpha = 0.08f), border = Linen.copy(alpha = 0.16f), subtle = Linen.copy(alpha = 0.06f),
    // Crimson stays crimson in the dark: fills use [action]; [primary] is the readable crimson-rose for text (9.6:1).
    primary = Color(0xFFF0A39B), onPrimary = Color(0xFF2A0B0B), primaryTint = Crimson.copy(alpha = 0.22f),
    primaryPressed = Color(0xFF8F1A27), action = Crimson, onAction = Color(0xFFFFF4E6),
    hero = Color(0xFF2A0A0C), heroEnd = Color(0xFF6A1016), onHero = Color(0xFFFFF4E6),
    gold = Color(0xFFE0B464), seal = Color(0xFFE5574C), onSeal = Color(0xFF2A0B0B),
    positive = Color(0xFF63C493), positiveTint = Color(0xFF63C493).copy(alpha = 0.12f),
    warning = Color(0xFFE6AA4E), warningTint = Color(0xFFE6AA4E).copy(alpha = 0.12f),
    danger = Color(0xFFFF9466), dangerTint = Color(0xFFFF9466).copy(alpha = 0.12f),
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

/**
 * One corner scale for the whole app. Each element gets the radius of its role, so a filter, a field, a card and
 * the hero differ in size but read as one family.
 */
object Shapes {
    /** Filters, choice slips, compact chips. */
    val small = RoundedCornerShape(12.dp)
    /** Text fields, the search field, buttons. */
    val field = RoundedCornerShape(16.dp)
    val button = field
    /** Cards and panels. */
    val standard = RoundedCornerShape(20.dp)
    /** The hero card and full-width moments. */
    val large = RoundedCornerShape(26.dp)
    /** Status capsules only. */
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

    /** Character reaction: the fox lands with a little life when its pose changes. */
    val hop: androidx.compose.animation.core.SpringSpec<Float> =
        androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
    /** A wax seal pressing down: heavier, one overshoot. Used only after a real success (sealed, account ready). */
    val stamp: androidx.compose.animation.core.SpringSpec<Float> =
        androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
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
        // Material components (text fields, dialogs) follow the same corner scale as Tegami's own.
        MaterialTheme(colorScheme = scheme, typography = TegamiTypography, content = content,
            shapes = androidx.compose.material3.Shapes(extraSmall = Shapes.field, small = Shapes.small, medium = Shapes.standard,
                large = Shapes.large, extraLarge = RoundedCornerShape(28.dp)))
    }
}
