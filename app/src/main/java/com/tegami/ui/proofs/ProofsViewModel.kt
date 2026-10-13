package com.tegami.ui.proofs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tegami.config.NetworkConfig
import com.tegami.data.AccountRepository
import com.tegami.data.Disclosure
import com.tegami.data.DisclosureLog
import com.tegami.data.LetterRepository
import com.tegami.domain.Money
import com.tegami.domain.TegamiError
import com.tegami.proof.DraftResult
import com.tegami.proof.SignedStatement
import com.tegami.proof.StatementBuilder
import com.tegami.proof.StatementCodec
import com.tegami.proof.StatementDraft
import android.app.Activity
import com.tegami.evm.EvmKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

enum class PeriodPreset(val label: String) { Last30("Last 30 days"), ThisMonth("This month"), Last90("Last 3 months"), Last365("Last 12 months") }

data class ProofForm(
    val amount: String = "",
    val from: LocalDate,
    val to: LocalDate,
    val preset: PeriodPreset? = PeriodPreset.Last30,
    val verifier: String = "",
    val validityDays: Long = StatementBuilder.DEFAULT_VALIDITY_DAYS,
)

sealed interface SignStep {
    data object Idle : SignStep
    data object AwaitingWallet : SignStep
    data class Signed(val signed: SignedStatement, val link: String, val shared: Boolean = false) : SignStep
    data class Failed(val error: TegamiError) : SignStep
}

data class ProofsUi(
    val form: ProofForm,
    /** Set when the user asks to review; null while editing. */
    val review: StatementDraft? = null,
    val formError: String? = null,
    val step: SignStep = SignStep.Idle,
)

@HiltViewModel
class ProofsViewModel @Inject constructor(
    private val config: NetworkConfig,
    private val accounts: AccountRepository,
    private val letters: LetterRepository,
    private val log: DisclosureLog,
    saved: SavedStateHandle,
) : ViewModel() {
    private val today get() = LocalDate.now(ZoneOffset.UTC)

    private val _ui = MutableStateFlow(ProofsUi(form = ProofForm(from = today.minusDays(29), to = today)))
    val ui: StateFlow<ProofsUi> = _ui

    init {
        // Pre-fill from navigation (Guardian proposal or a letter): ?amount=&from=&to=&verifier=
        val from = saved.get<String>("from")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val to = saved.get<String>("to")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        _ui.update { s ->
            s.copy(form = s.form.copy(
                amount = saved.get<String>("amount")?.take(20) ?: s.form.amount,
                verifier = saved.get<String>("verifier")?.take(60) ?: s.form.verifier,
                from = from ?: s.form.from, to = to ?: s.form.to,
                preset = if (from != null || to != null) null else s.form.preset,
            ))
        }
    }

    fun setAmount(v: String) = edit { it.copy(amount = v.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(20)) }
    fun setVerifier(v: String) = edit { it.copy(verifier = v.take(60)) }
    fun setValidity(days: Long) = edit { it.copy(validityDays = days) }
    fun setPreset(p: PeriodPreset) = edit {
        val t = today
        val from = when (p) {
            PeriodPreset.Last30 -> t.minusDays(29)
            PeriodPreset.ThisMonth -> t.withDayOfMonth(1)
            PeriodPreset.Last90 -> t.minusMonths(3).plusDays(1)
            PeriodPreset.Last365 -> t.minusYears(1).plusDays(1)
        }
        it.copy(preset = p, from = from, to = t)
    }

    private fun edit(f: (ProofForm) -> ProofForm) = _ui.update { it.copy(form = f(it.form), formError = null, review = null, step = SignStep.Idle) }

    /** Builds the exact statement and disclosure, without signing anything. */
    fun review() {
        val account = accounts.account.value
        if (account == null) { _ui.update { it.copy(formError = "Sign in with your passkey first.") }; return }
        val f = _ui.value.form
        val result = StatementBuilder.draft(
            config = config, recipient = account.address, letters = letters.state.value.letters,
            threshold = Money.parse(f.amount, config.asset.decimals, config.asset.symbol),
            from = f.from, to = f.to, verifier = f.verifier, today = today, validityDays = f.validityDays,
        )
        _ui.update {
            when (result) {
                is DraftResult.Ready -> it.copy(review = result.draft, formError = null)
                is DraftResult.Invalid -> it.copy(review = null, formError = result.reason)
            }
        }
    }

    fun backToEdit() = _ui.update { it.copy(review = null, step = SignStep.Idle) }

    /**
     * The passkey unlocks the account key (one biometric prompt unless a session is open) and the statement
     * text is signed on the device. The signature is verified before Tegami offers to share it.
     */
    fun sign(activity: Activity) {
        val draft = _ui.value.review ?: return
        if (_ui.value.step == SignStep.AwaitingWallet) return // no double submission
        _ui.update { it.copy(step = SignStep.AwaitingWallet) }
        viewModelScope.launch {
            val step = try {
                val message = draft.statement.message()
                val sig = EvmKeys.personalSign(message, accounts.signingKey(activity))
                check(EvmKeys.verifyPersonal(draft.statement.recipient, message, sig)) { "signature did not verify" }
                val signed = SignedStatement(draft.statement, sig)
                SignStep.Signed(signed, StatementCodec.link(signed))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SignStep.Failed(TegamiError.from(e))
            }
            _ui.update { it.copy(step = step) }
        }
    }

    /** Called when the user actually hands the statement to someone (share sheet or copy). */
    fun recordShared() {
        val step = _ui.value.step as? SignStep.Signed ?: return
        if (step.shared) return
        val s = step.signed.statement
        log.add(Disclosure(Disclosure.Kind.IncomeStatement, s.verifier, System.currentTimeMillis() / 1000, s.evidence.size, s.expiresAt, s.nonce))
        _ui.update { it.copy(step = step.copy(shared = true)) }
    }

    fun startOver() = _ui.update { ProofsUi(form = ProofForm(from = today.minusDays(29), to = today)) }
}
