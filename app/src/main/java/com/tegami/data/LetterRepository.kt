package com.tegami.data

import com.tegami.config.NetworkConfig
import com.tegami.domain.Letter
import com.tegami.domain.LetterRules
import com.tegami.domain.TegamiError
import com.tegami.evm.EvmKeys
import com.tegami.evm.EvmRpc
import com.tegami.evm.OnchainLetter
import com.tegami.evm.SealContract
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class LettersState(
    val owner: String? = null,
    val letters: List<Letter> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: TegamiError? = null,
    val canLoadMore: Boolean = false,
    val updatedAt: Instant? = null,
    /** Letters that arrived since the previous read in this session (never set on the first read). */
    val newIds: Set<String> = emptySet(),
)

/**
 * Incoming letters for one owner, read live from the TegamiSeal contract on Monad: the owner's letter
 * count, then each letter (newest first, paged), the payer's registry entry, and the paying transaction.
 * Kept in memory only.
 */
@Singleton
class LetterRepository @Inject constructor(
    private val rpc: EvmRpc,
    private val config: NetworkConfig,
) {
    private val _state = MutableStateFlow(LettersState())
    val state: StateFlow<LettersState> = _state
    private val mutex = Mutex()

    /** The next (older) index to load, or -1 once everything is loaded. */
    private var next: Long = -1
    private val names = mutableMapOf<String, String>()

    fun letter(id: String): Letter? = _state.value.letters.firstOrNull { it.id == id }

    suspend fun refresh(owner: String) = mutex.withLock {
        if (_state.value.owner != owner) { _state.value = LettersState(owner = owner); names.clear() }
        _state.update { it.copy(loading = true, error = null) }
        try {
            val count = SealContract.decodeCount(rpc.call(config.sealContract, SealContract.letterCountCall(owner)))
            val (letters, nextIndex) = loadPage(owner, count - 1)
            next = nextIndex
            _state.update {
                val before = it.letters.map { l -> l.id }.toSet()
                val arrived = if (before.isEmpty()) emptySet() else letters.map { l -> l.id }.toSet() - before
                it.copy(letters = letters, loading = false, canLoadMore = next >= 0, updatedAt = Instant.now(), newIds = arrived)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _state.update { it.copy(loading = false, error = TegamiError.from(e)) }
        }
    }

    suspend fun loadMore() = mutex.withLock {
        val owner = _state.value.owner ?: return@withLock
        if (next < 0) return@withLock
        _state.update { it.copy(loadingMore = true, error = null) }
        try {
            val (older, nextIndex) = loadPage(owner, next)
            next = nextIndex
            _state.update {
                val merged = (it.letters + older).distinctBy { l -> l.id }.sortedByDescending { l -> l.id.toLong() }
                it.copy(letters = LetterRules.markDuplicateInvoices(merged), loadingMore = false, canLoadMore = next >= 0)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _state.update { it.copy(loadingMore = false, error = TegamiError.from(e)) }
        }
    }

    fun clear() {
        next = -1
        names.clear()
        _state.value = LettersState()
    }

    /** Loads up to [PAGE] letters from [newest] downwards; returns them and the next index (-1 when done). */
    private suspend fun loadPage(owner: String, newest: Long): Pair<List<Letter>, Long> = coroutineScope {
        if (newest < 0) return@coroutineScope emptyList<Letter>() to -1L
        val oldest = maxOf(0, newest - PAGE + 1)
        val finalized = rpc.finalizedBlock()
        val gate = Semaphore(PARALLEL)
        val onchain = (newest downTo oldest).map { i ->
            async { gate.withPermit { SealContract.decodeLetter(i, rpc.call(config.sealContract, SealContract.letterAtCall(owner, i))) } }
        }.awaitAll().filter { EvmKeys.sameAddress(it.token, config.asset.address) }
        onchain.map { it.payer }.distinct().filter { it !in names }.forEach { payer ->
            names[payer] = SealContract.decodeString(rpc.call(config.sealContract, SealContract.payerNameCall(payer)))
        }
        val letters = onchain.map { l ->
            async {
                gate.withPermit {
                    LetterRules.toLetter(l, LetterRules.settlement(l.blockNumber, finalized), names[l.payer], txHash(owner, l), config)
                }
            }
        }.awaitAll()
        LetterRules.markDuplicateInvoices(letters) to (oldest - 1)
    }

    /** The paying transaction, found from the contract's `Sealed` event in the letter's own block. */
    private suspend fun txHash(owner: String, l: OnchainLetter): String? = runCatching {
        rpc.logs(config.sealContract, l.blockNumber, l.blockNumber,
            listOf(SealContract.SEALED_TOPIC, SealContract.topicAddress(owner), SealContract.topicUint(l.index)))
            .firstOrNull()?.txHash
    }.getOrNull()

    private companion object {
        const val PAGE = 25
        const val PARALLEL = 4 // the public RPC rate-limits bursts
    }
}
