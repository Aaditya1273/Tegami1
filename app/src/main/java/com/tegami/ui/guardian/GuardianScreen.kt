package com.tegami.ui.guardian

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.tegami.config.NetworkConfig
import com.tegami.data.AccountRepository
import com.tegami.data.Disclosure
import com.tegami.data.DisclosureLog
import com.tegami.data.LetterRepository
import com.tegami.guardian.Guardian
import com.tegami.guardian.GuardianAction
import com.tegami.guardian.GuardianContext
import com.tegami.guardian.GuardianReply
import com.tegami.ui.components.CodeBlock
import com.tegami.ui.components.FactLine
import com.tegami.ui.components.Gap
import com.tegami.ui.components.FoxSays
import com.tegami.ui.components.Pose
import com.tegami.ui.components.PrimaryButton
import com.tegami.ui.components.ScreenTitle
import com.tegami.ui.components.SecondaryButton
import com.tegami.ui.components.StatusPill
import com.tegami.ui.components.TextAction
import com.tegami.ui.components.Tone
import com.tegami.ui.theme.LocalReducedMotion
import com.tegami.ui.theme.Motion
import com.tegami.ui.theme.Spacing
import com.tegami.ui.theme.Tegami
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

enum class ProposalStatus { Pending, Approved, Dismissed }

data class Turn(val id: Int, val fromUser: Boolean, val text: String, val reply: GuardianReply? = null,
                val status: ProposalStatus? = null)

/** Holds the conversation and the outcome of every proposal: Guardian's action history for this session. */
@HiltViewModel
class GuardianViewModel @Inject constructor(
    private val config: NetworkConfig,
    private val accounts: AccountRepository,
    private val letters: LetterRepository,
    private val log: DisclosureLog,
) : ViewModel() {
    private val _turns = MutableStateFlow(listOf(Turn(0, false, Guardian.help().text, Guardian.help())))
    val turns: StateFlow<List<Turn>> = _turns

    private fun ctx() = GuardianContext(config, accounts.account.value?.address, letters.state.value.letters, LocalDate.now(ZoneOffset.UTC))

    fun ask(text: String) {
        val q = text.trim().take(500)
        if (q.isEmpty()) return
        val reply = Guardian.respond(q, ctx())
        append(Turn(0, true, q), Turn(0, false, reply.text, reply, if (reply is GuardianReply.Proposal) ProposalStatus.Pending else null))
    }

    fun explain(signature: String) {
        val letter = letters.letter(signature) ?: return
        append(Turn(0, true, "Explain this payment"), Turn(0, false, "", Guardian.explainReply(letter, ctx())).let { it.copy(text = it.reply!!.text) })
    }

    fun resolve(id: Int, status: ProposalStatus) = _turns.update { list -> list.map { if (it.id == id) it.copy(status = status) else it } }

    fun recordEvidenceShared(count: Int) =
        log.add(Disclosure(Disclosure.Kind.EvidenceSummary, "Shared from Guardian", System.currentTimeMillis() / 1000, count))

    private fun append(vararg t: Turn) = _turns.update { list ->
        var next = (list.maxOfOrNull { it.id } ?: 0)
        list + t.map { it.copy(id = ++next) }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuardianScreen(
    turns: List<Turn>,
    onAsk: (String) -> Unit,
    onResolve: (Int, ProposalStatus) -> Unit,
    onOpenStatementDraft: (GuardianAction.PrepareIncomeStatement) -> Unit,
    onEvidenceShared: (Int) -> Unit,
) {
    val c = Tegami.colors
    val ctx = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var reviewing by remember { mutableStateOf<Pair<Int, GuardianAction.ShareEvidenceSummary>?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(turns.size) { if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size) }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(horizontal = Spacing.gutter)) {
            item {
                ScreenTitle("Guardian")
                FoxSays(Pose.Guardian, "I explain your letters and prepare drafts. You approve everything; I can't sign or move money.",
                    foxHeight = 128.dp)
                Gap(12)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill("On-device rules · no AI model", Tone.Info)
                    StatusPill("Can't move money", Tone.Positive)
                }
                Gap(12)
            }
            items(turns, key = { it.id }) { t -> TurnView(t, onResolve, onOpenStatementDraft) { id, a -> reviewing = id to a } }
            item { Gap(12) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Guardian.SUGGESTIONS.forEach { s -> AssistChip(onClick = { onAsk(s) }, label = { Text(s) }) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.compact), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it.take(500) }, placeholder = { Text("Ask Guardian") },
                singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onAsk(input); input = "" }),
            )
            TextAction("Send") { onAsk(input); input = "" }
        }
    }

    reviewing?.let { (id, action) ->
        AlertDialog(
            onDismissRequest = { reviewing = null },
            title = { Text("Review before sharing") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("This exact text goes to the app you pick next.", style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
                    Gap(8)
                    CodeBlock(action.summary)
                }
            },
            confirmButton = {
                TextAction("Share") {
                    ctx.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, action.summary), "Share evidence"))
                    onEvidenceShared(action.paymentCount)
                    onResolve(id, ProposalStatus.Approved)
                    reviewing = null
                }
            },
            dismissButton = { TextAction("Cancel", color = c.inkSecondary) { reviewing = null } },
        )
    }
}

@Composable
private fun TurnView(
    t: Turn,
    onResolve: (Int, ProposalStatus) -> Unit,
    onOpenStatementDraft: (GuardianAction.PrepareIncomeStatement) -> Unit,
    onReviewEvidence: (Int, GuardianAction.ShareEvidenceSummary) -> Unit,
) {
    val c = Tegami.colors
    val reduced = LocalReducedMotion.current
    val enter = remember(t.id) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(t.id) { if (!reduced) enter.animateTo(1f, tween(Motion.control, easing = Motion.Standard)) }
    val bubble = Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 8.dp.toPx() }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = if (t.fromUser) Arrangement.End else Arrangement.Start) {
        if (t.fromUser) {
            Text(t.text, style = MaterialTheme.typography.bodyLarge, color = c.onPrimary,
                modifier = bubble.widthIn(max = 300.dp).background(c.primary, RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp))
            return@Row
        }
        Column(bubble.widthIn(max = 340.dp).background(c.surface, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
            .padding(14.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Text(t.text, style = MaterialTheme.typography.bodyLarge, color = c.ink)
            when (val r = t.reply) {
                is GuardianReply.Declined -> { Gap(8); StatusPill("Declined: outside what Guardian may do", Tone.Neutral) }
                is GuardianReply.Proposal -> {
                    Gap(10)
                    Text("If you approve", style = MaterialTheme.typography.labelMedium, color = c.inkTertiary)
                    r.effects.forEach { FactLine(it, Tone.Info) }
                    Text("What it reveals", style = MaterialTheme.typography.labelMedium, color = c.inkTertiary)
                    r.disclosures.forEach { FactLine(it, Tone.Warning) }
                    Gap(8)
                    when (t.status) {
                        ProposalStatus.Pending -> {
                            val action = r.action
                            PrimaryButton(if (action is GuardianAction.PrepareIncomeStatement) "Open draft" else "Review text") {
                                when (action) {
                                    is GuardianAction.PrepareIncomeStatement -> { onResolve(t.id, ProposalStatus.Approved); onOpenStatementDraft(action) }
                                    is GuardianAction.ShareEvidenceSummary -> onReviewEvidence(t.id, action)
                                }
                            }
                            Gap(6)
                            SecondaryButton("Dismiss") { onResolve(t.id, ProposalStatus.Dismissed) }
                        }
                        ProposalStatus.Approved -> StatusPill("Approved by you", Tone.Positive)
                        ProposalStatus.Dismissed -> StatusPill("Dismissed", Tone.Neutral)
                        null -> Unit
                    }
                }
                else -> Unit
            }
        }
    }
}
