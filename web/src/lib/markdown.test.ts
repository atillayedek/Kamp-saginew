import { describe, expect, it } from "vitest";
import { parseInline, parseMarkdown } from "./markdown";

describe("legal markdown", () => {
  it("reads the structures the legal texts use", () => {
    const blocks = parseMarkdown(`# Başlık

> **TASLAK — AVUKAT ONAYI GEREKLİ.**

İlk satır
devamı.

## 1. Bölüm

- Bir
- İki **kalın**
  devam
1. Birinci
2. İkinci

- [ ] Kutu

| A | B |
|---|---|
| x | \`y\` |
`);
    expect(blocks.map((b) => b.kind)).toEqual(["heading", "quote", "paragraph", "heading", "list", "list", "list", "table"]);
    expect(blocks[2]).toEqual({ kind: "paragraph", text: [{ kind: "text", text: "İlk satır devamı." }] });
    expect(blocks[4]).toMatchObject({ ordered: false, items: [{ checkbox: false }, { text: [{ text: "İki " }, { kind: "bold", text: "kalın" }, { text: " devam" }] }] });
    expect(blocks[5]).toMatchObject({ ordered: true });
    expect(blocks[6]).toMatchObject({ items: [{ checkbox: true, text: [{ text: "Kutu" }] }] });
    expect(blocks[7]).toMatchObject({ head: [[{ text: "A" }], [{ text: "B" }]], rows: [[[{ text: "x" }], [{ kind: "code", text: "y" }]]] });
  });

  it("keeps markup as plain text", () => {
    expect(parseInline("<script>alert(1)</script> **x**")).toEqual([
      { kind: "text", text: "<script>alert(1)</script> " },
      { kind: "bold", text: "x" },
    ]);
  });
});

describe("published drafts", () => {
  it("every text in docs/legal has a title, the draft banner and no stray table rows", async () => {
    const { readdirSync, readFileSync } = await import("node:fs");
    const { join } = await import("node:path");
    const dir = join(__dirname, "..", "..", "..", "docs", "legal");
    const files = readdirSync(dir).filter((f) => f.endsWith(".md"));
    expect(files.length).toBe(13);
    for (const file of files) {
      const blocks = parseMarkdown(readFileSync(join(dir, file), "utf8"));
      expect(blocks[0], file).toMatchObject({ kind: "heading", level: 1 });
      expect(JSON.stringify(blocks[1]), file).toContain("TASLAK — AVUKAT ONAYI GEREKLİ");
      for (const block of blocks) {
        if (block.kind === "paragraph") expect(block.text.map((t) => t.text).join(""), file).not.toMatch(/^\|/);
      }
    }
  });
});
