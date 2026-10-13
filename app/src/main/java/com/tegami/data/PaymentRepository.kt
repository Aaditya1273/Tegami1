package com.tegami.data

import com.tegami.config.NetworkConfig
import com.tegami.domain.Money
import com.tegami.domain.TegamiError
import com.tegami.evm.EvmKeys
import com.tegami.evm.EvmRpc
import com.tegami.evm.SealContract
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.web3j.crypto.Credentials
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.crypto.transaction.type.Transaction1559
import org.web3j.utils.Numeric
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/** What an account holds right now: the gas token and the payment asset. */
data class Balances(val gasWei: BigInteger, val asset: Money)

/**
 * Sends a sealed payment through the TegamiSeal contract: approve once (if needed), then `pay`, each an
 * EIP-1559 transaction signed by the account (passkey key on the device, or the Google account's Privy wallet),
 * broadcast by Tegami and confirmed before returning.
 */
@Singleton
class PaymentRepository @Inject constructor(
    private val rpc: EvmRpc,
    private val config: NetworkConfig,
    private val privy: PrivyWallet,
) {
    suspend fun balances(address: String): Balances = Balances(
        rpc.balance(address),
        Money(SealContract.decodeUint(rpc.call(config.asset.address, SealContract.balanceOfCall(address))).toLong(),
            config.asset.decimals, config.asset.symbol),
    )

    /** Sends transactions signed on this device with a passkey account's key. */
    fun passkeySender(key: ECKeyPair): suspend (String, String) -> String = { to, data ->
        send(EvmKeys.address(key), to, data) { Numeric.toHexString(TransactionEncoder.signMessage(it, Credentials.create(key))) }
    }

    /** Sends transactions signed by the Google account's Privy wallet (sender re-checked before broadcast). */
    fun privySender(address: String): suspend (String, String) -> String = { to, data ->
        send(address, to, data) { tx ->
            val fees = tx.transaction as Transaction1559
            privy.signTransaction(address, buildJsonObject {
                put("from", address); put("to", tx.to); put("data", tx.data); put("value", "0x0")
                put("chainId", config.chainId); put("type", 2); put("nonce", hex(tx.nonce)); put("gasLimit", hex(tx.gasLimit))
                put("maxFeePerGas", hex(fees.maxFeePerGas)); put("maxPriorityFeePerGas", hex(fees.maxPriorityFeePerGas))
            })
        }
    }

    private fun hex(n: BigInteger) = "0x" + n.toString(16)

    /**
     * Pays [amount] from [from] to [to] with an invoice and note; [sender] submits each transaction (signed on the
     * device for passkey accounts, by the Privy wallet for Google accounts). Returns the confirmed `pay` hash.
     */
    suspend fun pay(from: String, sender: suspend (to: String, data: String) -> String, to: String, amount: Money,
                    invoice: String, note: String, onStep: (String) -> Unit = {}): String {
        if (!EvmKeys.isAddress(to)) throw TegamiError.Invalid("That isn't a Monad address.")
        if (EvmKeys.sameAddress(from, to)) throw TegamiError.Invalid("You can't send a letter to yourself.")
        val units = BigInteger.valueOf(amount.units)
        val allowance = SealContract.decodeUint(rpc.call(config.asset.address, SealContract.allowanceCall(from, config.sealContract)))
        if (allowance < units) {
            onStep("Allowing the seal contract to send ${config.asset.symbol}…")
            confirm(sender(config.asset.address, SealContract.approveCall(config.sealContract, MAX_UINT)))
        }
        onStep("Sending and sealing…")
        return sender(config.sealContract, SealContract.payCall(config.asset.address, to, units, invoice, note)).also { confirm(it) }
    }

    private suspend fun send(from: String, to: String, data: String, sign: suspend (RawTransaction) -> String): String {
        // Monad charges for the gas limit, not gas used: estimate, then add only a small margin.
        val gas = try {
            rpc.estimateGas(from, to, data).multiply(BigInteger.valueOf(115)).divide(BigInteger.valueOf(100))
        } catch (e: TegamiError.Rpc) {
            throw TegamiError.Invalid("The network would reject this payment: ${e.detail}")
        }
        val maxFee = rpc.gasPrice().multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN)
        if (rpc.balance(from) < gas.multiply(maxFee)) {
            throw TegamiError.Invalid("Not enough MON for network fees. Get testnet MON at faucet.monad.xyz for $from.")
        }
        val tx = RawTransaction.createTransaction(config.chainId, rpc.nonce(from), gas, to, BigInteger.ZERO, data, PRIORITY_FEE, maxFee)
        return rpc.sendRaw(sign(tx))
    }

    private suspend fun confirm(hash: String) {
        repeat(60) {
            when (rpc.receiptStatus(hash)) {
                true -> return
                false -> throw TegamiError.Invalid("The transaction failed on-chain.")
                null -> delay(500)
            }
        }
        throw TegamiError.Invalid("Still waiting for the network to confirm $hash.")
    }

    private companion object {
        val MAX_UINT: BigInteger = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)
        val PRIORITY_FEE: BigInteger = BigInteger.valueOf(2_000_000_000)
    }
}
