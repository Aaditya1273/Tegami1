package com.tegami.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import com.tegami.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalView
import com.tegami.ui.theme.Haptics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tegami.ui.theme.Motion
import com.tegami.ui.theme.Tegami

enum class Tab(val route: String, val label: String, @DrawableRes val art: Int) {
    Home("home", "Home", R.drawable.art_home), Letters("letters", "Letters", R.drawable.art_envelope),
    Proofs("proofs", "Proofs", R.drawable.art_statement), Guardian("guardian", "Guardian", R.drawable.art_shield_crest),
    Settings("settings", "Settings", R.drawable.art_gear),
}

/** Unselected tab art is shown without colour, so only the current tab carries the crimson. */
private val Quiet = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Tabs: the selected tab's 3D art sits in a tinted pill in full colour; the rest are grey and lighter. */
@Composable
fun BottomBar(selected: Tab?, onSelect: (Tab) -> Unit) {
    val c = Tegami.colors
    val view = LocalView.current
    Column(Modifier.fillMaxWidth().background(c.canvas).navigationBarsPadding()) {
        Hairline()
        Row(Modifier.fillMaxWidth().height(68.dp).selectableGroup()) {
            Tab.entries.forEach { t ->
                val on = t == selected
                val pill by animateColorAsState(if (on) c.primaryTint else c.canvas, tween(Motion.control), label = "pill")
                Column(
                    Modifier.weight(1f).fillMaxSize().selectable(on, role = Role.Tab) {
                        if (!on) Haptics.tick(view)
                        onSelect(t)
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(width = 52.dp, height = 30.dp).background(pill, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                        val lift by animateFloatAsState(if (on) 1f else 0.55f, tween(Motion.control), label = "tabArt")
                        Image(painterResource(t.art), null, Modifier.size(24.dp).alpha(lift),
                            colorFilter = if (on) null else Quiet)
                    }
                    Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (on) c.primary else c.inkSecondary, maxLines = 1)
                }
            }
        }
    }
}
