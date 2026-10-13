package com.tegami.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** User preferences. No secrets here. */
@Singleton
class AppPrefs @Inject constructor(private val prefs: SharedPreferences) {
    private val _secureScreen = MutableStateFlow(prefs.getBoolean(SECURE, true))
    private val _hideAmounts = MutableStateFlow(prefs.getBoolean(HIDE, true))
    private val _reducedMotion = MutableStateFlow(prefs.getBoolean(MOTION, false))

    /** Blocks screenshots and blanks the app in the recent-apps switcher. On by default. */
    val secureScreen: StateFlow<Boolean> = _secureScreen
    /** Masks amounts on Home and Letters until the user opens a letter or taps to reveal. */
    val hideAmounts: StateFlow<Boolean> = _hideAmounts
    val reducedMotion: StateFlow<Boolean> = _reducedMotion

    fun setSecureScreen(v: Boolean) { _secureScreen.value = v; prefs.edit().putBoolean(SECURE, v).apply() }
    fun setHideAmounts(v: Boolean) { _hideAmounts.value = v; prefs.edit().putBoolean(HIDE, v).apply() }
    fun setReducedMotion(v: Boolean) { _reducedMotion.value = v; prefs.edit().putBoolean(MOTION, v).apply() }

    private companion object {
        const val SECURE = "secure_screen"
        const val HIDE = "hide_amounts"
        const val MOTION = "reduced_motion"
    }
}

/** One thing the user chose to share. Recorded so they can see what they've disclosed, and to whom. */
@Serializable
data class Disclosure(
    val kind: Kind,
    val to: String,
    val at: Long,
    val payments: Int,
    val expiresAt: Long? = null,
    val nonce: String? = null,
) {
    enum class Kind { IncomeStatement, EvidenceSummary }
}

@Singleton
class DisclosureLog @Inject constructor(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Disclosure.serializer())
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<Disclosure>> = _entries

    private fun load(): List<Disclosure> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()

    fun add(d: Disclosure) {
        val next = (listOf(d) + _entries.value).take(MAX)
        _entries.value = next
        prefs.edit().putString(KEY, json.encodeToString(serializer, next)).apply()
    }

    fun clear() {
        _entries.value = emptyList()
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "disclosures"
        const val MAX = 100
    }
}
