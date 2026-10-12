// La app llama aquí tras una compra en Google Play. Se comprueba la compra con la API de Google
// y, si es válida, se acredita (monedas) o se registra («Quitar anuncios»). Solo después la app
// consume o reconoce la compra. Idempotente: repetir la llamada no acredita dos veces.
// Secretos: GOOGLE_PLAY_SERVICE_ACCOUNT (JSON de la cuenta de servicio con acceso a Play Console).
// Variable opcional: PLAY_PACKAGE_NAME (por defecto, el paquete de la app).
import { accessToken, checkPurchase, fetchProductPurchase, type ServiceAccount } from "../_shared/google_play.ts";
import { env, json, rpcAsService, userFromRequest } from "../_shared/supabase.ts";

const PRODUCTS: Record<string, "coins" | "remove_ads"> = {
  remove_ads: "remove_ads",
  coins_300: "coins",
  coins_900: "coins",
  coins_2000: "coins",
};

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ ok: false, reason: "method" }, 405);
  const userId = await userFromRequest(req);
  if (!userId) return json({ ok: false, reason: "unauthenticated" }, 401);

  let body: { productId?: unknown; purchaseToken?: unknown };
  try {
    body = await req.json();
  } catch {
    return json({ ok: false, reason: "bad_request" }, 400);
  }
  const productId = typeof body.productId === "string" ? body.productId : "";
  const purchaseToken = typeof body.purchaseToken === "string" ? body.purchaseToken : "";
  const kind = PRODUCTS[productId];
  if (!kind || purchaseToken.length === 0 || purchaseToken.length > 1000) {
    return json({ ok: false, reason: "bad_request" }, 400);
  }

  try {
    const account = JSON.parse(env("GOOGLE_PLAY_SERVICE_ACCOUNT")) as ServiceAccount;
    const packageName = Deno.env.get("PLAY_PACKAGE_NAME") ?? "com.elderdavid.quientienelarazon";
    const purchase = await fetchProductPurchase(await accessToken(account), packageName, productId, purchaseToken);
    // Las compras no válidas responden 200 con ok=false: la app distingue así «no vale» de
    // «error, reintenta más tarde» (cualquier otro estado).
    if (!purchase) return json({ ok: false, reason: "not_found" });
    const check = checkPurchase(purchase, kind, userId);
    if (!check.ok) return json({ ok: false, reason: check.reason });
    const result = await rpcAsService<{ ok: boolean; coins?: number; duplicate: boolean }>("record_purchase", {
      p_user: userId,
      p_product: productId,
      p_token: purchaseToken,
      p_order: check.orderId,
    });
    return json(result);
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    if (message.includes("purchase_belongs_to_other_user")) return json({ ok: false, reason: "other_account" });
    console.error("verify-purchase", message);
    return json({ ok: false, reason: "server_error" }, 500);
  }
});
