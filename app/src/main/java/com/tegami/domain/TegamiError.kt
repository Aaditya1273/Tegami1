package com.tegami.domain

/** Every failure the app shows, with a message written for people. Never carries secrets or amounts. */
sealed class TegamiError(message: String) : Exception(message) {
    data class Network(val detail: String) : TegamiError("Network error: $detail")
    data object RateLimited : TegamiError("The public RPC is rate-limiting requests") { private fun readResolve(): Any = RateLimited }
    data class Rpc(val code: Int, val detail: String) : TegamiError("RPC error $code: $detail")
    data class Parse(val detail: String) : TegamiError("Unexpected response: $detail")
    data object PasskeyUnavailable : TegamiError("No passkey provider with PRF support") { private fun readResolve(): Any = PasskeyUnavailable }
    /** The person closed the passkey sheet. Their choice, not a failure. */
    data object PasskeyCancelled : TegamiError("Passkey request cancelled") { private fun readResolve(): Any = PasskeyCancelled }
    data object PasskeyTimedOut : TegamiError("Passkey request timed out") { private fun readResolve(): Any = PasskeyTimedOut }
    /** Sign-in found no Tegami passkey on this phone or its synced password manager. */
    data object PasskeyNotFound : TegamiError("No Tegami passkey found") { private fun readResolve(): Any = PasskeyNotFound }
    /** Any other Credential Manager failure; [detail] is for logs only and never shown. */
    data class PasskeyFailed(val detail: String) : TegamiError("Passkey request failed: $detail")
    data class WalletRejected(val detail: String) : TegamiError("Passkey request declined: $detail")
    data object NotConnected : TegamiError("No account unlocked") { private fun readResolve(): Any = NotConnected }
    data object WatchOnly : TegamiError("This address is watch-only") { private fun readResolve(): Any = WatchOnly }
    data class Invalid(val detail: String) : TegamiError(detail)

    /** The person chose to stop; the UI shows this calmly rather than as an error. */
    val isCancellation: Boolean get() = this == PasskeyCancelled || (this is WalletRejected && detail == "cancelled")

    /** Short, actionable copy for the UI. */
    val userMessage: String
        get() = when (this) {
            is Network -> "Can't reach the Monad network. Check your connection and try again."
            RateLimited -> "The public Monad RPC is busy. Wait a few seconds and try again."
            is Rpc -> "The Monad RPC returned an error ($code): $detail"
            is Parse -> "The network sent something unexpected. Try again."
            PasskeyUnavailable -> "This phone's passkey manager can't derive an account key (no PRF support). Google Password Manager on Android 9 or later can."
            PasskeyCancelled -> "Cancelled. Nothing was created or signed."
            PasskeyTimedOut -> "The passkey request timed out before it was approved. Try again and confirm with your fingerprint or screen lock."
            PasskeyNotFound -> "No Tegami passkey on this phone. Create an account, or sign in to the Google account that holds your passkey."
            is PasskeyFailed -> "Your passkey couldn't be used just now. Try again."
            is WalletRejected -> "The passkey request was cancelled."
            NotConnected -> "Unlock your passkey account first."
            WatchOnly -> "You're watching this address. Sign in with its passkey to sign."
            is Invalid -> detail
        }

    companion object {
        fun from(t: Throwable): TegamiError = t as? TegamiError ?: Network(t.message ?: t::class.simpleName.orEmpty())
    }
}
