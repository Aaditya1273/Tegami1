package com.tegami.evm

import com.tegami.config.NetworkConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/** A letter as Tegami's Envio indexer reports it. [noteKnown] is false when the indexer couldn't decode the note. */
data class IndexedLetter(val letter: OnchainLetter, val noteKnown: Boolean, val payerName: String, val txHash: String)

/**
 * Tegami's Envio HyperIndex indexer (indexer/ in this repo): a recipient's letters in one GraphQL query instead of
 * two RPC calls per letter. Used only to find and list letters; statements are always verified on-chain.
 */
@Singleton
class LetterIndex @Inject constructor(private val http: HttpClient, private val config: NetworkConfig) {
    val enabled: Boolean get() = config.indexerUrl != null

    /** Letters [newest] down to [oldest] for [owner], or null if the indexer isn't configured. Throws on errors. */
    suspend fun page(owner: String, newest: Long, oldest: Long): List<IndexedLetter>? {
        val url = config.indexerUrl ?: return null
        require(EvmKeys.isAddress(owner) && oldest in 0..newest)
        val query = """{ Letter(where: {recipient: {_eq: "${owner.lowercase()}"}, index: {_gte: "$oldest", _lte: "$newest"}},
            order_by: {index: desc}) { index token amount timestamp blockNumber invoice note txHash payer { id name } } }"""
        val body = buildJsonObject { put("query", JsonPrimitive(query)) }.toString()
        val reply = json.parseToJsonElement(http.post(url) { contentType(ContentType.Application.Json); setBody(body) }.bodyAsText()).jsonObject
        reply["errors"]?.let { throw IllegalStateException("indexer: ${it.toString().take(200)}") }
        return reply["data"]!!.jsonObject["Letter"]!!.jsonArray.map { e ->
            val o = e.jsonObject
            val payer = o["payer"]!!.jsonObject
            val note = o["note"]?.jsonPrimitive?.contentOrNull
            IndexedLetter(
                OnchainLetter(
                    index = o["index"]!!.jsonPrimitive.content.toLong(),
                    token = o["token"]!!.jsonPrimitive.content,
                    payer = EvmKeys.checksum(payer["id"]!!.jsonPrimitive.content),
                    amountUnits = BigInteger(o["amount"]!!.jsonPrimitive.content),
                    time = o["timestamp"]!!.jsonPrimitive.long,
                    blockNumber = o["blockNumber"]!!.jsonPrimitive.long,
                    invoice = o["invoice"]!!.jsonPrimitive.content,
                    note = note.orEmpty(),
                ),
                noteKnown = note != null,
                payerName = payer["name"]!!.jsonPrimitive.content,
                txHash = o["txHash"]!!.jsonPrimitive.content,
            )
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
