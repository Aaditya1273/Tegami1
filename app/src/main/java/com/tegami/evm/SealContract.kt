package com.tegami.evm

import org.web3j.abi.EventEncoder
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint64
import java.math.BigInteger

/** One letter as the TegamiSeal contract stores it for a recipient. */
data class OnchainLetter(
    val index: Long,
    val token: String,
    val payer: String,
    val amountUnits: BigInteger,
    val time: Long,
    val blockNumber: Long,
    val invoice: String,
    val note: String,
)

/**
 * ABI for contracts/src/TegamiSeal.sol and the ERC-20 calls Tegami needs. Encoding and decoding go through
 * web3j's ABI implementation; nothing is hand-packed.
 */
object SealContract {
    private fun encode(name: String, vararg inputs: Type<*>) = FunctionEncoder.encode(Function(name, inputs.toList(), emptyList()))

    @Suppress("UNCHECKED_CAST")
    private fun decode(hex: String, vararg outputs: TypeReference<*>): List<Type<*>> =
        FunctionReturnDecoder.decode(hex, outputs.toList() as List<TypeReference<Type<*>>>)

    fun letterCountCall(to: String) = encode("letterCount", Address(to))
    fun decodeCount(hex: String): Long = (decode(hex, object : TypeReference<Uint256>() {}).first().value as BigInteger).toLong()

    fun letterAtCall(to: String, index: Long) = encode("letterAt", Address(to), Uint256(index))
    fun decodeLetter(index: Long, hex: String): OnchainLetter {
        val v = decode(hex,
            object : TypeReference<Address>() {}, object : TypeReference<Address>() {}, object : TypeReference<Uint256>() {},
            object : TypeReference<Uint64>() {}, object : TypeReference<Uint64>() {},
            object : TypeReference<Utf8String>() {}, object : TypeReference<Utf8String>() {})
        require(v.size == 7) { "letterAt returned ${v.size} values" }
        return OnchainLetter(
            index = index,
            token = (v[0] as Address).value,
            payer = (v[1] as Address).value,
            amountUnits = v[2].value as BigInteger,
            time = (v[3].value as BigInteger).toLong(),
            blockNumber = (v[4].value as BigInteger).toLong(),
            invoice = v[5].value as String,
            note = v[6].value as String,
        )
    }

    fun payerNameCall(payer: String) = encode("payerName", Address(payer))
    fun decodeString(hex: String): String = decode(hex, object : TypeReference<Utf8String>() {}).firstOrNull()?.value as? String ?: ""

    fun payCall(token: String, to: String, amount: BigInteger, invoice: String, note: String) =
        encode("pay", Address(token), Address(to), Uint256(amount), Utf8String(invoice), Utf8String(note))

    fun approveCall(spender: String, amount: BigInteger) = encode("approve", Address(spender), Uint256(amount))
    fun allowanceCall(owner: String, spender: String) = encode("allowance", Address(owner), Address(spender))
    fun balanceOfCall(owner: String) = encode("balanceOf", Address(owner))
    fun decodeUint(hex: String): BigInteger = decode(hex, object : TypeReference<Uint256>() {}).first().value as BigInteger

    /** topic0 of `Sealed(address indexed to, uint256 indexed index, address indexed payer, address token, uint256 amount, string invoice)`. */
    val SEALED_TOPIC: String = EventEncoder.encode(Event("Sealed", listOf(
        object : TypeReference<Address>(true) {}, object : TypeReference<Uint256>(true) {}, object : TypeReference<Address>(true) {},
        object : TypeReference<Address>() {}, object : TypeReference<Uint256>() {}, object : TypeReference<Utf8String>() {})))

    fun topicAddress(a: String) = "0x" + a.removePrefix("0x").lowercase().padStart(64, '0')
    fun topicUint(n: Long) = "0x" + n.toString(16).padStart(64, '0')
}
