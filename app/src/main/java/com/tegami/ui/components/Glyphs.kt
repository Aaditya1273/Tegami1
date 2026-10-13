package com.tegami.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tegami.R
import com.tegami.ui.theme.Motion
import com.tegami.ui.theme.Spacing
import com.tegami.ui.theme.Tegami

/**
 * Tegami's icon pack, in the starter app's style: solid, rounded glyphs that sit in a tinted tile and
 * invert (solid tile, light glyph) when selected. Feather, Shield and Coins are ported from the starter;
 * the rest are drawn to match. Every glyph is drawn in a 24-unit box with one fill colour and one
 * [cut] colour for inner detail, so a glyph reads on any tile.
 */
enum class Glyph(@DrawableRes val art: Int) {
    Home(R.drawable.art_home), Envelope(R.drawable.art_envelope), Document(R.drawable.art_statement),
    Shield(R.drawable.art_shield_check), Gear(R.drawable.art_gear), Feather(R.drawable.art_letter),
    Coins(R.drawable.art_coins), Clock(R.drawable.art_clock), Lock(R.drawable.art_lock), Eye(R.drawable.art_eye),
    Question(R.drawable.art_magnifier), Link(R.drawable.art_chain), Check(R.drawable.art_check),
}

/** A 3D icon from the art sheets (crimson wax, cream paper, gold). Decorative: the text beside it says what it means. */
@Composable
fun ArtIcon(@DrawableRes art: Int, size: Dp, modifier: Modifier = Modifier) =
    Image(painterResource(art), contentDescription = null, modifier = modifier.size(size), contentScale = ContentScale.Fit)

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp = 24.dp, cut: Color = Tegami.colors.surface, modifier: Modifier = Modifier) =
    Canvas(modifier.size(size)) { drawGlyph(glyph, color, cut) }

/**
 * The pack's tile: the glyph's 3D art on a soft tinted square. [selected] fills the tile with the tone colour
 * and swaps in the flat glyph, so a selected or verdict tile reads as solid.
 */
@Composable
fun IconTile(glyph: Glyph, tone: Tone = Tone.Info, selected: Boolean = false, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val (fg, tint) = toneColors(tone)
    val bg by animateColorAsState(if (selected) fg else tint, tween(Motion.control), label = "tileBg")
    Box(modifier.size(size).background(bg, RoundedCornerShape(size * 0.25f)), contentAlignment = Alignment.Center) {
        if (selected) GlyphIcon(glyph, Tegami.colors.surface, size * 0.52f, cut = bg.compositeOver(Tegami.colors.surface))
        else ArtIcon(glyph.art, size * 0.84f)
    }
}

/** A card's heading: the pack tile and a title. */
@Composable
fun CardHeader(glyph: Glyph, title: String, tone: Tone = Tone.Info, trailing: @Composable () -> Unit = {}) =
    Row(Modifier.fillMaxWidth().padding(bottom = Spacing.compact), verticalAlignment = Alignment.CenterVertically) {
        IconTile(glyph, tone, size = 40.dp)
        Spacer(Modifier.width(Spacing.related))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Tegami.colors.ink,
            modifier = Modifier.weight(1f).semantics { heading() })
        trailing()
    }

internal fun DrawScope.drawGlyph(g: Glyph, ink: Color, cut: Color) {
    val w = size.width
    val h = size.height
    val u = w / 24f
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(a: Offset, b: Offset, c: Color = cut, width: Float = 1.8f) = drawLine(c, a, b, width * u, StrokeCap.Round)
    when (g) {
        Glyph.Feather -> { // starter: vane + quill
            val vane = Path().apply {
                moveTo(w * 0.5f, h * 0.02f)
                cubicTo(w * 1.0f, h * 0.22f, w * 0.95f, h * 0.62f, w * 0.52f, h * 0.86f)
                cubicTo(w * 0.05f, h * 0.62f, w * 0.0f, h * 0.22f, w * 0.5f, h * 0.02f)
                close()
            }
            drawPath(vane, ink)
            drawPath(Path().apply { moveTo(w * 0.5f, h * 0.1f); quadraticBezierTo(w * 0.53f, h * 0.6f, w * 0.48f, h * 0.98f) },
                cut.copy(alpha = 0.75f), style = Stroke(w * 0.06f, cap = StrokeCap.Round))
        }
        Glyph.Shield -> drawPath(Path().apply { // starter shield
            moveTo(w * 0.5f, h * 0.06f); lineTo(w * 0.88f, h * 0.2f); lineTo(w * 0.88f, h * 0.48f)
            cubicTo(w * 0.88f, h * 0.72f, w * 0.7f, h * 0.86f, w * 0.5f, h * 0.95f)
            cubicTo(w * 0.3f, h * 0.86f, w * 0.12f, h * 0.72f, w * 0.12f, h * 0.48f)
            lineTo(w * 0.12f, h * 0.2f); close()
        }, ink)
        Glyph.Coins -> { // starter coin stack
            val cw = w * 0.8f
            val ch = h * 0.22f
            for (i in 0..2) {
                val y = h * (0.72f - i * 0.24f)
                drawOval(ink, Offset((w - cw) / 2, y), Size(cw, ch))
                drawOval(cut, Offset((w - cw) / 2 + 2.2f * u, y + 1.3f * u), Size(cw - 4.4f * u, ch - 3.2f * u))
            }
        }
        Glyph.Home -> {
            drawPath(Path().apply {
                moveTo(12 * u, 2.5f * u); lineTo(22 * u, 11 * u); lineTo(19.5f * u, 11 * u); lineTo(19.5f * u, 21 * u)
                lineTo(4.5f * u, 21 * u); lineTo(4.5f * u, 11 * u); lineTo(2 * u, 11 * u); close()
            }, ink, style = androidx.compose.ui.graphics.drawscope.Fill)
            drawRoundRect(cut, o(10f, 14f), Size(4 * u, 7 * u), CornerRadius(1.2f * u))
        }
        Glyph.Envelope -> {
            drawRoundRect(ink, o(2f, 5f), Size(20 * u, 14.5f * u), CornerRadius(3 * u))
            drawPath(Path().apply { moveTo(4.5f * u, 8 * u); lineTo(12 * u, 13.2f * u); lineTo(19.5f * u, 8 * u) },
                cut, style = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Glyph.Document -> {
            drawRoundRect(ink, o(4.5f, 2f), Size(15 * u, 20 * u), CornerRadius(3 * u))
            drawPath(Path().apply { moveTo(8.5f * u, 12.5f * u); lineTo(11 * u, 15 * u); lineTo(15.8f * u, 9.5f * u) },
                cut, style = Stroke(2.1f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Glyph.Gear -> {
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val c = center + Offset((kotlin.math.cos(a) * 8.6f * u).toFloat(), (kotlin.math.sin(a) * 8.6f * u).toFloat())
                drawCircle(ink, 2.4f * u, c)
            }
            drawCircle(ink, 8.2f * u, center)
            drawCircle(cut, 3.2f * u, center)
        }
        Glyph.Clock -> {
            drawCircle(ink, 10 * u, center)
            line(center, o(12f, 6.5f), width = 2.1f)
            line(center, o(16f, 14f), width = 2.1f)
        }
        Glyph.Lock -> {
            drawArc(ink, 180f, 180f, false, o(6.5f, 3f), Size(11 * u, 12 * u), style = Stroke(2.4f * u, cap = StrokeCap.Round))
            drawLine(ink, o(6.5f, 9f), o(6.5f, 11f), 2.4f * u, StrokeCap.Round)
            drawLine(ink, o(17.5f, 9f), o(17.5f, 11f), 2.4f * u, StrokeCap.Round)
            drawRoundRect(ink, o(3.5f, 10f), Size(17 * u, 12 * u), CornerRadius(3 * u))
            drawCircle(cut, 1.7f * u, o(12f, 15.2f))
            line(o(12f, 15.5f), o(12f, 18.2f), width = 1.8f)
        }
        Glyph.Eye -> {
            drawPath(Path().apply {
                moveTo(1.5f * u, 12 * u); quadraticBezierTo(12 * u, 1.5f * u, 22.5f * u, 12 * u)
                quadraticBezierTo(12 * u, 22.5f * u, 1.5f * u, 12 * u); close()
            }, ink)
            drawCircle(cut, 4 * u, center)
            drawCircle(ink, 2 * u, center)
        }
        Glyph.Question -> {
            drawCircle(ink, 10 * u, center)
            drawPath(Path().apply {
                moveTo(9 * u, 9.5f * u); cubicTo(9 * u, 6 * u, 15.2f * u, 6 * u, 15 * u, 9.6f * u)
                cubicTo(14.8f * u, 12 * u, 12 * u, 12 * u, 12 * u, 14.2f * u)
            }, cut, style = Stroke(2.1f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(cut, 1.3f * u, o(12f, 17.6f))
        }
        Glyph.Link -> {
            drawCircle(ink, 10 * u, center)
            drawRoundRect(cut, o(5.2f, 9.3f), Size(8 * u, 5.4f * u), CornerRadius(2.7f * u), style = Stroke(1.8f * u))
            drawRoundRect(cut, o(10.8f, 9.3f), Size(8 * u, 5.4f * u), CornerRadius(2.7f * u), style = Stroke(1.8f * u))
        }
        Glyph.Check -> {
            drawCircle(ink, 10 * u, center)
            drawPath(Path().apply { moveTo(7.5f * u, 12.3f * u); lineTo(10.6f * u, 15.3f * u); lineTo(16.5f * u, 9 * u) },
                cut, style = Stroke(2.3f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
