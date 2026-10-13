import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import {
  ERROR_CODES, LENGTHS, clockRemaining, clockSeconds, displayCode, errorCode, isValidCode, normalizeCode,
  phaseClock, ranking, roundFor, signed, startsRound,
} from "../js/game.js";

const repo = new URL("../../", import.meta.url);
const read = (p) => readFileSync(new URL(p, repo), "utf8");

test("rondas por duración: 8 = 2+2+2+2, 10 = 3+2+2+3, 12 = 3+3+3+3", () => {
  const perRound = (n) => [1, 2, 3, 4].map((r) => [...Array(n).keys()].filter((i) => roundFor(i, n) === r).length);
  assert.deepEqual(perRound(8), [2, 2, 2, 2]);
  assert.deepEqual(perRound(10), [3, 2, 2, 3]);
  assert.deepEqual(perRound(12), [3, 3, 3, 3]);
  assert.deepEqual(LENGTHS, [8, 10, 12]);
});

test("la ronda se presenta en su primera pregunta", () => {
  assert.deepEqual([...Array(10).keys()].filter((i) => startsRound(i, 10)), [0, 3, 5, 7]);
  assert.deepEqual([...Array(12).keys()].filter((i) => startsRound(i, 12)), [0, 3, 6, 9]);
});

test("código de sala igual que RoomCode", () => {
  assert.equal(normalizeCode(" abc-def "), "ABCDEF");
  assert.equal(isValidCode("abc def"), true);
  assert.equal(isValidCode("ABCDE"), false);
  assert.equal(isValidCode("ABCDE0"), false, "sin 0, O, 1 ni I");
  assert.equal(isValidCode("ABCDEI"), false);
  assert.equal(displayCode("abcdef"), "ABC DEF");
  const kotlin = read("core/src/main/kotlin/com/elderdavid/quientienelarazon/core/online/RoomCode.kt");
  assert.match(kotlin, /ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"/);
});

test("reloj de fase: descuenta con el reloj local y no baja de 0", () => {
  const c = phaseClock(8000, 1000, 8000);
  assert.equal(clockRemaining(c, 1000), 8000);
  assert.equal(clockSeconds(c, 1000), 8);
  assert.equal(clockSeconds(c, 1001), 8);
  assert.equal(clockSeconds(c, 2000), 7);
  assert.equal(clockSeconds(c, 8999), 1);
  assert.equal(clockSeconds(c, 9000), 0);
  assert.equal(clockRemaining(c, 20000), 0);
  assert.equal(clockRemaining(phaseClock(9000, 0, 8000), 0), 8000, "nunca más que el total");
});

test("errores: busca el código dentro del mensaje del servidor", () => {
  assert.equal(errorCode("room_full"), "room_full");
  assert.equal(errorCode('P0001: room_in_progress'), "room_in_progress");
  assert.equal(errorCode("algo raro"), "unknown");
  assert.equal(errorCode(undefined), "unknown");
});

test("los códigos de error son los que lanza el servidor", () => {
  const sql = read("supabase/migrations/20261012010000_salas_online.sql") + read("supabase/migrations/20261012000000_perfiles.sql");
  const raised = new Set([...sql.matchAll(/raise exception '([a-z_]+)'/g)].map((m) => m[1]));
  raised.add("room_not_found"); // join_room_by_code responde {"error": "room_not_found"}
  for (const code of raised) assert.ok(ERROR_CODES.includes(code), `falta el mensaje de ${code}`);
});

test("ranking por puntos, ganadores primero en empate y luego asiento", () => {
  const view = {
    room: { winners: ["b"] },
    players: [
      { user_id: "a", seat: 0, score: 300 },
      { user_id: "b", seat: 1, score: 300 },
      { user_id: "c", seat: 2, score: 500 },
    ],
  };
  assert.deepEqual(ranking(view).map((p) => p.user_id), ["c", "b", "a"]);
});

test("signo de puntos", () => {
  assert.equal(signed(50), "+50");
  assert.equal(signed(-100), "−100");
  assert.equal(signed(0), "0");
});

test("rondas iguales que GameLength y _round_for del servidor", () => {
  const sql = readFileSync(new URL("supabase/migrations/20261012040000_duracion_partida.sql", repo), "utf8");
  assert.match(sql, /when 8 then p_idx \/ 2 \+ 1/);
  assert.match(sql, /when 10 then case when p_idx < 3 then 1 when p_idx < 5 then 2 when p_idx < 7 then 3 else 4 end/);
  assert.match(sql, /else p_idx \/ 3 \+ 1/);
  assert.match(sql, /question_count in \(8, 10, 12\)/);
});
