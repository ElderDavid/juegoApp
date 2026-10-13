import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const repo = new URL("../../", import.meta.url);
const read = (p) => readFileSync(new URL(p, repo), "utf8");

// Texto plano: sin marcas de markdown ni etiquetas HTML, espacios unidos.
const plain = (s) => s
  .replace(/<[^>]+>/g, "")
  .replace(/\*\*|`/g, "")
  .replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, "\"")
  .replace(/\s+/g, " ")
  .trim();

test("la página web de privacidad tiene el mismo texto que docs/POLITICA_PRIVACIDAD.md", () => {
  const md = read("docs/POLITICA_PRIVACIDAD.md");
  const html = plain(read("web/privacidad.html"));
  const blocks = md
    .split(/\n(?=#|- |\n)/)
    .map((b) => b.trim())
    .filter((b) => b && !b.startsWith(">") && !b.startsWith("# "));
  assert.ok(blocks.length > 10);
  for (const block of blocks) {
    const text = plain(block.replace(/^(##+|-)\s+/, ""));
    assert.ok(html.includes(text), `falta en web/privacidad.html: ${text.slice(0, 80)}`);
  }
});

test("la web enlaza la política y muestra el contacto", () => {
  assert.match(read("web/privacidad.html"), /mailto:vantivoc@gmail\.com/);
  assert.match(read("web/js/app.js"), /href="privacidad\.html"/);
});
