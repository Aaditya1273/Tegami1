package com.tegami

import com.tegami.Fixtures.config
import com.tegami.data.AccountRepository
import com.tegami.domain.Money
import com.tegami.evm.EvmKeys
import com.tegami.evm.OnchainLetter
import com.tegami.proof.DraftResult
import com.tegami.proof.IncomeRules
import com.tegami.proof.IncomeStatement
import com.tegami.proof.SignedStatement
import com.tegami.proof.StatementBuilder
import com.tegami.proof.StatementCodec
import com.tegami.proof.StatementVerifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.crypto.ECKeyPair
import org.web3j.utils.Numeric
import java.time.Instant
import java.time.LocalDate

class StatementTest {
    // A fixed key stands in for the recipient's passkey account.
    private val key = ECKeyPair.create(Numeric.toBigInt("0x" + "11".repeat(32)))
    private val me = EvmKeys.address(key)
    private val day = LocalDate.parse("2026-10-12")
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val usd = { s: String -> Money.parse(s, 6, "tUSD") }
    private val letters = Fixtures.letters()
    private val chain: Map<Long, OnchainLetter> = Fixtures.onchain().associateBy { it.index }

    private fun sign(s: IncomeStatement) = SignedStatement(s, EvmKeys.personalSign(s.message(), key))

    private fun draft(threshold: String, verifier: String = "Landlord") =
        StatementBuilder.draft(config, me, letters, usd(threshold), day.minusDays(29), day, verifier, day, now, nonce = "n1")

    /** Reads only letters paid to [me], like the contract: someone else's letters don't exist at our address. */
    private fun verifier(at: Instant = now, owner: String = me) = StatementVerifier(config,
        readFinalized = { to, i -> if (EvmKeys.sameAddress(to, owner)) chain[i] else null },
        registeredName = { Fixtures.names[it].orEmpty() }, now = { at })

    @Test fun meraDerivationMatchesReferenceImplementation() {
        // Reference: BIP-39 entropy 00..1f → seed → m/44'/60'/0'/0/0, computed with Python eth-account.
        val prf = ByteArray(32) { it.toByte() }
        assertEquals("0xF9297b542BDb5DA50C364f9AE4Cbe1F3933bA40F", EvmKeys.address(EvmKeys.fromPrfOutput(prf)))
        assertEquals("iW1GrErBkYhcRhN0Odt7tS-wXP8-zTSvfNrgoeDADbk", AccountRepository.SALT) // sha256("mera.prf.salt.v1")
    }

    @Test fun personalSignMatchesEthereumTooling() {
        assertEquals("0x19E7E376E7C213B7E7e7e46cc70A5dD086DAff2A", me)
        val sig = EvmKeys.personalSign("Tegami income statement v2\nhello", key)
        assertEquals("0xa9780716674545b51cc98c21d3d83ceb90083107ca58d5f8dc9958b9ccef1b087c18cc9e5545d4127ca8ad9ea2434458f833adb62f2ecb95d1a633698f6406761c", sig)
        assertTrue(EvmKeys.verifyPersonal(me, "Tegami income statement v2\nhello", sig))
        assertFalse(EvmKeys.verifyPersonal(me, "Tegami income statement v2\nhellO", sig))
        assertNull(EvmKeys.recoverPersonal("x", "0x1234"))
    }

    @Test fun availableIncomeCountsOnlyRegisteredFinalized() {
        // 1250 + 860.5 + 400 + 75 (registered agency, injection note) = 2585.5; the unregistered 340 is excluded.
        assertEquals(usd("2585.5"), IncomeRules.availableTotal(letters, day.minusDays(29), day, 6, "tUSD"))
    }

    @Test fun disclosesOnlyWhatTheClaimNeeds() {
        val d = (draft("2000") as DraftResult.Ready).draft
        assertEquals(listOf("0", "1"), d.statement.evidence)
        assertEquals(usd("2110.5"), d.selection.disclosedTotal)
        assertEquals(4, d.selection.qualifyingCount)
    }

    @Test fun builderRejectsBadInput() {
        assertTrue(draft("0") is DraftResult.Invalid)
        assertTrue(draft("999999") is DraftResult.Invalid)
        assertTrue(draft("100", verifier = " ") is DraftResult.Invalid)
        assertTrue(draft("100", verifier = "x".repeat(61)) is DraftResult.Invalid)
        assertTrue(StatementBuilder.draft(config, me, letters, usd("1"), day, day.plusDays(1), "L", day, now) is DraftResult.Invalid)
        assertTrue(StatementBuilder.draft(config, me, letters, usd("1"), day.minusDays(400), day, "L", day, now) is DraftResult.Invalid)
    }

    @Test fun signedStatementVerifiesAgainstChainData() = runBlocking {
        val signed = sign((draft("2000") as DraftResult.Ready).draft.statement)
        val decoded = StatementCodec.decode("Here you go: ${StatementCodec.link(signed)} thanks").getOrThrow()
        val report = verifier().verify(decoded)
        assertTrue(report.checks.joinToString { "${it.title}: ${it.detail}" }, report.verified)
        assertEquals(usd("2110.5"), report.total)
        assertEquals(2, report.disclosed.size)
    }

    @Test fun tamperedClaimFailsSignature() = runBlocking {
        val signed = sign((draft("2000") as DraftResult.Ready).draft.statement)
        val forged = signed.copy(statement = signed.statement.copy(thresholdUnits = 9_000_000_000))
        val report = verifier().verify(forged)
        assertFalse(report.verified)
        assertEquals("Account signature", report.checks.last().title)
    }

    @Test fun expiredStatementFails() = runBlocking {
        val report = verifier(now.plusSeconds(31L * 86_400)).verify(sign((draft("2000") as DraftResult.Ready).draft.statement))
        assertFalse(report.verified)
        assertEquals("Validity period", report.checks.last().title)
    }

    @Test fun unregisteredOrMissingEvidenceFails() = runBlocking {
        val base = (draft("100") as DraftResult.Ready).draft.statement
        for (id in listOf("3", "99")) {
            val report = verifier().verify(sign(base.copy(evidence = listOf(id))))
            assertFalse(id, report.verified)
            assertTrue(report.checks.last().title.startsWith("Letter"))
        }
    }

    @Test fun claimAboveEvidenceFails() = runBlocking {
        val base = (draft("2000") as DraftResult.Ready).draft.statement
        val report = verifier().verify(sign(base.copy(thresholdUnits = 5_000_000_000)))
        assertFalse(report.verified)
        assertEquals("Claim", report.checks.last().title)
    }

    @Test fun someoneElsesPaymentsCantBeClaimed() = runBlocking {
        // Signed by our key, but the chain holds these letters for another address.
        val s = (draft("1000") as DraftResult.Ready).draft.statement
        val report = verifier(owner = Fixtures.STRANGER).verify(sign(s))
        assertFalse(report.verified)
    }

    @Test fun statementForAnotherContractOrChainFails() = runBlocking {
        val s = (draft("1000") as DraftResult.Ready).draft.statement
        assertFalse(verifier().verify(sign(s.copy(chainId = 1))).verified)
        assertFalse(verifier().verify(sign(s.copy(sealContract = Fixtures.TOKEN))).verified)
    }

    @Test fun codecRejectsJunk() {
        assertTrue(StatementCodec.decode("").isFailure)
        assertTrue(StatementCodec.decode("tegami://verify?s=%%%").isFailure)
        assertTrue(StatementCodec.decode("a".repeat(40_000)).isFailure)
        assertTrue(StatementCodec.decode("eyJmb28iOjF9").isFailure) // {"foo":1}
    }
}
