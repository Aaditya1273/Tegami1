package com.tegami.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tegami.config.NetworkConfig
import com.tegami.domain.TegamiError
import com.tegami.evm.EvmKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MetaMask Mobile as an account. MetaMask 8.x signs inside its own dapp browser (the native SDK's service is
 * gone), so Tegami opens web/mm.html on its own domain there via MetaMask's deep link; the page asks MetaMask to
 * connect, sign or send, and returns the result with a `tegami://metamask?id=…` link.
 *
 * Nothing returned is trusted on its own: each reply must carry the one-time request id, signatures are verified
 * on the phone, and transactions are confirmed against Monad by [PaymentRepository]. Keys never leave MetaMask.
 */
@Singleton
class MetaMaskRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: NetworkConfig,
) {
    private val chainHex = "0x" + config.chainId.toString(16)
    private var pending: Pair<String, CompletableDeferred<Uri>>? = null

    /** Called by MainActivity for `tegami://metamask` links. Ignores replies that don't match the open request. */
    fun deliver(uri: Uri): Boolean {
        val (id, waiter) = pending ?: return false
        if (uri.getQueryParameter("id") != id) return false
        pending = null
        return waiter.complete(uri)
    }

    /** Connects MetaMask and proves ownership: MetaMask signs a one-time challenge that must recover to the address. */
    suspend fun connect(): String {
        val nonce = randomId()
        val challenge = "Connect MetaMask to Tegami on ${config.networkLabel}.\nThis signature costs nothing and moves no money.\nNonce: $nonce"
        val reply = roundTrip("connect", mapOf("msg" to challenge), nonce)
        val address = reply.getQueryParameter("address")?.takeIf(EvmKeys::isAddress) ?: throw TegamiError.WalletRejected("MetaMask returned no account")
        val sig = reply.getQueryParameter("sig") ?: throw TegamiError.WalletRejected("MetaMask returned no signature")
        if (!EvmKeys.verifyPersonal(address, challenge, sig)) throw TegamiError.WalletRejected("the connection signature did not verify")
        return EvmKeys.checksum(address)
    }

    /** EIP-191 `personal_sign` in MetaMask; the signature must recover to [address] or it is rejected. */
    suspend fun personalSign(address: String, message: String): String {
        val reply = roundTrip("sign", mapOf("msg" to message, "from" to address))
        val sig = reply.getQueryParameter("sig") ?: throw TegamiError.WalletRejected("MetaMask returned no signature")
        if (!EvmKeys.verifyPersonal(address, message, sig)) throw TegamiError.WalletRejected("the signature did not verify")
        return sig
    }

    /** `eth_sendTransaction` in MetaMask (MetaMask sets gas and nonce). Returns the hash; the caller confirms it on-chain. */
    suspend fun sendTransaction(from: String, to: String, data: String, label: String): String {
        val reply = roundTrip("send", mapOf("from" to from, "to" to to, "data" to data, "label" to label))
        return reply.getQueryParameter("hash")?.takeIf { Regex("^0x[0-9a-fA-F]{64}$").matches(it) }
            ?: throw TegamiError.WalletRejected("MetaMask returned no transaction hash")
    }

    private suspend fun roundTrip(op: String, params: Map<String, String>, id: String = randomId()): Uri {
        val waiter = CompletableDeferred<Uri>()
        pending = id to waiter
        val query = (params + mapOf("op" to op, "id" to id, "chain" to chainHex, "chainName" to config.networkLabel,
            "rpc" to config.rpcUrl, "explorer" to config.explorerUrl))
            .entries.joinToString("&") { "${it.key}=${Uri.encode(it.value)}" }
        val host = config.passkeyRpId ?: throw TegamiError.Invalid("This build has no MetaMask bridge page.")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://metamask.app.link/dapp/$host/mm.html?$query"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent.setPackage("io.metamask")) }
            .getOrElse { pending = null; throw TegamiError.Invalid("Install MetaMask to use it with Tegami.") }
        val reply = try {
            withTimeout(TIMEOUT_MS) { waiter.await() }
        } catch (e: TimeoutCancellationException) {
            pending = null
            throw TegamiError.WalletRejected("no answer from MetaMask")
        }
        reply.getQueryParameter("error")?.let { throw TegamiError.WalletRejected(it.take(160)) }
        return reply
    }

    private fun randomId() = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private companion object {
        const val TIMEOUT_MS = 180_000L // time for the person to approve in MetaMask
    }
}
