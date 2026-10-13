package com.tegami.proof

import com.tegami.config.NetworkConfig
import com.tegami.domain.Letter
import com.tegami.domain.Money
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A statement the user is about to sign: the claim, and exactly what it will disclose. */
data class StatementDraft(
    val statement: IncomeStatement,
    val selection: IncomeRules.Selection,
    val disclosedLetters: List<Letter>,
)

sealed interface DraftResult {
    data class Ready(val draft: StatementDraft) : DraftResult
    data class Invalid(val reason: String) : DraftResult
}

/** Deterministic policy for building statements. The UI and Guardian both go through here. */
object StatementBuilder {
    const val MAX_PERIOD_DAYS = 366L
    const val DEFAULT_VALIDITY_DAYS = 30L

    fun draft(
        config: NetworkConfig,
        recipient: String,
        letters: List<Letter>,
        threshold: Money?,
        from: LocalDate,
        to: LocalDate,
        verifier: String,
        today: LocalDate,
        now: Instant = Instant.now(),
        validityDays: Long = DEFAULT_VALIDITY_DAYS,
        nonce: String = newNonce(),
    ): DraftResult {
        val label = verifier.trim()
        when {
            threshold == null || threshold.units <= 0 -> return DraftResult.Invalid("Enter an amount greater than zero.")
            from.isAfter(to) -> return DraftResult.Invalid("The start date is after the end date.")
            to.isAfter(today) -> return DraftResult.Invalid("The period can't end in the future.")
            ChronoUnit.DAYS.between(from, to) + 1 > MAX_PERIOD_DAYS -> return DraftResult.Invalid("Choose a period of a year or less.")
            label.isEmpty() -> return DraftResult.Invalid("Say who this statement is for.")
            label.length > IncomeStatement.MAX_VERIFIER_LENGTH -> return DraftResult.Invalid("Keep the recipient name under ${IncomeStatement.MAX_VERIFIER_LENGTH} characters.")
            label.any { it.isISOControl() } -> return DraftResult.Invalid("The recipient name contains unsupported characters.")
            validityDays !in 1..IncomeStatement.MAX_VALIDITY_DAYS -> return DraftResult.Invalid("Validity must be 1–${IncomeStatement.MAX_VALIDITY_DAYS} days.")
        }
        val selection = IncomeRules.select(letters, from, to, threshold!!)
        if (selection.qualifyingCount == 0) return DraftResult.Invalid("No finalized payments from registered payers in this period.")
        if (!selection.meetsThreshold) {
            return DraftResult.Invalid("Verified income in this period is ${selection.availableTotal.format()}, below ${threshold.format()}.")
        }
        if (selection.evidence.size > IncomeStatement.MAX_EVIDENCE) return DraftResult.Invalid("The claim needs too many payments; lower the amount.")
        val statement = IncomeStatement(
            network = config.network,
            chainId = config.chainId,
            sealContract = config.sealContract,
            token = config.asset.address,
            symbol = config.asset.symbol,
            decimals = config.asset.decimals,
            recipient = recipient,
            thresholdUnits = threshold.units,
            from = from.toString(),
            to = to.toString(),
            verifier = label,
            issuedAt = now.epochSecond,
            expiresAt = now.plus(validityDays, ChronoUnit.DAYS).epochSecond,
            nonce = nonce,
            evidence = selection.evidence.map { it.id },
        )
        return DraftResult.Ready(StatementDraft(statement, selection, selection.evidence))
    }

    fun newNonce(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
}
