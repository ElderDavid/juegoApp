import { assertEquals, assertNotEquals } from "jsr:@std/assert@1";
import { type AdMobKey, derToRaw, signedPart, verifyRewardCallback } from "./admob.ts";
import { checkPurchase } from "./google_play.ts";

/** Firma raw r||s de WebCrypto → DER, como la manda AdMob. */
function rawToDer(raw: Uint8Array): Uint8Array {
  const int = (b: Uint8Array) => {
    let i = 0;
    while (i < b.length - 1 && b[i] === 0) i++;
    let v = b.slice(i);
    if (v[0] & 0x80) v = Uint8Array.from([0, ...v]);
    return Uint8Array.from([0x02, v.length, ...v]);
  };
  const body = Uint8Array.from([...int(raw.slice(0, 32)), ...int(raw.slice(32))]);
  return Uint8Array.from([0x30, body.length, ...body]);
}

function b64(bytes: Uint8Array) {
  let s = "";
  bytes.forEach((b) => s += String.fromCharCode(b));
  return btoa(s);
}

async function signedQuery(userId: string, tamper = false): Promise<{ query: string; keys: AdMobKey[] }> {
  const pair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const spki = new Uint8Array(await crypto.subtle.exportKey("spki", pair.publicKey));
  const content = `ad_network=5450213213286189855&ad_unit=1234567890&reward_amount=20&reward_item=monedas` +
    `&timestamp=1507770365237823&transaction_id=tx123&user_id=${userId}`;
  const raw = new Uint8Array(await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, pair.privateKey, new TextEncoder().encode(content)));
  const sig = b64(rawToDer(raw)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  const signedContent = tamper ? content.replace("reward_amount=20", "reward_amount=999") : content;
  return { query: `?${signedContent}&signature=${sig}&key_id=42`, keys: [{ keyId: 42, base64: b64(spki) }] };
}

const USER = "0f8fad5b-d9cb-469f-a165-70867728950e";

Deno.test("acepta un aviso bien firmado", async () => {
  const { query, keys } = await signedQuery(USER);
  const reward = await verifyRewardCallback(query, keys);
  assertEquals(reward, { userId: USER, transactionId: "tx123", adUnit: "1234567890" });
});

Deno.test("rechaza un aviso modificado", async () => {
  const { query, keys } = await signedQuery(USER, true);
  assertEquals(await verifyRewardCallback(query, keys), null);
});

Deno.test("rechaza clave desconocida o usuario inválido", async () => {
  const { query, keys } = await signedQuery(USER);
  assertEquals(await verifyRewardCallback(query, [{ keyId: 7, base64: keys[0].base64 }]), null);
  const bad = await signedQuery("no-es-un-uuid");
  assertEquals(await verifyRewardCallback(bad.query, bad.keys), null);
});

Deno.test("la parte firmada termina antes de la firma", () => {
  assertEquals(signedPart("?a=1&b=2&signature=x&key_id=3"), "a=1&b=2");
  assertEquals(signedPart("?signature=x"), null);
});

Deno.test("DER con ceros a la izquierda", () => {
  const raw = new Uint8Array(64).fill(1);
  raw[0] = 0x80; // obliga a añadir un 0 en DER
  assertEquals(derToRaw(rawToDer(raw)), raw);
  assertNotEquals(derToRaw(rawToDer(raw.map((v) => v ^ 1))), raw);
});

Deno.test("reglas de compra", () => {
  const ok = { purchaseState: 0, consumptionState: 0, orderId: "GPA.1", obfuscatedExternalAccountId: USER };
  assertEquals(checkPurchase(ok, "coins", USER), { ok: true, orderId: "GPA.1" });
  assertEquals(checkPurchase({ ...ok, purchaseState: 2 }, "coins", USER), { ok: false, reason: "pending" });
  assertEquals(checkPurchase({ ...ok, purchaseState: 1 }, "remove_ads", USER), { ok: false, reason: "cancelled" });
  assertEquals(checkPurchase({ ...ok, consumptionState: 1 }, "coins", USER), { ok: false, reason: "consumed" });
  assertEquals(checkPurchase({ ...ok, obfuscatedExternalAccountId: "otro" }, "coins", USER), { ok: false, reason: "other_account" });
  // Quitar anuncios es de la cuenta de Google: sirve aunque cambie la cuenta del juego.
  assertEquals(checkPurchase({ ...ok, obfuscatedExternalAccountId: "otro" }, "remove_ads", USER).ok, true);
});

Deno.test("JWT de la cuenta de servicio firmado con RS256", async () => {
  const { signedAssertion } = await import("./google_play.ts");
  const pair = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  );
  const pkcs8 = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey));
  const pem = `-----BEGIN PRIVATE KEY-----\n${b64(pkcs8).replace(/(.{64})/g, "$1\n")}\n-----END PRIVATE KEY-----\n`;
  const jwt = await signedAssertion({ client_email: "play@test.iam.gserviceaccount.com", private_key: pem }, 1_000);
  const [h, c, s] = jwt.split(".");
  const dec = (t: string) => Uint8Array.from(atob(t.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(t.length / 4) * 4, "=")), (x) => x.charCodeAt(0));
  const claims = JSON.parse(new TextDecoder().decode(dec(c)));
  assertEquals(claims.iss, "play@test.iam.gserviceaccount.com");
  assertEquals(claims.exp, 4_600);
  const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", pair.publicKey, dec(s) as BufferSource, new TextEncoder().encode(`${h}.${c}`));
  assertEquals(valid, true);
});
