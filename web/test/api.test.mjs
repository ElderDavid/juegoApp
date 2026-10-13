import assert from "node:assert/strict";
import { test } from "node:test";
import { ApiError, createApi } from "../js/api.js";

const config = { supabaseUrl: "https://x.supabase.co/", supabaseKey: "pub" };

function memoryStorage() {
  const m = new Map();
  return { getItem: (k) => m.get(k) ?? null, setItem: (k, v) => m.set(k, v), removeItem: (k) => m.delete(k), m };
}

/** fetch falso: responde según [routes] y apunta cada llamada. */
function fakeFetch(routes) {
  const calls = [];
  const fn = async (url, init) => {
    const path = url.replace("https://x.supabase.co", "");
    const body = init.body ? JSON.parse(init.body) : undefined;
    calls.push({ path, body, auth: init.headers.Authorization, apikey: init.headers.apikey });
    const handler = routes[path.split("?")[0]];
    if (!handler) throw new TypeError("Failed to fetch");
    const [status, data] = handler(body, calls.length);
    return { status, ok: status >= 200 && status < 300, text: async () => (data === undefined ? "" : JSON.stringify(data)) };
  };
  fn.calls = calls;
  return fn;
}

const session = (n = 1) => ({ access_token: `t${n}`, refresh_token: `r${n}`, expires_in: 3600, user: { id: "u1" } });

test("entra como invitado anónimo y llama a la función con su token", async () => {
  const fetch = fakeFetch({
    "/auth/v1/signup": () => [200, session()],
    "/rest/v1/rpc/join_room_by_code": (b) => [200, { room: { id: "r" }, me: "u1", players: [], echo: b }],
  });
  const storage = memoryStorage();
  const api = createApi(config, { fetch, storage, now: () => 1000 });
  const view = await api.joinByCode("ABCDEF");
  assert.deepEqual(view.echo, { p_code: "ABCDEF", p_kids: false });
  assert.equal(fetch.calls[1].auth, "Bearer t1");
  assert.equal(fetch.calls[1].apikey, "pub");
  assert.equal(api.userId, "u1");
  assert.ok(storage.m.get("qtr.session").includes("r1"), "la sesión se guarda para reconectar");
});

test("reutiliza la sesión guardada y la refresca cuando va a caducar", async () => {
  const storage = memoryStorage();
  storage.setItem("qtr.session", JSON.stringify({ access_token: "old", refresh_token: "r0", expires_at: 1030, user_id: "u1" }));
  const fetch = fakeFetch({
    "/auth/v1/token": (b) => (b.refresh_token === "r0" ? [200, session(2)] : [400, {}]),
    "/rest/v1/rpc/room_state": () => [200, { room: {} }],
  });
  const api = createApi(config, { fetch, storage, now: () => 1000 });
  await api.state("room-1");
  assert.equal(fetch.calls[0].path, "/auth/v1/token?grant_type=refresh_token");
  assert.equal(fetch.calls[1].auth, "Bearer t2");
  assert.deepEqual(fetch.calls[1].body, { p_room: "room-1" });
});

test("si el refresco no vale, empieza un invitado nuevo", async () => {
  const storage = memoryStorage();
  storage.setItem("qtr.session", JSON.stringify({ access_token: "old", refresh_token: "bad", expires_at: 0, user_id: "u0" }));
  const fetch = fakeFetch({
    "/auth/v1/token": () => [400, { error: "invalid_grant" }],
    "/auth/v1/signup": () => [200, session(3)],
    "/rest/v1/rpc/current_room": () => [200, null],
  });
  const api = createApi(config, { fetch, storage, now: () => 1000 });
  assert.equal(await api.currentRoom(), null);
  assert.equal(fetch.calls[2].auth, "Bearer t3");
});

test("un 401 refresca el token y repite una vez", async () => {
  const fetch = fakeFetch({
    "/auth/v1/signup": () => [200, session(1)],
    "/auth/v1/token": () => [200, session(2)],
    "/rest/v1/rpc/start_room": (_b, n) => (n === 2 ? [401, { message: "JWT expired" }] : [200, { ok: true }]),
  });
  const api = createApi(config, { fetch, storage: null, now: () => 1000 });
  assert.deepEqual(await api.start("r"), { ok: true });
  assert.equal(fetch.calls.at(-1).auth, "Bearer t2");
});

test("los errores del servidor llegan con su código", async () => {
  const fetch = fakeFetch({
    "/auth/v1/signup": () => [200, session()],
    "/rest/v1/rpc/submit_bet": () => [400, { code: "P0001", message: "invalid_bet" }],
    "/rest/v1/rpc/join_room_by_code": () => [200, { error: "room_not_found" }],
  });
  const api = createApi(config, { fetch, storage: null });
  await assert.rejects(api.bet("r", 6, 999), (e) => e instanceof ApiError && e.code === "invalid_bet");
  await assert.rejects(api.joinByCode("ZZZZZZ"), (e) => e.code === "room_not_found");
});

test("sin red es un error de red", async () => {
  const fetch = fakeFetch({ "/auth/v1/signup": () => [200, session()] });
  const api = createApi(config, { fetch, storage: null });
  await assert.rejects(api.state("r"), (e) => e.code === "network");
});

test("crear y buscar sala envían modalidad y duración", async () => {
  const fetch = fakeFetch({
    "/auth/v1/signup": () => [200, session()],
    "/rest/v1/rpc/create_room": (b) => [200, b],
    "/rest/v1/rpc/find_public_room": (b) => [200, b],
  });
  const api = createApi(config, { fetch, storage: null });
  assert.deepEqual(await api.createRoom("general", 8), {
    p_visibility: "private", p_mode: "general", p_questions: 8, p_audience: "standard", p_kids: false,
  });
  assert.deepEqual(await api.findPublic("cesar", 10), { p_mode: "cesar", p_questions: 10, p_audience: "standard", p_kids: false });
});

test("salas de niños y mixtas envían el tipo y el nivel", async () => {
  const fetch = fakeFetch({
    "/auth/v1/signup": () => [200, session()],
    "/rest/v1/rpc/create_room": (b) => [200, b],
    "/rest/v1/rpc/find_public_room": (b) => [200, b],
    "/rest/v1/rpc/join_room_by_code": (b) => [200, b],
    "/rest/v1/rpc/set_kids_level": (b) => [200, b],
  });
  const api = createApi(config, { fetch, storage: null });
  assert.equal((await api.createRoom("general", 12, "kids")).p_audience, "kids");
  assert.deepEqual(await api.findPublic("general", 12, "mixed", true), { p_mode: "general", p_questions: 12, p_audience: "mixed", p_kids: true });
  assert.deepEqual(await api.joinByCode("ABCDEF", true), { p_code: "ABCDEF", p_kids: true });
  assert.deepEqual(await api.setKidsLevel("r", false), { p_room: "r", p_kids: false });
});

test("el tiempo de respuesta se envía en milisegundos enteros", async () => {
  const fetch = fakeFetch({ "/auth/v1/signup": () => [200, session()], "/rest/v1/rpc/submit_answer": (b) => [200, b] });
  const api = createApi(config, { fetch, storage: null });
  assert.deepEqual(await api.answer("r", 3, 1, 2345.6), { p_room: "r", p_index: 3, p_choice: 1, p_client_elapsed_ms: 2346 });
  assert.equal((await api.answer("r", 3, 1, -5)).p_client_elapsed_ms, 0);
});
