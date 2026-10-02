"use client";

import { useState } from "react";
import { formatDay, formatNumber } from "@/lib/format";

export interface DailyPoint {
  day: string;
  value: number;
}

const HEIGHT = 180;
const GAP = 2;

/**
 * One series of daily counts as thin bars on a single axis, with a hover
 * tooltip per bar and a table view for screen readers and exact values.
 */
export function DailyBars({ label, points }: { label: string; points: DailyPoint[] }) {
  const [hover, setHover] = useState<number | null>(null);
  const [asTable, setAsTable] = useState(false);
  const max = Math.max(1, ...points.map((p) => p.value));
  const ticks = niceTicks(max);
  const top = ticks[ticks.length - 1];
  const total = points.reduce((sum, p) => sum + p.value, 0);

  return (
    <div>
      <div className="mb-3 flex items-baseline justify-between gap-3">
        <p className="text-sm text-ink-2">
          Son 30 gün: <span className="font-semibold tabular-nums text-ink">{formatNumber(total)}</span>
        </p>
        <button onClick={() => setAsTable((v) => !v)} className="text-xs font-medium text-ink-2 hover:text-ink">
          {asTable ? "Grafik" : "Tablo"}
        </button>
      </div>
      {asTable ? (
        <div className="max-h-56 overflow-auto rounded-lg border border-line">
          <table className="w-full text-sm">
            <caption className="sr-only">{label}</caption>
            <thead className="sticky top-0 bg-surface-2 text-left text-xs text-ink-2">
              <tr>
                <th className="px-3 py-2 font-medium">Gün</th>
                <th className="px-3 py-2 text-right font-medium">{label}</th>
              </tr>
            </thead>
            <tbody>
              {[...points].reverse().map((p) => (
                <tr key={p.day} className="border-t border-line">
                  <td className="px-3 py-1.5">{formatDay(p.day)}</td>
                  <td className="px-3 py-1.5 text-right tabular-nums">{formatNumber(p.value)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="relative flex gap-2">
          <div className="relative w-8 shrink-0 text-right text-[11px] tabular-nums text-ink-3" style={{ height: HEIGHT }} aria-hidden>
            {ticks.map((t) => (
              <span key={t} className="absolute right-0 leading-none" style={{ top: HEIGHT - (t / top) * HEIGHT - 5 }}>
                {formatNumber(t)}
              </span>
            ))}
          </div>
          <div className="relative flex-1" role="img" aria-label={`${label}, son 30 gün, toplam ${total}`}>
            <svg width="100%" height={HEIGHT} className="block overflow-visible" preserveAspectRatio="none">
              {ticks.map((t) => (
                <line
                  key={t}
                  x1="0"
                  x2="100%"
                  y1={HEIGHT - (t / top) * HEIGHT}
                  y2={HEIGHT - (t / top) * HEIGHT}
                  stroke="var(--border)"
                  strokeWidth={1}
                />
              ))}
            </svg>
            <div className="absolute inset-0 flex items-end" style={{ height: HEIGHT, gap: GAP }}>
              {points.map((p, i) => {
                const h = p.value === 0 ? 0 : Math.max(2, (p.value / top) * HEIGHT);
                return (
                  <div
                    key={p.day}
                    className="relative flex h-full flex-1 items-end"
                    onMouseEnter={() => setHover(i)}
                    onMouseLeave={() => setHover(null)}
                  >
                    <div
                      className="w-full rounded-t-[4px] transition-opacity"
                      style={{ height: h, background: "var(--series-1)", opacity: hover === null || hover === i ? 1 : 0.45 }}
                    />
                  </div>
                );
              })}
            </div>
            {hover !== null ? (
              <div
                className="pointer-events-none absolute -top-2 z-10 -translate-x-1/2 -translate-y-full whitespace-nowrap rounded-lg border border-line bg-surface px-2.5 py-1.5 text-xs shadow-lg"
                style={{ left: `${((hover + 0.5) / points.length) * 100}%` }}
              >
                <span className="text-ink-2">{formatDay(points[hover].day)}</span>{" "}
                <span className="font-semibold tabular-nums text-ink">{formatNumber(points[hover].value)}</span>
              </div>
            ) : null}
            <div className="mt-1.5 flex justify-between text-[11px] text-ink-3">
              <span>{points.length ? formatDay(points[0].day) : ""}</span>
              <span>{points.length ? formatDay(points[points.length - 1].day) : ""}</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/** 0 and two round steps covering `max`, e.g. 7 → [0, 5, 10]. */
export function niceTicks(max: number): number[] {
  const raw = max / 2;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const step = Math.max(1, [1, 2, 5, 10].map((m) => m * magnitude).find((s) => s >= raw) ?? raw);
  return [0, step, step * 2];
}
