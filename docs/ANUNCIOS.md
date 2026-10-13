# Anuncios y compras (Fase 8)

## Estado actual

| Pieza | Estado |
|---|---|
| Google Mobile Ads SDK `play-services-ads` 25.5.0 | Integrado |
| Consentimiento con User Messaging Platform (UMP) 4.0.0 | Integrado |
| Intersticial al salir de la pantalla final de una partida | Integrado, con límite de frecuencia |
| Banner adaptable en Reglas y Estadísticas | Integrado |
| Video con recompensa (monedas) | Integrado: solo al pulsar «Ver video» en la tienda. Ver `ECONOMIA.md` |
| Facturación (quitar anuncios y monedas) | Integrada con Google Play Billing 9.1.0 y verificación en el servidor. Ver `ECONOMIA.md` |
| Anuncio al abrir la app | No hay, a propósito |

## Identificadores

- **Versión debug:** usa siempre los identificadores de prueba públicos de Google. Nunca generan
  ingresos ni cuentan como clics reales, así que se puede tocar los anuncios sin riesgo.

  | Uso | Identificador de prueba |
  |---|---|
  | App | `ca-app-pub-3940256099942544~3347511713` |
  | Intersticial | `ca-app-pub-3940256099942544/1033173712` |
  | Banner | `ca-app-pub-3940256099942544/9214589741` |
  | Video con recompensa | `ca-app-pub-3940256099942544/5224354917` |

- **Versión release:** no muestra ningún anuncio salvo que se compile con identificadores reales
  en `local.properties` (que no se sube al repositorio):

  ```properties
  admob.appId=ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX
  admob.interstitialId=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX
  admob.bannerId=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX
  # Opcional: sin él no hay videos con monedas.
  admob.rewardedId=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX
  ```

  Si falta cualquiera de los tres, `BuildConfig.ADS_ENABLED` es `false` y la release no pide
  anuncios. No hay identificadores reales en el código.

Nunca uses identificadores reales en la versión debug ni toques tus propios anuncios reales: AdMob
puede suspender la cuenta por clics no válidos.

## Dónde y cuándo aparecen

- **Intersticial:** solo cuando el jugador sale de la pantalla final (Revancha, Cambiar jugadores,
  Menú principal, o Jugar otra / Menú principal en online). Nunca durante una pregunta, la ruleta,
  la configuración ni una partida online cancelada.
  - Como mucho uno cada 2 partidas terminadas y nunca dos con menos de 3 minutos de diferencia
    (`core/.../ads/InterstitialPacing.kt`). La primera partida de cada sesión nunca lleva anuncio.
  - Si el anuncio no está cargado, el juego sigue sin esperar.
- **Banner:** abajo, fijo, en Reglas y Estadísticas. No hay banners en el menú ni en el juego.
- **Contenido:** clasificación máxima de anuncios `PG` (apto con supervisión), elegida por ser un
  juego de preguntas para público general. Se puede cambiar en `AdMobAdsController`.

## Consentimiento

- Al abrir la app se consulta el UMP. El formulario solo aparece donde la ley lo exige (Espacio
  Económico Europeo, Reino Unido, Suiza y ciertos estados de EE. UU.) y solo si el mensaje está
  creado en AdMob → **Privacidad y mensajes**.
- No se piden anuncios hasta que el UMP diga que se puede (`canRequestAds`).
- Si la ley lo exige, Ajustes muestra **Opciones de privacidad** para cambiar el consentimiento.
  Si no, la opción no aparece.

## Lo que falta para anuncios reales (lo tiene que hacer el dueño de la cuenta)

1. Crear la cuenta de AdMob y la app (paquete `com.elderdavid.quientienelarazon`).
2. Crear dos bloques de anuncios: intersticial y banner. Poner los tres identificadores en
   `local.properties` como se indica arriba.
3. En **Privacidad y mensajes**, crear el mensaje de GDPR (y el de estados de EE. UU. si se
   quiere) para esta app.
4. Publicar `app-ads.txt` en el sitio web del desarrollador que figure en Google Play.
5. En Google Play Console: declarar que la app **contiene anuncios** y que usa el
   **identificador de publicidad** (el SDK añade el permiso `AD_ID`); completar la sección de
   seguridad de los datos con lo que recoge el SDK de anuncios.
6. Probar en un teléfono real con identificadores de prueba o registrando el teléfono como
   dispositivo de prueba en AdMob antes de publicar.

## Facturación (quitar anuncios)

Integrada: `billing/PlayBilling.kt`. Con «Quitar anuncios» comprado no se cargan intersticiales
ni banners; el video con recompensa sigue disponible. Lo que hay que configurar en Play Console y
Supabase está en `ECONOMIA.md`.

## Pruebas

- `core`: `InterstitialPacingTest` (primera partida sin anuncio, una partida se cuenta una vez,
  partidas e intervalo mínimos).
- Lo que depende del SDK (carga, consentimiento, banners) solo se puede comprobar en un teléfono o
  emulador con Google Play Services. Pasos manuales:
  1. Instalar la debug, jugar dos partidas locales y salir de la pantalla final: debe aparecer un
     intersticial con la etiqueta «Test Ad».
  2. Salir de otra partida enseguida: no debe aparecer (límite de frecuencia).
  3. Abrir Reglas y Estadísticas: banner de prueba abajo. Menú y juego: sin banner.
  4. Sin conexión: el juego no se bloquea ni espera al anuncio.
