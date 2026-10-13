package com.tegami.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tegami.domain.TegamiError
import com.tegami.ui.theme.LocalReducedMotion
import com.tegami.ui.theme.Motion
import com.tegami.ui.theme.Shapes
import com.tegami.ui.theme.Spacing
import com.tegami.ui.theme.Tegami

/**
 * Tegami's ink button: flat, like ink on paper. Pressing deepens the ink and settles it slightly, as if the
 * button were pressed into the page. Reduced motion keeps the colour change and drops the settle.
 */
@Composable
private fun InkButton(face: Color, pressedFace: Color, border: Color?, enabled: Boolean, modifier: Modifier,
                      onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val down = pressed && enabled
    val fill by animateColorAsState(if (down) pressedFace else face, tween(Motion.micro), label = "ink")
    val settle by animateFloatAsState(if (down && !reduced) 0.98f else 1f, tween(Motion.micro), label = "settle")
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).scale(settle).background(fill, Shapes.button)
        .then(if (border != null) Modifier.border(1.dp, border, Shapes.button) else Modifier)
        .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = Spacing.standard), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically, content = content)
}

private val ButtonLabel @Composable get() = MaterialTheme.typography.labelLarge

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false,
                  onClick: () -> Unit) {
    val c = Tegami.colors
    val on = enabled && !loading
    InkButton(if (on) c.action else c.subtle, c.primaryPressed, null, on,
        modifier.semantics { if (loading) stateDescription = "Working" }, onClick) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), color = c.ink, strokeWidth = 2.dp)
            Spacer(Modifier.width(Spacing.related))
        }
        Text(text, style = ButtonLabel, color = when { on -> c.onAction; loading -> c.ink; else -> c.inkTertiary })
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Tegami.colors
    InkButton(c.surface, c.subtle.compositeOver(c.surface), c.border, enabled, modifier, onClick) {
        Text(text, style = ButtonLabel, color = if (enabled) c.ink else c.inkTertiary)
    }
}

/** The light button that sits on the hero card. */
@Composable
fun HeroButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Tegami.colors
    InkButton(Color.White, Color.White.copy(alpha = 0.82f).compositeOver(c.hero), null, true, modifier, onClick) {
        Text(text, style = ButtonLabel, color = c.hero)
    }
}

@Composable
fun TextAction(text: String, modifier: Modifier = Modifier, color: Color = Tegami.colors.primary, onClick: () -> Unit) =
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }

enum class Tone { Neutral, Positive, Warning, Danger, Info, Seal }

@Composable
fun toneColors(tone: Tone): Pair<Color, Color> {
    val c = Tegami.colors
    return when (tone) {
        Tone.Neutral -> c.inkSecondary to c.subtle
        Tone.Positive -> c.positive to c.positiveTint
        Tone.Warning -> c.warning to c.warningTint
        Tone.Danger -> c.danger to c.dangerTint
        // Gold leaf, not the brand crimson: an informational status must never read as an alarm.
        Tone.Info -> c.ink to c.gold.copy(alpha = 0.22f)
        Tone.Seal -> c.seal to c.seal.copy(alpha = 0.10f)
    }
}

/** Small capsule status. Colour changes animate, so a confirmed→finalized change is visible but calm. */
@Composable
fun StatusPill(text: String, tone: Tone = Tone.Neutral, modifier: Modifier = Modifier) {
    val (fg, bg) = toneColors(tone)
    val fgA by animateColorAsState(fg, tween(Motion.control), label = "pillFg")
    val bgA by animateColorAsState(bg, tween(Motion.control), label = "pillBg")
    Row(
        modifier.background(bgA, Shapes.pill).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(fgA, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = fgA, maxLines = 1)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Tegami.colors.inkTertiary,
        modifier = modifier.padding(top = Spacing.section, bottom = Spacing.compact).semantics { heading() })

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) =
    Row(modifier.fillMaxWidth().padding(top = Spacing.standard, bottom = Spacing.compact),
        verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.headlineLarge, color = Tegami.colors.ink,
            modifier = Modifier.weight(1f).semantics { heading() })
        trailing()
    }

@Composable
fun Hairline(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(Tegami.colors.divider))

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))

/** A surface card. One level only: never nested. */
@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, contentPadding: PaddingValues = PaddingValues(Spacing.standard),
         content: @Composable ColumnScope.() -> Unit) {
    val c = Tegami.colors
    Column(
        modifier.fillMaxWidth()
            .background(c.surface, Shapes.standard)
            .border(1.dp, c.border, Shapes.standard)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** One label/value line. Long values wrap; [mono] for addresses and signatures. */
@Composable
fun KeyValue(key: String, value: String, mono: Boolean = false, valueColor: Color = Tegami.colors.ink, onClick: (() -> Unit)? = null) {
    val c = Tegami.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Copy $key", onClick = onClick) else Modifier)
            .padding(vertical = Spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.standard),
    ) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
        Text(value, modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = valueColor,
            style = if (mono) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MaterialTheme.typography.titleMedium.fontFamily),
            maxLines = if (mono) 2 else 3, overflow = TextOverflow.Ellipsis)
    }
}

/** A plain statement with a leading mark; used for "what we know / what we don't" lists. */
@Composable
fun FactLine(text: String, tone: Tone = Tone.Neutral) {
    val (fg, _) = toneColors(tone)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).background(fg, CircleShape))
        Spacer(Modifier.width(Spacing.related))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Tegami.colors.ink)
    }
}

@Composable
fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Tegami.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(role = Role.Switch, onClickLabel = title) { onChange(!checked) }
            .padding(vertical = Spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
        }
        Spacer(Modifier.width(Spacing.standard))
        Switch(checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = c.action, checkedThumbColor = c.onAction))
    }
}

/** Centered message: empty and error states. */
@Composable
fun MessageState(title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    val c = Tegami.colors
    Column(modifier.fillMaxWidth().padding(vertical = Spacing.major, horizontal = Spacing.standard),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink, textAlign = TextAlign.Center)
        Gap(6)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Gap(8)
            TextAction(action, onClick = onAction)
        }
    }
}

@Composable
fun ErrorBanner(error: TegamiError, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = Tegami.colors
    // A cancellation is the person's choice, not a failure: shown calmly, without the danger colour or mark.
    val calm = error.isCancellation
    val fg = if (calm) c.inkSecondary else c.danger
    Row(
        modifier.fillMaxWidth().background(if (calm) c.subtle else c.dangerTint, Shapes.standard)
            .padding(horizontal = Spacing.standard, vertical = Spacing.compact)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!calm) {
            // A mark as well as a colour, so the state never depends on colour alone.
            Canvas(Modifier.size(14.dp)) {
                val w = 2.dp.toPx()
                drawLine(fg, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), w, StrokeCap.Round)
                drawLine(fg, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), w, StrokeCap.Round)
            }
            Spacer(Modifier.width(Spacing.related))
        }
        Text(error.userMessage, style = MaterialTheme.typography.bodyMedium, color = fg, modifier = Modifier.weight(1f))
        if (onRetry != null) TextAction(if (calm) "Try again" else "Retry", color = if (calm) c.primary else c.danger, onClick = onRetry)
    }
}

@Composable
fun LoadingRow(text: String, modifier: Modifier = Modifier) {
    val c = Tegami.colors
    Row(modifier.fillMaxWidth().padding(vertical = Spacing.section).semantics(mergeDescendants = true) { contentDescription = text },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), color = c.primary, strokeWidth = 2.dp)
        Spacer(Modifier.width(Spacing.related))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
    }
}

/** Always visible outside mainnet: this app is showing test assets on a test network. */
@Composable
fun NetworkBadge(network: String, symbol: String, short: Boolean = false) {
    if (!network.contains("testnet", ignoreCase = true)) return
    StatusPill(if (short) network else "$network · $symbol is a test asset", Tone.Warning)
}

/** Monospace block for text the user is about to sign or share. */
@Composable
fun CodeBlock(text: String, modifier: Modifier = Modifier) {
    val c = Tegami.colors
    Text(text, modifier.fillMaxWidth().background(c.subtle, Shapes.small).padding(Spacing.related),
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = c.ink)
}

fun shortAddress(a: String) = if (a.length > 12) "${a.take(if (a.startsWith("0x")) 6 else 4)}…${a.takeLast(4)}" else a

/** Copies to the clipboard; [sensitive] hides the preview on Android 13+ (statements, addresses). */
fun copyToClipboard(context: Context, label: String, text: String, sensitive: Boolean = false) {
    val clip = ClipData.newPlainText(label, text)
    if (sensitive && Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}

/**
 * A collapsible explainer (privacy notes, limits, "what this means"): closed by default so screens stay
 * scannable, one tap to read. The open state survives rotation.
 */
@Composable
fun Expandable(title: String, glyph: Glyph? = null, tone: Tone = Tone.Info, initiallyOpen: Boolean = false,
               content: @Composable ColumnScope.() -> Unit) {
    val c = Tegami.colors
    var open by rememberSaveable(title) { mutableStateOf(initiallyOpen) }
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(Motion.control, easing = Motion.Standard), label = "chevron")
    Column(
        Modifier.fillMaxWidth().background(c.surface, Shapes.standard).border(1.dp, c.border, Shapes.standard)
            .animateContentSize(tween(if (LocalReducedMotion.current) 0 else Motion.control, easing = Motion.Standard)),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClickLabel = if (open) "Collapse" else "Expand") { open = !open }
                .padding(horizontal = Spacing.standard, vertical = Spacing.compact)
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) {
                IconTile(glyph, tone, size = 36.dp)
                Spacer(Modifier.width(Spacing.related))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
            Canvas(Modifier.size(20.dp).rotate(turn)) {
                val w = size.width
                drawPath(Path().apply { moveTo(w * 0.22f, w * 0.38f); lineTo(w * 0.5f, w * 0.64f); lineTo(w * 0.78f, w * 0.38f) },
                    c.inkSecondary, style = Stroke(w * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (open) Column(Modifier.padding(start = Spacing.standard, end = Spacing.standard, bottom = Spacing.standard), content = content)
    }
}

/** A paragraph inside an explainer. */
@Composable
fun Prose(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = Tegami.colors.inkSecondary,
    modifier = Modifier.padding(bottom = Spacing.compact))

/** Choice slips: paper with a hairline edge; the chosen one is inked solid. [columns] per row. */
@Composable
fun <T> ChoiceTiles(options: List<T>, selected: T?, columns: Int, label: (T) -> String, onSelect: (T) -> Unit) {
    val c = Tegami.colors
    val view = androidx.compose.ui.platform.LocalView.current
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.compact)) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.compact)) {
                row.forEach { o ->
                    val on = o == selected
                    val fill by animateColorAsState(if (on) c.action else c.surface, tween(Motion.control), label = "slipFill")
                    val ink by animateColorAsState(if (on) c.onAction else c.ink, tween(Motion.control), label = "slipInk")
                    Box(Modifier.weight(1f).heightIn(min = 48.dp)
                        .background(fill, Shapes.small)
                        .border(1.dp, if (on) c.action else c.border, Shapes.small)
                        .selectable(on, role = Role.RadioButton) { if (!on) com.tegami.ui.theme.Haptics.tick(view); onSelect(o) }
                        .padding(horizontal = Spacing.compact, vertical = 10.dp),
                        contentAlignment = Alignment.Center) {
                        Text(label(o), style = ButtonLabel, color = ink, textAlign = TextAlign.Center)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
