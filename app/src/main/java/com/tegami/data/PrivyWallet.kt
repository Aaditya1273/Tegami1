package com.tegami.data

import android.content.Context
import com.tegami.BuildConfig
import com.tegami.domain.TegamiError
import com.tegami.evm.EvmKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import io.privy.auth.oAuth.OAuthProvider
import io.privy.logging.PrivyLogLevel
import io.privy.sdk.Privy
import io.privy.sdk.PrivyConfig
import io.privy.wallet.ethereum.EmbeddedEthereumWallet
import io.privy.wallet.ethereum.EthereumRpcRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.web3j.crypto.SignedRawTransaction
import org.web3j.crypto.TransactionDecoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Continue with Google" through Privy: the Google account gets a self-custodial embedded wallet (no seed phrase, no
 * other app). It signs income statements and payments inside Tegami; Tegami broadcasts and confirms them on Monad.
 *
 * Nothing it returns is trusted on its own: a signature must recover to the account's address, and a signed
 * transaction's sender is recovered before Tegami broadcasts it.
 */
@Singleton
class PrivyWallet @Inject constructor(@ApplicationContext private val context: Context) {
    /** Builds without both public Privy IDs hide the Google option. */
    val configured: Boolean = BuildConfig.PRIVY_APP_ID.isNotBlank() && BuildConfig.PRIVY_CLIENT_ID.isNotBlank()

    private val privy: Privy by lazy {
        Privy.init(context.applicationContext, PrivyConfig(appId = BuildConfig.PRIVY_APP_ID,
            appClientId = BuildConfig.PRIVY_CLIENT_ID, logLevel = PrivyLogLevel.NONE))
    }

    /** Opens Google sign-in if there is no session, and returns the embedded wallet's address (created once). */
    suspend fun connect(): String = EvmKeys.checksum(wallet(login = true).address)

    /** EIP-191 `personal_sign` by the embedded wallet; the signature must recover to [address]. */
    suspend fun personalSign(address: String, message: String): String {
        val sig = request(address, EthereumRpcRequest.personalSign(message, address))
        if (!EvmKeys.verifyPersonal(address, message, sig)) throw TegamiError.WalletRejected("the signature did not verify")
        return sig
    }

    /** Signs [tx] (Privy's EIP-1559 JSON) and returns the raw signed hex, checked to be signed by [address]. */
    suspend fun signTransaction(address: String, tx: JsonObject): String {
        val signed = request(address, EthereumRpcRequest.ethSignTransaction(tx.toString()))
        val from = runCatching { (TransactionDecoder.decode(signed) as SignedRawTransaction).from }.getOrNull()
        if (from == null || !EvmKeys.sameAddress(from, address)) throw TegamiError.WalletRejected("the transaction was not signed by your wallet")
        return signed
    }

    suspend fun logout() {
        if (configured) runCatching { withContext(Dispatchers.Main) { privy.logout() } }
    }

    private suspend fun request(address: String, request: EthereumRpcRequest): String {
        val w = wallet(login = false)
        if (!EvmKeys.sameAddress(w.address, address)) throw TegamiError.WalletRejected("Google is signed in to a different wallet")
        return w.provider.request(request).getOrElse { throw TegamiError.WalletRejected(it.message ?: "signing failed") }.data
    }

    private suspend fun wallet(login: Boolean): EmbeddedEthereumWallet = withContext(Dispatchers.Main) {
        if (!configured) throw TegamiError.Invalid("This build has no Google sign-in.")
        privy.awaitReady()
        val user = privy.getUser()
            ?: if (login) privy.oAuth.login(OAuthProvider.Google, SCHEME).getOrElse { throw TegamiError.WalletRejected(it.message ?: "Google sign-in was cancelled") }
            else throw TegamiError.WalletRejected("sign in with Google again")
        user.embeddedEthereumWallets.firstOrNull()
            ?: user.createEthereumWallet(false).getOrElse { throw TegamiError.WalletRejected("could not create your wallet: ${it.message}") }
    }

    private companion object {
        const val SCHEME = "tegami-privy://"
    }
}
