// Cliente mínimo de Supabase: sesión de invitado anónimo (Auth) y llamadas a las funciones de sala
// (PostgREST /rpc). Sin librerías: solo fetch. La fuente de verdad es el servidor, igual que en la app.
import { errorCode } from "./game.js";

const SESSION_KEY = "qtr.session";
const REFRESH_MARGIN_S = 60;

export class ApiError extends Error {
  constructor(code, detail) {
    super(code);
    this.code = code;
    this.detail = detail;
  }
}

/**
 * @param {{supabaseUrl: string, supabaseKey: string}} config
 * @param {{fetch?: typeof fetch, storage?: Storage|null, now?: () => number}} deps
 */
export function createApi(config, deps = {}) {
  const doFetch = deps.fetch ?? ((...args) => fetch(...args));
  const storage = deps.storage === undefined ? safeLocalStorage() : deps.storage;
  const nowSeconds = deps.now ?? (() => Math.floor(Date.now() / 1000));
  const base = config.supabaseUrl.replace(/\/+$/, "");
  let session = readSession();
  let pendingAuth = null;

  function readSession() {
    try {
      const raw = storage?.getItem(SESSION_KEY);
      const s = raw ? JSON.parse(raw) : null;
      return s && s.access_token && s.refresh_token && s.user_id ? s : null;
    } catch {
      return null;
    }
  }

  function saveSession(s) {
    session = s;
    try {
      if (s) storage?.setItem(SESSION_KEY, JSON.stringify(s));
      else storage?.removeItem(SESSION_KEY);
    } catch {
      // Navegación privada sin almacenamiento: la sesión vive mientras la pestaña esté abierta.
    }
  }

  async function request(path, { method = "POST", body, token, headers = {} } = {}) {
    let res;
    try {
      res = await doFetch(base + path, {
        method,
        headers: {
          apikey: config.supabaseKey,
          Authorization: `Bearer ${token ?? config.supabaseKey}`,
          ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
          ...headers,
        },
        body: body !== undefined ? JSON.stringify(body) : undefined,
      });
    } catch (e) {
      throw new ApiError("network", e?.message);
    }
    const text = await res.text();
    let data = null;
    if (text) {
      try {
        data = JSON.parse(text);
      } catch {
        data = text;
      }
    }
    return { status: res.status, ok: res.ok, data };
  }

  function fromAuthResponse(data) {
    const expiresAt = data.expires_at ?? nowSeconds() + (data.expires_in ?? 3600);
    return {
      access_token: data.access_token,
      refresh_token: data.refresh_token,
      expires_at: expiresAt,
      user_id: data.user?.id,
    };
  }

  async function signInAnonymously() {
    const res = await request("/auth/v1/signup", { body: { data: {} } });
    if (!res.ok || !res.data?.access_token) {
      if (res.status >= 500 || res.status === 0) throw new ApiError("network", res.data);
      throw new ApiError("not_authenticated", res.data);
    }
    saveSession(fromAuthResponse(res.data));
  }

  async function refresh() {
    const res = await request("/auth/v1/token?grant_type=refresh_token", { body: { refresh_token: session.refresh_token } });
    if (res.ok && res.data?.access_token) {
      saveSession(fromAuthResponse(res.data));
      return;
    }
    if (res.status >= 500) throw new ApiError("network", res.data);
    // Token de refresco caducado o revocado: se empieza un invitado nuevo.
    saveSession(null);
    await signInAnonymously();
  }

  /** Garantiza una sesión válida: reutiliza la guardada, la refresca o crea un invitado. */
  async function ensureSession({ forceRefresh = false } = {}) {
    if (pendingAuth) return pendingAuth;
    const needs = !session || forceRefresh || session.expires_at - REFRESH_MARGIN_S <= nowSeconds();
    if (!needs) return session;
    pendingAuth = (session ? refresh() : signInAnonymously()).finally(() => {
      pendingAuth = null;
    });
    await pendingAuth;
    return session;
  }

  async function rpc(name, params = {}, retried = false) {
    await ensureSession();
    const res = await request(`/rest/v1/rpc/${name}`, { body: params, token: session.access_token });
    if (res.status === 401 && !retried) {
      await ensureSession({ forceRefresh: true });
      return rpc(name, params, true);
    }
    if (!res.ok) {
      if (res.status >= 500 && !res.data?.message) throw new ApiError("network", res.data);
      throw new ApiError(errorCode(res.data?.message), res.data);
    }
    // Algunas funciones responden {"error": "..."} sin lanzar excepción (p. ej. código inexistente).
    if (res.data && typeof res.data === "object" && typeof res.data.error === "string") {
      throw new ApiError(errorCode(res.data.error), res.data);
    }
    return res.data;
  }

  async function select(path) {
    await ensureSession();
    const res = await request(`/rest/v1/${path}`, { method: "GET", token: session.access_token });
    if (!res.ok) throw new ApiError(res.status >= 500 ? "network" : errorCode(res.data?.message), res.data);
    return res.data;
  }

  return {
    get userId() {
      return session?.user_id ?? null;
    },
    hasSession: () => session !== null,
    ensureSession,

    async profile() {
      const rows = await select(`profiles?id=eq.${encodeURIComponent(session?.user_id ?? "")}&select=display_name,avatar_id`);
      return Array.isArray(rows) && rows[0] ? { name: rows[0].display_name, avatarId: rows[0].avatar_id } : null;
    },
    updateProfile: (name, avatarId) => rpc("update_profile", { p_display_name: name, p_avatar_id: avatarId }),

    async currentRoom() {
      const data = await rpc("current_room");
      return data && typeof data === "object" ? data : null;
    },
    createRoom: (mode, questions) => rpc("create_room", { p_visibility: "private", p_mode: mode, p_questions: questions }),
    /** Busca una sala pública de la misma modalidad y duración; si no hay, crea una. */
    findPublic: (mode, questions) => rpc("find_public_room", { p_mode: mode, p_questions: questions }),
    joinByCode: (code) => rpc("join_room_by_code", { p_code: code }),
    state: (roomId) => rpc("room_state", { p_room: roomId }),
    start: (roomId) => rpc("start_room", { p_room: roomId }),
    leave: (roomId) => rpc("leave_room", { p_room: roomId }),
    bet: (roomId, index, amount) => rpc("submit_bet", { p_room: roomId, p_index: index, p_amount: amount }),
    answer: (roomId, index, choice, elapsedMs) =>
      rpc("submit_answer", {
        p_room: roomId,
        p_index: index,
        p_choice: choice,
        p_client_elapsed_ms: Math.min(Math.max(Math.round(elapsedMs), 0), 2147483647),
      }),
  };
}

function safeLocalStorage() {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}
