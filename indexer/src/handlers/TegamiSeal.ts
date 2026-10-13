import { indexer } from "envio";
import { decodeFunctionData, parseAbi } from "viem";

const PAY = parseAbi(["function pay(address token, address to, uint256 amount, string invoice, string note)"]);

/** The note isn't in the event; it is read from the pay() call when the payer called TegamiSeal directly. */
export function noteFrom(input: string | undefined, to: string, invoice: string): string | undefined {
  if (!input) return undefined;
  try {
    const { args } = decodeFunctionData({ abi: PAY, data: input as `0x${string}` });
    const [, callTo, , callInvoice, note] = args;
    // Only trust it if the call is the one that produced this letter.
    return callTo.toLowerCase() === to.toLowerCase() && callInvoice === invoice ? note : undefined;
  } catch {
    return undefined; // paid through another contract (e.g. a smart account): the app reads the note on-chain
  }
}

indexer.onEvent({ contract: "TegamiSeal", event: "PayerSet" }, async ({ event, context }) => {
  const id = event.params.payer.toLowerCase();
  const payer = await context.Payer.get(id);
  context.Payer.set({ id, name: event.params.name, lettersSent: payer?.lettersSent ?? 0 });
});

indexer.onEvent(
  { contract: "TegamiSeal", event: "Sealed", fields: { transaction: ["hash", "input"], block: ["timestamp"] } },
  async ({ event, context }) => {
    const { to, index, payer, token, amount, invoice } = event.params;
    const recipient = to.toLowerCase();
    const payerId = payer.toLowerCase();
    const [p, r] = await Promise.all([context.Payer.get(payerId), context.Recipient.get(recipient)]);
    context.Payer.set({ id: payerId, name: p?.name ?? "", lettersSent: (p?.lettersSent ?? 0) + 1 });
    context.Recipient.set({ id: recipient, letterCount: (r?.letterCount ?? 0) + 1, totalReceived: (r?.totalReceived ?? 0n) + amount });
    context.Letter.set({
      id: `${recipient}-${index}`,
      recipient,
      index,
      payer_id: payerId,
      token: token.toLowerCase(),
      amount,
      invoice,
      note: noteFrom(event.transaction.input, to, invoice),
      blockNumber: event.block.number,
      timestamp: event.block.timestamp,
      txHash: event.transaction.hash,
    });
  },
);
