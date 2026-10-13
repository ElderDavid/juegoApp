// Reglas y textos del juego que el navegador necesita para dibujar. Son copia de core/ (Kotlin):
// GameRules, RoundType, RoomCode, PhaseClock y GameLength. Los puntos los calcula siempre el servidor;
// aquí solo hay lo necesario para mostrar la partida. Las pruebas de web/test comprueban que coinciden.

export const MIN_PLAYERS = 2;
export const MAX_PLAYERS = 4;
export const FREE_BET_REWARD = 50;
export const DEFAULT_QUESTIONS = 12;
/** Duraciones de partida (GameLength en core): siempre 4 rondas. */
export const LENGTHS = [8, 10, 12];
export const AUTOSTART_TOTAL_MS = 15_000;

/**
 * Tipos de sala (OnlineAudience). En la de niños todos tienen preguntas para niños con 12 s; en la
 * mixta cada uno elige su nivel. Solo existen con preguntas generales.
 */
export const AUDIENCES = [
  { id: "standard", name: "Normal", hint: "Preguntas normales y 8 segundos para responder." },
  { id: "kids", name: "Niños", hint: "Todos reciben preguntas más fáciles, tienen 12 segundos y fallar una apuesta no resta puntos." },
  {
    id: "mixed",
    name: "Mixta",
    hint: "Niños y mayores juntos: cada uno recibe una pregunta de la misma categoría pero de su nivel. Los niños tienen 12 segundos y los mayores 8.",
  },
];

export function audienceOf(room) {
  return AUDIENCES.find((a) => a.id === room?.audience) ?? AUDIENCES[0];
}

export const ROUNDS = [
  { number: 1, title: "Conocimiento", rule: "Acierto: +100 puntos." },
  { number: 2, title: "Velocidad", rule: "Acierto: +100 y hasta +50 extra cuanto más rápido respondas." },
  { number: 3, title: "Estrategia", rule: "Antes de ver la pregunta apuestas 50 o 100 puntos. Aciertas: los ganas. Fallas: los pierdes." },
  { number: 4, title: "Final", rule: "Preguntas más difíciles. Acierto: +150 y bono por rapidez." },
];

/** Regla de la ronda; con preguntas de niños fallar la apuesta no resta (roundRule en la app). */
export function roundRule(round, kids = false) {
  return kids && round.number === 3 ? round.rule.replace("Fallas: los pierdes.", "Fallas: no pierdes nada.") : round.rule;
}

/** Ronda (1 a 4) de la pregunta [index] en una partida de [count] preguntas (GameLength.roundFor). */
export function roundFor(index, count = DEFAULT_QUESTIONS) {
  if (count === 8) return Math.floor(index / 2) + 1;
  if (count === 10) return index < 3 ? 1 : index < 5 ? 2 : index < 7 ? 3 : 4;
  return Math.floor(index / 3) + 1;
}

/** true si [index] es la primera pregunta de su ronda: ahí se presenta la ronda. */
export function startsRound(index, count = DEFAULT_QUESTIONS) {
  return index === 0 || roundFor(index - 1, count) !== roundFor(index, count);
}

// ------------------------------------------------------------------ Código de sala (RoomCode)

export const CODE_LENGTH = 6;
export const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export function normalizeCode(input) {
  return String(input ?? "").replace(/[^A-Za-z0-9]/g, "").toUpperCase();
}

export function isValidCode(input) {
  const code = normalizeCode(input);
  return code.length === CODE_LENGTH && [...code].every((c) => CODE_ALPHABET.includes(c));
}

/** «ABCDEF» → «ABC DEF», para leerlo en voz alta. */
export function displayCode(code) {
  const c = normalizeCode(code);
  return c.length === CODE_LENGTH ? `${c.slice(0, 3)} ${c.slice(3)}` : c;
}

// ------------------------------------------------------------------ Reloj de fase (PhaseClock)

/**
 * El servidor dice cuánto le queda a la fase; el navegador descuenta desde que recibió el dato con
 * su propio reloj monótono (performance.now), así la hora del teléfono no importa.
 */
export function phaseClock(remainingAtReceipt, receivedAt, totalMillis) {
  return { remainingAtReceipt, receivedAt, totalMillis };
}

export function clockRemaining(clock, now) {
  const left = clock.remainingAtReceipt - (now - clock.receivedAt);
  return Math.min(Math.max(left, 0), Math.max(clock.totalMillis, 0));
}

/** Segundos que se muestran: 8, 7… 1 y 0 solo cuando se acabó. */
export function clockSeconds(clock, now) {
  return Math.floor((clockRemaining(clock, now) + 999) / 1000);
}

// ------------------------------------------------------------------ Vista de sala (RoomView)

export function myPlayer(view) {
  return view.players.find((p) => p.user_id === view.me) ?? null;
}

export function playerById(view, id) {
  return view.players.find((p) => p.user_id === id) ?? null;
}

export function isHost(view) {
  return view.room.host_id === view.me;
}

export function inLobby(room) {
  return room.status === "waiting" || room.status === "ready";
}

export function isOver(room) {
  return room.status === "finished" || room.status === "cancelled";
}

export function questionCount(room) {
  return LENGTHS.includes(room.question_count) ? room.question_count : DEFAULT_QUESTIONS;
}

/** Jugadores por puntos; ante empate, primero los ganadores y luego por asiento. */
export function ranking(view) {
  const winners = view.room.winners ?? [];
  return [...view.players].sort(
    (a, b) =>
      b.score - a.score ||
      (winners.includes(a.user_id) ? 0 : 1) - (winners.includes(b.user_id) ? 0 : 1) ||
      a.seat - b.seat,
  );
}

export function optionLetter(index) {
  return String.fromCharCode(65 + index);
}

export function signed(n) {
  if (n > 0) return `+${n}`;
  if (n < 0) return `−${-n}`;
  return "0";
}

// ------------------------------------------------------------------ Errores del servidor (OnlineError)

const ERROR_MESSAGES = {
  not_authenticated: "No se pudo iniciar sesión. Revisa tu conexión.",
  room_not_found: "No hay ninguna sala abierta con ese código.",
  room_full: `La sala está llena (máximo ${MAX_PLAYERS} jugadores).`,
  room_in_progress: "Esa partida ya empezó. Pide un código nuevo para la siguiente.",
  room_closed: "Esa sala ya se cerró.",
  room_left: "Saliste de esa partida y no se puede volver a entrar.",
  not_in_room: "Ya no estás en la sala.",
  not_host: "Solo quien creó la sala puede empezar.",
  not_enough_players: `Hacen falta al menos ${MIN_PLAYERS} jugadores conectados.`,
  not_enough_questions: "Esta modalidad no tiene preguntas suficientes en el servidor.",
  rate_limited: "Demasiados intentos seguidos. Espera unos minutos.",
  time_expired: "Se acabó el tiempo: tu respuesta no llegó a tiempo.",
  already_answered: "Ya habías respondido.",
  already_bet: "Ya habías apostado.",
  wrong_phase: "La partida ya avanzó. Actualizando…",
  wrong_question: "La partida ya avanzó. Actualizando…",
  invalid_bet: "Esa apuesta no está permitida con tus puntos.",
  invalid_option: "Opción no válida.",
  invalid_mode: "Esa modalidad no existe en el servidor.",
  invalid_visibility: "Tipo de sala no válido.",
  invalid_audience: "Las salas de niños y mixtas solo tienen preguntas generales.",
  not_a_tiebreak_player: "Solo juegan el desempate los empatados en el primer puesto.",
  profile_missing: "No se encontró tu perfil. Recarga la página.",
  wrong_status: "La partida ya empezó: el nivel ya no se puede cambiar.",
  name_too_short: "El nombre debe tener al menos 3 caracteres.",
  name_too_long: "El nombre puede tener como mucho 16 caracteres.",
  name_invalid_characters: "Usa solo letras, números, espacios, puntos, guiones o guiones bajos.",
  name_offensive: "Ese nombre no está permitido. Elige otro.",
  avatar_invalid: "Elige uno de los avatares.",
  network: "Sin conexión con el servidor. Comprueba tu internet e inténtalo otra vez.",
  unknown: "Algo salió mal en el servidor. Inténtalo otra vez.",
};

export const ERROR_CODES = Object.keys(ERROR_MESSAGES).filter((c) => c !== "network" && c !== "unknown");

/** Busca un código conocido dentro del mensaje de error del servidor (OnlineError.from). */
export function errorCode(message) {
  if (!message) return "unknown";
  return ERROR_CODES.find((code) => String(message).includes(code)) ?? "unknown";
}

export function errorMessage(code) {
  return ERROR_MESSAGES[code] ?? ERROR_MESSAGES.unknown;
}
