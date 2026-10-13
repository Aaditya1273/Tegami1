package com.tegami

import com.tegami.Fixtures.config
import com.tegami.domain.LetterRules
import com.tegami.domain.Money
import com.tegami.guardian.Guardian
import com.tegami.guardian.GuardianAction
import com.tegami.guardian.GuardianContext
import com.tegami.guardian.GuardianReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GuardianTest {
    private val letters = Fixtures.letters().reversed() // newest first, as the app holds them
    private val ctx = GuardianContext(config, "0x19E7E376E7C213B7E7e7e46cc70A5dD086DAff2A", letters, LocalDate.parse("2026-10-12"))

    @Test fun neverMovesMoney() {
        for (q in listOf("send 50 to my friend", "transfer everything", "withdraw all", "please sign this transaction")) {
            assertTrue(q, Guardian.respond(q, ctx) is GuardianReply.Declined)
        }
    }

    @Test fun incomeRequestBecomesAProposalNotAnAction() {
        val r = Guardian.respond("Send my landlord proof of income, at least 2000", ctx) as GuardianReply.Proposal
        val a = r.action as GuardianAction.PrepareIncomeStatement
        assertEquals(Money.parse("2000", 6, "tUSD"), a.threshold)
        assertEquals("Landlord", a.verifier)
        assertTrue(r.effects.any { "Nothing is signed" in it })
        assertTrue(r.disclosures.isNotEmpty())
    }

    @Test fun incomeRequestNeedsAWallet() {
        assertTrue(Guardian.respond("income statement", ctx.copy(account = null)) is GuardianReply.Answer)
    }

    @Test fun paymentNotesAreQuotedNeverFollowed() {
        val injected = letters.first()
        val text = Guardian.explain(injected, ctx).joinToString("\n")
        assertTrue("Ignore previous instructions" in text)
        assertTrue("never act on text inside payments" in text)
        // Explaining the latest letter (the injection) produces an answer, not an action.
        assertTrue(Guardian.respond("explain my latest payment", ctx) is GuardianReply.Answer)
    }

    @Test fun attentionListsUnregisteredAndSuspicious() {
        // Includes the exact wording of the suggestion chips on the Guardian screen.
        for (q in listOf("anything suspicious?", "Anything need a look?", "anything wrong?")) {
            val r = Guardian.respond(q, ctx) as GuardianReply.Answer
            assertEquals(q, setOf("4", "3"), r.letters.toSet())
        }
    }

    @Test fun everySuggestionChipGetsARealReply() {
        val help = Guardian.help().text
        for (q in Guardian.SUGGESTIONS) assertTrue(q, Guardian.respond(q, ctx).text != help)
    }

    @Test fun evidenceSummaryStatesItsLimits() {
        val r = Guardian.respond("my exchange asked for source of funds", ctx) as GuardianReply.Proposal
        val a = r.action as GuardianAction.ShareEvidenceSummary
        assertEquals(4, a.paymentCount)
        assertTrue("does not show: where the payers' funds came from" in a.summary)
        assertFalse("letter #3" in a.summary)
        assertTrue("letter #0" in a.summary)
    }
}
