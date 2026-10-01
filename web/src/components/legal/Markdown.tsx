import type { ReactNode } from "react";
import { type Block, type Inline, parseMarkdown } from "@/lib/markdown";

function inline(parts: Inline[]): ReactNode[] {
  return parts.map((p, i) =>
    p.kind === "bold" ? <strong key={i} className="font-semibold text-ink">{p.text}</strong>
    : p.kind === "code" ? <code key={i} className="rounded bg-surface-2 px-1 font-mono text-[0.9em]">{p.text}</code>
    : <span key={i}>{p.text}</span>);
}

function block(b: Block, i: number): ReactNode {
  switch (b.kind) {
    case "heading":
      // The page already has the title as <h1>; the text's own headings start one level lower.
      return b.level === 1
        ? null
        : b.level === 2
          ? <h2 key={i} className="pt-4 text-lg font-semibold text-ink">{inline(b.text)}</h2>
          : <h3 key={i} className="pt-2 font-semibold text-ink">{inline(b.text)}</h3>;
    case "quote":
      return <p key={i} className="rounded-lg border border-warn/40 bg-warn-soft px-4 py-3 text-sm text-warn">{inline(b.text)}</p>;
    case "list": {
      const items = b.items.map((item, j) => (
        <li key={j} className={item.checkbox ? "list-none" : undefined}>
          {item.checkbox ? <span aria-hidden className="mr-2 inline-block size-3.5 rounded-sm border border-ink-3 align-middle" /> : null}
          {inline(item.text)}
        </li>
      ));
      return b.ordered
        ? <ol key={i} className="list-decimal space-y-1.5 pl-5">{items}</ol>
        : <ul key={i} className="list-disc space-y-1.5 pl-5">{items}</ul>;
    }
    case "table":
      return (
        <div key={i} className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr>{b.head.map((h, j) => <th key={j} className="border border-line bg-surface-2 px-2 py-1.5 text-left font-semibold text-ink">{inline(h)}</th>)}</tr>
            </thead>
            <tbody>
              {b.rows.map((row, r) => (
                <tr key={r}>{row.map((c, j) => <td key={j} className="border border-line px-2 py-1.5 align-top">{inline(c)}</td>)}</tr>
              ))}
            </tbody>
          </table>
        </div>
      );
    default:
      return <p key={i}>{inline(b.text)}</p>;
  }
}

export function Markdown({ source }: { source: string }) {
  return <>{parseMarkdown(source).map(block)}</>;
}
