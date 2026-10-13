package com.tegami

import com.tegami.evm.LetterIndex
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class LetterIndexTest {
    private fun index(reply: String, url: String? = "https://indexer.example/v1/graphql") =
        LetterIndex(HttpClient(MockEngine { respond(reply, headers = headersOf(HttpHeaders.ContentType, "application/json")) }),
            Fixtures.config.copy(indexerUrl = url))

    // The shape Envio's Hasura returns (captured from the local indexer on Monad testnet data).
    private val page = """{"data":{"Letter":[
        {"index":"1","token":"0x3e17a15ac573d9550ccc137d7bbc21c855797f4c","amount":"860500000","timestamp":1791912919,"blockNumber":70742193,
         "invoice":"HA-2026-031","note":null,"txHash":"0x00d24f31b656094a7c0fc68f89f36349272015b75d6829db08a921084525cb42",
         "payer":{"id":"0x1b55a860113c707e36ad67566a87910cf602ea98","name":"Hoshi Agency (test payer)"}},
        {"index":"0","token":"0x3e17a15ac573d9550ccc137d7bbc21c855797f4c","amount":"1250000000","timestamp":1791912916,"blockNumber":70742183,
         "invoice":"INV-1042","note":"October retainer","txHash":"0x0bc0de17c90c63b8004bb4aedb964a4830eb5eb91a4afa743b686b82ec8b351e",
         "payer":{"id":"0x0f355bd69ab69aac869ac5187b0823239a5c0a40","name":"Kumo Studio (test payer)"}}]}}"""

    @Test fun parsesAPage() = runBlocking {
        val letters = index(page).page(Fixtures.STRANGER, 1, 0)!!
        assertEquals(listOf(1L, 0L), letters.map { it.letter.index })
        val first = letters[0]
        assertEquals(BigInteger.valueOf(860_500_000), first.letter.amountUnits)
        assertEquals("0x1B55A860113C707e36ad67566A87910Cf602eA98", first.letter.payer) // checksummed
        assertEquals("Hoshi Agency (test payer)", first.payerName)
        assertFalse(first.noteKnown) // null note: the repository reads it from the contract
        assertTrue(letters[1].noteKnown)
        assertEquals("October retainer", letters[1].letter.note)
    }

    @Test fun offWithoutUrl() = runBlocking {
        assertNull(index(page, url = null).page(Fixtures.STRANGER, 1, 0))
    }

    @Test(expected = IllegalStateException::class)
    fun graphQlErrorsThrow(): Unit = runBlocking {
        index("""{"errors":[{"message":"field 'Letter' not found"}]}""").page(Fixtures.STRANGER, 1, 0)
    }
}
