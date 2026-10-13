package com.tegami.ui.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tegami.R
import com.tegami.config.NetworkConfig
import com.tegami.ui.components.ArtIcon
import com.tegami.ui.components.FactLine
import com.tegami.ui.components.Fox
import com.tegami.ui.components.Gap
import com.tegami.ui.components.Pose
import com.tegami.ui.components.PrimaryButton
import com.tegami.ui.components.Tone
import com.tegami.ui.components.copyToClipboard
import com.tegami.ui.theme.Haptics
import com.tegami.ui.theme.LocalReducedMotion
import com.tegami.ui.theme.Motion
import com.tegami.ui.theme.Shapes
import com.tegami.ui.theme.Spacing
import com.tegami.ui.theme.Tegami

/** How the person just arrived; the account-ready moment words itself accordingly. */
enum class Arrival { Created, Restored, MetaMask }

/**
 * The account-ready moment, shown only after the repository has created or restored the account. The fox delivers
 * a letter-card carrying the new address; the wax seal stamps it once (success haptic); then the facts appear.
 * Reduced motion: everything is shown at once, with the same words.
 */
@Composable
fun AccountReadyScreen(config: NetworkConfig, address: String, arrival: Arrival, onCopied: () -> Unit, onContinue: () -> Unit) {
    val c = Tegami.colors
    val ctx = LocalContext.current
    val view = LocalView.current
    val reduced = LocalReducedMotion.current
    val card = remember { Animatable(if (reduced) 1f else 0f) }
    val stamp = remember { Animatable(if (reduced) 1f else 0f) }
    val facts = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced) { Haptics.success(view); return@LaunchedEffect }
        kotlinx.coroutines.delay(250)
        card.animateTo(1f, tween(Motion.spatial, easing = Motion.Emphasized))
        Haptics.success(view)
        stamp.animateTo(1f, Motion.stamp)
        facts.animateTo(1f, tween(Motion.screen))
    }
    val title = when (arrival) {
        Arrival.Created -> "Your Tegami account is ready."
        Arrival.Restored -> "Welcome back."
        Arrival.MetaMask -> "MetaMask is connected."
    }
    val linked = when (arrival) {
        Arrival.Created -> "Linked to your passkey"
        Arrival.Restored -> "Restored from your passkey"
        Arrival.MetaMask -> "Your MetaMask account"
    }
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Gap(24)
        Fox(if (arrival == Arrival.Restored) Pose.Wave else Pose.Deliver, 168.dp)
        Gap(12)
        Text(title, style = MaterialTheme.typography.headlineLarge, color = c.ink, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite })
        Gap(20)
        // The letter-card: the address the account receives at, sealed once it is real.
        Box(Modifier.fillMaxWidth().graphicsLayer {
            alpha = card.value.coerceIn(0f, 1f); translationY = (1f - card.value) * 24.dp.toPx()
        }) {
            Column(Modifier.fillMaxWidth().background(c.surface, Shapes.large).border(1.dp, c.gold.copy(alpha = 0.5f), Shapes.large)
                .clickable(role = Role.Button, onClickLabel = "Copy address") { copyToClipboard(ctx, "Address", address); onCopied() }
                .padding(Spacing.section)) {
                Text("MONAD ADDRESS", style = MaterialTheme.typography.labelMedium, color = c.inkTertiary)
                Gap(6)
                Text(address.chunked(14).joinToString("\n"), style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    color = c.ink)
                Gap(8)
                Text("$linked · ${config.networkLabel} · tap to copy", style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
            }
            ArtIcon(R.drawable.art_seal, 72.dp, Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp).graphicsLayer {
                val s = 1.6f - 0.6f * stamp.value
                scaleX = s; scaleY = s; alpha = stamp.value.coerceIn(0f, 1f)
            })
        }
        Gap(20)
        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = facts.value }) {
            FactLine("Receive letters here: share this address with whoever pays you.", Tone.Info)
            when (arrival) {
                Arrival.MetaMask -> FactLine("MetaMask holds the key and asks you to approve every signature and payment.", Tone.Info)
                else -> {
                    FactLine("Sign income statements and payments with your fingerprint or screen lock.", Tone.Info)
                    FactLine("Your key isn't saved on this phone: your passkey recreates it when you sign.", Tone.Positive)
                }
            }
            if (config.isTestNetwork) FactLine("${config.networkLabel}: test money only.", Tone.Warning)
        }
        Spacer(Modifier.height(Spacing.section))
        PrimaryButton("Open my letters", onClick = onContinue)
        Gap(24)
    }
}
