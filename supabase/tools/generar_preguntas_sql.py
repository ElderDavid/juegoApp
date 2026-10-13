#!/usr/bin/env python3
"""Genera la migración que carga el banco de preguntas de la app en Supabase.

Fuente única: core/src/main/resources/questions/*.json (el mismo banco que valida QuestionBankContentTest).
Uso: python3 supabase/tools/generar_preguntas_sql.py > supabase/migrations/<fecha>_preguntas.sql
Se puede volver a ejecutar tras cambiar el banco: inserta o actualiza cada pregunta y borra las que ya no existen.
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
BANK = ROOT / "core/src/main/resources/questions"


def lit(value):
    if value is None:
        return "null"
    return "'" + str(value).replace("'", "''") + "'"


def main():
    index = json.loads((BANK / "index.json").read_text(encoding="utf-8"))
    rows, keys = [], []
    for name in index["files"]:
        for q in json.loads((BANK / name).read_text(encoding="utf-8")):
            if q.get("audience") == "KIDS":
                continue  # El modo niños solo existe en la partida local: no se sube al servidor.
            ev = q["evaluation"]
            is_vote = ev["type"] == "vote"
            rows.append(
                "(" + ", ".join([
                    lit(q["id"]), lit(q.get("region")), lit(q["category"]), lit(q["kind"]), lit(q["format"]),
                    lit(q["difficulty"]), "true" if is_vote else "false", lit(q["text"]),
                    lit(json.dumps(q["options"], ensure_ascii=False)) + "::jsonb",
                    lit(q["explanation"]), lit(q.get("source")),
                ]) + ")"
            )
            keys.append("(" + ", ".join([
                lit(q["id"]),
                "null" if is_vote else str(int(ev["index"])),
                lit(ev.get("rule")) if ev["type"] == "rule" else "null",
            ]) + ")")
    out = sys.stdout
    out.write("-- Generado por supabase/tools/generar_preguntas_sql.py a partir del banco de la app. No editar a mano.\n")
    out.write(f"-- {len(rows)} preguntas.\n\n")
    out.write("insert into public.questions (id, region_id, category_id, kind, format, difficulty, is_vote, text, options, explanation, source) values\n")
    out.write(",\n".join(rows))
    out.write("\non conflict (id) do update set region_id = excluded.region_id, category_id = excluded.category_id,\n"
              "  kind = excluded.kind, format = excluded.format, difficulty = excluded.difficulty, is_vote = excluded.is_vote,\n"
              "  text = excluded.text, options = excluded.options, explanation = excluded.explanation, source = excluded.source;\n\n")
    out.write("insert into public.question_keys (question_id, correct_index, game_rule) values\n")
    out.write(",\n".join(keys))
    out.write("\non conflict (question_id) do update set correct_index = excluded.correct_index, game_rule = excluded.game_rule;\n\n")
    ids = ", ".join(lit(r.split(",")[0][1:].strip("'")) for r in rows)
    out.write("-- Preguntas retiradas del banco (solo si ninguna sala las usó).\n")
    out.write(f"delete from public.questions q where q.id not in ({ids})\n"
              "  and not exists (select 1 from public.room_questions rq where rq.question_id = q.id);\n")


if __name__ == "__main__":
    main()
