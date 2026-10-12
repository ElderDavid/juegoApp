#!/usr/bin/env python3
"""Pruebas de la economía (monedas, premios, compras, videos) contra un PostgreSQL local.

Igual que salas_test.py: base desechable, auth simulado y todas las migraciones.
Uso: PGTEST_DSN="host=/tmp/pg port=5432 user=postgres" python3 supabase/tests/economia_test.py
Requiere psycopg2.
"""
import json
import os
import pathlib
import sys
import uuid

import psycopg2

ROOT = pathlib.Path(__file__).resolve().parents[2]
DSN = os.environ.get("PGTEST_DSN", "host=/tmp/pgtest-qtr port=54329 user=postgres")
DB = "qtr_economia_test"

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


def run(conn, sql, args=()):
    cur = conn.cursor()
    try:
        cur.execute(sql, args)
    except psycopg2.Error as e:
        raise ServerError((e.diag.message_primary or str(e)).strip()) from None
    if cur.description is None:
        return None
    value = cur.fetchone()[0]
    return json.loads(value) if isinstance(value, str) and value[:1] in ("{", "[") else value


class Player:
    def __init__(self, admin):
        self.id = str(uuid.uuid4())
        admin.cursor().execute("insert into auth.users (id) values (%s)", (self.id,))
        self.conn = connect(DB)
        cur = self.conn.cursor()
        cur.execute("set role authenticated")
        cur.execute("select set_config('request.jwt.claim.sub', %s, false)", (self.id,))

    def call(self, fn, *args):
        placeholders = ", ".join(["%s"] * len(args))
        return run(self.conn, f"select public.{fn}({placeholders})", args)

    def state(self):
        return self.call("economy_state")


class Service:
    def __init__(self):
        self.conn = connect(DB)
        self.conn.cursor().execute("set role service_role")

    def call(self, fn, *args):
        placeholders = ", ".join(["%s"] * len(args))
        return run(self.conn, f"select public.{fn}({placeholders})", args)


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


def main():
    admin = setup()
    service = Service()

    def shift_days(p, days):
        """Mueve la última racha reclamada; las referencias diarias viejas se renombran como si fueran de otro día."""
        cur = admin.cursor()
        cur.execute("update public.wallets set last_daily = last_daily + %s where user_id = %s", (days, p.id))
        cur.execute("update public.coin_ledger set ref = 'old-' || id where user_id = %s and reason = 'daily'", (p.id,))

    def set_coins(p, coins):
        p.state()  # crea el monedero
        admin.cursor().execute("update public.wallets set coins = %s where user_id = %s", (coins, p.id))

    print("— Estado inicial —")
    a = Player(admin)
    s = a.state()
    check(s["coins"] == 0 and s["level"] == 1 and s["owned"] == [], "monedero vacío y nivel 1")
    check(s["videos_left"] == 5 and s["local_xp_games_left"] == 5, "5 videos y 5 partidas con XP al día")
    check(s["daily_claimed"] is False and s["next_streak_day"] == 1, "racha sin reclamar")

    print("— Partidas locales —")
    r = a.call("record_local_game", "partida-0001", False, False)
    check(r["xp"] == 10, "10 XP por partida local")
    check(r["coins"] == 5 + 20, "5 monedas + logro primera partida (20)")
    r = a.call("record_local_game", "partida-0001", False, False)
    check(r["duplicate"] is True and r["xp"] == 0 and r["coins"] == 0, "la misma partida no cuenta dos veces")
    for i in range(2, 12):
        a.call("record_local_game", f"partida-{i:04d}", i % 2 == 0, False)
    s = a.state()
    check(s["local_xp_games_left"] == 0, "tope de 5 partidas con XP al día")
    check(s["xp"] == 50, f"XP local con tope: {s['xp']}")
    check(s["coins"] == 10 * 5 + 20, f"tope de 10 partidas con monedas al día: {s['coins']}")
    cur = admin.cursor()
    cur.execute("select xp, local_xp from public.player_stats where user_id = %s", (a.id,))
    check(cur.fetchone() == (0, 50), "el XP local no cambia el XP online de la clasificación")
    expect_error("invalid_game_key", lambda: a.call("record_local_game", "x", False, False), "clave de partida inválida")
    a.call("record_local_game", "partida-perfecta", False, True)
    check("perfect_round" in a.state()["achievements"], "logro de ronda perfecta")

    print("— Niveles —")
    b = Player(admin)
    admin.cursor().execute("update public.player_stats set xp = 100 where user_id = %s", (b.id,))
    s = b.state()
    check(s["level"] == 2 and s["coins"] == 70, f"nivel 2 da 70 monedas: {s}")
    check("frame_bronze" in s["owned"], "el marco del nivel 2 queda disponible")
    admin.cursor().execute("update public.player_stats set xp = 4500 where user_id = %s", (b.id,))
    s = b.state()
    expected = sum(50 + 10 * lvl for lvl in range(2, s["level"] + 1)) + 100
    check(s["level"] == 10, f"4500 XP = nivel 10: {s['level']}")
    check(s["coins"] == expected, f"bono de cada nivel + logro nivel 10: {s['coins']} vs {expected}")
    admin.cursor().execute("update public.player_stats set xp = 4400 where user_id = %s", (b.id,))
    admin.cursor().execute("update public.player_stats set xp = 4500 where user_id = %s", (b.id,))
    check(b.state()["coins"] == expected, "bajar y volver a subir no repite bonos")

    print("— Partidas online —")
    c = Player(admin)
    admin.cursor().execute("update public.player_stats set games_played = 1, wins = 1, losses = 0 where user_id = %s", (c.id,))
    s = c.state()
    check(s["coins"] == 5 + 10 + 50, f"partida + victoria + primera victoria: {s['coins']}")

    print("— Racha diaria —")
    r = a.call("claim_daily")
    check(r["coins"] == 10 and r["streak_day"] == 1, "día 1: 10 monedas")
    expect_error("already_claimed", lambda: a.call("claim_daily"), "una vez al día")
    shift_days(a, -1)
    r = a.call("claim_daily")
    check(r["coins"] == 15 and r["streak_day"] == 2, "día 2 seguido: 15 monedas")
    shift_days(a, -3)
    check(a.state()["streak_day"] == 0 and a.state()["next_streak_day"] == 1, "racha rota vuelve al día 1")
    shift_days(a, 2)
    admin.cursor().execute("update public.wallets set streak_day = 6 where user_id = %s", (a.id,))
    before = a.state()["coins"]
    r = a.call("claim_daily")
    check(r["coins"] == 100 and r["streak_day"] == 7, "día 7: 100 monedas")
    check(a.state()["coins"] == before + 100 + 100, "día 7 + logro semana completa")

    print("— Paquete de partida —")
    d = Player(admin)
    expect_error("not_enough_coins", lambda: d.call("buy_match_pack", "partida-pack-1"), "sin saldo")
    set_coins(d, 100)
    check(d.call("buy_match_pack", "partida-pack-1") == 40, "cuesta 60")
    expect_error("already_bought", lambda: d.call("buy_match_pack", "partida-pack-1"), "uno por partida")
    check(d.state()["coins"] == 40, "el segundo intento no cobra")

    print("— Cosméticos —")
    expect_error("not_for_sale", lambda: d.call("buy_cosmetic", "frame_bronze"), "lo de nivel no se vende")
    expect_error("not_for_sale", lambda: d.call("buy_cosmetic", "no_existe"), "id desconocido")
    expect_error("not_enough_coins", lambda: d.call("buy_cosmetic", "title_profe"), "sin saldo para el título")
    set_coins(d, 500)
    check(d.call("buy_cosmetic", "title_profe") == 350, "compra de título")
    expect_error("already_owned", lambda: d.call("buy_cosmetic", "title_profe"), "no se compra dos veces")
    d.call("equip_cosmetic", "TITLE", "title_profe")
    check(d.state()["equipped"] == {"TITLE": "title_profe"}, "título equipado")
    expect_error("not_owned", lambda: d.call("equip_cosmetic", "FRAME", "frame_oro"), "no se equipa lo que no tiene")
    expect_error("not_owned", lambda: d.call("equip_cosmetic", "FRAME", "frame_bronze"), "nivel insuficiente")
    expect_error("invalid_cosmetic", lambda: d.call("equip_cosmetic", "FRAME", "title_profe"), "tipo equivocado")
    b.call("equip_cosmetic", "THEME", "theme_neon_caribe")
    check(b.state()["equipped"]["THEME"] == "theme_neon_caribe", "lo ganado por nivel se equipa")
    d.call("equip_cosmetic", "TITLE", None)
    check(d.state()["equipped"] == {}, "quitar lo equipado")

    print("— Videos recompensados —")
    expect_error("permission denied", lambda: a.call("grant_rewarded_view", a.id, "tx-x"), "la app no puede acreditar videos")
    e = Player(admin)
    results = [service.call("grant_rewarded_view", e.id, f"tx-{i}") for i in range(6)]
    check(results == [True] * 5 + [False], f"5 videos pagados al día: {results}")
    check(service.call("grant_rewarded_view", e.id, "tx-0") is False, "una transacción no se paga dos veces")
    s = e.state()
    check(s["coins"] == 100 and s["videos_left"] == 0, f"20 por video: {s['coins']}")
    check(service.call("grant_rewarded_view", str(uuid.uuid4()), "tx-ghost") is False, "usuario inexistente")

    print("— Compras —")
    expect_error("permission denied", lambda: a.call("record_purchase", a.id, "coins_300", "tok", None), "la app no puede acreditar compras")
    f = Player(admin)
    r = service.call("record_purchase", f.id, "coins_300", "token-1", "GPA.1")
    check(r["ok"] and not r["duplicate"] and f.state()["coins"] == 300, "paquete de 300 monedas")
    r = service.call("record_purchase", f.id, "coins_300", "token-1", "GPA.1")
    check(r["duplicate"] and f.state()["coins"] == 300, "la misma compra no se acredita dos veces")
    expect_error("purchase_belongs_to_other_user", lambda: service.call("record_purchase", e.id, "coins_300", "token-1", "GPA.1"), "token de otro usuario")
    service.call("record_purchase", f.id, "remove_ads", "token-2", "GPA.2")
    check(f.state()["remove_ads"] is True, "quitar anuncios queda registrado")
    expect_error("unknown_product", lambda: service.call("record_purchase", f.id, "gemas", "token-3", None), "producto desconocido")

    print("— Acceso directo —")
    for table in ["wallets", "coin_ledger", "purchases", "owned_cosmetics", "rewarded_views"]:
        expect_error("permission denied", lambda: run(a.conn, f"select count(*) from public.{table}"), f"sin lectura directa de {table}")
    expect_error("permission denied", lambda: a.call("_move_coins", a.id, 1000, "hack", None), "sin acceso a funciones internas")

    print("— Borrar cuenta —")
    f.call("delete_my_account")
    cur = admin.cursor()
    cur.execute("select count(*) from public.wallets where user_id = %s", (f.id,))
    check(cur.fetchone()[0] == 0, "el monedero se borra con la cuenta")

    print(f"\n{passed} comprobaciones correctas, {len(failures)} fallos")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
