// Recibe de AdMob la confirmación firmada de que un usuario vio un video con recompensa y le paga
// las monedas (como mucho 5 videos al día; lo decide grant_rewarded_view).
// Se despliega sin verificación de JWT: quien llama es AdMob, y la seguridad es su firma.
//   supabase functions deploy admob-ssv --no-verify-jwt
// Variable opcional ADMOB_REWARDED_AD_UNIT: si se define, solo se acepta ese bloque de anuncios.
import { ADMOB_KEYS_URL, type AdMobKey, verifyRewardCallback } from "../_shared/admob.ts";
import { rpcAsService } from "../_shared/supabase.ts";

let cachedKeys: { keys: AdMobKey[]; at: number } | null = null;

async function admobKeys(force = false): Promise<AdMobKey[]> {
  if (!force && cachedKeys && Date.now() - cachedKeys.at < 12 * 3600_000) return cachedKeys.keys;
  const res = await fetch(ADMOB_KEYS_URL);
  if (!res.ok) throw new Error(`claves de AdMob: ${res.status}`);
  const keys = (await res.json()).keys as AdMobKey[];
  cachedKeys = { keys, at: Date.now() };
  return keys;
}

Deno.serve(async (req) => {
  if (req.method !== "GET") return new Response("method not allowed", { status: 405 });
  const query = new URL(req.url).search;
  try {
    let reward = await verifyRewardCallback(query, await admobKeys());
    // Google rota las claves: si la firma no cuadra, se prueba una vez con las claves recién bajadas.
    if (!reward) reward = await verifyRewardCallback(query, await admobKeys(true));
    if (!reward) return new Response("invalid signature", { status: 400 });

    const expectedUnit = Deno.env.get("ADMOB_REWARDED_AD_UNIT");
    if (expectedUnit && !reward.adUnit.endsWith(expectedUnit.split("/").pop()!)) {
      return new Response("unexpected ad unit", { status: 400 });
    }
    const credited = await rpcAsService<boolean>("grant_rewarded_view", {
      p_user: reward.userId,
      p_transaction: reward.transactionId,
    });
    // 200 también cuando no se paga (tope diario o repetido): así AdMob no reintenta.
    return new Response(credited ? "ok" : "not credited", { status: 200 });
  } catch (e) {
    console.error("admob-ssv", e instanceof Error ? e.message : e);
    return new Response("error", { status: 500 });
  }
});
