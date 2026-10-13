package com.tegami.evm

import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import org.web3j.crypto.MnemonicUtils
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric

/**
 * Keys and signatures for EVM accounts (secp256k1). Everything here is web3j's reviewed implementation:
 * EIP-191 personal-message signing and recovery, BIP-39 and BIP-32.
 */
object EvmKeys {
    private val ADDRESS = Regex("^0x[0-9a-fA-F]{40}$")

    fun isAddress(text: String) = ADDRESS.matches(text.trim())

    /** EIP-55 checksummed form, so the same address always prints the same way. */
    fun checksum(address: String): String = Keys.toChecksumAddress(address.trim())

    fun sameAddress(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

    fun address(key: ECKeyPair): String = checksum("0x" + Keys.getAddress(key))

    /**
     * Mera's account derivation: the passkey's 32-byte PRF output is BIP-39 entropy; its mnemonic gives the
     * seed (no passphrase), and BIP-32 path m/44'/60'/0'/0/[index] gives the EVM key. Matches the
     * "Create passkey accounts" recipe in the mera docs, so the same passkey yields the same account there.
     */
    fun fromPrfOutput(prfOutput: ByteArray, index: Int = 0): ECKeyPair {
        require(prfOutput.size == 32) { "PRF output must be 32 bytes" }
        val seed = MnemonicUtils.generateSeed(MnemonicUtils.generateMnemonic(prfOutput), "")
        val h = Bip32ECKeyPair.HARDENED_BIT
        return Bip32ECKeyPair.deriveKeyPair(Bip32ECKeyPair.generateKeyPair(seed), intArrayOf(44 or h, 60 or h, 0 or h, 0, index))
    }

    /** EIP-191 `personal_sign` over [message]; returns the 65-byte r‖s‖v signature as 0x-hex. */
    fun personalSign(message: String, key: ECKeyPair): String {
        val sig = Sign.signPrefixedMessage(message.toByteArray(Charsets.UTF_8), key)
        return Numeric.toHexString(sig.r + sig.s + sig.v)
    }

    /** The address that produced an EIP-191 signature over [message], or null if the signature is malformed. */
    fun recoverPersonal(message: String, signatureHex: String): String? = runCatching {
        val bytes = Numeric.hexStringToByteArray(signatureHex)
        require(bytes.size == 65)
        val v = bytes[64].let { if (it < 27) (it + 27).toByte() else it }
        val data = Sign.SignatureData(v, bytes.copyOfRange(0, 32), bytes.copyOfRange(32, 64))
        checksum("0x" + Keys.getAddress(Sign.signedPrefixedMessageToKey(message.toByteArray(Charsets.UTF_8), data)))
    }.getOrNull()

    fun verifyPersonal(address: String, message: String, signatureHex: String): Boolean =
        recoverPersonal(message, signatureHex)?.let { sameAddress(it, address) } == true
}
