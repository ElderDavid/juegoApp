# Versión web (navegador)

Para que quien tiene iPhone (o cualquier navegador) juegue **online** con quien tiene la app de Android.
Vive en `web/`: HTML, CSS y JavaScript sin dependencias ni paso de compilación.

## Qué hace

- **Las mismas salas que la app.** Crear sala privada, entrar con código, partida pública, apuestas,
  preguntas, revelación, marcador, desempate y podio. Usa las mismas funciones de Supabase
  (`create_room`, `join_room_by_code`, `find_public_room`, `current_room`, `room_state`, `start_room`,
  `submit_bet`, `submit_answer`, `leave_room`) y el mismo proyecto, así que un jugador de Android y uno
  del navegador están en la misma sala sin hacer nada más. Las reglas y los puntos los decide el servidor.
- **Duración** de 8, 10 o 12 preguntas, como en la app.
- **Invitado anónimo** de Supabase, como en la app. El nombre y el avatar se guardan con `update_profile`.
  La sesión queda en el navegador: si se cierra la pestaña a media partida, al volver sigue en su sala.
- **Enlace para invitar.** «Compartir código» manda un enlace `…/?sala=ABCDEF` que abre la web con el
  código ya escrito.
- **No incluye** el modo local sin conexión, la tienda, las monedas ni las compras (por ahora).

La lógica que repite reglas de `core/` (rondas por duración, código de sala, reloj de fase, nombres de
categorías y avatares) es una copia pequeña en `web/js/game.js` y `web/js/catalog.js`. No se compila
Kotlin a JavaScript: sería mucho más pesado para tan poco código. Las pruebas de `web/test` fallan si
la copia deja de coincidir con `core/`, con la app o con las migraciones.

## Pruebas

```
cd web && npm test     # Node 20 o más, sin instalar nada
```

Además, antes de abrir el PR se jugaron partidas completas (12 y 8 preguntas) con dos iPhone
simulados en Chromium contra un PostgreSQL local con todas las migraciones.

## Publicarla gratis: Cloudflare Pages

El repositorio es privado, así que GitHub Pages no sirve en el plan gratuito. Cloudflare Pages sí:
es gratis, se conecta al repositorio privado y publica cada cambio en `main` solo.

1. Crea una cuenta en https://dash.cloudflare.com (gratis).
2. **Workers & Pages → Create → Pages → Connect to Git** y autoriza el repositorio `juegoApp`.
3. Configura el proyecto:
   - Production branch: `main`
   - Framework preset: `None`
   - Build command: *(vacío)*
   - Build output directory: `web`
4. **Save and Deploy.** En un minuto queda en `https://<nombre>.pages.dev`. Pásale ese enlace a quien
   tenga iPhone.

En Supabase no hay que cambiar nada: los invitados anónimos no usan URLs de redirección.

Consejo para iPhone: en Safari, **Compartir → Añadir a pantalla de inicio** deja un icono que abre el
juego a pantalla completa, como una app.

## Anuncios: Google AdSense «H5 Games Ads»

Todo está preparado en `web/js/ads.js`, pero **apagado** hasta tener un ID de AdSense real.

- **Entre partidas:** al pulsar «Jugar otra», como mucho uno cada 2 partidas terminadas y con 3 minutos
  de separación (las mismas reglas que la app). Nunca durante una pregunta.
- **Video con recompensa:** la función existe (`rewarded`) pero ninguna pantalla la usa, porque la web
  aún no tiene nada que regalar. Cuando exista, la recompensa debe darla el servidor.

Pasos para activarlos:

1. **Dominio propio.** AdSense no aprueba subdominios como `*.pages.dev`. Compra uno (unos 10 USD al
   año; Cloudflare los vende a precio de coste) y añádelo en Cloudflare Pages → **Custom domains**.
2. Crea la cuenta en https://adsense.google.com, añade el sitio con tu dominio y solicita
   **H5 Games Ads** (Ad Placement API). Necesita una política de privacidad publicada en el sitio
   (puedes adaptar `docs/POLITICA_PRIVACIDAD.md`).
3. Con la aprobación, en `web/config.js`:
   - `adsenseClient: "ca-pub-…"` con tu ID de editor (sin él, o con uno mal escrito, no se carga nada).
   - Deja `adsTestMode: true` mientras pruebas; pásalo a `false` para cobrar.
4. Crea `web/ads.txt` con la línea que te da AdSense
   (`google.com, pub-…, DIRECT, f08c47fec0942fa0`, con tu número).
5. Si vas a tener jugadores de Europa o el Reino Unido, activa en AdSense el mensaje de consentimiento
   (**Privacidad y mensajes**): Google lo muestra solo, sin cambiar el código.
