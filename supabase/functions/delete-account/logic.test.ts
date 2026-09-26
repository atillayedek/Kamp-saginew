import { assertEquals } from "jsr:@std/assert@1";
import { collectUserFiles, isConfirmed, PAGE_SIZE } from "./logic.ts";

const USER = "0b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c10";

Deno.test("requires an explicit confirmation", () => {
  assertEquals(isConfirmed({ confirm: "DELETE" }), true);
  assertEquals(isConfirmed({ confirm: "delete" }), false);
  assertEquals(isConfirmed({ confirm: true }), false);
  assertEquals(isConfirmed({}), false);
  assertEquals(isConfirmed(null), false);
});

Deno.test("collects every page of the user's folder", async () => {
  const names = Array.from({ length: PAGE_SIZE + 3 }, (_, i) => `${i}.pdf`);
  const calls: string[] = [];
  const paths = await collectUserFiles(USER, (folder, offset, limit) => {
    calls.push(`${folder}@${offset}`);
    return Promise.resolve(names.slice(offset, offset + limit));
  });
  assertEquals(paths.length, PAGE_SIZE + 3);
  assertEquals(paths[0], `${USER}/0.pdf`);
  assertEquals(paths.at(-1), `${USER}/${PAGE_SIZE + 2}.pdf`);
  assertEquals(calls, [`${USER}@0`, `${USER}@${PAGE_SIZE}`]);
});

Deno.test("an empty folder needs one listing call", async () => {
  let calls = 0;
  const paths = await collectUserFiles(USER, () => {
    calls++;
    return Promise.resolve([]);
  });
  assertEquals(paths, []);
  assertEquals(calls, 1);
});

Deno.test("an exactly full page asks once more", async () => {
  const names = Array.from({ length: PAGE_SIZE }, (_, i) => `${i}.pdf`);
  let calls = 0;
  const paths = await collectUserFiles(USER, (_folder, offset, limit) => {
    calls++;
    return Promise.resolve(names.slice(offset, offset + limit));
  });
  assertEquals(paths.length, PAGE_SIZE);
  assertEquals(calls, 2);
});
