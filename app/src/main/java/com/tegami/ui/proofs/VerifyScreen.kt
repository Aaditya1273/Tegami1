package com.tegami.ui.proofs

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tegami.config.NetworkConfig
import com.tegami.domain.TegamiError
import com.tegami.proof.Check
import com.tegami.proof.StatementCodec
import com.tegami.proof.StatementVerifier
import com.tegami.proof.VerificationReport
import com.tegami.evm.EvmRpc
import com.tegami.evm.SealContract
import com.tegami.ui.IncomingLinks
import com.tegami.ui.components.Card
import com.tegami.ui.components.Hairline
import com.tegami.ui.components.ReceiptRow
import com.tegami.ui.components.Expandable
import com.tegami.ui.components.Glyph
import com.tegami.ui.components.Fox
import com.tegami.ui.components.Pose
import com.tegami.ui.components.ErrorBanner
import com.tegami.ui.components.FactLine
import com.tegami.ui.components.Gap
import com.tegami.ui.components.KeyValue
import com.tegami.ui.components.PrimaryButton
import com.tegami.ui.components.ScreenTitle
import com.tegami.ui.components.SectionLabel
import com.tegami.ui.components.TextAction
import com.tegami.ui.components.Tone
import com.tegami.ui.theme.Haptics
import com.tegami.ui.theme.LocalReducedMotion
import com.tegami.ui.theme.Motion
import com.tegami.ui.theme.Spacing
import com.tegami.ui.theme.Tegami
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VerifyUi(
    val input: String = "",
    val running: Boolean = false,
    val checks: List<Check> = emptyList(),
    val report: VerificationReport? = null,
    val inputError: String? = null,
    /** The check couldn't finish (network); different from "not verified". */
    val incomplete: TegamiError? = null,
    val statementFor: String? = null,
)

@HiltViewModel
class VerifyViewModel @Inject constructor(
    private val config: NetworkConfig,
    private val rpc: EvmRpc,
    links: IncomingLinks,
) : ViewModel() {
    private val _ui = MutableStateFlow(VerifyUi())
    val ui: StateFlow<VerifyUi> = _ui
    private var job: Job? = null

    init {
        viewModelScope.launch {
            links.pending.collect { link ->
                if (link != null) {
                    links.pending.value = null
                    _ui.value = VerifyUi(input = link)
                    verify()
                }
            }
        }
    }

    fun setInput(v: String) = _ui.update { VerifyUi(input = v) }

    fun verify() {
        val decoded = StatementCodec.decode(_ui.value.input)
        val signed = decoded.getOrNull()
        if (signed == null) {
            _ui.update { it.copy(inputError = "That isn't a readable Tegami statement.", report = null, checks = emptyList()) }
            return
        }
        job?.cancel()
        _ui.update { it.copy(running = true, checks = emptyList(), report = null, inputError = null, incomplete = null,
            statementFor = signed.statement.verifier) }
        job = viewModelScope.launch {
            try {
                val verifier = StatementVerifier(config,
                    readFinalized = { to, i ->
                        val count = SealContract.decodeCount(rpc.call(config.sealContract, SealContract.letterCountCall(to), "finalized"))
                        if (i >= count) null
                        else SealContract.decodeLetter(i, rpc.call(config.sealContract, SealContract.letterAtCall(to, i), "finalized"))
                    },
                    registeredName = { payer -> SealContract.decodeString(rpc.call(config.sealContract, SealContract.payerNameCall(payer), "finalized")) })
                val report = verifier.verify(signed) { check -> _ui.update { it.copy(checks = it.checks + check) } }
                _ui.update { it.copy(running = false, report = report) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(running = false, incomplete = TegamiError.from(e)) }
            }
        }
    }
}

@Composable
fun VerifyScreen(config: NetworkConfig, ui: VerifyUi, onInput: (String) -> Unit, onVerify: () -> Unit, onBack: () -> Unit) {
    val c = Tegami.colors
    val ctx = LocalContext.current
    val view = LocalView.current
    val reduced = LocalReducedMotion.current
    LaunchedEffect(ui.report) {
        ui.report?.let { if (it.verified) Haptics.success(view) else Haptics.reject(view) }
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter)) {
        TextAction("← Back", color = c.ink, onClick = onBack)
        ScreenTitle("Verify a statement")
        Text("Paste the link or text you were sent. Tegami checks the account signature, then re-reads every listed payment from ${config.networkLabel}.",
            style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
        Gap(12)
        OutlinedTextField(
            value = ui.input, onValueChange = onInput, label = { Text("Statement link") }, minLines = 3, maxLines = 6,
            isError = ui.inputError != null, supportingText = ui.inputError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            TextAction("Paste") {
                val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                clip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.take(40_000)?.let(onInput)
            }
        }
        Gap(8)
        PrimaryButton(if (ui.running) "Checking…" else "Verify", loading = ui.running, enabled = ui.input.isNotBlank(), onClick = onVerify)

        if (ui.checks.isNotEmpty() || ui.running) {
            SectionLabel("Trust receipt")
            Card {
                ui.checks.forEachIndexed { i, check ->
                    androidx.compose.runtime.key(i) {
                        AnimatedVisibility(remember { MutableTransitionState(false).apply { targetState = true } }, enter = if (reduced) fadeIn(tween(Motion.micro)) else fadeIn(tween(Motion.control)) + expandVertically(tween(Motion.control))) {
                            Column {
                                if (i > 0) Hairline()
                                ReceiptRow(check.title, check.passed, check.detail)
                            }
                        }
                    }
                }
                if (ui.running) Text("Reading from ${config.networkLabel}…", style = MaterialTheme.typography.bodyMedium, color = c.inkTertiary,
                    modifier = Modifier.padding(vertical = 6.dp))
            }
        }
        ui.incomplete?.let { Gap(12); ErrorBanner(it, onRetry = onVerify) }
        ui.report?.let { r ->
            Gap(16)
            Column(
                Modifier.fillMaxWidth().background(if (r.verified) c.positiveTint else c.dangerTint, com.tegami.ui.theme.Shapes.standard)
                    .padding(Spacing.standard).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Fox(if (r.verified) Pose.Verified else Pose.Review, 88.dp)
                    Spacer(Modifier.width(Spacing.related))
                    Text(if (r.verified) "Verified" else "Not verified", style = MaterialTheme.typography.headlineSmall,
                        color = if (r.verified) c.positive else c.danger)
                }
                Gap(6)
                Text(
                    if (r.verified) "The account owner signed this claim, and the listed payments on ${config.networkLabel} support it."
                    else r.checks.lastOrNull { !it.passed }?.detail ?: "A check failed.",
                    style = MaterialTheme.typography.bodyMedium, color = c.ink,
                )
            }
            if (r.verified) {
                SectionLabel("Payments shown to you")
                Card {
                    r.disclosed.forEach { p -> KeyValue(p.payerName, p.amount.format()) }
                    r.total?.let { KeyValue("Total", it.format()) }
                }
            }
            Gap(12)
            Expandable("What this does and doesn't mean", Glyph.Question, Tone.Warning) {
                FactLine("Payers were checked against this app's ${config.registry.name}, not against a live registry.", Tone.Info)
                FactLine("It doesn't show where the payers' money came from.")
                FactLine("It doesn't prove the person showing it to you controls the account, unless they signed it for you by name (“For: ${ui.statementFor ?: "—"}”).")
                if (config.isTestNetwork) FactLine("${config.networkLabel} test data: ${config.asset.symbol} has no value.", Tone.Warning)
            }
        }
        Gap(32)
    }
}
