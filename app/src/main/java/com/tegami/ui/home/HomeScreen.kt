package com.tegami.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tegami.ui.components.HeroButton
import com.tegami.ui.components.HeroCard
import com.tegami.ui.components.IconTile
import com.tegami.ui.components.FoxSays
import com.tegami.ui.components.Pose
import com.tegami.ui.components.WeekStrip
import com.tegami.ui.components.weekOf
import com.tegami.ui.theme.Shapes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tegami.config.NetworkConfig
import com.tegami.data.Account
import com.tegami.data.LettersState
import com.tegami.domain.LetterRules
import com.tegami.domain.Settlement
import com.tegami.proof.IncomeRules
import com.tegami.ui.components.Glyph
import com.tegami.ui.components.ErrorBanner
import com.tegami.ui.components.Gap
import com.tegami.ui.components.LetterCard
import com.tegami.ui.components.payerName
import com.tegami.ui.components.LoadingRow
import com.tegami.ui.components.WelcomePanel
import com.tegami.ui.components.NetworkBadge
import com.tegami.ui.components.SectionLabel
import com.tegami.ui.components.TextAction
import com.tegami.ui.components.Tone
import com.tegami.ui.components.copyToClipboard
import com.tegami.ui.components.shortAddress
import com.tegami.ui.theme.LocalReducedMotion
import com.tegami.ui.theme.Motion
import com.tegami.ui.theme.Spacing
import com.tegami.ui.theme.Tegami
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun HomeScreen(
    config: NetworkConfig,
    account: Account,
    state: LettersState,
    hideAmounts: Boolean,
    onToggleHide: () -> Unit,
    onRefresh: () -> Unit,
    onOpenLetter: (String) -> Unit,
    onSeeAll: () -> Unit,
    onReview: () -> Unit,
    onPrepareStatement: () -> Unit,
    onConnectWallet: () -> Unit,
    onPay: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val c = Tegami.colors
    val ctx = LocalContext.current
    val today = LocalDate.now(ZoneOffset.UTC)
    val from = today.minusDays(29)
    val verified = IncomeRules.availableTotal(state.letters, from, today, config.asset.decimals, config.asset.symbol)
    val counted = state.letters.count { IncomeRules.qualifies(it) && IncomeRules.inPeriod(it, from, today) }
    val attention = state.letters.count(LetterRules::needsAttention)
    val pending = state.letters.count { it.settlement == Settlement.Confirmed }

    val week = weekOf(state.letters, today)
    val weekLetters = state.letters.count { l -> l.time?.atZone(ZoneOffset.UTC)?.toLocalDate()?.let { it > today.minusDays(7) } == true }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.gutter)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.standard), verticalAlignment = Alignment.CenterVertically) {
                Text("Tegami", style = MaterialTheme.typography.headlineLarge, color = c.ink,
                    modifier = Modifier.semantics { heading() })
                Text("  手紙", style = MaterialTheme.typography.titleLarge, color = c.inkTertiary,
                    modifier = Modifier.weight(1f).clearAndSetSemantics {})
                NetworkBadge(config.networkLabel, config.asset.symbol, short = true)
            }
            Text(
                "${greeting()} · ${if (account.canSign) "passkey account" else "watching"} ${shortAddress(account.address)}",
                style = MaterialTheme.typography.bodyMedium, color = c.inkTertiary,
                modifier = Modifier.padding(top = 4.dp).semantics { contentDescription = "Showing letters for ${account.address}" },
            )
            Gap(14)
            // The fox says where things really stand; its pose follows the same state.
            val arrived = state.letters.filter { it.id in state.newIds }
            val (pose, note) = when {
                state.loading && state.letters.isEmpty() -> Pose.Read to "Reading your letters from ${config.networkLabel}…"
                // Only after a real refresh found letters that weren't there before.
                arrived.size == 1 -> Pose.Deliver to "A new letter from ${payerName(arrived[0].payer)} just arrived."
                arrived.size > 1 -> Pose.Deliver to "${arrived.size} new letters just arrived."
                state.letters.isEmpty() -> Pose.Deliver to "No letters yet. Payments to this address will land here."
                attention > 0 -> Pose.Review to "$attention payment${if (attention == 1) " needs" else "s need"} a look before you rely on ${if (attention == 1) "it" else "them"}."
                pending > 0 -> Pose.Read to "$pending payment${if (pending == 1) " isn't" else "s aren't"} final yet. I count only finalized ones."
                counted > 0 -> Pose.Content to "All in order. $counted payment${if (counted == 1) "" else "s"} from registered payers count${if (counted == 1) "s" else ""}."
                else -> Pose.Deliver to "Letters arrived, but none from a registered payer yet."
            }
            FoxSays(pose, note)
            Gap(18)
        }
        item {
            HeroCard("Verified income · 30 days", trailing = {
                Text(if (hideAmounts) "Show" else "Hide", style = MaterialTheme.typography.labelLarge, color = c.onHero,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onToggleHide).padding(8.dp))
            }) {
                Gap(4)
                val reduced = LocalReducedMotion.current
                AnimatedContent(hideAmounts to verified, label = "income", transitionSpec = {
                    fadeIn(tween(if (reduced) Motion.micro else Motion.control)) togetherWith fadeOut(tween(Motion.micro))
                }) { (hidden, total) ->
                    Text(if (hidden) "•••••  ${total.symbol}" else total.format(), style = MaterialTheme.typography.displayMedium, color = c.onHero,
                        modifier = Modifier.semantics { contentDescription = if (hidden) "Verified income hidden" else "Verified income ${total.format()}" })
                }
                Text(
                    when {
                        state.loading && state.letters.isEmpty() -> "Reading payments from ${config.networkLabel}…"
                        counted == 0 -> "No finalized payments from registered payers yet."
                        else -> "From $counted finalized payment${if (counted == 1) "" else "s"} by registered payers."
                    } + (pending.takeIf { it > 0 }?.let { " $it not final yet." } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = c.onHero.copy(alpha = 0.8f),
                )
                Gap(20)
                when {
                    !account.canSign -> HeroButton("Sign in with a passkey to sign", onClick = onConnectWallet)
                    counted > 0 -> HeroButton("Prepare an income statement  →", onClick = onPrepareStatement)
                    else -> Text("Statements unlock once a registered payer's payment is final.",
                        style = MaterialTheme.typography.titleMedium, color = c.onHero)
                }
            }
            if (attention > 0) {
                Gap(12)
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).background(c.surface, Shapes.standard).border(1.dp, c.border, Shapes.standard)
                    .clickable(role = Role.Button, onClick = onReview).padding(horizontal = Spacing.standard, vertical = Spacing.related),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Glyph.Question, Tone.Warning, size = 40.dp)
                    Spacer(Modifier.width(Spacing.related))
                    Column(Modifier.weight(1f)) {
                        Text("$attention need${if (attention == 1) "s" else ""} a look", style = MaterialTheme.typography.titleMedium, color = c.ink)
                        Text("Unregistered payers, reused invoices, odd notes", style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
                    }
                    Text("Review  ›", style = MaterialTheme.typography.labelLarge, color = c.primary)
                }
            }
            if (account.canSign) {
                Gap(12)
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).background(c.surface, Shapes.standard).border(1.dp, c.border, Shapes.standard)
                    .clickable(role = Role.Button, onClick = onPay).padding(horizontal = Spacing.standard, vertical = Spacing.related),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Glyph.Envelope, Tone.Info, size = 40.dp)
                    Spacer(Modifier.width(Spacing.related))
                    Column(Modifier.weight(1f)) {
                        Text("Send a sealed payment", style = MaterialTheme.typography.titleMedium, color = c.ink)
                        Text("Pay anyone, anywhere, with an invoice attached", style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
                    }
                    Text("Pay  ›", style = MaterialTheme.typography.labelLarge, color = c.primary)
                }
            }
            // This week, below the money and the actions: a glance at how often income arrived, in its own panel.
            SectionLabel("This week")
            Column(Modifier.fillMaxWidth().background(c.subtle, Shapes.standard).padding(Spacing.standard)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$weekLetters", style = MaterialTheme.typography.headlineSmall, color = if (week.any { it.paid }) c.primary else c.ink)
                    Spacer(Modifier.width(Spacing.compact))
                    Text("${if (weekLetters == 1) "letter" else "letters"} · ${week.count { it.paid }} of 7 days brought counted income",
                        style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
                }
                Gap(12)
                WeekStrip(week)
            }
        }
        state.error?.let { e -> item { Gap(12); ErrorBanner(e, onRetry = onRefresh) } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Recent letters", Modifier.weight(1f))
                if (state.letters.size > 3) TextAction("See all", onClick = onSeeAll)
                else TextAction(if (state.loading) "Updating…" else "Refresh", onClick = onRefresh)
            }
        }
        when {
            state.loading && state.letters.isEmpty() -> item { LoadingRow("Reading letters…") }
            state.letters.isEmpty() && state.error == null -> item {
                WelcomePanel("No letters yet",
                    "Payments of ${config.asset.symbol} to this address will appear here. Share your address with whoever pays you.",
                    // The fox above already says this; the panel carries only the action.
                    "Copy address", pose = null) {
                    copyToClipboard(ctx, "Address", account.address); onMessage("Address copied")
                }
            }
            else -> itemsIndexed(state.letters.take(3), key = { _, l -> l.id }) { i, l ->
                LetterCard(l, hideAmounts, i, isNew = l.id in state.newIds) { onOpenLetter(l.id) }
                Gap(10)
            }
        }
        item { Gap(40) } // clear of the tab bar
    }
}

private fun greeting() = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening"
}
