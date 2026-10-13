package com.tegami.ui.proofs

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import com.tegami.ui.components.FoxSays
import com.tegami.ui.components.Pose
import com.tegami.ui.components.SealRing
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tegami.config.NetworkConfig
import com.tegami.data.Account
import com.tegami.data.Disclosure
import com.tegami.proof.StatementDraft
import com.tegami.ui.components.Card
import com.tegami.ui.components.ChoiceTiles
import com.tegami.ui.components.CardHeader
import com.tegami.ui.components.Expandable
import com.tegami.ui.components.Glyph
import com.tegami.ui.components.CodeBlock
import com.tegami.ui.components.ErrorBanner
import com.tegami.ui.components.FactLine
import com.tegami.ui.components.Gap
import com.tegami.ui.components.KeyValue
import com.tegami.ui.components.WelcomePanel
import com.tegami.ui.components.PrimaryButton
import com.tegami.ui.components.ScreenTitle
import com.tegami.ui.components.SealMark
import com.tegami.ui.components.SecondaryButton
import com.tegami.ui.components.SectionLabel
import com.tegami.ui.components.StatusPill
import com.tegami.ui.components.TextAction
import com.tegami.ui.components.Tone
import com.tegami.ui.components.copyToClipboard
import com.tegami.ui.components.payerName
import com.tegami.ui.theme.LocalReducedMotion
import com.tegami.ui.theme.Motion
import com.tegami.ui.theme.Spacing
import com.tegami.ui.theme.Tegami
import com.tegami.domain.Seal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ProofsScreen(
    config: NetworkConfig,
    account: Account,
    ui: ProofsUi,
    disclosures: List<Disclosure>,
    lettersLoading: Boolean,
    onAmount: (String) -> Unit,
    onVerifier: (String) -> Unit,
    onPreset: (PeriodPreset) -> Unit,
    onValidity: (Long) -> Unit,
    onReview: () -> Unit,
    onEdit: () -> Unit,
    onSign: () -> Unit,
    onShared: () -> Unit,
    onStartOver: () -> Unit,
    onVerifyOwn: (String) -> Unit,
    onOpenVerifier: () -> Unit,
    onConnectWallet: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter)) {
        ScreenTitle("Proofs")
        val stage = when {
            ui.step is SignStep.Signed -> 2
            ui.review != null -> 1
            else -> 0
        }
        AnimatedContent(stage, label = "proofStage", transitionSpec = {
            fadeIn(tween(if (reduced) Motion.micro else Motion.screen)) togetherWith fadeOut(tween(Motion.micro))
        }) { s ->
            Column {
                when (s) {
                    0 -> Form(config, account, ui, lettersLoading, onAmount, onVerifier, onPreset, onValidity, onReview, onConnectWallet)
                    1 -> Review(ui.review!!, ui.step, onEdit, onSign)
                    else -> Done(ui.step as SignStep.Signed, onShared, onStartOver, onVerifyOwn, onMessage)
                }
            }
        }
        SectionLabel("Verify a statement")
        SecondaryButton("Check a statement someone sent you", onClick = onOpenVerifier)
        if (disclosures.isNotEmpty()) {
            SectionLabel("What you've shared")
            Card {
                disclosures.take(10).forEach { d ->
                    KeyValue(
                        "${if (d.kind == Disclosure.Kind.IncomeStatement) "Statement" else "Evidence"} → ${d.to.ifEmpty { "unnamed" }}",
                        "${d.payments} payment${if (d.payments == 1) "" else "s"} · ${formatDay(d.at)}",
                    )
                }
                FactLine("Statements can't be revoked once shared; they expire on the date inside them.")
            }
        }
        Gap(32)
    }
}

@Composable
private fun Form(
    config: NetworkConfig, account: Account, ui: ProofsUi, lettersLoading: Boolean,
    onAmount: (String) -> Unit, onVerifier: (String) -> Unit, onPreset: (PeriodPreset) -> Unit, onValidity: (Long) -> Unit,
    onReview: () -> Unit, onConnectWallet: () -> Unit,
) {
    val c = Tegami.colors
    Card {
        CardHeader(Glyph.Document, "Income statement")
        Text("“I received at least this much ${config.asset.symbol} from registered payers in this period.” Signed by your passkey account, checkable by anyone with Tegami.",
            style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
    }
    Gap(8)
    Expandable("What a statement reveals", Glyph.Eye, Tone.Warning) {
        FactLine("Not a zero-knowledge proof: the person you share it with sees each payment it lists (amount, payer, date, transaction) and your address.", Tone.Warning)
        FactLine("Tegami lists only as many payments as the claim needs, largest first. Your other payments and your total aren't listed.", Tone.Info)
        FactLine("Your address's public history can be looked up by anyone who has it.")
        FactLine("Statements can't be revoked; they expire on the date inside them.")
    }
    Gap(16)
    if (!account.canSign) {
        WelcomePanel("Signing needs your passkey", "You're watching this address. Sign in with its passkey to sign a statement.",
            "Sign in with a passkey", Pose.Official, onAction = onConnectWallet)
        return
    }
    OutlinedTextField(
        value = ui.form.amount, onValueChange = onAmount, singleLine = true,
        label = { Text("At least (${config.asset.symbol})") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    Gap(12)
    Text("Period (UTC)", style = MaterialTheme.typography.labelMedium, color = c.inkSecondary)
    Gap(6)
    ChoiceTiles(PeriodPreset.entries, ui.form.preset, columns = 2, label = { it.label }, onSelect = onPreset)
    Gap(4)
    Text("${ui.form.from} to ${ui.form.to}", style = MaterialTheme.typography.bodyMedium, color = c.inkTertiary)
    Gap(12)
    OutlinedTextField(
        value = ui.form.verifier, onValueChange = onVerifier, singleLine = true,
        label = { Text("Who is it for?") }, supportingText = { Text("e.g. “Landlord, Flat 4B”. Included in what you sign.") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth(),
    )
    Gap(8)
    Text("Valid for", style = MaterialTheme.typography.labelMedium, color = c.inkSecondary)
    Gap(6)
    ChoiceTiles(listOf(7L, 30L, 90L), ui.form.validityDays, columns = 3, label = { "$it days" }, onSelect = onValidity)
    ui.formError?.let {
        Gap(8)
        Text(it, style = MaterialTheme.typography.bodyMedium, color = c.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    Gap(16)
    PrimaryButton(if (lettersLoading) "Reading letters…" else "Review what you'll share", enabled = !lettersLoading, onClick = onReview)
}

@Composable
private fun Review(draft: StatementDraft, step: SignStep, onEdit: () -> Unit, onSign: () -> Unit) {
    val c = Tegami.colors
    val s = draft.statement
    Card {
        Text("Review before signing", style = MaterialTheme.typography.titleLarge, color = c.ink)
        Gap(8)
        KeyValue("Claim", "≥ ${s.threshold.format()}")
        KeyValue("Period", "${s.from} – ${s.to}")
        KeyValue("For", s.verifier)
        KeyValue("Expires", formatDay(s.expiresAt))
    }
    SectionLabel("${s.verifier} will see")
    Card {
        draft.disclosedLetters.forEach { l ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SealMark(l.seal, 22.dp)
                Text("  ${payerName(l.payer)}", style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
                Text(l.amount!!.format(), style = MaterialTheme.typography.titleMedium, color = c.ink)
            }
        }
        Gap(4)
        FactLine("Each payment's amount, payer, date and transaction, plus your account address.", Tone.Info)
        FactLine("Your other ${draft.selection.qualifyingCount - draft.disclosedLetters.size} qualifying payment(s) and your total (${draft.selection.availableTotal.format()}) are not listed.", Tone.Positive)
        FactLine("Your address's history is public on-chain: someone who looks it up can see other public transfers.", Tone.Warning)
    }
    SectionLabel("Your passkey will unlock signing of this text")
    CodeBlock(s.message())
    Gap(16)
    when (step) {
        is SignStep.Failed -> { ErrorBanner(step.error, onRetry = null); Gap(8) }
        else -> Unit
    }
    PrimaryButton(if (step == SignStep.AwaitingWallet) "Waiting for your passkey…" else "Sign with passkey",
        loading = step == SignStep.AwaitingWallet, onClick = onSign)
    Gap(8)
    SecondaryButton("Edit", enabled = step != SignStep.AwaitingWallet, onClick = onEdit)
}

@Composable
private fun Done(step: SignStep.Signed, onShared: () -> Unit, onStartOver: () -> Unit, onVerifyOwn: (String) -> Unit, onMessage: (String) -> Unit) {
    val c = Tegami.colors
    val ctx = LocalContext.current
    val s = step.signed.statement
    // The sealing moment plays once per signature, then gives way to the share card.
    var moment by rememberSaveable(step.signed.signature) { mutableStateOf(true) }
    if (moment) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Gap(8)
            Text("Statement sealed!", style = MaterialTheme.typography.headlineLarge, color = c.ink, textAlign = TextAlign.Center)
            Gap(6)
            Text("Your passkey account signed it, and the signature checks out on this device.", style = MaterialTheme.typography.bodyLarge,
                color = c.inkSecondary, textAlign = TextAlign.Center)
            Gap(12)
            StatusPill("${s.evidence.size} payment${if (s.evidence.size == 1) "" else "s"} · at least ${s.threshold.format()}", Tone.Info)
            Gap(28)
            SealRing()
            Gap(28)
            FoxSays(Pose.Cheer, "Ready for ${s.verifier}. They can check it in Tegami until ${formatDay(s.expiresAt)}.")
            Gap(24)
            PrimaryButton("Continue") { moment = false }
        }
        return
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SealMark(Seal.Sealed("statement"), 40.dp)
            Column(Modifier.padding(start = Spacing.related).weight(1f)) {
                Text("Signed and sealed", style = MaterialTheme.typography.titleLarge, color = c.ink)
                Text("Your account's signature checked out on this device.", style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
            }
        }
        Gap(8)
        StatusPill(if (step.shared) "Shared" else "Not shared yet", if (step.shared) Tone.Positive else Tone.Neutral)
        Gap(8)
        KeyValue("For", s.verifier)
        KeyValue("Payments listed", s.evidence.size.toString())
        KeyValue("Expires", formatDay(s.expiresAt))
    }
    Gap(16)
    PrimaryButton("Share") {
        val text = "Tegami income statement for ${s.verifier}. Open in the Tegami app to verify:\n${step.link}"
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share statement"))
        onShared()
    }
    Gap(8)
    SecondaryButton("Copy link") {
        copyToClipboard(ctx, "Tegami statement", step.link, sensitive = true); onShared(); onMessage("Statement link copied")
    }
    Gap(8)
    Row {
        TextAction("Check it like a verifier would") { onVerifyOwn(step.link) }
        TextAction("New statement", color = c.inkSecondary, onClick = onStartOver)
    }
}

private val dayFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private fun formatDay(epochSeconds: Long) = Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).format(dayFormat)

