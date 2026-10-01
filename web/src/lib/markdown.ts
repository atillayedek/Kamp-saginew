// A small Markdown reader for the legal texts (published from the admin panel). It knows
// only what the texts use: headings, paragraphs, lists (with [ ] boxes), block quotes,
// tables, **bold** and `code`. It produces data, never HTML, so nothing in a text can
// inject markup into the page.

export type Inline = { kind: "text" | "bold" | "code"; text: string };

export type Block =
  | { kind: "heading"; level: 1 | 2 | 3; text: Inline[] }
  | { kind: "paragraph"; text: Inline[] }
  | { kind: "quote"; text: Inline[] }
  | { kind: "list"; ordered: boolean; items: { checkbox: boolean; text: Inline[] }[] }
  | { kind: "table"; head: Inline[][]; rows: Inline[][][] };

export function parseInline(text: string): Inline[] {
  const out: Inline[] = [];
  const pattern = /\*\*(.+?)\*\*|`([^`]+)`/g;
  let last = 0;
  for (let match = pattern.exec(text); match; match = pattern.exec(text)) {
    if (match.index > last) out.push({ kind: "text", text: text.slice(last, match.index) });
    out.push(match[1] !== undefined ? { kind: "bold", text: match[1] } : { kind: "code", text: match[2] });
    last = match.index + match[0].length;
  }
  if (last < text.length) out.push({ kind: "text", text: text.slice(last) });
  return out;
}

function cells(line: string): string[] {
  return line.trim().replace(/^\|/, "").replace(/\|$/, "").split("|").map((c) => c.trim());
}

const LIST_ITEM = /^\s*(?:([-*])|(\d+)[.)])\s+(\[[ xX]\]\s+)?(.*)$/;

export function parseMarkdown(source: string): Block[] {
  const lines = source.replace(/\r\n?/g, "\n").split("\n");
  const blocks: Block[] = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (line.trim() === "") {
      i++;
      continue;
    }
    const heading = /^(#{1,3})\s+(.*)$/.exec(line);
    if (heading) {
      blocks.push({ kind: "heading", level: heading[1].length as 1 | 2 | 3, text: parseInline(heading[2].trim()) });
      i++;
      continue;
    }
    if (line.trimStart().startsWith(">")) {
      const parts: string[] = [];
      while (i < lines.length && lines[i].trimStart().startsWith(">")) parts.push(lines[i++].trimStart().replace(/^>\s?/, ""));
      blocks.push({ kind: "quote", text: parseInline(parts.join(" ").trim()) });
      continue;
    }
    if (line.trimStart().startsWith("|") && i + 1 < lines.length && /^\s*\|?\s*:?-{3,}/.test(lines[i + 1])) {
      const head = cells(line).map(parseInline);
      i += 2;
      const rows: Inline[][][] = [];
      while (i < lines.length && lines[i].trimStart().startsWith("|")) rows.push(cells(lines[i++]).map(parseInline));
      blocks.push({ kind: "table", head, rows });
      continue;
    }
    const first = LIST_ITEM.exec(line);
    if (first) {
      const ordered = first[2] !== undefined;
      const items: { checkbox: boolean; text: Inline[] }[] = [];
      while (i < lines.length) {
        const item = LIST_ITEM.exec(lines[i]);
        if (!item || (item[2] !== undefined) !== ordered) break;
        let text = item[4];
        i++;
        // Continuation lines (indented, not a new item) belong to the item.
        while (i < lines.length && /^\s{2,}\S/.test(lines[i]) && !LIST_ITEM.exec(lines[i])) text += " " + lines[i++].trim();
        items.push({ checkbox: item[3] !== undefined, text: parseInline(text.trim()) });
      }
      blocks.push({ kind: "list", ordered, items });
      continue;
    }
    const parts: string[] = [];
    while (
      i < lines.length && lines[i].trim() !== "" && !/^#{1,3}\s/.test(lines[i]) && !lines[i].trimStart().startsWith(">")
      && !lines[i].trimStart().startsWith("|") && !LIST_ITEM.exec(lines[i])
    ) {
      parts.push(lines[i++].trim());
    }
    blocks.push({ kind: "paragraph", text: parseInline(parts.join(" ")) });
  }
  return blocks;
}
