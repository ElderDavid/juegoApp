import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { AVATARS, CATEGORIES, REGIONS, modeName } from "../js/catalog.js";

const repo = new URL("../../", import.meta.url);
const read = (p) => readFileSync(new URL(p, repo), "utf8");

test("categorías y departamentos iguales que el banco de la app", () => {
  const index = JSON.parse(read("core/src/main/resources/questions/index.json"));
  assert.deepEqual(CATEGORIES, Object.fromEntries(index.categories.map((c) => [c.id, c.name])));
  assert.deepEqual(REGIONS, index.regions);
});

test("avatares iguales que la app y que el límite del servidor (0 a 11)", () => {
  const kotlin = read("app/src/main/kotlin/com/elderdavid/quientienelarazon/ui/screens/ProfileScreen.kt");
  const list = kotlin.match(/val AvatarEmojis = listOf\(([^)]*)\)/)[1];
  assert.deepEqual(AVATARS, [...list.matchAll(/"([^"]+)"/g)].map((m) => m[1]));
  assert.match(read("supabase/migrations/20261012000000_perfiles.sql"), new RegExp(`between 0 and ${AVATARS.length - 1}`));
});

test("nombre de la modalidad", () => {
  assert.equal(modeName("general"), "General");
  assert.equal(modeName("atlantico"), "De aquí somos: Atlántico");
});
