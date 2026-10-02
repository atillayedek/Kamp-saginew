// KVKK md.12: function logs must not contain e-mail addresses, message bodies, tokens or
// document contents. Checks every console call in the functions' source, and the redactor.

import { assertEquals } from "jsr:@std/assert@1";
import { redact } from "./http.ts";

const FORBIDDEN = /\$\{\s*(email|password|token|body|content|text|details|fullName|phone|recipient\.email|caller\.email)\b/i;

Deno.test("console calls never interpolate personal data", async () => {
  const offenders: string[] = [];
  const root = new URL("../", import.meta.url);
  for await (const dir of Deno.readDir(root)) {
    if (!dir.isDirectory) continue;
    for await (const file of Deno.readDir(new URL(`${dir.name}/`, root))) {
      if (!file.name.endsWith(".ts") || file.name.endsWith(".test.ts")) continue;
      const source = await Deno.readTextFile(new URL(`${dir.name}/${file.name}`, root));
      source.split("\n").forEach((line, i) => {
        if (/console\.(log|info|warn|error)\(/.test(line) && FORBIDDEN.test(line)) offenders.push(`${dir.name}/${file.name}:${i + 1}`);
      });
    }
  }
  assertEquals(offenders, []);
});

Deno.test("redact hides addresses and long numbers", () => {
  assertEquals(
    redact('invalid `to` field: ayse.yilmaz@ogr.example.edu.tr, phone 05551234567'),
    "invalid `to` field: [e-posta], phone [sayı]",
  );
});
