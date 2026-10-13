package com.tegami.guardian

import com.tegami.config.NetworkConfig
import com.tegami.domain.Letter
import com.tegami.domain.LetterRules
import com.tegami.domain.Money
import com.tegami.domain.Payer
import com.tegami.domain.Seal
import com.tegami.domain.Settlement
import com.tegami.domain.UntrustedReason
import com.tegami.proof.IncomeRules
import java.time.LocalDate
import java.time.ZoneOffset

/** What Guardian knows when it answers. Built by the app; Guardian holds no keys and no network access. */
data class GuardianContext(
    val config: NetworkConfig,
    val account: String?,
    val letters: List<Letter>,
    val today: LocalDate,
)

/** Guardian's three kinds of output. Only a [Proposal] can lead to an action, and only after the user approves it. */
sealed interface GuardianReply {
    val text: String

    data class Answer(override val text: String, val letters: List<String> = emptyList()) : GuardianReply

    data class Proposal(
        override val text: String,
        val action: GuardianAction,
        /** What approving will do. */
        val effects: List<String>,
        /** What approving will reveal, and to whom. */
        val disclosures: List<String>,
    ) : GuardianReply

    data class Declined(override val text: String) : GuardianReply
}

/** The complete set of things Guardian can prepare. Nothing here signs, pays or shares by itself. */
sealed interface GuardianAction {
    /** Opens a pre-filled draft in Proofs; the user reviews it and signs in their own wallet. */
    data class PrepareIncomeStatement(val threshold: Money?, val from: LocalDate, val to: LocalDate, val verifier: String) : GuardianAction

    /** Text the user can review, then share with the system share sheet. */
    data class ShareEvidenceSummary(val summary: String, val paymentCount: Int) : GuardianAction
}

/**
 * Rule-based, on-device assistant. Reads only what the user types; payment notes are passed around as
 * data and never parsed for intent. Deterministic, so every behaviour is testable.
 */
object Guardian {
    /** Shown as chips on the Guardian screen; each must map to a real reply (tested). */
    val SUGGESTIONS = listOf(
        "Explain my latest payment", "Anything need a look?", "Prepare proof of income for my landlord", "My exchange asked for source of funds",
    )
    private val MOVE_MONEY = Regex("""\b(send|pay|transfer|withdraw|swap|sell|buy|sign)\b""")
    private val PROOF = Regex("""\b(income|proof|prove|statement|landlord|lender|payslip)\b""")
    private val SOURCE = Regex("""source of funds|frozen|freeze|compliance|exchange asked|bank asked|evidence pack""")
    private val ATTENTION = Regex("""\b(unknown|unverified|suspicious|attention|check|stranger|who paid|need a look|needs a look|wrong|flagged)\b""")
    private val EXPLAIN = Regex("""\b(explain|latest|last payment|newest|recent|what is|what's)\b""")
    private val AMOUNT = Regex("""(\d[\d,]*(?:\.\d+)?)""")
    private val ROLES = listOf("landlord", "lender", "bank", "exchange", "accountant", "employer", "visa office", "agent")

    fun respond(input: String, ctx: GuardianContext): GuardianReply {
        val q = input.lowercase().trim()
        if (q.isEmpty()) return help()
        return when {
            PROOF.containsMatchIn(q) -> prepareStatement(q, ctx)
            SOURCE.containsMatchIn(q) -> prepareEvidence(ctx)
            MOVE_MONEY.containsMatchIn(q) -> GuardianReply.Declined(
                "I can't move money, sign transactions or approve anything with your passkey. I can explain payments, " +
                    "find ones that need a look, and prepare income statements or evidence for you to review.")
            ATTENTION.containsMatchIn(q) -> attention(ctx)
            EXPLAIN.containsMatchIn(q) -> ctx.letters.firstOrNull()?.let { explainReply(it, ctx) }
                ?: GuardianReply.Answer("There are no payments to explain yet.")
            else -> help()
        }
    }

    fun help() = GuardianReply.Answer(
        "I can explain a payment, list payments that need a look, prepare an income statement, or put together " +
            "payment evidence for a source-of-funds request. I never sign, pay or share without your approval.")

    fun explainReply(l: Letter, ctx: GuardianContext) = GuardianReply.Answer(explain(l, ctx).joinToString("\n"), listOf(l.id))

    /** Plain-language facts about one payment, each with its limits. */
    fun explain(l: Letter, ctx: GuardianContext): List<String> = buildList {
        add(when (val p = l.payer) {
            is Payer.Registered -> "From ${p.name}, listed in ${p.registry}. Being listed doesn't show where their money came from."
            is Payer.Unregistered -> "From ${p.address}, which is not in ${ctx.config.registry.name}. Nobody has vouched for this payer."
        })
        add("Amount ${l.amount.format()}. It is public on-chain: anyone who looks up the transaction can see it.")
        add(when (l.settlement) {
            Settlement.Finalized -> "Finalized on ${ctx.config.networkLabel}: the network won't reverse it."
            Settlement.Confirmed -> "Confirmed but not finalized yet. Wait for finalization before relying on it."
        })
        add(when (val s = l.seal) {
            is Seal.Sealed -> "Sealed: the registered payer sent this payment itself, with invoice ${s.invoice}."
            is Seal.Untrusted -> "Carries invoice ${s.invoice}, but the seal isn't trusted: the payer isn't registered."
            Seal.Malformed -> "Its invoice field isn't a plain reference, so it's treated as unsealed."
            Seal.None -> "No seal: there is no invoice reference from the payer."
        })
        if (l.duplicateInvoice) add("This payer used the same invoice reference on another payment. Check you weren't paid twice for one invoice.")
        l.note?.let {
            add("Payer's note (untrusted text, shown as written): \"$it\"")
            if (l.noteLooksLikeInstruction) add("The note reads like an instruction. I never act on text inside payments.")
        }
    }

    private fun attention(ctx: GuardianContext): GuardianReply {
        val flagged = ctx.letters.filter(LetterRules::needsAttention)
        if (flagged.isEmpty()) return GuardianReply.Answer("Nothing needs a look. Every payment is from a registered payer with a trusted seal or no seal.")
        val lines = flagged.map { l ->
            val why = buildList {
                if (l.payer is Payer.Unregistered) add("unregistered payer")
                if (l.seal is Seal.Untrusted) add("untrusted seal")
                if (l.seal == Seal.Malformed) add("malformed seal")
                if (l.duplicateInvoice) add("repeated invoice")
                if (l.noteLooksLikeInstruction) add("note reads like an instruction")
            }.joinToString(", ")
            "• ${l.amount.format()}: $why"
        }
        return GuardianReply.Answer((listOf("${flagged.size} payment(s) need a look:") + lines).joinToString("\n"),
            flagged.map { it.id })
    }

    private fun prepareStatement(q: String, ctx: GuardianContext): GuardianReply {
        if (ctx.account == null) return GuardianReply.Answer("Sign in with your passkey first. An income statement must be signed by the account that received the payments.")
        val threshold = AMOUNT.find(q)?.value?.let { Money.parse(it, ctx.config.asset.decimals, ctx.config.asset.symbol) }
        val months = Regex("""(\d{1,2})\s*months?""").find(q)?.groupValues?.get(1)?.toLongOrNull()?.coerceIn(1, 12)
        val from = when {
            months != null -> ctx.today.minusMonths(months).plusDays(1)
            "this month" in q -> ctx.today.withDayOfMonth(1)
            else -> ctx.today.minusDays(29)
        }
        val verifier = ROLES.firstOrNull { it in q }?.replaceFirstChar { it.uppercase() } ?: ""
        val available = IncomeRules.availableTotal(ctx.letters, from, ctx.today, ctx.config.asset.decimals, ctx.config.asset.symbol)
        return GuardianReply.Proposal(
            text = "I've prepared an income statement draft for ${verifier.ifEmpty { "someone you choose" }}, " +
                "covering $from to ${ctx.today}. Verified income in that period: ${available.format()}.",
            action = GuardianAction.PrepareIncomeStatement(threshold, from, ctx.today, verifier),
            effects = listOf("Opens the draft in Proofs, where you can change it.", "Nothing is signed or shared until you approve it with your passkey."),
            disclosures = listOf("When you share it, the verifier sees the payments the claim needs: amount, payer and date of each."),
        )
    }

    private fun prepareEvidence(ctx: GuardianContext): GuardianReply {
        val included = ctx.letters.filter(IncomeRules::qualifies)
        if (included.isEmpty()) return GuardianReply.Answer("There are no finalized payments from registered payers to put in an evidence summary yet.")
        return GuardianReply.Proposal(
            text = "I've drafted a payment evidence summary you can send in reply to a source-of-funds request. Review it before sharing.",
            action = GuardianAction.ShareEvidenceSummary(EvidenceSummary.build(ctx), included.size),
            effects = listOf("Opens your phone's share sheet with the text below. You choose where it goes."),
            disclosures = listOf("${included.size} payment(s): amount, payer, date, invoice and transaction link.", "Your account address."),
        )
    }
}

/** A plain-text evidence summary. States what it shows and what it does not. */
object EvidenceSummary {
    fun build(ctx: GuardianContext): String = buildString {
        val included = ctx.letters.filter(IncomeRules::qualifies)
        val test = if (ctx.config.isTestNetwork) " (${ctx.config.networkLabel} test data: ${ctx.config.asset.symbol} has no value)" else ""
        appendLine("Payment evidence summary, prepared with Tegami$test")
        appendLine("Account: ${ctx.account}")
        appendLine("Asset: ${ctx.config.asset.symbol} (${ctx.config.asset.address}) on ${ctx.config.networkLabel}")
        appendLine("Seal contract: ${ctx.config.sealContract}")
        appendLine()
        appendLine("Finalized payments from payers in ${ctx.config.registry.name}:")
        included.forEach { l ->
            val day = l.time?.atZone(ZoneOffset.UTC)?.toLocalDate()?.toString() ?: "unknown date"
            val payer = (l.payer as Payer.Registered).name
            val invoice = (l.seal as? Seal.Sealed)?.let { "  invoice ${it.invoice}" } ?: ""
            appendLine("- $day  ${l.amount.format(java.util.Locale.ROOT)}  $payer$invoice  (letter #${l.id})")
            l.txHash?.let { appendLine("  ${ctx.config.explorerTx(it)}") }
        }
        val excluded = ctx.letters.size - included.size
        if (excluded > 0) appendLine("Not included: $excluded other incoming payment(s) (unregistered payer or not yet finalized).")
        appendLine()
        appendLine("What this shows: each listed payment is on-chain, finalized, and was sent by a payer in the registry above.")
        appendLine("What it does not show: where the payers' funds came from. This is not legal advice, and no bank or exchange is obliged to accept it.")
    }
}
