import assert from "node:assert/strict";
import { test } from "node:test";
import { MIN_INTERVAL_MS, createAds, createPacing } from "../js/ads.js";

test("ritmo de anuncios igual que InterstitialPacing: cada 2 partidas y 3 minutos", () => {
  let p = createPacing();
  assert.equal(p.canShow(0), false);
  p = p.gameFinished("a").gameFinished("a");
  assert.equal(p.canShow(0), false, "la misma partida cuenta una vez");
  p = p.gameFinished("b");
  assert.equal(p.canShow(0), true);
  p = p.shown(1000).gameFinished("c").gameFinished("d");
  assert.equal(p.canShow(1000 + MIN_INTERVAL_MS - 1), false);
  assert.equal(p.canShow(1000 + MIN_INTERVAL_MS), true);
});

test("sin ID de AdSense no se carga nada y el juego sigue", () => {
  const doc = { createElement: () => assert.fail("no debe crear el script"), head: {} };
  const ads = createAds({ adsenseClient: "" }, doc, {});
  assert.equal(ads.enabled, false);
  let done = false;
  ads.gameFinished("a");
  ads.gameFinished("b");
  ads.betweenGames(() => (done = true), 0);
  assert.equal(done, true);
});

test("un ID inventado o mal escrito no activa anuncios", () => {
  assert.equal(createAds({ adsenseClient: "ca-pub-XXXX" }, null, null).enabled, false);
});

test("con ID carga H5 Games Ads y pide un anuncio entre partidas", () => {
  const appended = [];
  const doc = { createElement: () => ({ dataset: {} }), head: { appendChild: (s) => appended.push(s) } };
  const win = {};
  const ads = createAds({ adsenseClient: "ca-pub-1234567890123456", adsTestMode: true }, doc, win);
  assert.equal(ads.enabled, true);
  assert.match(appended[0].src, /adsbygoogle\.js\?client=ca-pub-1234567890123456$/);
  assert.equal(appended[0].dataset.adbreakTest, "on");
  assert.deepEqual(win.adsbygoogle[0], { preloadAdBreaks: "on", sound: "on" });
  ads.gameFinished("a");
  ads.gameFinished("b");
  let done = false;
  ads.betweenGames(() => (done = true), 0);
  const brk = win.adsbygoogle[1];
  assert.equal(brk.type, "next");
  assert.equal(done, false);
  brk.adBreakDone();
  assert.equal(done, true);
});
