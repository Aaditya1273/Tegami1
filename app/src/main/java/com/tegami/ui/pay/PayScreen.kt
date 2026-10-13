package com.tegami.ui.pay

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tegami.config.NetworkConfig
import com.tegami.data.AccountRepository
import com.tegami.data.Balances
import com.tegami.data.PaymentRepository
import com.tegami.domain.Money
import com.tegami.domain.SealMemo
import com.tegami.domain.TegamiError
import com.tegami.domain.UntrustedText
import com.tegami.ui.components.Card
import com.tegami.ui.components.CardHeader
import com.tegami.ui.components.ErrorBanner
import com.tegami.ui.components.FactLine
import com.tegami.ui.components.FoxSays
import com.tegami.ui.components.Gap
import com.tegami.ui.components.Glyph
import com.tegami.ui.components.KeyValue
import com.tegami.ui.components.Pose
import com.tegami.ui.components.PrimaryButton
import com.tegami.ui.components.ScreenTitle
import com.tegami.ui.components.SealRing
import com.tegami.ui.components.SecondaryButton
import com.tegami.ui.components.TextAction
import com.tegami.ui.components.Tone
import com.tegami.ui.components.shortAddress
import com.tegami.ui.theme.Spacing
import com.tegami.ui.theme.Tegami
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

data class PayUi(
    val to: String = "",
    val amount: String = "",
    val invoice: String = "",
    val note: String = "",
    val balances: Balances? = null,
    val formError: String? = null,
    val progress: String? = null,
    val sentTx: String? = null,
    val error: TegamiError? = null,
)

@HiltViewModel
class PayViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val payments: PaymentRepository,
    private val config: NetworkConfig,
) : ViewModel() {
    private val _ui = MutableStateFlow(PayUi())
    val ui: StateFlow<PayUi> = _ui

    fun loadBalances() {
        val a = accounts.account.value ?: return
        viewModelScope.launch {
            runCatching { payments.balances(a.address) }.onSuccess { b -> _ui.update { it.copy(balances = b) } }
        }
    }

    fun setTo(v: String) = _ui.update { it.copy(to = v.trim(), formError = null) }
    fun setAmount(v: String) = _ui.update { it.copy(amount = v, formError = null) }
    fun setInvoice(v: String) = _ui.update { it.copy(invoice = v.take(40), formError = null) }
    fun setNote(v: String) = _ui.update { it.copy(note = v.take(UntrustedText.MAX), formError = null) }
    fun reset() { _ui.value = PayUi(balances = _ui.value.balances); loadBalances() }

    fun send(activity: Activity) {
        val u = _ui.value
        if (u.progress != null) return
        val amount = Money.parse(u.amount, config.asset.decimals, config.asset.symbol)
        val problem = when {
            !com.tegami.evm.EvmKeys.isAddress(u.to) -> "Enter the recipient's Monad address (0x followed by 40 hex characters)."
            amount == null || amount.units <= 0 -> "Enter an amount greater than zero."
            u.balances != null && amount.units > u.balances.asset.units -> "You have ${u.balances.asset.format()}."
            u.invoice.isNotBlank() && SealMemo.parse(u.invoice) !is SealMemo.Valid -> "Invoice references use letters, numbers, dots, dashes and underscores."
            else -> null
        }
        if (problem != null) { _ui.update { it.copy(formError = problem) }; return }
        _ui.update { it.copy(progress = "Unlocking with your passkey…", error = null) }
        viewModelScope.launch {
            try {
                val key = accounts.signingKey(activity)
                val tx = payments.pay(key, u.to, amount!!, u.invoice.trim(), UntrustedText.sanitize(u.note)) { step ->
                    _ui.update { it.copy(progress = step) }
                }
                _ui.update { it.copy(progress = null, sentTx = tx) }
                loadBalances()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(progress = null, error = TegamiError.from(e)) }
            }
        }
    }
}

/** Gas in MON, rounded for display. */
private fun mon(wei: BigInteger) = BigDecimal(wei).movePointLeft(18).setScale(4, java.math.RoundingMode.DOWN).toPlainString() + " MON"

@Composable
fun PayScreen(
    config: NetworkConfig,
    address: String,
    ui: PayUi,
    onTo: (String) -> Unit,
    onAmount: (String) -> Unit,
    onInvoice: (String) -> Unit,
    onNote: (String) -> Unit,
    onSend: () -> Unit,
    onLoad: () -> Unit,
    onAnother: () -> Unit,
    onBack: () -> Unit,
) {
    val c = Tegami.colors
    val ctx = LocalContext.current
    LaunchedEffect(address) { onLoad() }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter)) {
        TextAction("← Back", color = c.ink, onClick = onBack)
        val sent = ui.sentTx
        if (sent != null) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Letter delivered!", style = MaterialTheme.typography.headlineLarge, color = c.ink, textAlign = TextAlign.Center)
                Gap(6)
                Text("Paid and sealed in one transaction on ${config.networkLabel}.", style = MaterialTheme.typography.bodyLarge,
                    color = c.inkSecondary, textAlign = TextAlign.Center)
                Gap(24)
                SealRing()
                Gap(24)
                FoxSays(Pose.Cheer, "Delivered to ${shortAddress(ui.to)}. It's in their Letters now, with your invoice and note.")
                Gap(20)
                PrimaryButton("Open in Monad Explorer") {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(config.explorerTx(sent))))
                }
                Gap(8)
                SecondaryButton("Send another", onClick = onAnother)
            }
            Gap(32)
            return@Column
        }
        ScreenTitle("Send a sealed payment")
        FoxSays(Pose.Deliver, "I'll carry it: the money, your invoice and a note, sealed in one transaction.")
        Gap(16)
        Card {
            CardHeader(Glyph.Coins, "Your balance", Tone.Positive)
            KeyValue("Account", shortAddress(address), mono = true)
            KeyValue(config.asset.symbol, ui.balances?.asset?.format() ?: "…")
            KeyValue("Network fees", ui.balances?.gasWei?.let(::mon) ?: "…")
            if (config.isTestNetwork) FactLine("Test money only: ${config.asset.symbol} has no value. Fees are paid in testnet MON (faucet.monad.xyz).", Tone.Warning)
        }
        Gap(12)
        OutlinedTextField(ui.to, onTo, label = { Text("Pay to (0x…)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next))
        Gap(8)
        OutlinedTextField(ui.amount, onAmount, label = { Text("Amount (${config.asset.symbol})") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next))
        Gap(8)
        OutlinedTextField(ui.invoice, onInvoice, label = { Text("Invoice reference (optional)") }, singleLine = true,
            supportingText = { Text("Sealed with the payment, e.g. INV-1042.") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ui.note, onNote, label = { Text("Note (optional)") }, maxLines = 3,
            supportingText = { Text("Public on-chain, like the amount.") }, modifier = Modifier.fillMaxWidth())
        ui.formError?.let {
            Gap(4)
            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        ui.error?.let { Gap(8); ErrorBanner(it, onRetry = null) }
        Gap(16)
        PrimaryButton(ui.progress ?: "Pay and seal with passkey", loading = ui.progress != null, onClick = onSend)
        Gap(8)
        FactLine("Your passkey unlocks the key on this phone; nothing is sent to a server. You approve the exact amount and recipient here.", Tone.Info)
        Gap(32)
    }
}
