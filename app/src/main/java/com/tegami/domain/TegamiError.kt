package com.tegami.domain

/** Every failure the app shows, with a message written for people. Never carries secrets or amounts. */
sealed class TegamiError(message: String) : Exception(message) {
    data class Network(val detail: String) : TegamiError("Network error: $detail")
    data object RateLimited : TegamiError("The public RPC is rate-limiting requests") { private fun readResolve(): Any = RateLimited }
    data class Rpc(val code: Int, val detail: String) : TegamiError("RPC error $code: $detail")
    data class Parse(val detail: String) : TegamiError("Unexpected response: $detail")
    data object PasskeyUnavailable : TegamiError("No passkey provider with PRF support") { private fun readResolve(): Any = PasskeyUnavailable }
    data class WalletRejected(val detail: String) : TegamiError("Passkey request declined: $detail")
    data object NotConnected : TegamiError("No account unlocked") { private fun readResolve(): Any = NotConnected }
    data object WatchOnly : TegamiError("This address is watch-only") { private fun readResolve(): Any = WatchOnly }
    data class Invalid(val detail: String) : TegamiError(detail)

    /** Short, actionable copy for the UI. */
    val userMessage: String
        get() = when (this) {
            is Network -> "Can't reach the Monad network. Check your connection and try again."
            RateLimited -> "The public Monad RPC is busy. Wait a few seconds and try again."
            is Rpc -> "The Monad RPC returned an error ($code): $detail"
            is Parse -> "The network sent something unexpected. Try again."
            PasskeyUnavailable -> "This phone's passkey provider doesn't support the PRF extension. Use Google Password Manager (Android 9+) or 1Password."
            is WalletRejected -> "The passkey request was cancelled."
            NotConnected -> "Unlock your passkey account first."
            WatchOnly -> "You're watching this address. Sign in with its passkey to sign."
            is Invalid -> detail
        }

    companion object {
        fun from(t: Throwable): TegamiError = t as? TegamiError ?: Network(t.message ?: t::class.simpleName.orEmpty())
    }
}
