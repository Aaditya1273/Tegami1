import { describe, it } from "vitest";
import { createTestIndexer } from "envio";
import { createPublicClient, encodeFunctionData, http, parseAbi } from "viem";
import { noteFrom } from "./handlers/TegamiSeal";

const SEAL = "0x74b4413B3f8433Ec62469Edd03099a4CFE87fFD8";
const ABI = parseAbi([
  "function pay(address token, address to, uint256 amount, string invoice, string note)",
  "function letterAt(address to, uint256 index) view returns (address token, address payer, uint256 amount, uint64 time, uint64 blockNumber, string invoice, string note)",
]);
const TO = "0x0C6C42699A17ECECD053aCA23882381e4E11953B";

describe("note decoding", () => {
  const input = encodeFunctionData({ abi: ABI, functionName: "pay", args: [SEAL, TO, 25n, "TEST-0001", "thanks!"] });
  it("reads the note from a direct pay() call", (t) => t.expect(noteFrom(input, TO.toLowerCase(), "TEST-0001")).toBe("thanks!"));
  it("refuses a call that isn't this letter's", (t) => {
    t.expect(noteFrom(input, SEAL, "TEST-0001")).toBeUndefined();
    t.expect(noteFrom(input, TO, "OTHER")).toBeUndefined();
    t.expect(noteFrom("0x12345678", TO, "TEST-0001")).toBeUndefined();
  });
});

describe("Monad testnet", () => {
  it("indexes letters exactly as the contract stores them", async (t) => {
    const indexer = createTestIndexer();
    let letters: Awaited<ReturnType<typeof indexer.Letter.getAll>> = [];
    for (let i = 0; i < 12 && letters.length === 0; i++) {
      await indexer.process({ chains: { 10143: {} } });
      letters = await indexer.Letter.getAll();
    }
    t.expect(letters.length, "found a sealed letter on chain").toBeGreaterThan(0);
    const client = createPublicClient({ transport: http("https://testnet-rpc.monad.xyz") });
    for (const l of letters) {
      const [token, payer, amount, , blockNumber, invoice, note] = await client.readContract({
        address: SEAL, abi: ABI, functionName: "letterAt", args: [l.recipient as `0x${string}`, l.index],
      });
      t.expect(l.token).toBe(token.toLowerCase());
      t.expect(l.payer_id).toBe(payer.toLowerCase());
      t.expect(l.amount).toBe(amount);
      t.expect(BigInt(l.blockNumber)).toBe(blockNumber);
      t.expect(l.invoice).toBe(invoice);
      t.expect(l.note).toBe(note);
      t.expect(l.txHash).toMatch(/^0x[0-9a-f]{64}$/);
    }
  }, 120_000);
});
