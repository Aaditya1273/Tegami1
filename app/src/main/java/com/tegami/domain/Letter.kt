package com.tegami.domain

import com.tegami.config.NetworkConfig
import com.tegami.evm.OnchainLetter
import java.math.BigInteger
import java.time.Instant

/**
 * One incoming payment as Tegami presents it. Each property is a separate claim with its own evidence;
 * none implies another. In particular, nothing here says where the payer's money came from.
 */
data class Letter(
    /** The letter's index in the recipient's list on the TegamiSeal contract: its permanent id. */
    val id: String,
    val block: Long,
    /** The paying transaction, when the explorer lookup found it. */
    val txHash: String?,
    val time: Instant?,
    val settlement: Settlement,
    val payer: Payer,
    val amount: Money,
    val seal: Seal,
    /** The payer's note, sanitized. Untrusted text: displayed, never acted on. */
    val note: String?,
    /** The same payer used this invoice reference on another payment. */
    val duplicateInvoice: Boolean = false,
) {
    val noteLooksLikeInstruction: Boolean get() = note?.let(UntrustedText::looksLikeInstruction) == true
}

/** Network status. "Finalized" is the only state treated as settled. */
enum class Settlement { Confirmed, Finalized }

/** Who paid (the transaction's sender), and what the on-chain registry says about that address. */
sealed interface Payer {
    val address: String

    /** Listed in the TegamiSeal registry. Says nothing about the origin of the payer's funds. */
    data class Registered(override val address: String, val name: String, val registry: String) : Payer
    data class Unregistered(override val address: String) : Payer
}

sealed interface Seal {
    /** Registered payer + the payer itself sent this payment + a plain invoice reference in the same transaction. */
    data class Sealed(val invoice: String) : Seal
    /** An invoice is attached but the payer isn't registered, so it can't be trusted. */
    data class Untrusted(val invoice: String, val reason: UntrustedReason) : Seal
    data object Malformed : Seal
    data object None : Seal
}

enum class UntrustedReason { PayerNotRegistered }

object LetterRules {
    fun settlement(block: Long, finalizedBlock: Long): Settlement =
        if (block <= finalizedBlock) Settlement.Finalized else Settlement.Confirmed

    /** [registeredName] is the payer's name in the on-chain registry, or null when not listed. */
    fun toLetter(l: OnchainLetter, settlement: Settlement, registeredName: String?, txHash: String?, config: NetworkConfig): Letter {
        val payer = registeredName?.takeIf { it.isNotBlank() }
            ?.let { Payer.Registered(l.payer, it, config.registry.name) }
            ?: Payer.Unregistered(l.payer)
        val seal = when (val memo = SealMemo.parse(l.invoice)) {
            SealMemo.None -> Seal.None
            SealMemo.Malformed -> Seal.Malformed
            is SealMemo.Valid -> if (payer is Payer.Registered) Seal.Sealed(memo.invoice)
                else Seal.Untrusted(memo.invoice, UntrustedReason.PayerNotRegistered)
        }
        return Letter(
            id = l.index.toString(),
            block = l.blockNumber,
            txHash = txHash,
            time = l.time.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
            settlement = settlement,
            payer = payer,
            amount = Money(l.amountUnits.min(BigInteger.valueOf(Long.MAX_VALUE)).toLong(), config.asset.decimals, config.asset.symbol),
            seal = seal,
            note = UntrustedText.sanitize(l.note).ifEmpty { null },
        )
    }
    /** Flags every sealed letter whose (payer, invoice) pair appears more than once. */
    fun markDuplicateInvoices(letters: List<Letter>): List<Letter> {
        val counts = letters.mapNotNull { l -> (l.seal as? Seal.Sealed)?.let { l.payer.address to it.invoice } }
            .groupingBy { it }.eachCount()
        return letters.map { l ->
            val key = (l.seal as? Seal.Sealed)?.let { l.payer.address to it.invoice }
            if (key != null && (counts[key] ?: 0) > 1) l.copy(duplicateInvoice = true) else l
        }
    }

    /** Letters the user should look at: unregistered payers, untrusted or malformed seals, duplicates, odd notes. */
    fun needsAttention(l: Letter): Boolean =
        l.payer is Payer.Unregistered || l.seal is Seal.Untrusted || l.seal == Seal.Malformed ||
            l.duplicateInvoice || l.noteLooksLikeInstruction
}
