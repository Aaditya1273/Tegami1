# Tegami on Monad: engineering notes

What this build actually is, how it works, and what it does not do. If anything elsewhere disagrees with this
file, this file describes the code.

## Status at a glance

| Area | State |
|---|---|
| `TegamiSeal` contract: sealed payments (`pay`) + per-recipient letters + owner-kept payer registry | **Deployed on Monad testnet**, 4 Foundry tests |
| Letters inbox read live from the contract (`letterCount`/`letterAt`), finality from Monad's `finalized` block | **Working** |
| Passkey accounts (mera model: PRF → BIP-39 → `m/44'/60'/0'/0/0`) via Android Credential Manager | **Verified on a real phone** (Realme, Android, Google Password Manager): account creation, statement signing, payment signing; derivation checked against a reference implementation |
| Income statements: EIP-191 signature by the passkey account, minimal disclosure | **Working** |
| Statement verifier: re-reads each letter and the registry at the finalized block | **Working**, including from a `tegami://verify` link |
| Sending sealed payments from the app (approve once + `pay`, EIP-1559) | **Working**: a payment sent from the phone is on-chain (25 tUSD, invoice TEST-0001); needs testnet MON for fees |
| Guardian | Deterministic on-device rules. **No language model** |
| Real AUSD, mainnet, gas sponsorship | **Not built** (the router accepts any ERC-20; testnet uses `tUSD`) |

## Build and run

```bash
. scripts/toolchain.env
./gradlew testDebugUnitTest            # 30 JVM tests
./gradlew assembleDebug
(cd contracts && forge test)           # 4 Foundry tests
PASSKEY_RP_ID=tegami-monad.vercel.app scripts/monad_setup.sh [0xADDRESS…]
```

`scripts/monad_setup.sh` (testnet only) reads `PRIVATE_KEY` from `.env.local` (git-ignored) and:
1. Deploys `TestDollar` and `TegamiSeal` once.
2. Registers two test payers, Kumo Studio and Hoshi Agency, plus one unregistered "stranger".
3. Mints `tUSD` to them and tops up their MON for fees.
4. Sends five real sealed payments to a demo freelancer, and to any addresses passed as arguments. These include a duplicate invoice from the unregistered payer and a note written as a prompt injection.
5. Writes `app/src/main/assets/monad-testnet.json`.

Payer keys live in `.monad/`, which is git-ignored.

## The contract

`pay(token, to, amount, invoice, note)`:
1. Calls `transferFrom(msg.sender, to, amount)` on the token, so the caller needs an allowance.
2. Appends `Letter{token, payer = msg.sender, amount, time, blockNumber, invoice, note}` to `to`'s list.
3. Emits `Sealed(to, index, payer, token, amount, invoice)`.

Invoice ≤ 64 bytes, note ≤ 280 bytes. Non-standard tokens that return nothing are handled with a low-level call. The registry `payerName` is written only by the deployer.

Because the payer is `msg.sender`, "the payer signed this payment" is enforced by the chain. The Solana version needed a separate signer check for that.

## The claims, kept separate

| Claim | Evidence | What it does **not** mean |
|---|---|---|
| Registered payer | `payerName(payer)` non-empty | Anything about the source of the payer's funds |
| Sealed | Registered payer + `msg.sender` paid + plain invoice (`[A-Za-z0-9._-]{1,40}`) | "Clean funds" |
| Finalized | letter block ≤ `eth_getBlockByNumber("finalized")` | — |
| Statement verified | EIP-191 recover = recipient; each letter re-read with `eth_call` at `finalized`; registry at `finalized`; token, period, sum | Zero-knowledge |

## Security model

- **Keys.**
  - The account key is derived from the passkey's PRF output when needed and is never written to storage.
  - A **signing session** keeps it in memory so a second signature needs no prompt. The session is shown as "Signing unlocked" on Proofs, Pay and Settings.
  - The session closes when the person taps **Lock**, after 10 minutes, on sign-out, or when Android ends the process. Lock is hidden while a signature or payment is in flight.
  - Locking drops Tegami's reference to the key; the JVM gives no guarantee the old copy is zeroed immediately.
  - Prefs store the address, a label and the credential id, none of them secret.
  - A different passkey can't sign for a saved account: the derived address must match.
- **Passkey errors.** Credential Manager failures are classified (cancelled, timed out, no passkey, no PRF, other). People see plain language; the raw system text goes to the log only.
- **MetaMask accounts.** MetaMask signs inside its own browser via `web/mm.html`. Results return by `tegami://metamask?id=…` and must match the open request id. Signatures are verified on the phone, and transactions are confirmed on Monad before they count.
- **Passkey binding.** The rpId `tegami-monad.vercel.app` serves `/.well-known/assetlinks.json` listing the app's package and both signing certificates (debug and release). Android refuses passkeys for an rpId that doesn't vouch for the app.
- **Statements.**
  - Each statement is bound to network, chain id, seal contract, token, recipient, period, verifier label, expiry (≤ 90 days) and a random nonce.
  - Any edit breaks the signature.
  - Statements can't be revoked; they expire.
- **Guardian boundary.**
  - Guardian has no network, signing or sharing capability.
  - Payment notes are sanitized (control and bidi characters stripped, 280-character cap), shown as quoted data, and never parsed for intent.
- **Device.**
  - `FLAG_SECURE` is on by default, amounts are masked by default, backups are off, cleartext traffic is off.
  - `INTERNET` is the only permission.
- **Logs.** The app logs no addresses, amounts or statements.

## Known risks and trust assumptions

- The **registry owner** decides who is "registered". Production needs KYB and governance (or a multisig owner).
- The **public RPC** is trusted for data on the user's device; verifiers re-check against their own RPC.
- **Losing the passkey** loses the account. Mera's secret-vault export pattern isn't built yet.
- On Monad, amounts, payers, invoices and notes are **public**.
- **No external audit.**

## Release blockers

1. Mainnet deployment with AUSD and a governed registry owner.
2. Gas sponsorship (paymaster or relayer) so new accounts can pay without holding MON.
3. Account recovery and export path (mera secret vault) before real funds.
4. Privacy policy, Play data-safety, legal review of "income statement" wording.
5. External security review of `TegamiSeal` and the app.
