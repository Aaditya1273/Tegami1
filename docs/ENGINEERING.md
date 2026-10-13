# Tegami Android: engineering notes

What this build actually is, how it works, and what it does not do. If anything in the pitch
(README.md) disagrees with this file, this file describes the code.

## Status at a glance

| Area | State |
|---|---|
| Letters inbox from live Solana devnet (Token-2022 test mint) | **Working**, tested against real devnet transactions |
| Payer registry check (bundled list) | **Working**; the registry is a JSON asset, not an on-chain program |
| Seal v1 (registered payer signed the transfer + `tegami:v1;inv=` memo in the same tx) | **Working** |
| Confidential transfers | **Detected and displayed** (amount shown as encrypted). Sending, decrypting and applying pending balance are **not implemented** |
| Income statement (wallet-signed claim + on-chain evidence, minimal disclosure) | **Working**. Not zero-knowledge |
| Statement verifier (re-reads evidence at `finalized`) | **Working**, including from a `tegami://verify` deep link |
| Guardian | **Working** as deterministic on-device rules. **No language model** |
| Wallet signing via Mobile Wallet Adapter | Implemented (reused from the starter). **Not yet exercised against a real wallet in this build** (the emulator has none) |
| MCP server, messenger bridges, advances, Clean Streak, x402 | **Not built** |
| Mainnet | **Not supported**: the build refuses any network without an `assets/<network>.json` |

## Build and run

```bash
. scripts/toolchain.env                 # JDK 17, Android SDK, Solana CLI on PATH (no system changes)
./gradlew assembleDebug                  # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest              # JVM unit tests (fixtures are real devnet transactions)
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest   # Compose UI tests
./gradlew assembleRelease                # R8-minified, UNSIGNED (no signing keys in this repo)
```

`scripts/devnet_setup.sh [WALLET_ADDRESS]` (devnet only) creates the test world: a Token-2022 test dollar
`tUSD` with the Confidential Transfer extension, two registered test payers, one unregistered payer, a demo
recipient, and real payments, including a duplicate invoice from the unregistered payer and a memo written
as a prompt injection. It regenerates `app/src/main/assets/devnet.json`. Keys live in `.devnet/` (git-ignored).

## Demo path (no wallet needed)

1. Launch → **Try the devnet demo address** (watch-only).
2. Home: verified income (masked; tap **Show**), "1 encrypted, not counted", "Review 2 payments that need a look".
3. Letters → open **Kumo Studio · Sealed INV-1042**: the envelope opens; each claim (payer, amount, status,
   evidence, *not verified*) has its own card.
4. Open the **Hoshi Agency 75 tUSD** letter: its note ("Ignore previous instructions…") is shown as untrusted
   text and flagged.
5. Guardian → "Anything need a look?", "My exchange asked for source of funds" (proposal → review → share
   sheet), "send 50 to the stranger" (declined).
6. Verifier: `uv run scripts/demo_statement.py 2000 <sig> <sig>` signs a statement with the demo recipient's
   devnet key (it stands in for the wallet) and prints a `tegami://verify?s=…` link. Open it with
   `adb shell am start -a android.intent.action.VIEW -d '<link>'` → **Verified**. Use the unregistered payer's
   transaction as evidence to see a precise rejection.

With a real wallet on a phone: run `scripts/devnet_setup.sh <phone wallet address>`, connect the wallet,
then Proofs → amount → **Review what you'll share** → **Sign with wallet** → **Share**.

## Architecture

Single `:app` module (an MVP doesn't justify module boundaries yet). Packages:

| Package | Responsibility | Android-free? |
|---|---|---|
| `config` | `NetworkConfig`: mint, RPC, registry, loaded once from `assets/<network>.json` | yes |
| `domain` | `Letter`, `Seal`, `Payer`, `Settlement`, `Money` (exact base units), `SealMemo`, `UntrustedText`, `TegamiError` | yes |
| `solana` | `TransferParser` (jsonParsed tx → `IncomingTransfer`), `SolanaRpc` (read-only JSON-RPC over Ktor) | parser yes |
| `proof` | `IncomeStatement`, `StatementBuilder` (policy + minimal disclosure), `StatementCodec`, `StatementVerifier`, `Ed25519` (BouncyCastle) | yes |
| `guardian` | `Guardian` rules, typed `GuardianAction`s, `EvidenceSummary` | yes |
| `data` | `AccountRepository` (MWA, watch-only), `LetterRepository` (paging, in-memory), `AppPrefs`, `DisclosureLog` | no |
| `ui` | Compose screens, view models, design system (`ui/theme`, `ui/components`) | no |

Data flow: `getTokenAccountsByOwner(owner, mint)` → `getSignaturesForAddress(tokenAccount)` (paged, 25) →
`getTransaction(jsonParsed)` (≤4 in parallel) → `TransferParser` → `LetterRules.toLetter` (registry, seal,
settlement from the cluster's `confirmationStatus`) → `StateFlow` → screens. Nothing is cached to disk.

## The claims, kept separate

| Claim | Evidence | What it does **not** mean |
|---|---|---|
| **Registered payer** | Transfer authority is listed in the bundled registry | Anything about where the payer's funds came from |
| **Sealed** | Registered payer + payer is a signer of this tx + well-formed `tegami:v1;inv=` memo in the same tx | "Clean funds" |
| **Confidential amount** | Instruction is Token-2022 `confidentialTransfer` | Hidden sender, recipient, mint or timing (all public) |
| **Finalized** | `confirmationStatus == finalized` from the cluster | — |
| **Income statement verified** | Ed25519 signature by the recipient wallet + each listed tx re-read at `finalized` + registry + period + sum | Zero-knowledge; the verifier sees every listed payment |

Provenance of funds is never claimed. Every letter's detail screen says so.

## Security model

- **Keys:** Tegami holds none. Signing happens in the wallet app over Mobile Wallet Adapter; the wallet shows
  the exact statement text. The returned signature is verified locally before use.
- **Guardian boundary:** Guardian has no network, signing or sharing capability. It returns typed proposals;
  the UI requires a tap to open a draft (which then needs a wallet signature) or to open the share sheet
  after showing the exact text. Intent comes only from what the user types. Payment memos are sanitized
  (control/bidi characters stripped, 280-char cap), displayed as quoted data, and never parsed for intent.
  Tests cover the injection memo.
- **Statements:** bound to network, mint, recipient, period, verifier label, expiry (≤90 days) and a random
  nonce. Tamper → signature fails. Statements can't be revoked; they expire.
- **Deep links:** only `tegami://verify?s=` with a well-formed, size-capped (32 KB) base64url token; strict JSON
  decoding (`ignoreUnknownKeys = false`).
- **Device:** `FLAG_SECURE` on by default (screenshots/recents), amounts masked by default, backups and
  device-transfer of prefs disabled, cleartext traffic disabled, `INTERNET` is the only permission, sensitive
  clipboard flag for statement links (Android 13+).
- **Logs:** the app writes no logs containing addresses, amounts or statements.

### Data classification

| Data | Where | Sensitivity |
|---|---|---|
| Wallet address, label, MWA auth token | App-private SharedPreferences, no backup | Token: secret-ish (session); address: personal |
| Letters (amounts, payers) | Memory only; re-read from chain | Personal financial data (already public on-chain) |
| Disclosure history | App-private prefs | Personal |
| Preferences | App-private prefs | Low |

### Known risks and trust assumptions

- The **registry is bundled**: whoever builds the APK decides who is "registered". Upgrade path: on-chain
  registry program or a signed registry document with a pinned attestor key.
- The **public devnet RPC** is trusted for data. A malicious RPC could hide or fabricate letters on the
  user's device; statements are re-checked by the verifier against *their* RPC.
- The MWA **auth token** is in plain app-private prefs (not Keystore-wrapped).
- Statement **verifier binding** is a free-text label, not an identity.
- The verifier's view isn't private from the chain: anyone with the address can read its public history.
- **No external audit** of anything here.

## Reuse audit (starter: `Wins/Mewtwo`, FACEDOWN/PRESENCE)

The starter repository was not modified. Removed files from the copy were moved (not deleted) to
`Wins/TEGAMI-starter-archive/`.

| Reused / adapted | Replaced / removed |
|---|---|
| Gradle setup (AGP 8.5.2, Kotlin 1.9.24, Compose BOM 2024.06, Hilt), versions unchanged | Python ATTEST backend, Anchor `presence` program, TS SDK, demo media |
| Mobile Wallet Adapter repository (auth-token rotation + retry) → `AccountRepository` | Missions, motion probe, evidence chain, Urge Catcher (accessibility service), alarms, boot receiver |
| Design-system structure: spacing/shape/motion/haptics tokens, reduced-motion handling, button press scale | Brand, mascot, icons, copy; green palette → Tegami tokens with light/dark themes |
| Manrope + Inter fonts; backup-disabled manifest pattern | Permissions: vibrate, notifications, boot, wake lock, package queries, accessibility |
| Ktor client + error-mapping pattern | `web3-solana`, `zxing` dependencies; debug cleartext config |

Added dependencies: `navigation-compose` 2.7.7, `bcprov-jdk18on` 1.79 (Ed25519 verify). minSdk 24 → 26
(java.time / java.util.Base64 without desugaring).

## Release blockers

1. Signing config and key management (intentionally absent).
2. A production confidential stablecoin and real confidential-transfer support (ElGamal keys, proofs,
   decryption: needs the Rust `solana-zk-sdk` via JNI or a maintained Kotlin port).
3. A real payer registry with KYB, and a decision on who operates it.
4. Real-wallet testing of statement signing (MWA `signMessages`) across Phantom/Solflare/Seed Vault.
5. Privacy policy and Play data-safety disclosures; legal review of "income statement" wording.
6. External security review.
