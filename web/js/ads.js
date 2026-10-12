// Anuncios web con Google AdSense «H5 Games Ads» (Ad Placement API: adBreak/adConfig).
// Sin adsenseClient en config.js no se carga nada de Google y estas funciones no hacen nada.
// Igual que en la app (InterstitialPacing): nunca durante una pregunta, solo al salir de la pantalla
// final, como mucho uno cada 2 partidas terminadas y con 3 minutos de separación.

export const MIN_GAMES_BETWEEN = 2;
export const MIN_INTERVAL_MS = 3 * 60 * 1000;

/** Frecuencia de anuncios entre partidas. Inmutable, como core/ads/InterstitialPacing. */
export function createPacing(state = { gamesSince: 0, lastShownAt: null, lastGameKey: null }) {
  return {
    ...state,
    gameFinished(key) {
      return key === state.lastGameKey ? this : createPacing({ ...state, gamesSince: state.gamesSince + 1, lastGameKey: key });
    },
    canShow(now) {
      if (state.gamesSince < MIN_GAMES_BETWEEN) return false;
      return state.lastShownAt === null || now - state.lastShownAt >= MIN_INTERVAL_MS;
    },
    shown(now) {
      return createPacing({ ...state, gamesSince: 0, lastShownAt: now });
    },
  };
}

export function createAds(config, doc = globalThis.document, win = globalThis.window) {
  const client = (config.adsenseClient ?? "").trim();
  const enabled = /^ca-pub-\d{10,20}$/.test(client);
  let pacing = createPacing();

  if (enabled && doc && win) {
    const script = doc.createElement("script");
    script.async = true;
    script.src = `https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${client}`;
    script.crossOrigin = "anonymous";
    if (config.adsTestMode) script.dataset.adbreakTest = "on";
    doc.head.appendChild(script);
    win.adsbygoogle = win.adsbygoogle || [];
    win.adBreak = win.adConfig = (o) => win.adsbygoogle.push(o);
    win.adConfig({ preloadAdBreaks: "on", sound: "on" });
  }

  return {
    enabled,

    /** Anota una partida terminada (la misma clave cuenta una sola vez). */
    gameFinished(key) {
      pacing = pacing.gameFinished(key);
    },

    /**
     * Anuncio entre partidas. Llama a [done] cuando termina (o enseguida si no toca o no hay anuncio),
     * así el juego sigue igual con o sin anuncios.
     */
    betweenGames(done, now = performance.now()) {
      if (!enabled || !pacing.canShow(now)) return done();
      pacing = pacing.shown(now);
      win.adBreak({ type: "next", name: "fin-partida", adBreakDone: () => done() });
    },

    /**
     * Video con recompensa, preparado para cuando la web tenga algo que regalar (hoy no lo usa
     * ninguna pantalla). Resuelve true solo si el jugador vio el anuncio entero.
     * Ojo: la recompensa debe darla el servidor, nunca el navegador.
     */
    rewarded(name) {
      if (!enabled) return Promise.resolve(false);
      return new Promise((resolve) => {
        let viewed = false;
        win.adBreak({
          type: "reward",
          name,
          beforeReward: (showAdFn) => showAdFn(),
          adViewed: () => {
            viewed = true;
          },
          adDismissed: () => {},
          adBreakDone: () => resolve(viewed),
        });
      });
    },
  };
}
