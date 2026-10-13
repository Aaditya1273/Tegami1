package com.tegami.data

import android.app.Activity
import android.content.SharedPreferences
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.tegami.config.NetworkConfig
import com.tegami.domain.TegamiError
import com.tegami.evm.EvmKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.web3j.crypto.ECKeyPair
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** The address whose letters Tegami shows. Passkey and Google (Privy) accounts can sign; watch-only can't. */
data class Account(val address: String, val label: String, val kind: Kind) {
    enum class Kind { Passkey, Google, WatchOnly }
    val canSign: Boolean get() = kind != Kind.WatchOnly
}

/**
 * Passkey accounts in the mera model: the passkey's WebAuthn PRF output (fixed salt) is the root secret, and
 * the EVM key is derived from it on demand. Nothing secret is stored: prefs hold the public address, a label
 * and the credential id. The derived key lives in memory for a signing session and is dropped on [lock].
 */
@Singleton
class AccountRepository @Inject constructor(
    private val prefs: SharedPreferences,
    private val config: NetworkConfig,
) {
    private val _account = MutableStateFlow(load())
    val account: StateFlow<Account?> = _account
    private var session: ECKeyPair? = null
    private val _signingUnlocked = MutableStateFlow(false)
    /**
     * True while a passkey signing session is open: the derived key is held in memory, so the next signature needs no
     * prompt. It closes on [lock], on sign-out, after [SESSION_MS], or when Android ends the process.
     */
    val signingUnlocked: StateFlow<Boolean> = _signingUnlocked
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var expiry: Job? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val b64 = Base64.getUrlEncoder().withoutPadding()

    private fun load(): Account? {
        val address = prefs.getString(KEY_ADDRESS, null) ?: return null
        val kind = runCatching { Account.Kind.valueOf(prefs.getString(KEY_KIND, "") ?: "") }.getOrNull() ?: return null
        return Account(address, prefs.getString(KEY_LABEL, "") ?: "", kind)
    }

    private val rpId: String get() = config.passkeyRpId ?: throw TegamiError.PasskeyUnavailable

    /** Creates a new passkey and its account. One biometric prompt, plus a second if the provider defers PRF. */
    suspend fun createPasskeyAccount(activity: Activity): Account {
        // Each passkey gets its creation time, so several Tegami passkeys can be told apart in the picker.
        val label = "Tegami · " + java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", java.util.Locale.ENGLISH))
        val request = """{"challenge":"${random()}","rp":{"id":"$rpId","name":"Tegami"},""" +
            """"user":{"id":"${random()}","name":"$label","displayName":"$label"},""" +
            """"pubKeyCredParams":[{"type":"public-key","alg":-7},{"type":"public-key","alg":-257}],""" +
            """"authenticatorSelection":{"residentKey":"required","requireResidentKey":true,"userVerification":"required"},""" +
            """"attestation":"none","timeout":60000,"extensions":{"prf":{"eval":{"first":"$SALT"}}}}"""
        val response = try {
            CredentialManager.create(activity).createCredential(activity, CreatePublicKeyCredentialRequest(request))
        } catch (e: CreateCredentialCancellationException) {
            throw TegamiError.PasskeyCancelled
        } catch (e: CreateCredentialException) {
            throw classify(e.type, e.message)
        }
        val reg = json.parseToJsonElement((response as CreatePublicKeyCredentialResponse).registrationResponseJson).jsonObject
        val credentialId = reg["id"]?.jsonPrimitive?.content ?: throw TegamiError.Parse("passkey without id")
        // Some providers return PRF output at creation; others only confirm support and need one assertion.
        val prf = prfOutput(reg) ?: assertPrf(activity, credentialId).second
        return remember(prf, credentialId)
    }

    /** Signs in with an existing passkey (this phone or one synced to it) and restores the same account. */
    suspend fun signInWithPasskey(activity: Activity): Account {
        val (credentialId, prf) = assertPrf(activity, null)
        return remember(prf, credentialId)
    }

    /** A Google account: its Privy embedded wallet holds the key and signs on request. */
    fun rememberGoogle(address: String): Account {
        lock()
        return Account(EvmKeys.checksum(address), "Google · Privy wallet", Account.Kind.Google).also {
            prefs.edit().putString(KEY_ADDRESS, it.address).putString(KEY_LABEL, it.label)
                .putString(KEY_KIND, it.kind.name).remove(KEY_CREDENTIAL).apply()
            _account.value = it
        }
    }

    /** Read-only: shows an address's letters without any ability to sign for it. */
    fun watch(address: String, label: String = "Watch-only"): Account {
        val clean = address.trim()
        if (!EvmKeys.isAddress(clean)) throw TegamiError.Invalid("That isn't a Monad address (0x followed by 40 hex characters).")
        lock()
        return Account(EvmKeys.checksum(clean), label, Account.Kind.WatchOnly).also {
            prefs.edit().putString(KEY_ADDRESS, it.address).putString(KEY_LABEL, it.label)
                .putString(KEY_KIND, it.kind.name).remove(KEY_CREDENTIAL).apply()
            _account.value = it
        }
    }

    /**
     * The signing key for the current passkey account: the session key if unlocked, otherwise one passkey
     * prompt. The derived address must match the saved one, so a different passkey can never sign for it.
     */
    suspend fun signingKey(activity: Activity): ECKeyPair {
        val current = _account.value ?: throw TegamiError.NotConnected
        if (current.kind != Account.Kind.Passkey) throw TegamiError.WatchOnly
        session?.let { return it }
        val (_, prf) = assertPrf(activity, prefs.getString(KEY_CREDENTIAL, null))
        val key = EvmKeys.fromPrfOutput(prf)
        prf.fill(0)
        if (!EvmKeys.sameAddress(EvmKeys.address(key), current.address)) throw TegamiError.Invalid("That passkey belongs to a different account.")
        open(key)
        return key
    }

    /** Ends the signing session: Tegami drops its reference to the key, and the next signature asks for the passkey. */
    fun lock() {
        expiry?.cancel()
        session = null
        _signingUnlocked.value = false
    }

    private fun open(key: ECKeyPair) {
        session = key
        _signingUnlocked.value = true
        expiry?.cancel()
        expiry = scope.launch { delay(SESSION_MS); lock() }
    }

    fun disconnect() {
        lock()
        prefs.edit().remove(KEY_ADDRESS).remove(KEY_LABEL).remove(KEY_KIND).remove(KEY_CREDENTIAL).apply()
        _account.value = null
    }

    private fun remember(prf: ByteArray, credentialId: String): Account {
        val key = EvmKeys.fromPrfOutput(prf)
        prf.fill(0)
        open(key)
        return Account(EvmKeys.address(key), "Passkey account", Account.Kind.Passkey).also {
            prefs.edit().putString(KEY_ADDRESS, it.address).putString(KEY_LABEL, it.label)
                .putString(KEY_KIND, it.kind.name).putString(KEY_CREDENTIAL, credentialId).apply()
            _account.value = it
        }
    }

    /** One assertion ceremony evaluating the PRF with the fixed salt. Returns (credential id, 32-byte output). */
    private suspend fun assertPrf(activity: Activity, credentialId: String?): Pair<String, ByteArray> {
        val allow = credentialId?.let { ""","allowCredentials":[{"type":"public-key","id":"$it"}]""" } ?: ""
        val request = """{"challenge":"${random()}","rpId":"$rpId","userVerification":"required","timeout":60000$allow,""" +
            """"extensions":{"prf":{"eval":{"first":"$SALT"}}}}"""
        val result = try {
            CredentialManager.create(activity).getCredential(activity, GetCredentialRequest(listOf(GetPublicKeyCredentialOption(request))))
        } catch (e: GetCredentialCancellationException) {
            throw TegamiError.PasskeyCancelled
        } catch (e: NoCredentialException) {
            throw TegamiError.PasskeyNotFound
        } catch (e: GetCredentialException) {
            throw classify(e.type, e.message)
        }
        val cred = result.credential as? PublicKeyCredential ?: throw TegamiError.Parse("not a passkey credential")
        val auth = json.parseToJsonElement(cred.authenticationResponseJson).jsonObject
        val id = auth["id"]?.jsonPrimitive?.content ?: throw TegamiError.Parse("passkey without id")
        return id to (prfOutput(auth) ?: throw TegamiError.PasskeyUnavailable)
    }

    /** Maps Credential Manager failures to messages people can act on; the raw text stays in the log only. */
    private fun classify(type: String, message: String?): TegamiError {
        android.util.Log.w("Tegami", "passkey: $type ${message.orEmpty()}")
        val text = "$type ${message.orEmpty()}".lowercase()
        return when {
            "timed out" in text || "timeout" in text -> TegamiError.PasskeyTimedOut
            "cancel" in text || "user_canceled" in text -> TegamiError.PasskeyCancelled
            "no credential" in text || "no_credential" in text -> TegamiError.PasskeyNotFound
            "unsupported" in text || "not supported" in text -> TegamiError.PasskeyUnavailable
            else -> TegamiError.PasskeyFailed(type)
        }
    }

    private fun prfOutput(response: JsonObject): ByteArray? = runCatching {
        val first = response["clientExtensionResults"]!!.jsonObject["prf"]!!.jsonObject["results"]!!.jsonObject["first"]!!
            .jsonPrimitive.contentOrNull!!
        Base64.getUrlDecoder().decode(first.trimEnd('=')).takeIf { it.size == 32 }
    }.getOrNull()

    private fun random(): String = b64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

    companion object {
        private const val KEY_ADDRESS = "account_address"
        private const val KEY_LABEL = "account_label"
        private const val KEY_KIND = "account_kind"
        private const val KEY_CREDENTIAL = "passkey_credential_id"
        /** How long a signing session stays open after a passkey prompt. */
        const val SESSION_MS = 10 * 60_000L

        /** mera's fixed PRF salt: SHA-256("mera.prf.salt.v1"), base64url. Same salt, same account as mera. */
        val SALT: String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest("mera.prf.salt.v1".toByteArray()))
    }
}
