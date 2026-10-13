package com.tegami.proof

import com.tegami.config.NetworkConfig
import com.tegami.domain.LetterRules
import com.tegami.domain.Money
import com.tegami.domain.Settlement
import com.tegami.evm.EvmKeys
import com.tegami.evm.OnchainLetter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** One line of the verification report, in the order the checks ran. */
data class Check(val title: String, val passed: Boolean, val detail: String)

data class DisclosedPayment(val id: String, val amount: Money, val payerName: String, val time: Instant?)

data class VerificationReport(
    val checks: List<Check>,
    val disclosed: List<DisclosedPayment>,
    val total: Money?,
) {
    val verified: Boolean get() = checks.isNotEmpty() && checks.all { it.passed }
}

/**
 * Re-checks a signed statement against the chain using THIS device's network config.
 * [readFinalized] returns the recipient's letter at the finalized block (null if it doesn't exist there);
 * [registeredName] returns the payer's name in the on-chain registry at the finalized block (blank if not listed).
 * Stops at the first failed check, so a report never mixes "passed" with unchecked claims.
 */
class StatementVerifier(
    private val config: NetworkConfig,
    private val readFinalized: suspend (recipient: String, index: Long) -> OnchainLetter?,
    private val registeredName: suspend (payer: String) -> String,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun verify(signed: SignedStatement, onCheck: (Check) -> Unit = {}): VerificationReport {
        val s = signed.statement
        val checks = mutableListOf<Check>()
        fun record(c: Check): Boolean { checks += c; onCheck(c); return c.passed }
        fun fail() = VerificationReport(checks, emptyList(), null)

        val formatOk = s.v == IncomeStatement.VERSION && s.network == config.network && s.chainId == config.chainId &&
            EvmKeys.sameAddress(s.sealContract, config.sealContract) && EvmKeys.sameAddress(s.token, config.asset.address) &&
            s.decimals == config.asset.decimals && s.thresholdUnits > 0 && EvmKeys.isAddress(s.recipient)
        if (!record(Check("Format and network", formatOk,
                if (formatOk) "Version ${s.v}, ${config.networkLabel}, ${config.asset.symbol}"
                else "Expected ${config.networkLabel} / ${config.asset.symbol} on this app's seal contract"))) return fail()

        val sigOk = EvmKeys.verifyPersonal(s.recipient, s.message(), signed.signature)
        if (!record(Check("Account signature", sigOk,
                if (sigOk) "Signed by the recipient account ${short(s.recipient)}"
                else "The signature does not match the recipient account or the statement was altered"))) return fail()

        val t = now().epochSecond
        val windowOk = s.issuedAt <= t + CLOCK_SKEW_S && t < s.expiresAt &&
            s.expiresAt - s.issuedAt <= IncomeStatement.MAX_VALIDITY_DAYS * 86_400
        if (!record(Check("Validity period", windowOk,
                if (windowOk) "Valid until ${Instant.ofEpochSecond(s.expiresAt)}"
                else if (t >= s.expiresAt) "Expired ${Instant.ofEpochSecond(s.expiresAt)}" else "Issue/expiry times are not acceptable"))) return fail()

        val from = runCatching { LocalDate.parse(s.from) }.getOrNull()
        val to = runCatching { LocalDate.parse(s.to) }.getOrNull()
        val indices = s.evidence.map { it.toLongOrNull() }
        val listOk = from != null && to != null && !from.isAfter(to) && s.evidence.isNotEmpty() &&
            s.evidence.size <= IncomeStatement.MAX_EVIDENCE && s.evidence.toSet().size == s.evidence.size &&
            indices.all { it != null && it >= 0 }
        if (!record(Check("Evidence list", listOk,
                if (listOk) "${s.evidence.size} letter(s), ${s.from} to ${s.to}" else "Empty, duplicated, too long, or bad dates"))) return fail()

        val disclosed = mutableListOf<DisclosedPayment>()
        var total = Money.zero(s.decimals, config.asset.symbol)
        for (index in indices.map { it!! }) {
            val l = readFinalized(s.recipient, index)
            val name = l?.let { registeredName(it.payer) }.orEmpty()
            val problem = when {
                l == null -> "Letter #$index isn't finalized on ${config.networkLabel}"
                !EvmKeys.sameAddress(l.token, config.asset.address) -> "Not a ${config.asset.symbol} payment"
                name.isBlank() -> "Payer ${short(l.payer)} is not in the on-chain registry (${config.registry.name})"
                l.time <= 0 -> "The letter has no time"
                Instant.ofEpochSecond(l.time).atZone(ZoneOffset.UTC).toLocalDate().let { it.isBefore(from) || it.isAfter(to) } ->
                    "Outside the stated period"
                else -> null
            }
            if (problem != null) {
                record(Check("Letter #$index", false, problem))
                return VerificationReport(checks, disclosed, null)
            }
            val letter = LetterRules.toLetter(l!!, Settlement.Finalized, name, null, config)
            disclosed += DisclosedPayment(letter.id, letter.amount, name, letter.time)
            total += letter.amount
            record(Check("Letter #$index", true, "${letter.amount.format()} from $name, finalized"))
        }

        val enough = total >= s.threshold
        record(Check("Claim", enough,
            if (enough) "Total ${total.format()} ≥ ${s.threshold.format()}" else "Total ${total.format()} < ${s.threshold.format()}"))
        return VerificationReport(checks, disclosed, total)
    }

    private fun short(a: String) = if (a.length > 12) "${a.take(6)}…${a.takeLast(4)}" else a

    companion object {
        const val CLOCK_SKEW_S = 300L
    }
}
