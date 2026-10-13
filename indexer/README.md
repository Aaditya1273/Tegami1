# Tegami indexer (Envio HyperIndex)

Indexes TegamiSeal on Monad testnet (`0x74b4…fFD8`, from block 70739528) into three GraphQL entities:

- `Letter`: one sealed payment (recipient, index, payer, token, amount, invoice, note, block, time, tx hash).
  The note is not in the `Sealed` event; it is decoded from the `pay()` call and kept only if that call matches the letter.
- `Payer`: registry name (from `PayerSet`) and letters sent.
- `Recipient`: letter count and total received.

The Tegami app reads letters from here (one query per page) and falls back to the contract when the indexer is
unreachable or behind. Income statements are always verified against the chain, never against the indexer.

```bash
cp .env.example .env   # add ENVIO_API_TOKEN (https://envio.dev/app/api-tokens)
pnpm install
pnpm test              # handler tests + indexes real Monad testnet letters and checks them against letterAt()
pnpm dev               # local GraphQL at http://localhost:8080 (needs Docker)
```

Example query:

```graphql
{ Letter(where: {recipient: {_eq: "0x0c6c…953b"}}, order_by: {index: desc}, limit: 25) {
    index amount invoice note timestamp blockNumber txHash token payer { id name } } }
```
