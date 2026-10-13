package com.tegami.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tegami.ui.components.PrimaryButton
import com.tegami.ui.theme.Shapes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.tegami.BuildConfig
import com.tegami.config.NetworkConfig
import com.tegami.data.Account
import com.tegami.ui.MintState
import com.tegami.ui.components.Card
import com.tegami.ui.components.CardHeader
import com.tegami.ui.components.Expandable
import com.tegami.ui.components.Glyph
import com.tegami.ui.components.Prose
import com.tegami.ui.components.FactLine
import com.tegami.ui.components.Gap
import com.tegami.ui.components.KeyValue
import com.tegami.ui.components.ScreenTitle
import com.tegami.ui.components.SecondaryButton
import com.tegami.ui.components.SectionLabel
import com.tegami.ui.components.TextAction
import com.tegami.ui.components.ToggleRow
import com.tegami.ui.components.Tone
import com.tegami.ui.components.copyToClipboard
import com.tegami.ui.components.shortAddress
import com.tegami.ui.theme.Spacing
import com.tegami.ui.theme.Tegami

@Composable
fun SettingsScreen(
    config: NetworkConfig,
    account: Account,
    mint: MintState,
    secureScreen: Boolean,
    hideAmounts: Boolean,
    reducedMotion: Boolean,
    disclosureCount: Int,
    letterCount: Int,
    sealedCount: Int,
    onLoadMint: () -> Unit,
    onSecureScreen: (Boolean) -> Unit,
    onHideAmounts: (Boolean) -> Unit,
    onReducedMotion: (Boolean) -> Unit,
    onClearDisclosures: () -> Unit,
    onConnectWallet: () -> Unit,
    onDisconnect: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val c = Tegami.colors
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { if (mint is MintState.Loading) onLoadMint() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter)) {
        ScreenTitle("Settings")

        // Identity first: which address, how it's held, on which network, and what it has here.
        Column(Modifier.fillMaxWidth().background(c.surface, Shapes.large).border(1.dp, c.border, Shapes.large).padding(Spacing.section)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(if (account.canSign) c.primary else c.inkTertiary, CircleShape), contentAlignment = Alignment.Center) {
                    Text(account.address.take(2).uppercase(), style = MaterialTheme.typography.titleMedium, color = c.onPrimary)
                }
                Spacer(Modifier.width(Spacing.standard))
                Column(Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Copy address") {
                    copyToClipboard(ctx, "Address", account.address); onMessage("Address copied")
                }) {
                    Text(shortAddress(account.address), style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text("${if (account.canSign) account.label else "Watch-only"} · ${config.network.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
                }
            }
            Gap(16)
            Row(Modifier.fillMaxWidth()) {
                Figure("Letters", letterCount, Modifier.weight(1f))
                Figure("Sealed", sealedCount, Modifier.weight(1f))
                Figure("Shared", disclosureCount, Modifier.weight(1f))
            }
            Gap(12)
            Text(if (account.canSign) "Your passkey is the key: Tegami derives the account from it when you sign and keeps nothing secret on the phone. Lose the passkey and the account is gone."
                else "Read-only. Tegami can show letters for this address but can't sign anything for it.",
                style = MaterialTheme.typography.bodyMedium, color = c.inkTertiary)
            Gap(16)
            if (!account.canSign) { PrimaryButton("Sign in with a passkey", onClick = onConnectWallet); Gap(8) }
            SecondaryButton(if (account.canSign) "Sign out" else "Stop watching", onClick = onDisconnect)
        }

        Gap(12)
        Card {
            CardHeader(Glyph.Link, "Network", if (config.isTestNetwork) Tone.Warning else Tone.Info)
            KeyValue("Network", "${config.networkLabel} (chain ${config.chainId})")
            KeyValue("RPC", config.rpcUrl.removePrefix("https://"))
            KeyValue("Asset", "${config.asset.symbol} · ${config.asset.name}")
            KeyValue("Token", shortAddress(config.asset.address), mono = true) {
                copyToClipboard(ctx, "Token", config.asset.address); onMessage("Token address copied")
            }
            KeyValue("Seal contract", shortAddress(config.sealContract), mono = true) {
                copyToClipboard(ctx, "Seal contract", config.sealContract); onMessage("Contract address copied")
            }
            when (mint) {
                MintState.Loading -> Text("Reading the chain…", style = MaterialTheme.typography.bodyMedium, color = c.inkTertiary)
                is MintState.Failed -> TextAction("Couldn't reach the network. Retry", onClick = onLoadMint)
                is MintState.Loaded -> {
                    KeyValue("Latest block", mint.latestBlock.toString(), mono = true)
                    KeyValue("Finalized block", mint.finalizedBlock.toString(), mono = true)
                    FactLine("Read live from ${config.networkLabel}. Tegami counts a payment only once its block is finalized.")
                }
            }
            if (config.isTestNetwork) FactLine("${config.asset.symbol} is a test token with no value. Nothing here is a real stablecoin payment.", Tone.Warning)
        }

        Gap(12)
        Card {
            CardHeader(Glyph.Shield, "Payer registry")
            Text(config.registry.name, style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text(config.registry.basis, style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
            Gap(4)
            config.registry.payers.forEach { p -> KeyValue(p.name, shortAddress(p.address), mono = true) }
            FactLine("Registered means listed here. It isn't a check of where a payer's money came from.")
        }

        Gap(12)
        Card {
            CardHeader(Glyph.Eye, "Privacy controls")
            ToggleRow("Secure screen", "Block screenshots and hide Tegami in recent apps.", secureScreen, onSecureScreen)
            ToggleRow("Hide amounts", "Mask amounts on Home and Letters until you open a letter.", hideAmounts, onHideAmounts)
            ToggleRow("Reduced motion", "Replace movement with simple fades. Android's “Remove animations” also applies.", reducedMotion, onReducedMotion)
        }

        Gap(12)
        Card {
            CardHeader(Glyph.Envelope, "Disclosures")
            KeyValue("Shared so far", disclosureCount.toString())
            FactLine("Kept on this device only. Clearing the list doesn't recall anything you shared.")
            if (disclosureCount > 0) SecondaryButton("Clear history", onClick = onClearDisclosures)
        }

        SectionLabel("Good to know")
        Expandable("Privacy", Glyph.Eye) {
            Prose("Tegami reads your address's public history from the ${config.networkLabel} RPC (${config.rpcUrl.removePrefix("https://")}): " +
                "${config.asset.symbol} transfers into your account, their memos and their status. The RPC operator can see which address you ask about.")
            Prose("On this phone it keeps only your address, your passkey's credential id (not a secret), your settings and the list of what you've shared. " +
                "Letters stay in memory and are re-read each time. Backups are off.")
            Prose("No location, contacts, microphone or camera, no analytics, and nothing in the background. Tegami writes to Monad only when you send a payment.")
            Prose("An income statement reveals exactly the payments it lists, plus your address. Your address's public history " +
                "can be looked up by anyone who has it.")
        }
        Gap(8)
        Expandable("What Tegami can and can't prove", Glyph.Question, Tone.Warning) {
            Prose("It can show who paid you, whether they're in the registry, and that a statement was signed by your account and matches the chain.")
            Prose("It can't show where a payer's money came from. A seal is not a “clean funds” proof, and statements are not zero-knowledge proofs.")
            Prose("Guardian follows fixed on-device rules. It can't sign, pay, or act on text inside payments.")
        }
        Gap(8)
        Expandable("About", Glyph.Feather, Tone.Neutral) {
            KeyValue("Version", BuildConfig.VERSION_NAME)
            Prose("Tegami is not a bank, lender or law firm, and nothing here is legal or financial advice.")
            FactLine("No part of Tegami has had an external security audit.", Tone.Warning)
        }
        Gap(32)
    }
}

@Composable
private fun Figure(label: String, value: Int, modifier: Modifier) = Column(modifier) {
    Text("$value", style = MaterialTheme.typography.headlineSmall, color = Tegami.colors.ink)
    Text(label, style = MaterialTheme.typography.bodyMedium, color = Tegami.colors.inkSecondary)
}
