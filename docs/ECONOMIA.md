# Monedas, premios y compras

## Qué hay

| Pieza | Cómo funciona |
|---|---|
| Quitar anuncios | Compra única `remove_ads` en Google Play. Quita el intersticial y los banners para siempre en esa cuenta de Google («Restaurar compras» en Ajustes). Los videos con monedas siguen disponibles porque los elige el jugador. |
| Paquetes de monedas | `coins_300`, `coins_900`, `coins_2000`. Contenido fijo, sin cajas sorpresa. |
| Video con recompensa | Botón «Ver video» en la tienda: +20 monedas, máximo 5 al día. |
| Racha diaria | 10, 15, 20, 30, 40, 60 y 100 monedas por días seguidos. Si se rompe, vuelve al día 1 sin perder nada. |
| Bono por nivel | 50 + 10 × nivel (nivel 5 = 100 monedas). |
| Monedas por jugar | 5 por partida terminada (local u online, 10 al día) y 10 por victoria online (10 al día). |
| XP local | La cuenta del teléfono gana 10 XP por partida local terminada (5 al día). No cuenta para la clasificación online. |
| Logros | 8 logros con monedas una sola vez (`core/economy/Economy.kt`). |
| Cosméticos | Marcos, títulos, temas, sonidos de ruleta y animaciones de victoria. Unos se ganan por nivel (2, 3, 5, 7, 10, 15, 20, 25, 30) y otros se compran con monedas (150 a 600). |
| Comodines (solo local) | 50/50, +5 s y cambiar pregunta. Cada jugador tiene 1 gratis por partida, como mucho 1 por pregunta y ninguno en el desempate. El «paquete de partida» (60 monedas, uno por partida) lo paga el dueño del teléfono y da 1 comodín más **a todos**. En online no hay comodines. |

Ninguna pregunta ni modo se bloquea: todo el contenido es gratis.

## Quién decide qué

- **El servidor** (`supabase/migrations/20261012050000_economia.sql`) guarda el saldo y cada
  movimiento en `coin_ledger`. Los premios únicos llevan una referencia (nivel, logro, día,
  compra, video, partida) y no se pueden cobrar dos veces. La app no puede leer ni escribir esas
  tablas: solo llama a funciones (`economy_state`, `record_local_game`, `claim_daily`,
  `buy_match_pack`, `buy_cosmetic`, `equip_cosmetic`).
- **Videos**: AdMob avisa al servidor (función `admob-ssv`) con un mensaje firmado. Solo entonces
  se pagan las monedas. La app manda el id de la cuenta a AdMob para saber a quién pagar.
- **Compras**: tras pagar en Google Play, la app llama a `verify-purchase`, que consulta la compra
  en la API de Google Play. Si es válida, acredita las monedas o registra «Quitar anuncios», y
  solo después la app consume o reconoce la compra. Una compra de monedas solo vale para la cuenta
  que la hizo.
- **Partidas locales**: no pasan por el servidor, así que el teléfono informa del resultado. Por
  eso lo que dan tiene tope diario.

Las constantes están repetidas en `core/economy/Economy.kt`; `EconomyCatalogTest` falla si el
catálogo o los números del servidor y de la app no coinciden.

## Lo que tiene que hacer el dueño de las cuentas

### Supabase

1. Aplicar la migración `20261012050000_economia.sql` (SQL Editor o `supabase db push`).
2. Desplegar las funciones:
   ```
   supabase functions deploy verify-purchase
   supabase functions deploy admob-ssv --no-verify-jwt
   ```
   `admob-ssv` va sin verificación de JWT porque quien la llama es AdMob; su seguridad es la firma.
3. Secretos (`supabase secrets set …`):
   - `GOOGLE_PLAY_SERVICE_ACCOUNT`: el JSON completo de la cuenta de servicio (ver abajo).
   - Opcional `ADMOB_REWARDED_AD_UNIT`: el id del bloque de video, para no aceptar otros.

### Google Play Console

1. Cuenta de comerciante (perfil de pagos) activa.
2. **Monetizar → Productos → Productos integrados en la aplicación**: crear `remove_ads`,
   `coins_300`, `coins_900` y `coins_2000` (activos). Precios orientativos en Colombia:
   9.900, 3.900, 9.900 y 19.900 COP.
3. Cuenta de servicio con acceso a la API: en Google Cloud crear la cuenta de servicio y su clave
   JSON, activar «Google Play Android Developer API», y en Play Console → **Usuarios y permisos**
   invitar el correo de la cuenta de servicio con permiso para **ver información financiera y
   gestionar pedidos**. La clave JSON solo va al secreto de Supabase, nunca al repositorio.
4. Probar con **verificadores de licencias** (Configuración → Pruebas de licencias): compran sin
   cobro real.

### AdMob

1. Crear un bloque **Bonificado** (rewarded) y ponerlo en `local.properties`:
   `admob.rewardedId=ca-app-pub-…/…`. Sin él la release no ofrece videos (el resto de anuncios
   sigue igual).
2. En ese bloque, activar la **verificación del lado del servidor** con la URL
   `https://<proyecto>.supabase.co/functions/v1/admob-ssv`, y usar «Verificar URL».
3. La recompensa que muestra AdMob es solo informativa: las monedas las decide el servidor (20).

La versión debug usa siempre el bloque de video de prueba de Google.

## Políticas

- Los videos son opcionales, anuncian la recompensa antes y nunca premian por tocar un anuncio.
- No se venden premios al azar, así que no hay probabilidades que publicar.
- Las monedas compradas no se pueden cambiar por dinero ni apostar: las apuestas de la ronda 3 son
  de puntos de la partida.
- Público mixto con política de Familias (ver `MODO_NINOS.md`): los menores de 13 no ven videos con
  recompensa y en el modo niños no se ofrece el paquete de partida.
- En online no hay comodines: comprar no da ventaja contra otros jugadores.

## Pruebas

- `core`: `WildcardsTest`, `EconomyTest`, `EconomyCatalogTest`.
- App: `LocalGameEconomyTest`.
- Servidor: `python3 supabase/tests/economia_test.py` (igual que `salas_test.py`).
- Funciones: `cd supabase/functions && deno test _shared/` (firmas de AdMob, reglas de compra y
  JWT de la cuenta de servicio).
- Manual en un teléfono: comprar con un verificador de licencias, ver un video de prueba (las
  monedas solo llegan con la función desplegada y la verificación activada en AdMob), usar los
  tres comodines y el paquete de partida, subir de nivel y equipar un tema.
