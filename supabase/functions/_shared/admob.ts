// Verificación de los avisos de AdMob para anuncios con recompensa (server-side verification).
// AdMob llama a la URL configurada con la recompensa firmada con ECDSA P-256 / SHA-256.
// Documentación: https://developers.google.com/admob/android/ssv

export const ADMOB_KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json";

export interface AdMobKey {
  keyId: number | string;
  base64: string;
}

export interface RewardCallback {
  /** null si el aviso no trae un usuario válido, como la prueba de «Verificar URL» de AdMob. */
  userId: string | null;
  transactionId: string;
  adUnit: string;
}

/** Lo que firma AdMob: la consulta tal cual llega, hasta antes de `&signature=`. */
export function signedPart(rawQuery: string): string | null {
  const q = rawQuery.startsWith("?") ? rawQuery.slice(1) : rawQuery;
  const at = q.indexOf("&signature=");
  return at <= 0 ? null : q.slice(0, at);
}

export function base64UrlDecode(text: string): Uint8Array {
  const b64 = text.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(text.length / 4) * 4, "=");
  const bin = atob(b64);
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

/** Firma ECDSA en DER (SEQUENCE de dos INTEGER) al formato r||s de 64 bytes que usa WebCrypto. */
export function derToRaw(der: Uint8Array): Uint8Array {
  let i = 0;
  if (der[i++] !== 0x30) throw new Error("firma DER inválida");
  if (der[i] & 0x80) i += 1 + (der[i] & 0x7f);
  else i += 1;
  const out = new Uint8Array(64);
  for (let part = 0; part < 2; part++) {
    if (der[i++] !== 0x02) throw new Error("firma DER inválida");
    const len = der[i++];
    let bytes = der.slice(i, i + len);
    i += len;
    while (bytes.length > 32 && bytes[0] === 0) bytes = bytes.slice(1);
    if (bytes.length > 32) throw new Error("firma DER inválida");
    out.set(bytes, part * 32 + (32 - bytes.length));
  }
  return out;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Comprueba la firma de un aviso de AdMob. Devuelve los datos de la recompensa o null si la firma
 * no es válida o falta la transacción. [keys]: claves públicas de AdMob.
 */
export async function verifyRewardCallback(rawQuery: string, keys: AdMobKey[]): Promise<RewardCallback | null> {
  const message = signedPart(rawQuery);
  if (message === null) return null;
  const params = new URLSearchParams(rawQuery.startsWith("?") ? rawQuery.slice(1) : rawQuery);
  const signature = params.get("signature");
  const keyId = params.get("key_id");
  const userId = params.get("user_id") ?? "";
  const transactionId = params.get("transaction_id") ?? "";
  if (!signature || !keyId) return null;
  const key = keys.find((k) => String(k.keyId) === keyId);
  if (!key) return null;

  let valid = false;
  try {
    const publicKey = await crypto.subtle.importKey(
      "spki",
      Uint8Array.from(atob(key.base64), (c) => c.charCodeAt(0)),
      { name: "ECDSA", namedCurve: "P-256" },
      false,
      ["verify"],
    );
    valid = await crypto.subtle.verify(
      { name: "ECDSA", hash: "SHA-256" },
      publicKey,
      derToRaw(base64UrlDecode(signature)) as BufferSource,
      new TextEncoder().encode(message),
    );
  } catch {
    return null;
  }
  if (!valid) return null;
  if (transactionId.length === 0 || transactionId.length > 200) return null;
  // La app pone el id del usuario de Supabase; sin él no hay a quién pagar.
  return {
    userId: UUID.test(userId) ? userId.toLowerCase() : null,
    transactionId,
    adUnit: params.get("ad_unit") ?? "",
  };
}
