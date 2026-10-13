package com.tegami.ui.letters

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tegami.config.NetworkConfig
import com.tegami.data.LettersState
import com.tegami.domain.Letter
import com.tegami.domain.LetterRules
import com.tegami.domain.Seal
import com.tegami.ui.components.ErrorBanner
import com.tegami.ui.components.Gap
import com.tegami.ui.components.LetterCard
import com.tegami.ui.components.SearchField
import com.tegami.ui.components.FilterRow
import com.tegami.ui.components.LoadingRow
import com.tegami.ui.components.MessageState
import com.tegami.ui.components.ScreenTitle
import com.tegami.ui.components.SecondaryButton
import com.tegami.ui.components.TextAction
import com.tegami.ui.components.payerName
import com.tegami.ui.theme.Spacing
import com.tegami.ui.theme.Tegami
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class LetterFilter(val label: String, val matches: (Letter) -> Boolean) {
    All("All", { true }),
    Sealed("Sealed", { it.seal is Seal.Sealed }),
    Attention("Needs a look", LetterRules::needsAttention),
    Encrypted("Encrypted", { it.amount == null }),
}

@Composable
fun LettersScreen(
    config: NetworkConfig,
    state: LettersState,
    hideAmounts: Boolean,
    initialFilter: LetterFilter = LetterFilter.All,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val c = Tegami.colors
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = state.letters.filter(filter.matches).filter { l ->
        q.isEmpty() || payerName(l.payer).lowercase().contains(q) || l.payer.address.lowercase().startsWith(q) ||
            l.id.lowercase().startsWith(q) || (l.seal as? Seal.Sealed)?.invoice?.lowercase()?.contains(q) == true ||
            (l.seal as? Seal.Untrusted)?.invoice?.lowercase()?.contains(q) == true
    }
    val g = Modifier.padding(horizontal = Spacing.gutter)
    // Bottom padding so the last letter scrolls fully clear of the tab bar.
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = Spacing.section)) {
        item {
            ScreenTitle("Letters", g) { TextAction(if (state.loading) "Updating…" else "Refresh", onClick = onRefresh) }
            SearchField(query, { query = it.take(64) }, "Search payer, invoice or address", g)
            Gap(10)
        }
        item {
            FilterRow(LetterFilter.entries, filter, label = { it.label }, count = { f -> state.letters.count(f.matches) }, onSelect = { filter = it })
        }
        item {
            Column(g) {
            Gap(8)
            state.updatedAt?.let {
                Text("Read from ${config.networkLabel} at ${it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}",
                    style = MaterialTheme.typography.bodyMedium, color = c.inkTertiary)
            }
            Gap(12)
            state.error?.let { ErrorBanner(it, onRetry = onRefresh); Gap(12) }
            }
        }
        when {
            state.loading && state.letters.isEmpty() -> item { LoadingRow("Reading letters…") }
            shown.isEmpty() -> item {
                if (state.letters.isEmpty()) MessageState("No letters yet", "Payments of ${config.asset.symbol} to this address will appear here.")
                else MessageState("Nothing matches", "Try another filter or search.", action = "Clear") { filter = LetterFilter.All; query = "" }
            }
            else -> itemsIndexed(shown, key = { _, l -> l.id }) { i, l ->
                Column(g) {
                    LetterCard(l, hideAmounts, i, isNew = l.id in state.newIds) { onOpen(l.id) }
                    Gap(10)
                }
            }
        }
        item {
            Column(g) {
            when {
                state.loadingMore -> LoadingRow("Reading older letters…")
                state.canLoadMore -> SecondaryButton("Load older letters", onClick = onLoadMore)
                state.letters.isNotEmpty() -> Text("That's every letter for this address.", style = MaterialTheme.typography.bodyMedium,
                    color = c.inkTertiary, modifier = Modifier.fillMaxWidth())
            }
            Gap(24)
            }
        }
    }
}
