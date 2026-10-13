package com.tegami.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tegami.ui.theme.Haptics
import com.tegami.ui.theme.LocalReducedMotion
import com.tegami.ui.theme.Motion
import com.tegami.ui.theme.Shapes
import com.tegami.ui.theme.Spacing
import com.tegami.ui.theme.Tegami

/** The search field: field geometry, a drawn magnifier, a clear button, and a focus ring in the action crimson. */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = Tegami.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val edge by animateColorAsState(if (focused) c.action else c.border, tween(Motion.control), label = "searchEdge")
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).background(c.surface, Shapes.field)
        .border(if (focused) 2.dp else 1.dp, edge, Shapes.field).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(18.dp)) {
            val w = 2.dp.toPx()
            drawCircle(c.inkTertiary, size.width * 0.34f, Offset(size.width * 0.42f, size.height * 0.42f), style = Stroke(w))
            drawLine(c.inkTertiary, Offset(size.width * 0.67f, size.height * 0.67f), Offset(size.width * 0.95f, size.height * 0.95f), w, StrokeCap.Round)
        }
        Spacer(Modifier.width(Spacing.related))
        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = c.inkTertiary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(value, onValueChange, singleLine = true, interactionSource = source,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink), cursorBrush = SolidColor(c.action),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder })
        }
        if (value.isNotEmpty()) {
            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = "Clear search") { onValueChange("") },
                contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(12.dp)) {
                    val w = 2.dp.toPx()
                    drawLine(c.inkSecondary, Offset.Zero, Offset(size.width, size.height), w, StrokeCap.Round)
                    drawLine(c.inkSecondary, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
                }
            }
        } else Spacer(Modifier.width(10.dp))
    }
}

/**
 * Filters as compact slips (the choice-tile geometry): the selected one is inked crimson and carries a check, so
 * selection never depends on colour alone. Scrolls sideways only when it must, starting at the gutter.
 */
@Composable
fun <T> FilterRow(options: List<T>, selected: T, label: (T) -> String, count: (T) -> Int, onSelect: (T) -> Unit,
                  modifier: Modifier = Modifier) {
    val c = Tegami.colors
    val view = LocalView.current
    LazyRow(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.compact)) {
        items(options) { o ->
            val on = o == selected
            val fill by animateColorAsState(if (on) c.action else c.surface, tween(Motion.control), label = "filterFill")
            val ink by animateColorAsState(if (on) c.onAction else c.ink, tween(Motion.control), label = "filterInk")
            Row(Modifier.heightIn(min = 44.dp).background(fill, Shapes.small)
                .then(if (on) Modifier else Modifier.border(1.dp, c.border, Shapes.small))
                .selectable(on, role = Role.Tab) { if (!on) Haptics.tick(view); onSelect(o) }
                .padding(horizontal = 14.dp)
                .semantics { stateDescription = if (on) "Selected" else "Not selected" },
                verticalAlignment = Alignment.CenterVertically) {
                if (on) {
                    Canvas(Modifier.size(12.dp)) {
                        drawPath(Path().apply { moveTo(size.width * 0.08f, size.height * 0.55f); lineTo(size.width * 0.38f, size.height * 0.85f); lineTo(size.width * 0.95f, size.height * 0.18f) },
                            ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Text(label(o), style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text(count(o).toString(), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.72f))
            }
        }
    }
}

/** A round send action: filled crimson when there is something to send, quiet when empty. */
@Composable
fun SendButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tegami.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val settle by animateFloatAsState(if (pressed && enabled && !reduced) 0.92f else 1f, tween(Motion.micro), label = "send")
    val fill by animateColorAsState(if (enabled) c.action else c.subtle, tween(Motion.control), label = "sendFill")
    Box(modifier.size(52.dp).scale(settle).background(fill, CircleShape)
        .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClickLabel = "Send", onClick = onClick)
        .semantics { contentDescription = "Send" }, contentAlignment = Alignment.Center) {
        val ink = if (enabled) c.onAction else c.inkTertiary
        Canvas(Modifier.size(20.dp)) {
            val w = 2.4.dp.toPx()
            drawLine(ink, Offset(size.width * 0.5f, size.height * 0.88f), Offset(size.width * 0.5f, size.height * 0.14f), w, StrokeCap.Round)
            drawPath(Path().apply { moveTo(size.width * 0.18f, size.height * 0.44f); lineTo(size.width * 0.5f, size.height * 0.12f); lineTo(size.width * 0.82f, size.height * 0.44f) },
                ink, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * Where you are in a short task, from real state only (e.g. Proofs: claim, review, sign). Done steps get a check,
 * the current step is inked, later steps are quiet.
 */
@Composable
fun StepHeader(steps: List<String>, current: Int, modifier: Modifier = Modifier) {
    val c = Tegami.colors
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        contentDescription = "Step ${current + 1} of ${steps.size}: ${steps[current]}"
    }, verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, s ->
            val done = i < current
            val now = i == current
            Box(Modifier.size(26.dp).background(if (done || now) c.action else c.subtle, CircleShape), contentAlignment = Alignment.Center) {
                if (done) Canvas(Modifier.size(11.dp)) {
                    drawPath(Path().apply { moveTo(size.width * 0.08f, size.height * 0.55f); lineTo(size.width * 0.38f, size.height * 0.85f); lineTo(size.width * 0.95f, size.height * 0.18f) },
                        c.onAction, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = if (now) c.onAction else c.inkTertiary)
            }
            Spacer(Modifier.width(6.dp))
            Text(s, style = MaterialTheme.typography.labelLarge, color = if (now) c.ink else c.inkTertiary)
            if (i < steps.lastIndex) Box(Modifier.weight(1f).padding(horizontal = 8.dp).heightIn(min = 2.dp)
                .background(if (done) c.action else c.divider, CircleShape))
        }
    }
}
