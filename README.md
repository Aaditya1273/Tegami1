# Tegami 手紙 — every payment, sealed

**A mobile app on Monad where a freelancer's income arrives as sealed letters, and proving that income takes one fingerprint, not a bank statement.**

Tegami (手紙, Japanese for "letter") is for people who are paid in stablecoins across borders: freelancers, contractors, creators. When a client pays through Tegami, the money and the invoice travel in **one Monad transaction**, recorded as a letter for the recipient. Later, when a landlord, lender or visa office asks "prove your income", the recipient signs a short **income statement** with their passkey. It lists only the payments the claim needs, and anyone can check it against the chain in seconds.

No seed phrase. No wallet app. No server holding keys. A passkey (Face ID, fingerprint) *is* the account.

**Live on Monad testnet** · Android (Kotlin, Jetpack Compose) · [Download the APK](https://github.com/Aaditya1273/Tegami1/raw/main/apk/Tegami-monad-testnet.apk)

| | |
|---|---|
| Track | **Consumer Products & Payments** |
| Bounties | Best Mera-Powered UX · Mera: One Passkey, Many Keys · Agora Cross-Border Payments (AUSD-ready router; testnet uses a test dollar) |
| Contracts (Monad testnet, chain 10143) | `TegamiSeal` [`0x74b4…ffD8`](https://testnet.monadexplorer.com/address/0x74b4413B3f8433Ec62469Edd03099a4CFE87fFD8) · `tUSD` test dollar [`0x3e17…7F4c`](https://testnet.monadexplorer.com/address/0x3e17a15aC573d9550CCC137d7BBC21c855797F4c) |
| Passkey domain | [`tegami-monad.vercel.app`](https://tegami-monad.vercel.app/.well-known/assetlinks.json) (Digital Asset Links for the app) |

---

## Why this matters

A freelancer in Lagos, Manila or Pune paid in dollar stablecoins has the money, but not the paperwork. Banks and landlords ask for statements; exchanges ask for "source of funds". Today the choices are to hand over a whole wallet history (every payment, every counterparty) or to screenshot a balance anyone can fake.

Tegami makes the payment itself the paperwork:

1. **Pay → sealed letter.** The client pays through the `TegamiSeal` contract with an invoice reference and a note. The contract moves the money and stores the letter in the same transaction, so the invoice is provably attached by the payer.
2. **Letters inbox.** The recipient sees each payment as a letter: who paid, whether the payer is in the on-chain registry, finalized or not, sealed or not, and anything that needs a look (unregistered payer, reused invoice, a note that reads like a prompt-injection attempt).
3. **Income statement.** "I received at least 2,000 tUSD from registered payers between Sep 14 and Oct 13, for Landlord." Tegami picks the fewest payments that prove it (largest first), shows exactly what will be revealed, and the passkey signs it. The statement travels as a link.
4. **Anyone verifies.** The verifier re-reads every listed letter and the registry **at Monad's finalized block**, checks the signature and the dates, and shows a check-by-check trust receipt.

Guardian, a prepare-only assistant, explains letters, flags problems and drafts statements or evidence summaries, but it can't sign, pay or act on text inside payments.

## Why Monad

- **Finality you can show a landlord.** Letters count only once their block is finalized, and Monad finalizes within about a second. The app reads `finalized` directly; no waiting a day for "settled".
- **Fees low enough for every invoice.** Storing the letter (payer, amount, invoice, note) next to the payment is affordable on Monad, so the receipt lives where the money moves, not in someone's database.
- **Passkey-native accounts with Mera.** Monad's mera model turns a passkey into an account. Tegami implements it natively on Android, so onboarding is one biometric prompt.
- **AUSD.** `TegamiSeal.pay(token, …)` works with any ERC-20, built for Agora's AUSD on Monad. On testnet it uses `tUSD`, a test dollar with no value.

## Passkey accounts: mera, natively on Android

Tegami follows mera's account model exactly, implemented in Kotlin with Android Credential Manager (mera's own library targets web and React Native):

```
passkey ceremony (user verification required)
  └─ WebAuthn PRF, salt = SHA-256("mera.prf.salt.v1")  ── 32 bytes, same on every synced device
       └─ BIP-39 entropy → seed (no passphrase)
            └─ BIP-32 m/44'/60'/0'/0/0 → secp256k1 key → Monad address
```

- **Same account as mera.** Same salt, same derivation as mera's "Create passkey accounts" recipe. A unit test checks the address against an independent Python implementation.
- **Nothing secret is stored.** The phone keeps the address and the credential id. The key is re-derived at signing time and held in memory for a signing session, mera's "signing sessions" pattern.
- **One passkey, many keys** (the creative use the Mera bounty asks for). The same passkey signs payments **and** income statements. A statement is an EIP-191 message, so any Ethereum tool can verify it, not only Tegami.
- **Recovery is the passkey.** It syncs through Google Password Manager, so a new phone restores the same account ("I already have a passkey").

## What's proven, and what isn't

Tegami is careful to claim only what the chain shows.

| Claim | Evidence | What it does **not** mean |
|---|---|---|
| **Registered payer** | `TegamiSeal.payerName(payer)` is set by the contract owner | Anything about where the payer's money came from |
| **Sealed** | Registered payer + the payer itself sent the transaction (`msg.sender`) + a plain invoice reference in the same transaction | "Clean funds" |
| **Finalized** | The letter's block ≤ Monad's finalized block | — |
| **Income statement verified** | EIP-191 signature by the recipient account + each listed letter re-read at the finalized block + registry + period + sum | Zero-knowledge: the verifier sees every listed payment |

On Monad, amounts, payers, invoices and notes are public. A statement keeps disclosure *small*, not secret.

## Try it

**No account needed (about 1 minute):** install the [APK](https://github.com/Aaditya1273/Tegami1/raw/main/apk/Tegami-monad-testnet.apk) (Android 9+), then tap **Try the Monad testnet demo address**. Then:
- **Home:** the fox reads the inbox and flags two payments.
- **Letters:** open the 75 tUSD letter; its note is a prompt-injection attempt, shown as quoted untrusted text.
- **Guardian:** ask "Anything need a look?" or "send 50 to the stranger" (it declines).

**With your own passkey account:** tap **Create account with a passkey** and approve with your fingerprint. To receive letters, run `scripts/monad_setup.sh 0xYOUR_ADDRESS`, which sends real sealed testnet payments to it. Then:
- **Proofs** → amount → **Review what you'll share** → **Sign with passkey** → **Share**.
- Open the link: **Verified**, with a trust receipt.
- **Home → Send a sealed payment** pays anyone with an invoice attached (needs testnet MON for fees: [faucet.monad.xyz](https://faucet.monad.xyz)).

## Build and run

```bash
. scripts/toolchain.env                     # JDK 17 + Android SDK on PATH
./gradlew testDebugUnitTest                 # 34 JVM tests (incl. mera + EIP-191 reference vectors)
./gradlew assembleDebug                     # app/build/outputs/apk/debug/app-debug.apk
# Optional "Continue with Google" (Privy): add -Ptegami.privyAppId=… -Ptegami.privyClientId=… (public IDs)
(cd contracts && forge test)                # 4 Foundry tests for TegamiSeal
(cd indexer && pnpm i && pnpm test)         # Envio indexer: handler tests + real Monad letters checked against letterAt()
PASSKEY_RP_ID=tegami-monad.vercel.app scripts/monad_setup.sh   # deploy + seed (needs PRIVATE_KEY in .env.local)
```

## Architecture

| Part | What it does |
|---|---|
| `contracts/src/TegamiSeal.sol` | `pay(token, to, amount, invoice, note)`: transferFrom + letter, one transaction; `letterCount`/`letterAt`; owner-kept payer registry |
| `contracts/src/TestDollar.sol` | `tUSD`: minimal ERC-20 test dollar (no value) |
| `app/…/evm` | Monad JSON-RPC (`eth_call` at `finalized`, logs, EIP-1559 send), ABI via web3j, EIP-191 sign/recover, mera derivation |
| `app/…/data` | Passkey accounts (Credential Manager + PRF), Google accounts (Privy embedded wallet), letters, sealed payments |
| `app/…/proof` | Statement builder (minimal disclosure), codec (`tegami://verify?s=`), verifier |
| `app/…/guardian` | Deterministic on-device rules; typed proposals; never acts on payment text |
| `indexer/` | Envio HyperIndex: `Letter`, `Payer`, `Recipient` from `Sealed`/`PayerSet` via HyperSync; the app lists letters with one GraphQL query and falls back to the contract |
| `scripts/monad_setup.sh` | Deploys, registers test payers, sends real sealed payments |

More detail, including security model and known limits: [docs/ENGINEERING.md](docs/ENGINEERING.md). The longer-term product vision: [docs/VISION.md](docs/VISION.md).

## Honest limits

- **Testnet only.** `tUSD` is a test token; nothing here is real money. Mainnet AUSD needs a deployment and a funded registry owner.
- **The registry is one owner's list.** Real use needs a KYB process and a decision about who runs it.
- **Fees.** A passkey account needs testnet MON to send payments (receiving and signing statements cost nothing). Gas sponsorship isn't built.
- **No external audit** of the contract or the app.
