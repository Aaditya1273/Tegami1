package com.tegami.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tegami.domain.Letter
import com.tegami.domain.LetterRules
import com.tegami.domain.Money
import com.tegami.domain.Payer
import com.tegami.domain.Seal
import com.tegami.domain.Settlement
import com.tegami.ui.theme.LocalReducedMotion
import com.tegami.ui.theme.Motion
import com.tegami.ui.theme.Shapes
import com.tegami.ui.theme.Spacing
import com.tegami.ui.theme.Tegami
import com.tegami.ui.theme.TegamiColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// ---- Presentation vocabulary: one place decides how each claim is worded. ----

fun settlementLabel(s: Settlement) = when (s) {
    Settlement.Finalized -> "Finalized"
    Settlement.Confirmed -> "Confirmed · not final"
}

fun settlementTone(s: Settlement) = when (s) {
    Settlement.Finalized -> Tone.Positive
    Settlement.Confirmed -> Tone.Warning
}

fun sealLabel(s: Seal) = when (s) {
    is Seal.Sealed -> "Sealed · ${s.invoice}"
    is Seal.Untrusted -> "Seal not trusted"
    Seal.Malformed -> "Malformed seal"
    Seal.None -> "No seal"
}

fun sealTone(s: Seal) = when (s) {
    is Seal.Sealed -> Tone.Seal
    is Seal.Untrusted, Seal.Malformed -> Tone.Warning
    Seal.None -> Tone.Neutral
}

fun payerName(p: Payer) = when (p) {
    is Payer.Registered -> p.name
    is Payer.Unregistered -> "Unregistered payer"
}

fun payerTone(p: Payer) = if (p is Payer.Registered) Tone.Info else Tone.Warning

private val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
fun letterTime(l: Letter): String = l.time?.atZone(ZoneId.systemDefault())?.format(dateFormat) ?: "Time not reported"

// ---- The seal mark ----

/**
 * The seal on a letter. Sealed: a vermilion hanko-style stamp carrying a small envelope. Untrusted or malformed: a warning ring. No seal: a dashed ring. Always described for screen readers.
 */
@Composable
fun SealMark(seal: Seal, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val c = Tegami.colors
    val description = when (seal) {
        is Seal.Sealed -> "Sealed with invoice ${seal.invoice}"
        is Seal.Untrusted -> "Seal present but not trusted"
        Seal.Malformed -> "Malformed seal"
        Seal.None -> "Not sealed"
    }
    Canvas(modifier.size(size).semantics { contentDescription = description }) { drawSeal(seal, c) }
}

private fun DrawScope.drawSeal(seal: Seal, c: TegamiColors, center: Offset = this.center, radius: Float = size.minDimension / 2f) {
    when (seal) {
        is Seal.Sealed -> {
            drawCircle(c.seal, radius, center)
            drawCircle(c.onSeal.copy(alpha = 0.35f), radius * 0.80f, center, style = Stroke(radius * 0.06f))
            drawSealGlyph(c.onSeal, center, radius)
        }
        is Seal.Untrusted, Seal.Malformed -> {
            drawCircle(c.warningTint, radius, center)
            drawCircle(c.warning, radius * 0.92f, center, style = Stroke(radius * 0.12f))
            drawLine(c.warning, center + Offset(0f, -radius * 0.42f), center + Offset(0f, radius * 0.12f), radius * 0.16f, StrokeCap.Round)
            drawCircle(c.warning, radius * 0.09f, center + Offset(0f, radius * 0.40f))
        }
        Seal.None -> drawCircle(c.inkTertiary, radius * 0.9f, center,
            style = Stroke(radius * 0.09f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(radius * 0.28f, radius * 0.22f))))
    }
}

/** A small envelope stamped into the seal: reads as "sealed letter" at any size, with no font dependency. */
private fun DrawScope.drawSealGlyph(color: Color, center: Offset, r: Float) {
    val w = r * 0.11f
    val half = Size(r * 1.0f, r * 0.70f)
    val tl = center - Offset(half.width / 2f, half.height / 2f)
    drawRoundRect(color, tl, half, CornerRadius(r * 0.10f), style = Stroke(w, join = StrokeJoin.Round))
    val v = Path().apply {
        moveTo(tl.x + w, tl.y + w)
        lineTo(center.x, center.y + half.height * 0.08f)
        lineTo(tl.x + half.width - w, tl.y + w)
    }
    drawPath(v, color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ---- The envelope (letter detail header) ----

/**
 * An envelope. When [opens] is true it plays once on entry: the flap swings up past the top edge, the seal
 * lifts away with it, and the letter rises out of the pocket. Purely presentational; it says nothing about
 * network status.
 */
@Composable
fun Envelope(seal: Seal, modifier: Modifier = Modifier, opens: Boolean = true, onOpened: () -> Unit = {}) {
    val c = Tegami.colors
    val reduced = LocalReducedMotion.current
    val open = remember { Animatable(if (!opens) 0f else if (reduced) 1f else 0f) }
    LaunchedEffect(opens) {
        if (opens && !reduced) open.animateTo(1f, tween(Motion.seal, delayMillis = 120, easing = Motion.Emphasized))
        if (opens) onOpened()
    }
    val description = if (opens) "Letter, opened" else "Sealed letter"
    Canvas(modifier.fillMaxWidth().aspectRatio(1.75f).semantics { contentDescription = description }) {
        val p = open.value
        val w = size.width
        val h = size.height
        val top = h * 0.30f
        val bodyH = h - top
        val r = CornerRadius(w * 0.035f)
        val foldY = top + bodyH * 0.52f
        val stroke = Stroke(w * 0.006f, join = StrokeJoin.Round)
        val tipY = lerp(top + bodyH * 0.56f, top - h * 0.28f, p)
        val flap = Path().apply { moveTo(0f, top); lineTo(w * 0.5f, tipY); lineTo(w, top); close() }
        // Open flap sits behind everything.
        if (p >= 0.5f) { drawPath(flap, c.raised); drawPath(flap, c.border, style = stroke) }
        // Back of the envelope.
        drawRoundRect(c.subtle, Offset(0f, top), Size(w, bodyH), r)
        // The letter, rising out as the flap opens.
        val sheetTop = top + bodyH * 0.10f - p * h * 0.30f
        val sheetX = w * 0.07f
        val sheetW = w * 0.86f
        drawRoundRect(c.surface, Offset(sheetX, sheetTop), Size(sheetW, bodyH * 0.84f), CornerRadius(w * 0.02f))
        drawRoundRect(c.border, Offset(sheetX, sheetTop), Size(sheetW, bodyH * 0.84f), CornerRadius(w * 0.02f), style = stroke)
        listOf(0.16f, 0.27f, 0.38f).forEachIndexed { i, f ->
            val y = sheetTop + bodyH * f
            drawLine(c.divider, Offset(sheetX + w * 0.07f, y), Offset(sheetX + sheetW * (if (i == 2) 0.55f else 0.86f), y), w * 0.012f, StrokeCap.Round)
        }
        // Once open, a sealed letter keeps its stamp on the sheet.
        if (seal is Seal.Sealed && p > 0f) drawSealFaded(seal, c, Offset(sheetX + sheetW * 0.86f, sheetTop + bodyH * 0.22f), w * 0.048f, alpha = p)
        // Front pocket covers the lower part of the letter.
        val pocket = Path().apply {
            moveTo(0f, top + bodyH * 0.08f); lineTo(w * 0.5f, foldY); lineTo(w, top + bodyH * 0.08f)
            lineTo(w, h - r.x); quadraticBezierTo(w, h, w - r.x, h); lineTo(r.x, h); quadraticBezierTo(0f, h, 0f, h - r.x); close()
        }
        drawPath(pocket, c.raised)
        drawPath(pocket, c.border, style = stroke)
        // Closed flap on top, carrying the seal at its tip.
        if (p < 0.5f) { drawPath(flap, c.raised); drawPath(flap, c.border, style = stroke) }
        if (p < 0.98f) drawSealFaded(seal, c, Offset(w * 0.5f, tipY - w * 0.01f), w * 0.075f, alpha = 1f - p)
    }
}

private fun DrawScope.drawSealFaded(seal: Seal, c: TegamiColors, center: Offset, r: Float, alpha: Float) {
    val faded = c.copy(seal = c.seal.copy(alpha = alpha), onSeal = c.onSeal.copy(alpha = alpha),
        warning = c.warning.copy(alpha = alpha), warningTint = c.warningTint.copy(alpha = c.warningTint.alpha * alpha),
        inkTertiary = c.inkTertiary.copy(alpha = c.inkTertiary.alpha * alpha))
    drawSeal(seal, faded, center, r)
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

// ---- Amounts ----

/** The amount as this user may see it: masked, encrypted, or the number. */
@Composable
fun AmountText(amount: Money?, hidden: Boolean, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier,
               color: Color = Tegami.colors.ink) {
    val c = Tegami.colors
    when {
        amount == null -> Text("Encrypted", style = style, color = c.inkSecondary,
            modifier = modifier.semantics { contentDescription = "Amount encrypted on-chain" })
        hidden -> Text("•••••  ${amount.symbol}", style = style, color = color,
            modifier = modifier.semantics { contentDescription = "Amount hidden" })
        else -> Text(amount.format(), style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
    }
}

// ---- Inbox card ----

/**
 * One letter in the inbox. Arrives with a short rise-and-fade (staggered by [index]) the first time it is shown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LetterCard(letter: Letter, hideAmount: Boolean, index: Int = 0, isNew: Boolean = false, onClick: () -> Unit) {
    val c = Tegami.colors
    val reduced = LocalReducedMotion.current
    val enter = remember(letter.id) { Animatable(if (reduced) 1f else 0f) }
    // A genuinely new letter (it arrived since the last read): a gold outline that glows once and fades.
    val glow = remember(letter.id, isNew) { Animatable(if (isNew) 1f else 0f) }
    LaunchedEffect(letter.id) {
        if (!reduced) enter.animateTo(1f, tween(Motion.spatial, delayMillis = (index.coerceAtMost(8)) * Motion.stagger, easing = Motion.Emphasized))
    }
    LaunchedEffect(letter.id, isNew) { if (isNew && !reduced) { kotlinx.coroutines.delay(900); glow.animateTo(0.25f, tween(1600)) } }
    val attention = LetterRules.needsAttention(letter)
    val registered = letter.payer is Payer.Registered
    val invoice = when (val s = letter.seal) { is Seal.Sealed -> s.invoice; is Seal.Untrusted -> s.invoice; else -> null }
    val a11y = buildString {
        if (isNew) append("New. ")
        append(payerName(letter.payer)); append(". ")
        append(if (hideAmount) "Amount hidden" else letter.amount.format()); append(". ")
        append(if (registered) "Registered payer. " else "Unregistered payer. ")
        append(settlementLabel(letter.settlement)); append(". ")
        append(sealLabel(letter.seal)); append(". ")
        if (attention) append("Needs a look. ")
        append(letterTime(letter))
    }
    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 12.dp.toPx() }
            .background(c.surface, Shapes.standard)
            .border(if (glow.value > 0f) 2.dp else 1.dp,
                if (glow.value > 0f) c.gold.copy(alpha = glow.value.coerceIn(0.25f, 1f)) else c.divider, Shapes.standard)
            .clickable(role = Role.Button, onClickLabel = "Open letter", onClick = onClick)
            .heightIn(min = 84.dp)
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .semantics(mergeDescendants = true) { contentDescription = a11y },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Needs-a-look letters carry a warning edge as well as a labelled capsule.
        Box(Modifier.width(4.dp).fillMaxHeight().background(if (attention) c.warning else Color.Transparent,
            RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)))
        Spacer(Modifier.width(Spacing.related))
        SealMark(letter.seal, 40.dp)
        Spacer(Modifier.width(Spacing.related))
        Column(Modifier.weight(1f).padding(top = Spacing.related, bottom = Spacing.related, end = Spacing.standard)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(payerName(letter.payer), style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(Spacing.compact))
                AmountText(letter.amount, hideAmount, MaterialTheme.typography.titleMedium)
            }
            Text(listOfNotNull(if (registered) "Registered payer" else "Unregistered payer", invoice).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = if (registered) c.inkSecondary else c.warning,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.heightIn(min = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isNew) StatusPill("New", Tone.Seal)
                StatusPill(settlementLabel(letter.settlement), settlementTone(letter.settlement))
                if (attention) StatusPill("! Needs a look", Tone.Warning)
                Text(letterTime(letter), style = MaterialTheme.typography.labelMedium, color = c.inkTertiary, maxLines = 1)
            }
        }
    }
}
