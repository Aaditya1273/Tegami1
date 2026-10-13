package com.tegami.proof

import com.tegami.domain.Letter
import com.tegami.domain.Money
import com.tegami.domain.Payer
import com.tegami.domain.Settlement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64

/**
 * A signed income statement: "this wallet received at least X of the test asset from registered payers
 * between two dates", with the specific transactions that show it.
 *
 * What it is: the account owner's EIP-191 signature over the claim, plus on-chain evidence anyone can re-check
 * (each evidence entry is the recipient's letter index on the TegamiSeal contract).
 * What it is NOT: a zero-knowledge proof. The verifier sees every listed payment's amount, payer and date.
 * Tegami keeps disclosure small by listing only as many payments as the claim needs.
 */
@Serializable
data class IncomeStatement(
    val v: Int = VERSION,
    val network: String,
    val chainId: Long,
    /** The seal contract the evidence indices refer to. */
    val sealContract: String,
    val token: String,
    val symbol: String,
    val decimals: Int,
    val recipient: String,
    val thresholdUnits: Long,
    /** Inclusive UTC dates, yyyy-MM-dd. */
    val from: String,
    val to: String,
    /** Who the statement is for, as the user typed it. Bound into the signature. */
    val verifier: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val nonce: String,
    val evidence: List<String>,
) {
    val threshold: Money get() = Money(thresholdUnits, decimals, symbol)

    /** The exact text the wallet signs and the verifier checks. Human-readable on purpose. */
    fun message(): String = buildString {
        appendLine("Tegami income statement v$v")
        appendLine("Network: $network (chain $chainId)")
        appendLine("Seal contract: $sealContract")
        appendLine("Asset: $symbol ($token)")
        appendLine("Recipient: $recipient")
        appendLine("Claim: received at least ${threshold.format(java.util.Locale.ROOT)} from registered payers")
        appendLine("Period (UTC): $from to $to")
        appendLine("For: $verifier")
        appendLine("Issued: ${Instant.ofEpochSecond(issuedAt)}")
        appendLine("Expires: ${Instant.ofEpochSecond(expiresAt)}")
        appendLine("Nonce: $nonce")
        appendLine("Evidence (${evidence.size} letters): ${evidence.joinToString(", ")}")
    }

    companion object {
        const val VERSION = 2
        const val MAX_EVIDENCE = 100
        const val MAX_VERIFIER_LENGTH = 60
        const val MAX_VALIDITY_DAYS = 90L
    }
}

@Serializable
data class SignedStatement(val statement: IncomeStatement, /** 0x-hex EIP-191 signature over [IncomeStatement.message]. */ val signature: String)

/** Text form for sharing: `tegami://verify?s=<base64url(json)>`. Strict and size-capped on the way in. */
object StatementCodec {
    const val LINK_PREFIX = "tegami://verify?s="
    private const val MAX_ENCODED = 32_000
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }

    fun encode(s: SignedStatement): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(json.encodeToString(SignedStatement.serializer(), s).toByteArray())

    fun link(s: SignedStatement) = LINK_PREFIX + encode(s)

    /** Accepts a bare token, a tegami:// link, or text containing one. */
    fun decode(input: String): Result<SignedStatement> = runCatching {
        val token = input.trim().let { t ->
            val at = t.indexOf(LINK_PREFIX)
            if (at >= 0) t.substring(at + LINK_PREFIX.length).takeWhile { !it.isWhitespace() } else t
        }
        require(token.isNotEmpty()) { "Empty statement" }
        require(token.length <= MAX_ENCODED) { "Statement is too large" }
        require(token.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "Not a Tegami statement" }
        val bytes = Base64.getUrlDecoder().decode(token)
        json.decodeFromString(SignedStatement.serializer(), bytes.decodeToString())
    }
}

/** Which letters count towards an income claim, and which of them to disclose. */
object IncomeRules {
    /** Counts only finalized payments, from registered payers, whose amount this device can read. */
    fun qualifies(l: Letter): Boolean =
        l.settlement == Settlement.Finalized && l.payer is Payer.Registered

    fun inPeriod(l: Letter, from: LocalDate, to: LocalDate): Boolean {
        val day = l.time?.atZone(ZoneOffset.UTC)?.toLocalDate() ?: return false
        return !day.isBefore(from) && !day.isAfter(to)
    }

    data class Selection(
        /** The payments that will be disclosed. */
        val evidence: List<Letter>,
        val disclosedTotal: Money,
        /** Everything that qualifies in the period, disclosed or not. */
        val availableTotal: Money,
        val qualifyingCount: Int,
        val meetsThreshold: Boolean,
    )

    fun availableTotal(letters: List<Letter>, from: LocalDate, to: LocalDate, decimals: Int, symbol: String): Money =
        letters.filter { qualifies(it) && inPeriod(it, from, to) }.fold(Money.zero(decimals, symbol)) { acc, l -> acc + l.amount }

    /** Smallest-disclosure choice: the largest qualifying payments first, stopping once the claim is met. */
    fun select(letters: List<Letter>, from: LocalDate, to: LocalDate, threshold: Money): Selection {
        val qualifying = letters.filter { qualifies(it) && inPeriod(it, from, to) }
            .sortedWith(compareByDescending<Letter> { it.amount.units }.thenBy { it.id.toLong() })
        val zero = Money.zero(threshold.decimals, threshold.symbol)
        val available = qualifying.fold(zero) { acc, l -> acc + l.amount }
        val chosen = mutableListOf<Letter>()
        var sum = zero
        for (l in qualifying) {
            if (sum >= threshold && chosen.isNotEmpty()) break
            chosen += l
            sum += l.amount
        }
        val met = threshold.units > 0 && sum >= threshold
        return Selection(if (met) chosen else emptyList(), if (met) sum else zero, available, qualifying.size, met)
    }
}
