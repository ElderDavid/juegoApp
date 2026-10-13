// Cliente web del juego online. Misma lógica que OnlineGameViewModel (Android): el servidor manda,
// el navegador consulta room_state cada segundo y dibuja lo que recibe.
import { config } from "../config.js";
import { createAds } from "./ads.js";
import { ApiError, createApi } from "./api.js";
import { AVATARS, MODES, categoryName, modeName } from "./catalog.js";
import {
  AUDIENCES,
  AUTOSTART_TOTAL_MS,
  CODE_LENGTH,
  FREE_BET_REWARD,
  LENGTHS,
  MAX_PLAYERS,
  MIN_PLAYERS,
  ROUNDS,
  audienceOf,
  clockRemaining,
  clockSeconds,
  displayCode,
  errorMessage,
  inLobby,
  isHost,
  isOver,
  isValidCode,
  myPlayer,
  normalizeCode,
  optionLetter,
  phaseClock,
  playerById,
  questionCount,
  ranking,
  roundRule,
  BET_WIN_MULTIPLIER,
  HEIST_RULE,
  signed,
  startsRound,
} from "./game.js";

const GAME_POLL_MS = 1000;
const LOBBY_POLL_MS = 1500;
const MIN_POLL_MS = 250;
const MAX_BACKOFF_MS = 5000;
const PREFS_KEY = "qtr.prefs";

const api = createApi(config);
const ads = createAds(config);
const now = () => performance.now();

const prefs = loadPrefs();
const state = {
  screen: "loading", // loading | profile | menu | room
  profile: null,
  profileDraft: null,
  mode: MODES.some((m) => m.id === prefs.mode) ? prefs.mode : "general",
  length: LENGTHS.includes(prefs.length) ? prefs.length : 12,
  audience: AUDIENCES.some((a) => a.id === prefs.audience) ? prefs.audience : "standard",
  kidsLevel: prefs.kidsLevel === true,
  codeInput: normalizeCode(new URLSearchParams(location.search).get("sala") ?? "").slice(0, CODE_LENGTH),
  busy: null,
  view: null,
  clock: null,
  error: null,
  sending: false,
  reconnecting: false,
};

let requestSeq = 0;
let appliedSeq = 0;
let shownKey = null;
let shownAt = 0;
let pollTimer = null;
let pollFailures = 0;

const root = document.getElementById("app");
let lastHtml = "";

// ================================================================== Arranque

async function boot() {
  render();
  try {
    await api.ensureSession();
    state.profile = await api.profile().catch(() => null);
    const room = await api.currentRoom().catch(() => null);
    if (room) {
      state.screen = "room";
      show(room, ++requestSeq);
    } else {
      state.screen = prefs.profileDone && state.profile ? "menu" : "profile";
    }
  } catch (e) {
    state.screen = "menu";
    state.error = codeOf(e);
  }
  if (state.screen === "profile") startProfileEdit();
  render();
}

// ================================================================== Acciones

const actions = {
  "pick-mode": (el) => {
    state.mode = el.dataset.value;
    // Las salas de niños y mixtas solo tienen preguntas generales.
    if (state.mode !== "general") state.audience = "standard";
    savePrefs({ mode: state.mode, audience: state.audience });
  },
  "pick-audience": (el) => {
    state.audience = el.dataset.value;
    if (state.audience !== "standard") state.mode = "general";
    savePrefs({ mode: state.mode, audience: state.audience });
  },
  "pick-level": (el) => setKidsLevel(el.dataset.value === "kids"),
  "pick-length": (el) => {
    state.length = Number(el.dataset.value);
    savePrefs({ length: state.length });
  },
  "pick-avatar": (el) => {
    state.profileDraft.avatarId = Number(el.dataset.value);
  },
  "edit-profile": () => startProfileEdit(),
  "cancel-profile": () => {
    state.screen = "menu";
    state.error = null;
  },
  "save-profile": () => saveProfile(),
  "dismiss-error": () => {
    state.error = null;
  },
  create: () => enter("creating", () => api.createRoom(state.mode, state.length, state.audience, state.kidsLevel)),
  join: () => {
    if (!isValidCode(state.codeInput)) return;
    // El tipo de sala lo puso quien la creó; aquí solo cuenta el nivel, por si es mixta.
    enter("joining", () => api.joinByCode(state.codeInput, state.kidsLevel));
  },
  "find-public": () => enter("searching", () => api.findPublic(state.mode, state.length, state.audience, state.kidsLevel)),
  start: () => startRoom(),
  bet: (el) => act((v) => api.bet(v.room.id, v.current.index, Number(el.dataset.value))),
  answer: (el) => {
    const elapsed = Math.max(0, now() - shownAt);
    act((v) => api.answer(v.room.id, v.current.index, Number(el.dataset.value), elapsed));
  },
  share: () => shareCode(),
  leave: () => leave(),
  abandon: () => {
    if (window.confirm("¿Abandonar la partida? Contará como derrota y no podrás volver a esta partida. Los demás seguirán jugando.")) leave();
  },
  "play-again": () => finishAndLeave(),
};

root.addEventListener("click", (event) => {
  const el = event.target.closest("[data-action]");
  if (!el || el.disabled || !root.contains(el)) return;
  const action = actions[el.dataset.action];
  if (!action) return;
  event.preventDefault();
  action(el);
  render();
});

root.addEventListener("input", (event) => {
  const el = event.target;
  if (el.id === "code-input") {
    const clean = normalizeCode(el.value).slice(0, CODE_LENGTH);
    if (el.value !== clean) el.value = clean;
    state.codeInput = clean;
    state.error = null;
    const join = root.querySelector('[data-action="join"]');
    if (join) join.disabled = state.busy !== null || !isValidCode(clean);
  } else if (el.id === "name-input" && state.profileDraft) {
    state.profileDraft.name = el.value;
  }
});

root.addEventListener("submit", (event) => {
  event.preventDefault();
  if (event.target.id === "join-form") actions.join();
  if (event.target.id === "profile-form") actions["save-profile"]();
  render();
});

document.addEventListener("visibilitychange", () => {
  // Safari congela los temporizadores en segundo plano: al volver se pide el estado enseguida.
  if (document.visibilityState === "visible" && state.view && !isOver(state.view.room)) schedulePoll(0);
});

function startProfileEdit() {
  state.profileDraft = {
    name: state.profile?.name ?? "",
    avatarId: state.profile?.avatarId ?? 0,
  };
  state.screen = "profile";
}

async function saveProfile() {
  if (state.busy) return;
  const name = state.profileDraft.name.trim().replace(/\s+/g, " ");
  state.busy = "saving";
  state.error = null;
  render();
  try {
    await api.updateProfile(name, state.profileDraft.avatarId);
    state.profile = { name, avatarId: state.profileDraft.avatarId };
    savePrefs({ profileDone: true });
    state.screen = "menu";
  } catch (e) {
    state.error = codeOf(e);
  }
  state.busy = null;
  render();
}

async function enter(busy, call) {
  if (state.busy || state.view) return;
  state.busy = busy;
  state.error = null;
  render();
  const seq = ++requestSeq;
  try {
    const view = await call();
    state.busy = null;
    state.screen = "room";
    show(view, seq);
  } catch (e) {
    state.busy = null;
    handleError(e);
  }
  render();
}

/** Nivel en salas mixtas: antes de entrar se recuerda; en la sala de espera de una mixta se cambia en el servidor. */
async function setKidsLevel(kids) {
  state.kidsLevel = kids;
  savePrefs({ kidsLevel: kids });
  const room = state.view?.room;
  if (!room || room.audience !== "mixed" || !inLobby(room) || state.busy) return;
  state.busy = "level";
  state.error = null;
  render();
  const seq = ++requestSeq;
  try {
    show(await api.setKidsLevel(room.id, kids), seq);
  } catch (e) {
    handleError(e);
  }
  state.busy = null;
  render();
}

async function startRoom() {
  const room = state.view?.room;
  if (!room || state.busy) return;
  state.busy = "starting";
  state.error = null;
  render();
  const seq = ++requestSeq;
  try {
    show(await api.start(room.id), seq);
  } catch (e) {
    handleError(e);
  }
  state.busy = null;
  render();
}

async function act(call) {
  const view = state.view;
  if (!view || state.sending || !view.current) return;
  state.sending = true;
  state.error = null;
  render();
  const seq = ++requestSeq;
  try {
    show(await call(view), seq);
  } catch (e) {
    handleError(e);
    // Si el servidor rechazó la acción (p. ej. llegó tarde), el estado real se pide enseguida.
    schedulePoll(0);
  }
  state.sending = false;
  render();
}

/** Sale de la sala (en partida, cuenta como abandono) y vuelve al menú. */
function leave() {
  const room = state.view?.room;
  stopPolling();
  shownKey = null;
  Object.assign(state, { view: null, clock: null, reconnecting: false, busy: null, sending: false, screen: "menu" });
  if (room && !isOver(room)) api.leave(room.id).catch(() => {});
  history.replaceState(null, "", location.pathname);
}

function finishAndLeave() {
  const room = state.view?.room;
  if (room) ads.gameFinished(room.id);
  leave();
  render();
  ads.betweenGames(() => render());
}

async function shareCode() {
  const code = state.view?.room.code;
  if (!code) return;
  const url = `${location.origin}${location.pathname}?sala=${code}`;
  const text = `¡Juega conmigo a ¿Quién tiene la razón? Entra con el código ${displayCode(code)}`;
  try {
    if (navigator.share) {
      await navigator.share({ title: "¿Quién tiene la razón?", text, url });
    } else {
      await navigator.clipboard.writeText(`${text}\n${url}`);
      toast("Enlace copiado");
    }
  } catch {
    // El jugador cerró el menú de compartir: no es un error.
  }
}

// ================================================================== Estado del servidor

function handleError(e) {
  const code = codeOf(e);
  if (code === "network") {
    if (state.view) state.reconnecting = true;
    else state.error = code;
  } else if (code === "not_in_room" || code === "room_left") {
    stopPolling();
    Object.assign(state, { view: null, clock: null, reconnecting: false, error: code, screen: "menu" });
  } else {
    state.error = code;
  }
}

function show(view, seq) {
  if (seq < appliedSeq) return;
  appliedSeq = seq;
  const t = now();
  const current = view.current;
  if (current?.question) {
    const key = `${current.is_tiebreak}:${current.index}`;
    if (key !== shownKey) {
      shownKey = key;
      shownAt = t;
    }
  }
  const room = view.room;
  state.clock =
    room.phase_remaining_ms != null
      ? phaseClock(room.phase_remaining_ms, t, room.phase_total_ms ?? room.phase_remaining_ms)
      : room.autostart_remaining_ms != null
        ? phaseClock(room.autostart_remaining_ms, t, AUTOSTART_TOTAL_MS)
        : null;
  state.view = view;
  state.reconnecting = false;
  if (isOver(room)) stopPolling();
  else if (!pollTimer) schedulePoll();
}

function schedulePoll(delay) {
  clearTimeout(pollTimer);
  const s = state;
  if (!s.view || isOver(s.view.room)) {
    pollTimer = null;
    return;
  }
  if (delay === undefined) {
    const base = inLobby(s.view.room) ? LOBBY_POLL_MS : GAME_POLL_MS;
    // Al terminar una fase se consulta enseguida para no quedarse viendo un reloj en cero.
    const untilPhaseEnd = s.clock ? clockRemaining(s.clock, now()) + 150 : base;
    delay = pollFailures > 0 ? Math.min(1000 * 2 ** Math.min(pollFailures, 3), MAX_BACKOFF_MS) : Math.min(base, untilPhaseEnd);
    delay = Math.max(delay, MIN_POLL_MS);
  }
  pollTimer = setTimeout(poll, delay);
}

async function poll() {
  pollTimer = null;
  const room = state.view?.room;
  if (!room) return;
  const seq = ++requestSeq;
  try {
    show(await api.state(room.id), seq);
    pollFailures = 0;
  } catch (e) {
    pollFailures += 1;
    handleError(e);
  }
  render();
  if (state.view && !isOver(state.view.room) && !pollTimer) schedulePoll();
}

function stopPolling() {
  clearTimeout(pollTimer);
  pollTimer = null;
  pollFailures = 0;
}

function codeOf(e) {
  return e instanceof ApiError ? e.code : "unknown";
}

// ================================================================== Dibujo

function render() {
  const html = screenHtml();
  if (html !== lastHtml) {
    const focused = document.activeElement?.id;
    root.innerHTML = html;
    lastHtml = html;
    if (focused) document.getElementById(focused)?.focus();
  }
  tickTimers();
}

function screenHtml() {
  switch (state.screen) {
    case "loading":
      return `<div class="center"><div class="spinner" aria-label="Cargando"></div></div>`;
    case "profile":
      return profileHtml();
    case "menu":
      return menuHtml();
    default: {
      const view = state.view;
      if (!view) return menuHtml();
      return inLobby(view.room) ? lobbyHtml(view) : gameHtml(view);
    }
  }
}

function errorCard() {
  if (!state.error) return "";
  return `<div class="card accent-error" role="alert">
    <p>${esc(errorMessage(state.error))}</p>
    <button class="text-button" data-action="dismiss-error">Entendido</button>
  </div>`;
}

function reconnectBanner() {
  if (!state.reconnecting) return "";
  return `<div class="card accent-orange" role="status">
    <div class="row"><div class="spinner small"></div><div>
      <p class="orange">Conexión perdida. Reconectando…</p>
      <p class="muted small">Sigues en la partida mientras vuelve la conexión.</p>
    </div></div>
  </div>`;
}

function avatarHtml(player, size = "") {
  const emoji = AVATARS[player.avatar_id] ?? esc((player.name ?? "?").slice(0, 1).toUpperCase());
  return `<span class="avatar seat-${player.seat % 4} ${size}" aria-hidden="true">${emoji}</span>`;
}

// ------------------------------------------------------------------ Perfil

function profileHtml() {
  const d = state.profileDraft ?? { name: "", avatarId: 0 };
  const first = !prefs.profileDone;
  return `<header class="top"><h1 class="logo">¿Quién tiene<br><span>la razón?</span></h1></header>
  <main class="stack">
    ${errorCard()}
    <form id="profile-form" class="card accent-violet stack">
      <h2 class="violet">${first ? "¿Cómo te llamamos?" : "Tu perfil"}</h2>
      <p class="muted">Es el nombre que verán los demás jugadores en la partida.</p>
      <label class="field">
        <span>Nombre</span>
        <input id="name-input" value="${esc(d.name)}" maxlength="16" autocomplete="nickname" autocapitalize="words" enterkeyhint="done" required>
      </label>
      <fieldset class="avatars">
        <legend>Avatar</legend>
        ${AVATARS.map(
          (a, i) => `<button type="button" class="avatar-pick${i === d.avatarId ? " selected" : ""}" data-action="pick-avatar" data-value="${i}" aria-pressed="${i === d.avatarId}" aria-label="Avatar ${i + 1}">${a}</button>`,
        ).join("")}
      </fieldset>
      <button type="submit" class="button magenta" ${state.busy ? "disabled" : ""}>${state.busy === "saving" ? "Guardando…" : "Listo"}</button>
      ${first ? "" : `<button type="button" class="button outline" data-action="cancel-profile">Cancelar</button>`}
    </form>
  </main>`;
}

// ------------------------------------------------------------------ Menú

function menuHtml() {
  const busy = state.busy;
  const p = state.profile;
  return `<header class="top">
    <h1 class="logo">¿Quién tiene<br><span>la razón?</span></h1>
    ${p ? `<button class="profile-chip" data-action="edit-profile" aria-label="Cambiar nombre o avatar">${AVATARS[p.avatarId] ?? "🙂"} ${esc(p.name)}</button>` : ""}
  </header>
  <main class="stack">
    ${errorCard()}
    <section class="card accent-violet">
      <h2 class="violet">Preguntas</h2>
      <div class="chips" role="group" aria-label="Preguntas">
        ${MODES.map((m) => chip("pick-mode", m.id, m.id === "general" ? "General" : `De aquí somos: ${m.name}`, m.id === state.mode)).join("")}
      </div>
      <h2 class="violet">Tipo de sala</h2>
      <div class="chips" role="group" aria-label="Tipo de sala">
        ${AUDIENCES.map((a) => chip("pick-audience", a.id, a.name, a.id === state.audience)).join("")}
      </div>
      <p class="muted">${esc(audienceOf({ audience: state.audience }).hint)}${state.audience !== "standard" ? " Solo con preguntas generales." : ""}</p>
      ${state.audience === "mixed" ? levelChips(state.kidsLevel, !!busy) : ""}
      <h2 class="violet">Duración</h2>
      <div class="chips" role="group" aria-label="Duración">
        ${LENGTHS.map((n) => chip("pick-length", n, `${n} preguntas`, n === state.length)).join("")}
      </div>
    </section>
    <section class="card accent-magenta stack">
      <h2 class="magenta">Con amigos</h2>
      <p class="muted">Crea una sala y comparte el código, o entra con el código que te pasaron. Da igual si los demás juegan en Android o en el navegador. Al entrar con código, el tipo de sala lo decide quien la creó.</p>
      <button class="button magenta" data-action="create" ${busy ? "disabled" : ""}>${busy === "creating" ? "Creando sala…" : "Crear sala privada"}</button>
      <form id="join-form" class="stack">
        <label class="field">
          <span>Código de sala</span>
          <input id="code-input" value="${esc(state.codeInput)}" maxlength="7" autocomplete="off" autocapitalize="characters" spellcheck="false" enterkeyhint="go" inputmode="text">
        </label>
        <button type="submit" class="button outline magenta" data-action="join" ${busy || !isValidCode(state.codeInput) ? "disabled" : ""}>${busy === "joining" ? "Entrando…" : "Entrar con código"}</button>
      </form>
    </section>
    <section class="card accent-cyan stack">
      <h2 class="cyan">Partida pública</h2>
      <p class="muted">Te unimos con otros jugadores que eligieron las mismas preguntas, el mismo tipo de sala y la misma duración.</p>
      <button class="button cyan" data-action="find-public" ${busy ? "disabled" : ""}>${busy === "searching" ? "Buscando…" : "Buscar partida"}</button>
    </section>
    <p class="muted small center-text">Juego online de 2 a 4 jugadores. Necesitas conexión a internet. <a class="muted" href="privacidad.html">Política de privacidad</a></p>
  </main>`;
}

function chip(action, value, label, selected) {
  return `<button class="chip${selected ? " selected" : ""}" data-action="${action}" data-value="${esc(String(value))}" aria-pressed="${selected}">${esc(label)}</button>`;
}

/** Nivel del jugador en una sala mixta. */
function levelChips(kids, disabled) {
  const options = [
    ["adult", "Normales (8 s)", !kids],
    ["kids", "De niños (12 s)", kids],
  ];
  return `<p class="label muted">Mis preguntas</p>
      <div class="chips" role="group" aria-label="Mis preguntas">
        ${options
          .map(
            ([value, label, selected]) =>
              `<button class="chip${selected ? " selected" : ""}" data-action="pick-level" data-value="${value}" aria-pressed="${selected}" ${disabled ? "disabled" : ""}>${label}</button>`,
          )
          .join("")}
      </div>`;
}

// ------------------------------------------------------------------ Sala de espera

function lobbyHtml(view) {
  const room = view.room;
  const players = view.players.length;
  const isPublic = room.visibility === "public";
  const audience = audienceOf(room);
  let footer;
  if (isPublic) {
    footer =
      players >= MIN_PLAYERS && state.clock
        ? `<p class="orange center-text">La partida empieza en <span data-seconds></span> s o al llegar a ${MAX_PLAYERS} jugadores.</p>`
        : `<div class="row center"><div class="spinner small"></div><p class="muted">Esperando a otro jugador…</p></div>`;
  } else if (isHost(view)) {
    const label = state.busy === "starting" ? "Empezando…" : players < MIN_PLAYERS ? "Esperando jugadores…" : "Empezar partida";
    footer = `<button class="button magenta" data-action="start" ${players < MIN_PLAYERS || state.busy ? "disabled" : ""}>${label}</button>`;
  } else {
    footer = `<p class="muted center-text">Esperando a que el anfitrión empiece la partida.</p>`;
  }
  return `<header class="top"><h1 class="title">${isPublic ? "Buscando rivales" : "Sala privada"}</h1></header>
  <main class="stack">
    ${reconnectBanner()}
    ${errorCard()}
    <p class="muted center-text">Preguntas: ${esc(modeName(room.mode))} · ${questionCount(room)} preguntas${audience.id !== "standard" ? ` · Sala ${audience.name.toLowerCase()}` : ""}</p>
    ${
      audience.id === "standard"
        ? ""
        : `<section class="card accent-orange stack">
        <p class="muted">${esc(audience.hint)}</p>
        ${audience.id === "mixed" ? levelChips(!!view.my_kids, !!state.busy) : ""}
      </section>`
    }
    ${
      isPublic
        ? ""
        : `<section class="card accent-orange stack">
        <p class="label center-text">Código</p>
        <p class="code" aria-label="Código ${esc([...room.code].join(" "))}">${esc(displayCode(room.code))}</p>
        <button class="button outline orange" data-action="share">Compartir código</button>
      </section>`
    }
    <section class="card accent-cyan stack">
      <h2 class="cyan">Jugadores (${players}/${MAX_PLAYERS})</h2>
      ${view.players
        .map((p) => {
          const tags = [p.user_id === room.host_id ? "anfitrión" : null, p.kids && audience.id === "mixed" ? "preguntas de niños" : null].filter(Boolean);
          return playerRow(p, view, tags.join(" · ") || null);
        })
        .join("")}
    </section>
    ${footer}
    <button class="button outline" data-action="leave">Salir de la sala</button>
  </main>`;
}

function playerRow(p, view, trailing = null, withScore = false) {
  const status = p.left ? "abandonó" : !p.connected ? "reconectando…" : trailing;
  return `<div class="player">
    ${avatarHtml(p)}
    <div class="grow">
      <p class="name">${esc(p.name)}${p.user_id === view.me ? " (tú)" : ""}</p>
      ${status ? `<p class="small ${p.left || !p.connected ? "orange" : "muted"}">${esc(status)}</p>` : ""}
    </div>
    ${withScore ? `<p class="score seat-text-${p.seat % 4}">${p.score}</p>` : ""}
  </div>`;
}

// ------------------------------------------------------------------ Partida

function gameHtml(view) {
  const room = view.room;
  const playing = room.status === "playing";
  let body;
  if (room.status === "cancelled") body = cancelledHtml(view);
  else if (room.status === "finished") body = podiumHtml(view);
  else {
    switch (room.phase) {
      case "roulette":
        body = rouletteHtml(view);
        break;
      case "betting":
        body = bettingHtml(view);
        break;
      case "question":
      case "tiebreak_question":
        body = questionHtml(view);
        break;
      case "reveal":
      case "tiebreak_reveal":
        body = revealHtml(view);
        break;
      case "summary":
        body = summaryHtml(view);
        break;
      default:
        body = `<div class="center"><div class="spinner"></div></div>`;
    }
  }
  return `<main class="stack game">
    ${reconnectBanner()}
    ${errorCard()}
    ${body}
    ${playing ? `<button class="text-button muted" data-action="abandon">Abandonar partida</button>` : ""}
  </main>`;
}

function roundOf(view) {
  const r = view.current?.round;
  return r >= 1 && r <= 4 ? ROUNDS[r - 1] : null;
}

function roundHeader(view) {
  const round = roundOf(view);
  if (!round) return "";
  const heist = view.current.heist
    ? `<section class="card accent-magenta"><h2 class="magenta">¡PREGUNTA ROBO!</h2><p>${HEIST_RULE}</p></section>`
    : "";
  return `<p class="label cyan">RONDA ${round.number} · ${round.title.toUpperCase()} · PREGUNTA ${view.current.index + 1}/${questionCount(view.room)}</p>${heist}`;
}

function timer(label) {
  if (!state.clock) return "";
  return `<div class="timer" data-timer>
    <div class="row between"><p class="cyan strong">${esc(label)}</p><p class="seconds" data-seconds aria-live="off"></p></div>
    <div class="bar"><div class="fill"></div></div>
  </div>`;
}

function waitingFor(view, done) {
  const active = view.players.filter((p) => !p.left && (!view.current?.is_tiebreak || p.in_tiebreak));
  return `<p class="muted small center-text">${active.map((p) => `${done(p) ? "✓" : "…"} ${esc(p.name)}`).join("&nbsp;&nbsp; ")}</p>`;
}

function rouletteHtml(view) {
  const current = view.current;
  const round = roundOf(view);
  if (!current || !round) return "";
  const intro = startsRound(current.index, questionCount(view.room))
    ? `<section class="card accent-violet"><h2 class="violet">Ronda ${round.number}: ${round.title}</h2><p class="muted">${roundRule(round, view.my_kids, view.room.audience === "standard")}</p></section>`
    : "";
  return `${roundHeader(view)}${intro}
  <section class="card accent-orange roulette">
    <p class="label muted center-text">La ruleta eligió</p>
    <p class="category" aria-live="polite">${esc(categoryName(current.category_id))}</p>
  </section>`;
}

function bettingHtml(view) {
  const current = view.current;
  if (!current || !roundOf(view)) return "";
  const me = myPlayer(view);
  const allowed = current.allowed_bets ?? [];
  let choice = "";
  if (!me || me.left) choice = "";
  else if (current.my_bet != null) {
    choice = `<p class="big orange center-text">${current.my_bet === 0 ? "Juegas con apuesta libre." : `Apostaste ${current.my_bet} puntos.`}</p>`;
  } else if (allowed.length === 0) {
    choice = `<section class="card"><h2 class="cyan">Apuesta libre</h2>
      <p class="muted">No te alcanza para la apuesta mínima. Si aciertas ganas ${FREE_BET_REWARD} puntos y si fallas no pierdes nada.</p></section>
      <button class="button cyan" data-action="bet" data-value="0" ${state.sending ? "disabled" : ""}>Jugar apuesta libre</button>`;
  } else {
    const max = Math.max(...allowed);
    choice =
      allowed
        .map((a) => `<button class="button ${a === max ? "magenta" : "cyan"}" data-action="bet" data-value="${a}" ${state.sending ? "disabled" : ""}>Apostar ${a} · ganas ${a * BET_WIN_MULTIPLIER}</button>`)
        .join("") + `<p class="muted small">Si no eliges, se aplica la apuesta mínima.</p>`;
  }
  return `${roundHeader(view)}${timer("Apuesta")}
  <section class="card accent-orange">
    <h2>Categoría: ${esc(categoryName(current.category_id))}</h2>
    <p class="muted">Tienes ${me?.score ?? 0} puntos. La pregunta aparece cuando termine el tiempo de apuesta.</p>
    ${view.my_kids ? `<p class="success">Con preguntas de niños, si fallas no pierdes lo apostado.</p>` : ""}
  </section>
  ${choice}
  ${waitingFor(view, (p) => p.bet_placed)}`;
}

function kindLabel(q) {
  if (q.kind === "OPINION") return "Opinión: gana la opción más votada";
  if (q.kind === "DEBATE") return q.is_vote ? "Debate: gana la opción más votada" : "Debate: se resuelve con la regla del juego";
  return "";
}

function questionHtml(view) {
  const current = view.current;
  const q = current?.question;
  if (!q) return "";
  const me = myPlayer(view);
  const tiebreak = current.is_tiebreak;
  const participating = me && !me.left && (!tiebreak || me.in_tiebreak);
  const chosen = current.my_choice;
  const head = tiebreak
    ? `<h1 class="neon magenta">¡Desempate!</h1><p class="muted center-text">Gana quien acierte más rápido. No cambia las estadísticas.</p>`
    : roundHeader(view);
  const bet = !tiebreak && current.my_bet != null ? `<p class="label orange">${current.my_bet === 0 ? "APUESTA LIBRE" : `APUESTA: ${current.my_bet}`}</p>` : "";
  const kind = kindLabel(q);
  const options = q.options
    .map(
      (o, i) => `<button class="option${chosen === i ? " chosen" : ""}" data-action="answer" data-value="${i}" ${participating && chosen == null && !state.sending ? "" : "disabled"}>
        <span class="letter">${optionLetter(i)}</span><span>${esc(o)}</span></button>`,
    )
    .join("");
  let note = "";
  if (!participating && tiebreak) note = `<p class="muted">Solo juegan los empatados en el primer puesto.</p>`;
  else if (!participating) note = `<p class="muted">Abandonaste la partida: solo puedes mirar.</p>`;
  else if (chosen != null) note = `<p class="magenta center-text">Respuesta enviada. Esperando a los demás…</p>`;
  return `${head}${timer(participating ? "Tu respuesta" : "Mirando")}${bet}
  <section class="card accent-violet">
    <p class="label violet">${esc(categoryName(current.category_id).toUpperCase())}</p>
    ${kind ? `<p class="label orange">${kind}</p>` : ""}
    <p class="question">${esc(q.text)}</p>
  </section>
  <div class="options">${options}</div>
  ${note}
  ${waitingFor(view, (p) => p.answered)}`;
}

function revealHtml(view) {
  const current = view.current;
  const q = current?.question;
  const result = view.result;
  if (!q || !result) return "";
  const tiebreak = current.is_tiebreak;
  const options = q.options
    .map((o, i) => {
      const votes = result.vote_counts?.[String(i)] ?? 0;
      const label = q.is_vote ? `${esc(o)} · ${votes} voto${votes === 1 ? "" : "s"}` : esc(o);
      const win = result.winning_options.includes(i);
      return `<div class="option ${win ? "correct" : "dim"}"><span class="letter">${optionLetter(i)}</span><span>${label}</span>${win ? '<span class="sr">(correcta)</span>' : ""}</div>`;
    })
    .join("");
  const outcomes = result.outcomes
    .map((o) => {
      const p = playerById(view, o.user_id);
      if (!p) return "";
      // En una sala mixta, quien tiene otro nivel respondió otra pregunta: su opción no es de esta.
      const answer =
        o.same_question === false
          ? `${p.kids ? "Su pregunta de niños" : "Su pregunta normal"}: ${o.won ? "acertó" : o.choice == null ? "sin respuesta" : "falló"}`
          : o.choice != null
            ? `${optionLetter(o.choice)}. ${esc(q.options[o.choice] ?? "")}`
            : "Sin respuesta";
      let extra = "";
      if (tiebreak && o.won && o.elapsed_ms != null) extra = ` · ${(o.elapsed_ms / 1000).toFixed(2).replace(".", ",")} s`;
      else if (o.bet != null) extra = o.bet === 0 ? " · apuesta libre" : ` · apostó ${o.bet}`;
      const points = tiebreak
        ? ""
        : `<div class="right"><p class="points ${o.points > 0 ? "success" : o.points < 0 ? "error" : "muted"}">${signed(o.points)}</p><p class="small muted">${p.score} pts</p></div>`;
      const heist = o.stolen > 0 ? `¡Robó ${o.stolen} puntos!` : o.stolen < 0 ? `Le robaron ${-o.stolen} puntos` : "";
      const heistLine = heist ? `<p class="small magenta">${heist}</p>` : "";
      return `<div class="player">${avatarHtml(p, "small")}<div class="grow"><p class="name">${esc(p.name)}</p><p class="small muted">${answer}${extra}</p>${heistLine}</div>${points}</div>`;
    })
    .join("");
  const winner = tiebreak ? playerById(view, result.tiebreak_winner)?.name : null;
  return `${tiebreak ? `<h1 class="neon magenta">Desempate</h1>` : roundHeader(view)}
  <section class="card accent-violet">
    <p class="label violet">${esc(categoryName(current.category_id).toUpperCase())}</p>
    <p class="question small-q">${esc(q.text)}</p>
  </section>
  <div class="options">${options}</div>
  ${q.is_vote && !result.scored ? `<p class="orange">Menos de 2 votos válidos: esta pregunta no da puntos.</p>` : ""}
  <section class="card">
    <h2 class="cyan">${q.is_vote ? "Resultado de la votación" : "Respuesta"}</h2>
    ${result.game_rule ? `<p class="orange">Regla del juego: ${esc(result.game_rule)}</p>` : ""}
    <p class="muted">${esc(result.explanation)}</p>
    ${sourceLink(result.source)}
  </section>
  <section class="card accent-magenta stack">${outcomes}</section>
  ${tiebreak ? `<p class="big orange center-text">${winner ? `¡${esc(winner)} gana el desempate!` : "Sigue el empate."}</p>` : ""}
  ${timer("Siguiente")}`;
}

function sourceLink(url) {
  if (!url || !/^https?:\/\//i.test(url)) return "";
  const host = url.replace(/^https?:\/\//i, "").split("/")[0];
  return `<a class="source" href="${esc(url)}" target="_blank" rel="noopener noreferrer">Fuente: ${esc(host)}</a>`;
}

function summaryHtml(view) {
  const finished = roundOf(view);
  if (!finished) return "";
  const next = ROUNDS[finished.number];
  return `<h1 class="neon">Fin de la ronda ${finished.number}</h1>
  ${scoreboard(view)}
  ${next ? `<section class="card accent-violet"><h2 class="violet">Siguiente: ronda ${next.number}, ${next.title}</h2><p class="muted">${roundRule(next, view.my_kids, view.room.audience === "standard")}</p></section>` : ""}
  ${timer("Siguiente ronda")}`;
}

function scoreboard(view) {
  return `<section class="card accent-cyan stack">${ranking(view)
    .map((p, i) => `<div class="row"><p class="position muted">${i + 1}.</p><div class="grow">${playerRow(p, view, null, true)}</div></div>`)
    .join("")}</section>`;
}

function podiumHtml(view) {
  const winners = (view.room.winners ?? []).map((id) => playerById(view, id)?.name).filter(Boolean);
  const title = winners.length === 1 ? `¡${esc(winners[0])} tiene la razón!` : winners.length > 1 ? "¡Victoria compartida!" : "Partida terminada";
  const final = view.final
    ? `<section class="card"><h2 class="cyan">Aciertos</h2>${view.final
        .map((f) => {
          const name = playerById(view, f.user_id)?.name;
          return name ? `<p class="muted">${esc(name)}: ${f.correct}</p>` : "";
        })
        .join("")}<p class="muted small">Tus estadísticas y tu nivel ya se actualizaron en tu perfil.</p></section>`
    : "";
  return `<h1 class="neon orange">${title}</h1>
  ${winners.length > 1 ? `<p class="big center-text">${winners.map(esc).join(" y ")}</p>` : ""}
  ${scoreboard(view)}
  ${final}
  <button class="button magenta" data-action="play-again">Jugar otra</button>`;
}

function cancelledHtml(view) {
  const reason =
    view.room.cancel_reason === "abandoned"
      ? "Todos los jugadores se desconectaron o abandonaron. No cuenta para las estadísticas."
      : "La sala se quedó sin jugadores.";
  return `<h1 class="neon orange">Partida cancelada</h1>
  <section class="card"><p class="muted">${reason}</p></section>
  <button class="button magenta" data-action="leave">Volver</button>`;
}

// ------------------------------------------------------------------ Relojes

function tickTimers() {
  const clock = state.clock;
  if (!clock) return;
  const t = now();
  const seconds = clockSeconds(clock, t);
  const fraction = clock.totalMillis > 0 ? clockRemaining(clock, t) / clock.totalMillis : 0;
  for (const el of root.querySelectorAll("[data-seconds]")) {
    const text = el.closest("[data-timer]") ? `${seconds} s` : String(seconds);
    if (el.textContent !== text) el.textContent = text;
  }
  for (const el of root.querySelectorAll("[data-timer]")) {
    el.classList.toggle("urgent", seconds <= 3);
    const fill = el.querySelector(".fill");
    if (fill) fill.style.transform = `scaleX(${Math.min(Math.max(fraction, 0), 1)})`;
  }
}

function frame() {
  tickTimers();
  requestAnimationFrame(frame);
}

// ------------------------------------------------------------------ Utilidades

function esc(text) {
  return String(text ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function toast(text) {
  const el = document.createElement("div");
  el.className = "toast";
  el.setAttribute("role", "status");
  el.textContent = text;
  document.body.appendChild(el);
  setTimeout(() => el.remove(), 2200);
}

function loadPrefs() {
  try {
    return JSON.parse(localStorage.getItem(PREFS_KEY) ?? "{}") ?? {};
  } catch {
    return {};
  }
}

function savePrefs(change) {
  Object.assign(prefs, change);
  try {
    localStorage.setItem(PREFS_KEY, JSON.stringify(prefs));
  } catch {
    // Sin almacenamiento (navegación privada): las preferencias duran mientras la pestaña esté abierta.
  }
}

requestAnimationFrame(frame);
boot();
