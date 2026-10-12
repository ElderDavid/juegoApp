// Consulta de compras en la API de Google Play Developer con una cuenta de servicio.
// La clave privada de la cuenta de servicio vive solo en los secretos de Supabase
// (GOOGLE_PLAY_SERVICE_ACCOUNT, el JSON completo), nunca en la app ni en el repositorio.

export interface ServiceAccount {
  client_email: string;
  private_key: string;
}

/** Respuesta de purchases.products.get (solo los campos que se usan). */
export interface ProductPurchase {
  purchaseState?: number; // 0 comprado, 1 cancelado, 2 pendiente
  consumptionState?: number; // 0 sin consumir, 1 consumido
  acknowledgementState?: number;
  orderId?: string;
  obfuscatedExternalAccountId?: string;
}

export type PurchaseCheck =
  | { ok: true; orderId: string | null }
  | { ok: false; reason: "pending" | "cancelled" | "consumed" | "other_account" };

/**
 * Reglas para aceptar una compra. Las monedas exigen que la compra no esté consumida y que la
 * hiciera esta cuenta (la app manda su id como obfuscatedAccountId al comprar). «Quitar anuncios»
 * pertenece a la cuenta de Google, así que se acepta desde cualquier cuenta del juego.
 */
export function checkPurchase(p: ProductPurchase, kind: "coins" | "remove_ads", userId: string): PurchaseCheck {
  if (p.purchaseState === 2) return { ok: false, reason: "pending" };
  if (p.purchaseState !== 0) return { ok: false, reason: "cancelled" };
  if (kind === "coins") {
    if (p.consumptionState !== 0) return { ok: false, reason: "consumed" };
    if (p.obfuscatedExternalAccountId !== userId) return { ok: false, reason: "other_account" };
  }
  return { ok: true, orderId: p.orderId ?? null };
}

function base64Url(bytes: Uint8Array | string): string {
  const raw = typeof bytes === "string" ? new TextEncoder().encode(bytes) : bytes;
  let bin = "";
  raw.forEach((b) => bin += String.fromCharCode(b));
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem: string): Uint8Array {
  const body = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  return Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
}

/** JWT firmado para pedir un token de acceso (flujo de cuenta de servicio de Google). */
export async function signedAssertion(account: ServiceAccount, nowSeconds: number): Promise<string> {
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64Url(JSON.stringify({
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    iat: nowSeconds,
    exp: nowSeconds + 3600,
  }));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(account.private_key) as BufferSource,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`));
  return `${header}.${claims}.${base64Url(new Uint8Array(signature))}`;
}

export async function accessToken(account: ServiceAccount): Promise<string> {
  const assertion = await signedAssertion(account, Math.floor(Date.now() / 1000));
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  if (!res.ok) throw new Error(`token de Google: ${res.status}`);
  return (await res.json()).access_token;
}

export async function fetchProductPurchase(
  token: string,
  packageName: string,
  productId: string,
  purchaseToken: string,
): Promise<ProductPurchase | null> {
  const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName)}` +
    `/purchases/products/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}`;
  const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
  if (res.status === 404 || res.status === 400 || res.status === 410) return null;
  if (!res.ok) throw new Error(`Google Play: ${res.status}`);
  return await res.json();
}
