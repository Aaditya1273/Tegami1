package com.tegami.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything network-specific, loaded once from assets/<network>.json. The build picks the network
 * (BuildConfig.NETWORK); nothing switches it at runtime.
 */
@Serializable
data class NetworkConfig(
    val network: String,
    val chainId: Long,
    val rpcUrl: String,
    val explorerUrl: String,
    /** The TegamiSeal contract: sealed payments and the payer registry live here. */
    val sealContract: String,
    val asset: Asset,
    val registry: Registry,
    /** An address that scripts/monad_setup.sh pays, for trying Tegami without an account. */
    val demoRecipient: String? = null,
    /** Domain that vouches for this app's passkeys (serves /.well-known/assetlinks.json). Null: passkeys off. */
    val passkeyRpId: String? = null,
    /** GraphQL endpoint of Tegami's Envio indexer (indexer/). Null: letters are read from the contract only. */
    val indexerUrl: String? = null,
) {
    @Serializable
    data class Asset(val address: String, val symbol: String, val name: String, val decimals: Int)

    /**
     * The payer policy. The authoritative list is on-chain (TegamiSeal.payerName); [payers] is the snapshot
     * the setup script registered, shown in Settings so people can see who is listed.
     */
    @Serializable
    data class Registry(val name: String, val basis: String, val payers: List<RegisteredPayer>)

    @Serializable
    data class RegisteredPayer(val address: String, val name: String)

    val isTestNetwork: Boolean get() = network != "monad-mainnet"
    val networkLabel: String get() = if (isTestNetwork) "Monad testnet" else "Monad"

    fun explorerTx(hash: String) = "${explorerUrl.trimEnd('/')}/tx/$hash"
    fun explorerAddress(address: String) = "${explorerUrl.trimEnd('/')}/address/$address"

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): NetworkConfig = json.decodeFromString(serializer(), text).also {
            require(it.rpcUrl.startsWith("https://")) { "RPC must use HTTPS" }
            require(it.indexerUrl == null || it.indexerUrl.startsWith("https://")) { "indexer must use HTTPS" }
            require(it.asset.decimals in 0..18) { "unsupported decimals" }
        }
    }
}
