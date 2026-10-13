package com.tegami.ui.welcome

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tegami.config.NetworkConfig
import com.tegami.ui.components.Fox
import com.tegami.ui.components.Pose
import com.tegami.ui.components.Gap
import com.tegami.ui.components.NetworkBadge
import com.tegami.ui.components.PrimaryButton
import com.tegami.ui.components.SecondaryButton
import com.tegami.ui.components.TextAction
import com.tegami.ui.theme.Spacing
import com.tegami.ui.theme.Tegami

@Composable
fun WelcomeScreen(
    config: NetworkConfig,
    connecting: Boolean,
    onConnect: () -> Unit,
    onSignIn: () -> Unit,
    onGoogle: (() -> Unit)?,
    onWatch: (String) -> String?,
    onDemo: () -> String?,
    onVerify: () -> Unit,
) {
    val c = Tegami.colors
    var watching by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Gap(24)
        NetworkBadge(config.networkLabel, config.asset.symbol)
        Gap(24)
        Fox(Pose.Wave, 220.dp)
        Gap(28)
        Text("Every payment, sealed.", style = MaterialTheme.typography.headlineLarge, color = c.ink,
            modifier = Modifier.fillMaxWidth().semantics { heading() })
        Gap(10)
        Text(
            "Tegami reads the payments sent to your Monad account, shows which come from registered payers, " +
                "and lets you prove your income to someone without handing over your whole history.",
            style = MaterialTheme.typography.bodyLarge, color = c.inkSecondary, modifier = Modifier.fillMaxWidth(),
        )
        Gap(28)
        PrimaryButton(if (connecting) "Waiting for your passkey…" else "Create account with a passkey", loading = connecting, onClick = onConnect)
        Gap(8)
        Text("Your fingerprint or face unlocks it. No seed phrase, no wallet app: the key comes from your passkey and is never stored.",
            style = MaterialTheme.typography.bodyMedium, color = c.inkTertiary, modifier = Modifier.fillMaxWidth())
        Gap(8)
        SecondaryButton("I already have a passkey", enabled = !connecting, onClick = onSignIn)
        Gap(8)
        if (onGoogle != null) {
            SecondaryButton("Continue with Google", enabled = !connecting, onClick = onGoogle)
            Gap(8)
        }
        if (!watching) {
            SecondaryButton("Watch an address instead") { watching = true }
        } else {
            OutlinedTextField(
                value = address, onValueChange = { address = it.trim(); error = null },
                label = { Text("Monad address (0x…)") }, singleLine = true, isError = error != null,
                supportingText = { Text(error ?: "Read-only: you can view letters but not sign statements.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Gap(8)
            SecondaryButton("Watch this address", enabled = address.isNotBlank()) { error = onWatch(address) }
        }
        if (config.demoRecipient != null) {
            Gap(8)
            TextAction("Try the ${config.networkLabel} demo address") { error = onDemo() }
        }
        TextAction("I received a statement to verify", onClick = onVerify)
        Spacer(Modifier.height(Spacing.major))
    }
}
