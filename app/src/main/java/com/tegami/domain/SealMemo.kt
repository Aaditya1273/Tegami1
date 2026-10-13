package com.tegami.domain

/**
 * The invoice reference a payer attaches when paying through the TegamiSeal contract.
 *
 * It is authenticated only because the payer submitted it in the same transaction that moved the money;
 * on its own it proves nothing. The payer's free-text note is separate and always untrusted.
 */
sealed interface SealMemo {
    data class Valid(val invoice: String) : SealMemo
    /** An invoice field that isn't a plain reference; shown as malformed, never as sealed. */
    data object Malformed : SealMemo
    data object None : SealMemo

    companion object {
        private val INVOICE = Regex("""[A-Za-z0-9._\-]{1,40}""")

        fun parse(invoice: String?): SealMemo {
            val text = invoice?.trim().orEmpty()
            if (text.isEmpty()) return None
            return if (INVOICE.matches(text)) Valid(text) else Malformed
        }
    }
}

/** A payer's free-text note, made safe to display. It is data: nothing in it is ever acted on. */
object UntrustedText {
    const val MAX = 280
    private val INSTRUCTION_HINTS = listOf(
        "ignore", "instruction", "send ", "transfer", "approve", "sign ", "seed", "private key", "password",
        "http://", "https://", "urgent", "verify your",
    )

    /** Strips control and bidi-override characters and caps the length. */
    fun sanitize(raw: String): String = raw
        .filterNot { it.isISOControl() || it in '‪'..'‮' || it in '⁦'..'⁩' }
        .trim()
        .let { if (it.length > MAX) it.take(MAX) + "…" else it }

    /** Heuristic only: used to warn the user, never to decide anything. */
    fun looksLikeInstruction(text: String): Boolean {
        val lower = text.lowercase()
        return INSTRUCTION_HINTS.any { it in lower }
    }
}
