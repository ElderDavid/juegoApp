#!/usr/bin/env python3
"""Pruebas de las salas online contra un PostgreSQL local con auth simulado.

Crea una base de datos desechable, aplica auth_simulado.sql y todas las migraciones, y juega
partidas completas llamando a las funciones como lo haría la app (rol authenticated).
El paso del tiempo se simula moviendo las marcas de tiempo de la sala con el superusuario.

Uso: PGTEST_DSN="host=/tmp/pg port=5432 user=postgres" python3 supabase/tests/salas_test.py
Requiere psycopg2.
"""
import json
import os
import pathlib
import re
import sys
import uuid

import psycopg2
import psycopg2.extensions

ROOT = pathlib.Path(__file__).resolve().parents[2]
DSN = os.environ.get("PGTEST_DSN", "host=/tmp/pgtest-qtr port=54329 user=postgres")
DB = "qtr_salas_test"

passed = 0
failures = []


def connect(dbname="postgres"):
    conn = psycopg2.connect(DSN + f" dbname={dbname}")
    conn.autocommit = True
    return conn


def setup():
    admin = connect()
    cur = admin.cursor()
    cur.execute(f"drop database if exists {DB}")
    cur.execute(f"create database {DB}")
    admin.close()
    conn = connect(DB)
    cur = conn.cursor()
    cur.execute((ROOT / "supabase/tests/auth_simulado.sql").read_text(encoding="utf-8"))
    for path in sorted((ROOT / "supabase/migrations").glob("*.sql")):
        cur.execute(path.read_text(encoding="utf-8"))
    return conn


class ServerError(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.code = code


class Player:
    def __init__(self, admin, name):
        self.id = str(uuid.uuid4())
        self.name = name
        admin.cursor().execute("insert into auth.users (id) values (%s)", (self.id,))
        admin.cursor().execute("update public.profiles set display_name = %s where id = %s", (name, self.id))
        self.conn = connect(DB)
        cur = self.conn.cursor()
        cur.execute("set role authenticated")
        cur.execute("select set_config('request.jwt.claim.sub', %s, false)", (self.id,))

    def call(self, fn, *args):
        cur = self.conn.cursor()
        placeholders = ", ".join(["%s"] * len(args))
        try:
            cur.execute(f"select public.{fn}({placeholders})", args)
        except psycopg2.Error as e:
            message = (e.diag.message_primary or str(e)).strip()
            raise ServerError(message) from None
        value = cur.fetchone()[0]
        if value is None or value == "":
            return None
        return value if not isinstance(value, str) else json.loads(value)

    def state(self, room):
        return self.call("room_state", room)


def check(condition, label):
    global passed
    if condition:
        passed += 1
    else:
        failures.append(label)
        print("  FALLO:", label)


def expect_error(code, fn, label):
    try:
        fn()
    except ServerError as e:
        check(code in e.code, f"{label}: se esperaba {code}, llegó {e.code}")
        return
    check(False, f"{label}: se esperaba el error {code}")


def expire(admin, room, seconds=30):
    """Hace que la fase actual de la sala haya vencido."""
    admin.cursor().execute(
        "update public.rooms set phase_started_at = phase_started_at - make_interval(secs => %s),"
        " phase_ends_at = now() - interval '1 millisecond' where id = %s",
        (seconds, room),
    )


def age_phase(admin, room, millis):
    """Retrasa el inicio de la fase actual sin vencerla (para simular respuestas lentas)."""
    admin.cursor().execute(
        "update public.rooms set phase_started_at = phase_started_at - make_interval(secs => %s / 1000.0) where id = %s",
        (millis, room),
    )


def key_for(admin, room, is_tiebreak, idx):
    cur = admin.cursor()
    cur.execute(
        "select k.correct_index, q.is_vote, jsonb_array_length(q.options) from public.room_questions rq"
        " join public.question_keys k on k.question_id = rq.question_id"
        " join public.questions q on q.id = rq.question_id"
        " where rq.room_id = %s and rq.is_tiebreak = %s and rq.idx = %s",
        (room, is_tiebreak, idx),
    )
    return cur.fetchone()


def stats(admin, player):
    cur = admin.cursor()
    cur.execute(
        "select games_played, wins, losses, correct_answers, objective_answered, best_score, xp"
        " from public.player_stats where user_id = %s",
        (player.id,),
    )
    return cur.fetchone()


def wrong(correct, n):
    return (correct + 1) % n


# ---------------------------------------------------------------------------------------------

def test_private_room_and_security(admin):
    print("Sala privada, permisos y validaciones")
    a, b, c = Player(admin, "Ana"), Player(admin, "Beto"), Player(admin, "Caro")
    s = a.call("create_room", "private", "general")
    room, code = s["room"]["id"], s["room"]["code"]
    check(re.fullmatch(r"[A-HJ-NP-Z2-9]{6}", code) is not None, "código de 6 caracteres sin I, O, 0 ni 1")
    check(s["room"]["status"] == "waiting" and s["room"]["host_id"] == a.id, "sala nueva esperando, Ana anfitriona")

    expect_error("not_enough_players", lambda: a.call("start_room", room), "no se puede empezar sola")
    s = b.call("join_room_by_code", code.lower()[:3] + " " + code.lower()[3:])
    check(s["room"]["status"] == "ready" and len(s["players"]) == 2, "Beto entra con el código (minúsculas y espacio)")
    check(b.call("join_room_by_code", code)["room"]["id"] == room, "volver a entrar es una reconexión, no un duplicado")
    check(c.call("join_room_by_code", "ZZZZZZ") == {"error": "room_not_found"}, "código inexistente")
    expect_error("not_in_room", lambda: c.state(room), "quien no está en la sala no ve su estado")
    expect_error("not_host", lambda: b.call("start_room", room), "solo la anfitriona empieza")

    cur = b.conn.cursor()
    for table in ("rooms", "room_answers", "question_keys", "questions"):
        try:
            cur.execute(f"select * from public.{table} limit 1")
            check(False, f"lectura directa de {table} bloqueada")
        except psycopg2.Error:
            check(True, "")
    try:
        cur.execute("update public.room_players set score = 9999")
        check(False, "escritura directa de puntos bloqueada")
    except psycopg2.Error:
        check(True, "")
    try:
        cur.execute("select public._advance(%s)", (room,))
        check(False, "funciones internas no accesibles")
    except psycopg2.Error:
        check(True, "")

    anon = connect(DB)
    anon.cursor().execute("set role authenticated")
    try:
        anon.cursor().execute("select public.create_room('private', 'general')")
        check(False, "sin sesión no se crean salas")
    except psycopg2.Error as e:
        check("not_authenticated" in str(e), "sin sesión no se crean salas")

    expect_error("invalid_mode", lambda: c.call("create_room", "private", "atlantida"), "modalidad inexistente")

    s = a.call("start_room", room)
    check(s["room"]["status"] == "playing" and s["room"]["phase"] == "roulette", "empieza con la ruleta")
    check("question" not in json.dumps(s.get("current", {}).get("question") or {}), "la ruleta no muestra la pregunta")
    check(s["current"]["question"] is None, "en la ruleta la pregunta va vacía")
    expect_error("room_in_progress", lambda: c.call("join_room_by_code", code), "no se entra a una partida en curso")
    return a, b, room


def test_full_game_three_players(admin):
    print("Partida completa de 3 jugadores")
    a, b, c = Player(admin, "Dani"), Player(admin, "Eva"), Player(admin, "Fede")
    s = a.call("create_room", "private", "general")
    room, code = s["room"]["id"], s["room"]["code"]
    b.call("join_room_by_code", code)
    c.call("join_room_by_code", code)
    a.call("start_room", room)

    cur = admin.cursor()
    cur.execute("select count(*), count(distinct question_id) from public.room_questions where room_id = %s and not is_tiebreak", (room,))
    check(cur.fetchone() == (12, 12), "12 preguntas distintas planificadas")
    cur.execute("select count(*) from public.room_questions where room_id = %s and is_tiebreak", (room,))
    check(cur.fetchone()[0] == 5, "5 preguntas de desempate reservadas")
    cur.execute(
        "select count(*) from public.room_questions rq join public.questions q on q.id = rq.question_id"
        " where rq.room_id = %s and not rq.is_tiebreak and q.is_vote", (room,))
    check(cur.fetchone()[0] <= 1, "como máximo una votación por partida")

    expected = {a.id: 0, b.id: 0, c.id: 0}
    seen_questions = 0
    for _ in range(400):
        s = a.state(room)
        b.state(room)
        c.state(room)
        r = s["room"]
        if r["status"] == "finished":
            break
        phase = r["phase"]
        if phase == "betting":
            check(s["current"]["question"] is None, "la apuesta se hace sin ver la pregunta")
            for p in (a, b, c):
                ps = p.state(room)
                allowed = ps["current"]["allowed_bets"]
                amount = max(allowed) if allowed else 0
                p.call("submit_bet", room, ps["current"]["index"], amount)
            expect_error("already_bet", lambda: a.call("submit_bet", room, s["current"]["index"], 50), "no se apuesta dos veces")
            expire(admin, room)
        elif phase == "question":
            idx = s["current"]["index"]
            seen_questions += 1
            text = json.dumps(s)
            check('"result"' not in text and "winning_options" not in text, f"pregunta {idx + 1}: sin solución durante la pregunta")
            correct, is_vote, n = key_for(admin, room, False, idx)
            if is_vote:
                a.call("submit_answer", room, idx, 0, 1000)
                b.call("submit_answer", room, idx, 0, 1000)
                c.call("submit_answer", room, idx, 1, 1000)
            else:
                a.call("submit_answer", room, idx, correct, 1000)
                expect_error("already_answered", lambda: a.call("submit_answer", room, idx, correct, 500), "respuesta duplicada rechazada")
                expect_error("invalid_option", lambda: b.call("submit_answer", room, idx, 9, 500), "opción inexistente")
                expect_error("wrong_question", lambda: b.call("submit_answer", room, idx + 1, 0, 500), "respuesta a otra pregunta")
                b.call("submit_answer", room, idx, wrong(correct, n), 1000)
                # Fede no responde: la pregunta se cierra por tiempo.
                expire(admin, room)
            s = a.state(room)
            check(s["room"]["phase"] == "reveal", f"pregunta {idx + 1}: pasa a revelación")
            res = s["result"]
            outcomes = {o["user_id"]: o for o in res["outcomes"]}
            check(len(outcomes) == 3, f"pregunta {idx + 1}: resultado para los 3")
            if not is_vote:
                check(outcomes[c.id]["choice"] is None, f"pregunta {idx + 1}: Fede sin respuesta")
            for pid, o in outcomes.items():
                expected[pid] += o["points"]
            # Volver a consultar no vuelve a puntuar.
            again = {p["user_id"]: p["score"] for p in b.state(room)["players"]}
            check(again == expected, f"pregunta {idx + 1}: marcador = suma de puntos, sin dobles")
            check(all(v >= 0 for v in again.values()), f"pregunta {idx + 1}: nadie en negativo")
            if is_vote:
                check(res["winning_options"] == [0] and res["vote_counts"] == {"0": 2, "1": 1}, "votación: gana la mayoría")
                check(outcomes[a.id]["points"] == 100 or s["current"]["round"] == 3, "votación: +100 a la mayoría")
            else:
                check(res["winning_options"] == [correct], f"pregunta {idx + 1}: se revela la correcta")
                check(outcomes[b.id]["won"] is False, f"pregunta {idx + 1}: Eva falla")
            expire(admin, room)
        else:
            expire(admin, room)
    s = a.state(room)
    check(s["room"]["status"] == "finished", "la partida termina")
    check(seen_questions == 12, f"se jugaron 12 preguntas ({seen_questions})")
    check(s["room"]["winners"] == [a.id], "gana quien acertó todo")
    check(len(s["final"]) == 3, "resumen final para los 3")

    st = stats(admin, a)
    xp = 20 + st[3] * 5 + 30
    check(st[0] == 1 and st[1] == 1 and st[2] == 0, "Dani: 1 partida, 1 victoria")
    check(st[4] in (11, 12) and st[3] == st[4], "Dani: aciertos = preguntas objetivas")
    check(st[5] == expected[a.id] and st[6] == xp, "Dani: mejor puntuación y XP")
    check(stats(admin, c)[:3] == (1, 0, 1), "Fede: 1 partida, 1 derrota")
    a.state(room)
    b.state(room)
    check(stats(admin, a)[0] == 1, "las estadísticas se aplican una sola vez")


def test_tiebreak_two_players(admin):
    print("Empate y desempate")
    a, b = Player(admin, "Gabi"), Player(admin, "Hugo")
    s = a.call("create_room", "private", "general")
    room = s["room"]["id"]
    b.call("join_room_by_code", s["room"]["code"])
    a.call("start_room", room)
    cur = admin.cursor()
    cur.execute("select count(*) from public.room_questions rq join public.questions q on q.id = rq.question_id"
                " where rq.room_id = %s and q.is_vote", (room,))
    check(cur.fetchone()[0] == 0, "con 2 jugadores no hay votaciones")
    for _ in range(300):
        s = a.state(room)
        b.state(room)
        phase = s["room"]["phase"]
        if phase in ("tiebreak_question",) or s["room"]["status"] == "finished":
            break
        if phase == "betting":
            for p in (a, b):
                allowed = p.state(room)["current"]["allowed_bets"]
                p.call("submit_bet", room, s["current"]["index"], max(allowed) if allowed else 0)
            expire(admin, room)
        elif phase == "question":
            idx = s["current"]["index"]
            correct, _, _ = key_for(admin, room, False, idx)
            age_phase(admin, room, 8100)  # los dos responden al límite: sin bono de rapidez, empate exacto
            a.call("submit_answer", room, idx, correct, 8100)
            b.call("submit_answer", room, idx, correct, 8100)
        else:
            expire(admin, room)
    s = a.state(room)
    check(s["room"]["phase"] == "tiebreak_question", "empate en el primer puesto: desempate")
    scores = [p["score"] for p in s["players"]]
    check(scores[0] == scores[1], "empate real en puntos")
    check(all(p["in_tiebreak"] for p in s["players"]), "los dos juegan el desempate")
    before = stats(admin, a)
    correct, _, _ = key_for(admin, room, True, 0)
    a.call("submit_answer", room, 0, correct, 300)
    age_phase(admin, room, 6000)
    b.call("submit_answer", room, 0, correct, 300)  # el servidor mide ~6 s: no puede declarar 0,3 s
    s = a.state(room)
    check(s["room"]["phase"] == "tiebreak_reveal", "revelación del desempate")
    outcomes = {o["user_id"]: o for o in s["result"]["outcomes"]}
    check(outcomes[b.id]["elapsed_ms"] >= 4500, "el tiempo declarado por el cliente se acota al medido")
    check(s["result"]["tiebreak_winner"] == a.id, "gana el acierto más rápido")
    expire(admin, room)
    s = a.state(room)
    check(s["room"]["status"] == "finished" and s["room"]["winners"] == [a.id], "termina con un ganador")
    after = stats(admin, a)
    check(after[3] - before[3] == 12 and after[4] - before[4] == 12, "el desempate no suma aciertos")
    check(stats(admin, b)[1:3] == (0, 1), "Hugo pierde")


def test_timing_and_disconnects(admin):
    print("Tiempo, desconexión, reconexión y abandono")
    a, b, c = Player(admin, "Iris"), Player(admin, "Juan"), Player(admin, "Kim")
    s = a.call("create_room", "private", "general")
    room = s["room"]["id"]
    b.call("join_room_by_code", s["room"]["code"])
    c.call("join_room_by_code", s["room"]["code"])
    a.call("start_room", room)
    expire(admin, room)
    s = a.state(room)
    check(s["room"]["phase"] == "question", "pregunta en curso")
    idx = s["current"]["index"]
    check(0 < s["room"]["phase_remaining_ms"] <= 8000, "el reloj que ve el jugador es de 8 s")

    age_phase(admin, room, 9700)
    expect_error("time_expired", lambda: b.call("submit_answer", room, idx, 0, 100), "respuesta fuera de tiempo")

    # Se reinicia la pregunta para probar el cierre anticipado.
    admin.cursor().execute("update public.rooms set phase_started_at = now(), phase_ends_at = now() + interval '9.5 seconds' where id = %s", (room,))
    admin.cursor().execute("update public.room_players set last_seen_at = now() - interval '30 seconds' where room_id = %s and user_id = %s", (room, c.id))
    s = a.state(room)
    kim = next(p for p in s["players"] if p["user_id"] == c.id)
    check(kim["connected"] is False, "Kim aparece desconectada")
    a.call("submit_answer", room, idx, 0, 500)
    s = b.call("submit_answer", room, idx, 0, 500)
    check(s["room"]["phase"] == "reveal", "sin esperar a la desconectada: se revela al responder los conectados")
    s = c.state(room)
    kim = next(p for p in s["players"] if p["user_id"] == c.id)
    check(kim["connected"] and s["room"]["phase"] == "reveal", "Kim vuelve y sigue en la partida")

    check(c.call("current_room")["room"]["id"] == room, "al volver a abrir la app se recupera la sala activa")
    b.call("leave_room", room)
    expect_error("room_left", lambda: b.call("join_room_by_code", s["room"]["code"]), "quien abandona no vuelve a la misma partida")
    s = a.state(room)
    juan = next(p for p in s["players"] if p["user_id"] == b.id)
    check(juan["left"], "Juan figura como retirado")

    admin.cursor().execute("update public.room_players set last_seen_at = now() - interval '2 minutes' where room_id = %s", (room,))
    admin.cursor().execute("select public._advance(%s)", (room,))
    cur = admin.cursor()
    cur.execute("select status, cancel_reason from public.rooms where id = %s", (room,))
    check(cur.fetchone() == ("cancelled", "abandoned"), "sala sin nadie conectado: cancelada")


def test_lobby_and_matchmaking(admin):
    print("Sala de espera, anfitrión y partidas públicas")
    players = [Player(admin, f"Jug{i}") for i in range(5)]
    s = players[0].call("create_room", "private", "la-guajira")
    room, code = s["room"]["id"], s["room"]["code"]
    for p in players[1:4]:
        p.call("join_room_by_code", code)
    expect_error("room_full", lambda: players[4].call("join_room_by_code", code), "sala llena con 4")
    players[0].call("leave_room", room)
    s = players[1].state(room)
    check(s["room"]["host_id"] != players[0].id and len(s["players"]) == 3, "si sale la anfitriona, otro toma el control")
    s = players[4].call("join_room_by_code", code)
    check(len(s["players"]) == 4, "el asiento libre se reutiliza")

    # Pública: misma modalidad comparte sala, otra modalidad no.
    p1, p2, p3 = Player(admin, "Pub1"), Player(admin, "Pub2"), Player(admin, "Pub3")
    r1 = p1.call("find_public_room", "general")["room"]
    r2 = p2.call("find_public_room", "general")["room"]
    r3 = p3.call("find_public_room", "atlantico")["room"]
    check(r1["id"] == r2["id"], "dos jugadores de la misma modalidad se emparejan")
    check(r3["id"] != r1["id"], "otra modalidad va a otra sala")
    check(r2["status"] == "ready" and r2["autostart_remaining_ms"] is not None, "con 2 jugadores empieza la cuenta atrás")
    check(p1.call("find_public_room", "general")["room"]["id"] == r1["id"], "buscar otra vez no crea otra sala")
    admin.cursor().execute("update public.rooms set autostart_at = now() - interval '1 second' where id = %s", (r1["id"],))
    s = p1.state(r1["id"])
    check(s["room"]["status"] == "playing", "la sala pública arranca sola")

    # Entrar a otra sala saca de la anterior.
    check(Player(admin, "Nadie").call("current_room") is None, "sin sala activa no hay nada que recuperar")
    q = Player(admin, "Viajero")
    first = q.call("create_room", "private", "general")["room"]["id"]
    q.call("find_public_room", "cesar")
    cur = admin.cursor()
    cur.execute("select status from public.rooms where id = %s", (first,))
    check(cur.fetchone()[0] == "cancelled", "al cambiar de sala, la anterior vacía se cancela")


def test_rate_limit(admin):
    print("Límite de frecuencia")
    p = Player(admin, "Spam")
    for _ in range(10):
        p.call("create_room", "private", "general")
    expect_error("rate_limited", lambda: p.call("create_room", "private", "general"), "más de 10 salas en 10 minutos")
    q = Player(admin, "Adivino")
    for i in range(20):
        q.call("join_room_by_code", "AAAAA" + "23456789ABCDEFGHJKLMNP"[i])
    expect_error("rate_limited", lambda: q.call("join_room_by_code", "BBBBBB"), "más de 20 códigos en 10 minutos")


def test_planner_all_modes(admin):
    print("Planificación en todas las modalidades")
    owner = Player(admin, "Planner")
    cur = admin.cursor()
    cur.execute("select distinct region_id from public.questions where region_id is not null order by 1")
    modes = ["general"] + [r[0] for r in cur.fetchall()]
    cur.execute("insert into public.rooms (code, visibility, mode, host_id, created_by, status)"
                " values ('PLAN22', 'private', 'general', %s, %s, 'cancelled') returning id", (owner.id, owner.id))
    room = cur.fetchone()[0]
    for mode in modes:
        ok = True
        for players in (2, 3, 4):
            for _ in range(15):
                try:
                    cur.execute("select public._plan_room(%s, %s, %s)", (room, mode, players))
                except psycopg2.Error as e:
                    ok = False
                    print("   ", mode, players, e.diag.message_primary)
                    break
                cur.execute(
                    "select count(distinct rq.question_id), bool_and(not q.is_vote or rq.round in (1, 3)),"
                    " bool_and(q.region_id is not distinct from nullif(%s, 'general'))"
                    " from public.room_questions rq join public.questions q on q.id = rq.question_id"
                    " where rq.room_id = %s and not rq.is_tiebreak", (mode, room))
                distinct, votes_ok, same_pool = cur.fetchone()
                ok = ok and distinct == 12 and votes_ok and same_pool
        check(ok, f"modalidad {mode}: 12 preguntas distintas de su banco, votaciones solo en rondas 1 y 3")
    cur.execute("delete from public.rooms where id = %s", (room,))


def main():
    admin = setup()
    cur = admin.cursor()
    cur.execute("select count(*), count(*) filter (where region_id is null) from public.questions")
    total, general = cur.fetchone()
    print(f"Banco en la base de datos: {total} preguntas ({general} generales)")
    check(total == 380 and general == 140, "banco completo cargado")
    test_planner_all_modes(admin)
    test_private_room_and_security(admin)
    test_full_game_three_players(admin)
    test_tiebreak_two_players(admin)
    test_timing_and_disconnects(admin)
    test_lobby_and_matchmaking(admin)
    test_rate_limit(admin)
    print(f"\n{passed} comprobaciones correctas, {len(failures)} fallos")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
