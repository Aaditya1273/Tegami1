package com.tegami.ui.letters

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tegami.config.NetworkConfig
import com.tegami.domain.Letter
import com.tegami.domain.Payer
import com.tegami.domain.Seal
import com.tegami.domain.Settlement
import com.tegami.proof.IncomeRules
import com.tegami.ui.components.AmountText
import com.tegami.ui.components.Card
import com.tegami.ui.components.CardHeader
import com.tegami.ui.components.Expandable
import com.tegami.ui.components.Glyph
import com.tegami.ui.components.Prose
import com.tegami.ui.components.Envelope
import com.tegami.ui.components.FactLine
import com.tegami.ui.components.Gap
import com.tegami.ui.components.KeyValue
import com.tegami.ui.components.MessageState
import com.tegami.ui.components.PrimaryButton
import com.tegami.ui.components.SecondaryButton
import com.tegami.ui.components.SectionLabel
import com.tegami.ui.components.StatusPill
import com.tegami.ui.components.TextAction
import com.tegami.ui.components.Tone
import com.tegami.ui.components.copyToClipboard
import com.tegami.ui.components.letterTime
import com.tegami.ui.components.payerName
import com.tegami.ui.components.payerTone
import com.tegami.ui.components.sealLabel
import com.tegami.ui.components.sealTone
import com.tegami.ui.components.settlementLabel
import com.tegami.ui.components.settlementTone
import com.tegami.ui.components.shortAddress
import com.tegami.ui.theme.LocalReducedMotion
import com.tegami.ui.theme.Motion
import com.tegami.ui.theme.Shapes
import com.tegami.ui.theme.Spacing
import com.tegami.ui.theme.Tegami

/**
 * One opened letter. Answers, in order: who paid, what is known about them, what amount you can see, the real
 * status, the evidence, what remains unverified, and what you can safely do next.
 */
@Composable
fun LetterDetailScreen(
    config: NetworkConfig,
    letter: Letter?,
    canSign: Boolean,
    onBack: () -> Unit,
    onAskGuardian: (String) -> Unit,
    onUseInStatement: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val c = Tegami.colors
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter)) {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.compact), verticalAlignment = Alignment.CenterVertically) {
            TextAction("← Back", color = c.ink, onClick = onBack)
        }
        if (letter == null) {
            MessageState("Letter not loaded", "It may be older than the letters read so far. Go back and load older letters.")
            return@Column
        }
        var opened by rememberSaveable(letter.id) { mutableStateOf(false) }
        val reduced = LocalReducedMotion.current

        Envelope(letter.seal, Modifier.align(Alignment.CenterHorizontally).widthIn(max = 320.dp)) { opened = true }
        Gap(12)
        Text(payerName(letter.payer), style = MaterialTheme.typography.headlineSmall, color = c.ink)
        AnimatedVisibility(opened, enter = fadeIn(tween(if (reduced) Motion.micro else Motion.spatial)) +
            slideInVertically(tween(if (reduced) 0 else Motion.spatial, easing = Motion.Emphasized)) { it / 4 }) {
            Column {
                AmountText(letter.amount, hidden = false, style = MaterialTheme.typography.displayMedium)
            }
        }
        Gap(8)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            StatusPill(settlementLabel(letter.settlement), settlementTone(letter.settlement))
            StatusPill(sealLabel(letter.seal), sealTone(letter.seal))
        }

        Gap(16)
        // 1–2. Who paid, and what is known about them
        Card {
            CardHeader(Glyph.Shield, "Payer", payerTone(letter.payer)) {
                StatusPill(if (letter.payer is Payer.Registered) "Registered" else "Not registered", payerTone(letter.payer))
            }
            Text(payerName(letter.payer), style = MaterialTheme.typography.titleMedium, color = c.ink)
            KeyValue("Address", shortAddress(letter.payer.address), mono = true) {
                copyToClipboard(ctx, "Payer address", letter.payer.address); onMessage("Payer address copied")
            }
            when (val p = letter.payer) {
                is Payer.Registered -> FactLine("Listed in ${p.registry}. Being listed doesn't show where their money came from.", Tone.Info)
                is Payer.Unregistered -> FactLine("Not in ${config.registry.name}. Nobody has vouched for this payer.", Tone.Warning)
            }
        }
        Gap(12)

        // 3. The amount
        Card {
            CardHeader(Glyph.Coins, "Amount", Tone.Positive)
            KeyValue("Amount", letter.amount.format())
        }
        Gap(12)

        // 4. Real status
        Card {
            CardHeader(Glyph.Clock, "Status", settlementTone(letter.settlement))
            KeyValue("Network status", settlementLabel(letter.settlement), valueColor = when (letter.settlement) {
                Settlement.Finalized -> c.positive; Settlement.Confirmed -> c.warning })
            KeyValue("Time", letterTime(letter))
            KeyValue("Block", letter.block.toString(), mono = true)
            if (letter.settlement == Settlement.Confirmed) FactLine("Not final yet. Refresh in a minute; don't rely on it until it's finalized.", Tone.Warning)
        }
        Gap(12)

        // 5. Evidence
        Card {
            CardHeader(Glyph.Document, "Evidence", sealTone(letter.seal))
            KeyValue("Letter", "#${letter.id} on the seal contract", mono = true)
            letter.txHash?.let { tx ->
                KeyValue("Transaction", shortAddress(tx), mono = true) {
                    copyToClipboard(ctx, "Transaction hash", tx); onMessage("Transaction hash copied")
                }
            }
            when (val s = letter.seal) {
                is Seal.Sealed -> FactLine("Seal: the registered payer sent this payment itself, with invoice ${s.invoice} in the same transaction.", Tone.Seal)
                is Seal.Untrusted -> FactLine("Invoice ${s.invoice} is attached, but the seal isn't trusted: the payer isn't registered.", Tone.Warning)
                Seal.Malformed -> FactLine("The invoice field isn't a plain reference, so it's ignored.", Tone.Warning)
                Seal.None -> FactLine("No invoice: the payer attached no invoice reference.", Tone.Neutral)
            }
            if (letter.duplicateInvoice) FactLine("This payer used the same invoice on another payment.", Tone.Warning)
            letter.txHash?.let { tx ->
                TextAction("Open in Monad Explorer") {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(config.explorerTx(tx))))
                }
            }
        }

        letter.note?.let { note ->
            Gap(12)
            Card {
                CardHeader(Glyph.Feather, "Payer's note", if (letter.noteLooksLikeInstruction) Tone.Warning else Tone.Neutral)
                Text("Untrusted text from the sender, shown as written", style = MaterialTheme.typography.labelMedium, color = c.inkTertiary)
                Gap(6)
                Text("“$note”", style = MaterialTheme.typography.bodyLarge, color = c.ink)
                if (letter.noteLooksLikeInstruction) {
                    Gap(8)
                    FactLine("This reads like an instruction. Tegami and Guardian never act on text inside payments.", Tone.Warning)
                }
            }
        }

        // 6. Privacy and what remains unverified, collapsed so the facts above stay scannable
        Gap(16)
        Expandable("Who can see this payment", Glyph.Eye) {
            Prose("The amount is public: anyone who looks up this transaction can see it.")
            Prose("On Monad the sender, recipient, token, invoice, note and time of every payment are public too.")
            Prose("Tegami keeps this letter in memory only and re-reads it from ${config.networkLabel} each time.")
        }
        Gap(8)
        Expandable("What isn't verified", Glyph.Question, Tone.Warning) {
            FactLine("Where the payer's money came from. Tegami doesn't trace funds, so this is not a “clean funds” proof.")
            if (letter.payer is Payer.Registered) FactLine("Who the registry owner is vouching for, beyond this on-chain listing.")
            if (config.isTestNetwork) FactLine("Real-world value: ${config.asset.symbol} is a ${config.networkLabel} test token.", Tone.Warning)
        }

        // 7. Next steps
        SectionLabel("Next")
        if (IncomeRules.qualifies(letter) && canSign) {
            PrimaryButton("Use in an income statement", onClick = onUseInStatement)
            Gap(8)
        }
        SecondaryButton("Ask Guardian about this payment") { onAskGuardian(letter.id) }
        Gap(32)
    }
}
