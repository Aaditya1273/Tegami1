package com.tegami.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tegami.config.NetworkConfig
import com.tegami.data.AccountRepository
import com.tegami.data.AppPrefs
import com.tegami.data.DisclosureLog
import com.tegami.data.LetterRepository
import com.tegami.domain.TegamiError
import com.tegami.evm.EvmRpc
import android.app.Activity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Passkey prompts need the hosting Activity; screens get it from here. */
val LocalHostActivity = staticCompositionLocalOf<Activity?> { null }

/** A `tegami://verify` link the app was opened with, waiting for the verifier screen. */
@Singleton
class IncomingLinks @Inject constructor() {
    val pending = MutableStateFlow<String?>(null)
}

/** The chain as this device sees it right now, for Settings → Network. */
sealed interface MintState {
    data object Loading : MintState
    data class Loaded(val latestBlock: Long, val finalizedBlock: Long) : MintState
    data class Failed(val error: TegamiError) : MintState
}

/** App-wide state shared by every tab: who we're looking at, their letters, and preferences. */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val letterRepo: LetterRepository,
    private val prefs: AppPrefs,
    private val disclosureLog: DisclosureLog,
    private val links: IncomingLinks,
    private val rpc: EvmRpc,
    val config: NetworkConfig,
) : ViewModel() {
    val account = accounts.account
    val letters = letterRepo.state
    val hideAmounts = prefs.hideAmounts
    val reducedMotion = prefs.reducedMotion
    val secureScreen = prefs.secureScreen
    val disclosures = disclosureLog.entries

    private val _mint = MutableStateFlow<MintState>(MintState.Loading)
    val mint: StateFlow<MintState> = _mint

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-off messages for the snackbar. */
    val messages: SharedFlow<String> = _messages

    init {
        viewModelScope.launch {
            accounts.account.distinctUntilChangedBy { it?.address }.collect { a ->
                if (a != null) letterRepo.refresh(a.address) else letterRepo.clear()
            }
        }
    }

    fun refresh() {
        val a = accounts.account.value ?: return
        viewModelScope.launch { letterRepo.refresh(a.address) }
    }

    fun loadMore() = viewModelScope.launch { letterRepo.loadMore() }

    /** Creates a passkey account, or signs in with an existing passkey when [existing] is true. */
    fun connect(activity: Activity, existing: Boolean = false) = viewModelScope.launch {
        _connecting.value = true
        try {
            if (existing) accounts.signInWithPasskey(activity) else accounts.createPasskeyAccount(activity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _messages.tryEmit(TegamiError.from(e).userMessage)
        } finally {
            _connecting.value = false
        }
    }

    /** Returns an error message, or null when the address is now being watched. */
    fun watch(address: String, label: String = "Watch-only"): String? =
        runCatching { accounts.watch(address, label) }.exceptionOrNull()?.let { TegamiError.from(it).userMessage }

    fun watchDemo(): String? = config.demoRecipient?.let { watch(it, "Demo freelancer (testnet)") } ?: "This build has no demo address."

    fun disconnect() = accounts.disconnect()

    fun loadMint() = viewModelScope.launch {
        _mint.value = MintState.Loading
        _mint.value = try {
            MintState.Loaded(rpc.blockNumber(), rpc.finalizedBlock())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MintState.Failed(TegamiError.from(e))
        }
    }

    fun setHideAmounts(v: Boolean) = prefs.setHideAmounts(v)
    fun setReducedMotion(v: Boolean) = prefs.setReducedMotion(v)
    fun setSecureScreen(v: Boolean) = prefs.setSecureScreen(v)
    fun clearDisclosures() = disclosureLog.clear()
    fun message(text: String) { _messages.tryEmit(text) }

    /** Hands a statement to the verifier screen (it can be long, so it doesn't travel in the route). */
    fun openStatement(link: String) { links.pending.value = link }
}
