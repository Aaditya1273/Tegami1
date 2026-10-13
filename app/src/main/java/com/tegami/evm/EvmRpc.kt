package com.tegami.evm

import com.tegami.domain.TegamiError
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.math.BigInteger

/** A log entry, reduced to what Tegami reads. */
data class EvmLog(val txHash: String, val blockNumber: Long, val topics: List<String>)

/** Minimal Ethereum JSON-RPC client for Monad: reads, plus sending already-signed transactions. */
class EvmRpc(private val http: HttpClient, private val url: String) {
    private val json = Json { ignoreUnknownKeys = true }

    /** `eth_call` at [block] ("latest", "finalized" or a hex number). Returns the raw hex result. */
    suspend fun call(to: String, data: String, block: String = "latest"): String =
        call("eth_call", buildJsonArray {
            add(buildJsonObject { put("to", to); put("data", data) })
            add(JsonPrimitive(block))
        }).jsonPrimitive.content

    suspend fun blockNumber(): Long = hexLong(call("eth_blockNumber", JsonArray(emptyList())).jsonPrimitive.content)

    /** The newest block the network reports as finalized. */
    suspend fun finalizedBlock(): Long {
        val block = call("eth_getBlockByNumber", buildJsonArray { add(JsonPrimitive("finalized")); add(JsonPrimitive(false)) })
        return hexLong((block as JsonObject)["number"]!!.jsonPrimitive.content)
    }

    suspend fun logs(address: String, fromBlock: Long, toBlock: Long, topics: List<String?>): List<EvmLog> {
        val result = call("eth_getLogs", buildJsonArray {
            add(buildJsonObject {
                put("address", address)
                put("fromBlock", hex(fromBlock))
                put("toBlock", hex(toBlock))
                put("topics", buildJsonArray { topics.forEach { add(if (it == null) JsonNull else JsonPrimitive(it)) } })
            })
        })
        return result.jsonArray.map { e ->
            val o = e.jsonObject
            EvmLog(o["transactionHash"]!!.jsonPrimitive.content, hexLong(o["blockNumber"]!!.jsonPrimitive.content),
                o["topics"]!!.jsonArray.map { it.jsonPrimitive.content })
        }
    }

    suspend fun nonce(address: String): BigInteger =
        hexBig(call("eth_getTransactionCount", buildJsonArray { add(JsonPrimitive(address)); add(JsonPrimitive("pending")) }).jsonPrimitive.content)

    suspend fun balance(address: String): BigInteger =
        hexBig(call("eth_getBalance", buildJsonArray { add(JsonPrimitive(address)); add(JsonPrimitive("latest")) }).jsonPrimitive.content)

    suspend fun gasPrice(): BigInteger = hexBig(call("eth_gasPrice", JsonArray(emptyList())).jsonPrimitive.content)

    suspend fun estimateGas(from: String, to: String, data: String): BigInteger =
        hexBig(call("eth_estimateGas", buildJsonArray {
            add(buildJsonObject { put("from", from); put("to", to); put("data", data) })
        }).jsonPrimitive.content)

    suspend fun sendRaw(signedHex: String): String =
        call("eth_sendRawTransaction", buildJsonArray { add(JsonPrimitive(signedHex)) }).jsonPrimitive.content

    /** Null while pending; otherwise whether the transaction succeeded. */
    suspend fun receiptStatus(txHash: String): Boolean? {
        val r = call("eth_getTransactionReceipt", buildJsonArray { add(JsonPrimitive(txHash)) }) as? JsonObject ?: return null
        return r["status"]?.jsonPrimitive?.contentOrNull == "0x1"
    }

    private suspend fun call(method: String, params: JsonArray): JsonElement {
        val body = buildJsonObject { put("jsonrpc", "2.0"); put("id", 1); put("method", method); put("params", params) }
        val text = try {
            val response = http.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            if (response.status.value == 429) throw TegamiError.RateLimited
            if (response.status.value !in 200..299) throw TegamiError.Rpc(response.status.value, "HTTP ${response.status.value}")
            response.bodyAsText()
        } catch (e: TegamiError) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw TegamiError.Network(e.message ?: e::class.simpleName.orEmpty())
        }
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse { throw TegamiError.Parse("RPC returned non-JSON") }
        (obj["error"] as? JsonObject)?.let { err ->
            val code = err["code"]?.jsonPrimitive?.int ?: 0
            if (code == 429) throw TegamiError.RateLimited
            throw TegamiError.Rpc(code, err["message"]?.jsonPrimitive?.contentOrNull ?: "RPC error")
        }
        return obj["result"] ?: JsonNull
    }

    companion object {
        fun hex(n: Long) = "0x" + n.toString(16)
        fun hexLong(h: String) = h.removePrefix("0x").ifEmpty { "0" }.toLong(16)
        fun hexBig(h: String) = BigInteger(h.removePrefix("0x").ifEmpty { "0" }, 16)
    }
}
