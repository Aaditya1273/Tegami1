package com.tegami.data

import android.content.Context
import com.tegami.config.NetworkConfig
import com.tegami.domain.TegamiError
import com.tegami.evm.EvmKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import io.metamask.androidsdk.DappMetadata
import io.metamask.androidsdk.Ethereum
import io.metamask.androidsdk.EthereumFlow
import io.metamask.androidsdk.EthereumRequest
import io.metamask.androidsdk.Result
import io.metamask.androidsdk.SDKOptions
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MetaMask Mobile as an account: Tegami asks, MetaMask shows the request and the user approves it there.
 * Keys never leave MetaMask. Every signature MetaMask returns is checked locally, and transactions are
 * confirmed against Tegami's own RPC, so a misbehaving wallet can't fake a result.
 */
@Singleton
class MetaMaskRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val config: NetworkConfig,
) {
    private val chainHex = "0x" + config.chainId.toString(16)
    private val ethereum by lazy {
        EthereumFlow(Ethereum(context, DappMetadata("Tegami", "https://${config.passkeyRpId ?: "tegami-monad.vercel.app"}"),
            SDKOptions("", mapOf(chainHex to config.rpcUrl))))
    }

    /** Opens MetaMask to approve the connection; returns the selected account, on Monad. */
    suspend fun connect(): String {
        val address = when (val r = ethereum.connect()) {
            is Result.Success.Item -> r.value
            is Result.Success.Items -> r.value.firstOrNull()
            is Result.Error -> throw TegamiError.WalletRejected(r.error.message)
            else -> null
        }?.takeIf(EvmKeys::isAddress) ?: ethereum.selectedAddress.takeIf(EvmKeys::isAddress)
            ?: throw TegamiError.WalletRejected("MetaMask returned no account")
        ensureMonad()
        return EvmKeys.checksum(address)
    }

    /** Switches MetaMask to Monad, adding the network first if MetaMask doesn't know it. */
    private suspend fun ensureMonad() {
        if (ethereum.chainId.equals(chainHex, ignoreCase = true)) return
        val switched = ethereum.sendRequest(EthereumRequest(method = "wallet_switchEthereumChain", params = listOf(mapOf("chainId" to chainHex))))
        if (switched !is Result.Error) return
        val added = ethereum.sendRequest(EthereumRequest(method = "wallet_addEthereumChain", params = listOf(mapOf(
            "chainId" to chainHex,
            "chainName" to config.networkLabel,
            "rpcUrls" to listOf(config.rpcUrl),
            "blockExplorerUrls" to listOf(config.explorerUrl),
            "nativeCurrency" to mapOf("name" to "MON", "symbol" to "MON", "decimals" to 18),
        ))))
        if (added is Result.Error) throw TegamiError.WalletRejected("MetaMask didn't switch to ${config.networkLabel}: ${added.error.message}")
    }

    /** EIP-191 `personal_sign` in MetaMask; the signature must recover to [address] or it is rejected. */
    suspend fun personalSign(address: String, message: String): String {
        ensureMonad()
        val hexMessage = "0x" + message.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
        val sig = when (val r = ethereum.sendRequest(EthereumRequest(method = "personal_sign", params = listOf(hexMessage, address)))) {
            is Result.Success.Item -> r.value
            is Result.Error -> throw TegamiError.WalletRejected(r.error.message)
            else -> throw TegamiError.WalletRejected("no signature returned")
        }
        if (!EvmKeys.verifyPersonal(address, message, sig)) throw TegamiError.WalletRejected("the signature did not verify")
        return sig
    }

    /** `eth_sendTransaction` in MetaMask (MetaMask sets gas and nonce); returns the transaction hash. */
    suspend fun sendTransaction(from: String, to: String, data: String): String {
        ensureMonad()
        val r = ethereum.sendRequest(EthereumRequest(method = "eth_sendTransaction",
            params = listOf(mapOf("from" to from, "to" to to, "value" to "0x0", "data" to data))))
        return when (r) {
            is Result.Success.Item -> r.value.takeIf { it.startsWith("0x") && it.length == 66 }
                ?: throw TegamiError.WalletRejected("MetaMask returned no transaction hash")
            is Result.Error -> throw TegamiError.WalletRejected(r.error.message)
            else -> throw TegamiError.WalletRejected("MetaMask returned no transaction hash")
        }
    }

    fun disconnect() = runCatching { ethereum.disconnect(true) }
}
