package com.tegami.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.tegami.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tegami.domain.Letter
import com.tegami.domain.Seal
import com.tegami.proof.IncomeRules
import com.tegami.ui.theme.Haptics
import com.tegami.ui.theme.LocalReducedMotion
import com.tegami.ui.theme.Motion
import com.tegami.ui.theme.Shapes
import com.tegami.ui.theme.Spacing
import com.tegami.ui.theme.Tegami
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/** The fox messenger's poses. Each belongs to one real state, so the pose itself tells you where you are. */
enum class Pose(@DrawableRes val res: Int) {
    Wave(R.drawable.fox_wave),            // welcome
    Content(R.drawable.fox_content),      // all in order
    Deliver(R.drawable.fox_deliver),      // waiting for letters / presenting one
    Read(R.drawable.fox_read),            // reading from the chain, or payments not final yet
    Review(R.drawable.fox_review),        // something needs a look; a statement didn't verify
    Verified(R.drawable.fox_verified),    // a statement verified
    Cheer(R.drawable.fox_cheer),          // your statement was signed and sealed
    Guardian(R.drawable.fox_guardian),    // Guardian, the prepare-only assistant
    Official(R.drawable.fox_official),    // statements and signing
}

/** The fox in a pose. Hops in once whenever the pose changes; reduced motion shows it still. Decorative. */
@Composable
fun Fox(pose: Pose, height: Dp, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val hop = remember(pose) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(pose) { if (!reduced) hop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
    Image(painterResource(pose.res), contentDescription = null, contentScale = ContentScale.Fit,
        modifier = modifier.height(height).graphicsLayer {
            translationY = (1f - hop.value) * 20.dp.toPx(); alpha = hop.value.coerceIn(0f, 1f)
        })
}

/**
 * The fox hands you a note saying where things really stand. The note is paper with a small tail toward
 * the fox; a new message fades in.
 */
@Composable
fun FoxSays(pose: Pose, text: String, modifier: Modifier = Modifier, foxHeight: Dp = 116.dp) {
    val c = Tegami.colors
    val reduced = LocalReducedMotion.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Fox(pose, foxHeight)
        Spacer(Modifier.width(Spacing.related))
        Box(Modifier.weight(1f)
            .drawBehind {
                val r = CornerRadius(16.dp.toPx())
                val b = 1.dp.toPx()
                drawRoundRect(c.surface, cornerRadius = r)
                drawRoundRect(c.border, cornerRadius = r, style = Stroke(b))
                val mid = size.height / 2; val half = 7.dp.toPx(); val tail = 9.dp.toPx()
                drawPath(Path().apply { moveTo(b, mid - half); lineTo(-tail, mid); lineTo(b, mid + half); close() }, c.surface)
                drawLine(c.border, Offset(0f, mid - half), Offset(-tail, mid), b, StrokeCap.Round)
                drawLine(c.border, Offset(-tail, mid), Offset(0f, mid + half), b, StrokeCap.Round)
            }
            .padding(horizontal = Spacing.standard, vertical = 14.dp)) {
            AnimatedContent(text, label = "note", transitionSpec = {
                fadeIn(tween(if (reduced) Motion.micro else Motion.control)) togetherWith fadeOut(tween(Motion.micro))
            }) { t -> Text(t, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = c.ink) }
        }
    }
}

data class DayMark(val date: LocalDate, val paid: Boolean, val today: Boolean)

/** The last seven UTC days (statement periods are UTC too); a day is "paid" if a payment there counts as income. */
fun weekOf(letters: List<Letter>, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): List<DayMark> {
    val paid = letters.filter(IncomeRules::qualifies).mapNotNull { it.time?.atZone(ZoneOffset.UTC)?.toLocalDate() }.toSet()
    return (6 downTo 0).map { today.minusDays(it.toLong()) }.map { DayMark(it, it in paid, it == today) }
}

/** Seven circles: a check for each day income arrived, today ringed, quiet days outlined. */
@Composable
fun WeekStrip(days: List<DayMark>, modifier: Modifier = Modifier) {
    val c = Tegami.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { d ->
            val label = if (d.today) "Today" else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(38.dp)
                    .then(if (d.paid && d.today) Modifier.border(2.dp, c.primary, CircleShape).padding(3.dp) else Modifier)
                    .background(if (d.paid) c.primary else c.surface, CircleShape)
                    .then(if (!d.paid) Modifier.border(2.dp, if (d.today) c.primary else c.border, CircleShape) else Modifier)
                    .semantics { contentDescription = "$label: " + if (d.paid) "income received" else "no counted payment" },
                    contentAlignment = Alignment.Center) {
                    if (d.paid) Canvas(Modifier.size(15.dp)) {
                        val p = Path().apply { moveTo(size.width * 0.12f, size.height * 0.52f); lineTo(size.width * 0.4f, size.height * 0.78f); lineTo(size.width * 0.9f, size.height * 0.22f) }
                        drawPath(p, c.onPrimary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (d.today) c.primary else c.inkTertiary)
            }
        }
    }
}

/** The one dominant card on a screen: red-black lacquer with a gold label, its action inside it. */
@Composable
fun HeroCard(label: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {},
             content: @Composable ColumnScope.() -> Unit) {
    val c = Tegami.colors
    Box(modifier.fillMaxWidth().background(Brush.linearGradient(listOf(c.hero, c.heroEnd)), Shapes.large)
        .border(1.dp, c.gold.copy(alpha = 0.35f), Shapes.large)) {
        // A wax seal pressed faintly into the lacquer, bottom right.
        ArtIcon(R.drawable.art_seal, 150.dp, Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 4.dp).alpha(0.10f))
        Column(Modifier.padding(Spacing.section)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                    color = c.gold, modifier = Modifier.weight(1f))
                trailing()
            }
            content()
        }
    }
}

/**
 * The sealing moment, shown once after the wallet's signature checks out: the ring closes, then the
 * vermilion seal stamps into the middle with a success haptic. Reduced motion: drawn complete.
 */
@Composable
fun SealRing(modifier: Modifier = Modifier, ringSize: Dp = 200.dp) {
    val c = Tegami.colors
    val reduced = LocalReducedMotion.current
    val view = LocalView.current
    val fill = remember { Animatable(if (reduced) 1f else 0f) }
    val stamp = remember { Animatable(if (reduced) 1f else 0f) }
    var stamped by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) {
        if (reduced) { Haptics.success(view); return@LaunchedEffect }
        kotlinx.coroutines.delay(250)
        fill.animateTo(1f, tween(Motion.seal + 180, easing = Motion.Emphasized))
        stamped = true; Haptics.success(view)
        stamp.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    Box(modifier.size(ringSize).semantics { contentDescription = "Statement sealed" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            drawArc(c.subtle, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(c.primary, -90f, 360f * fill.value, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        if (stamped) ArtIcon(R.drawable.art_seal, ringSize * 0.56f, Modifier.graphicsLayer {
            val s = 1.6f - 0.6f * stamp.value
            scaleX = s; scaleY = s; alpha = stamp.value.coerceIn(0f, 1f)
        })
    }
}

/** One line of a trust receipt: what was checked on the left, the result on the right, the detail below. */
@Composable
fun ReceiptRow(title: String, passed: Boolean, detail: String? = null, resultLabel: String = if (passed) "Passed" else "Failed") {
    val c = Tegami.colors
    val color: Color = if (passed) c.positive else c.danger
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp)
        .semantics(mergeDescendants = true) { contentDescription = "$title: $resultLabel. ${detail.orEmpty()}" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary, modifier = Modifier.weight(1f))
            Canvas(Modifier.size(14.dp)) {
                val w = 2.2.dp.toPx()
                if (passed) drawPath(Path().apply { moveTo(size.width * 0.1f, size.height * 0.55f); lineTo(size.width * 0.4f, size.height * 0.82f); lineTo(size.width * 0.92f, size.height * 0.2f) },
                    color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                else { drawLine(color, Offset(2f, 2f), Offset(size.width - 2f, size.height - 2f), w, StrokeCap.Round)
                    drawLine(color, Offset(size.width - 2f, 2f), Offset(2f, size.height - 2f), w, StrokeCap.Round) }
            }
            Spacer(Modifier.width(6.dp))
            Text(resultLabel, style = MaterialTheme.typography.titleMedium, color = color)
        }
        if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium,
            color = if (passed) c.inkTertiary else c.danger, modifier = Modifier.padding(top = 2.dp))
    }
}

/** A state with one clear next step, on paper: the fox, a line, an action. */
@Composable
fun WelcomePanel(title: String, body: String, action: String, pose: Pose? = Pose.Deliver, onAction: () -> Unit) {
    val c = Tegami.colors
    Column(Modifier.fillMaxWidth().background(c.surface, Shapes.large).border(1.dp, c.border, Shapes.large).padding(Spacing.section),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (pose != null) {
            Fox(pose, 168.dp)
            Spacer(Modifier.height(Spacing.standard))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(Spacing.standard))
        PrimaryButton(action, onClick = onAction)
    }
}
