import { describe, expect, it } from "vitest";
import { backendState, isClientKey } from "./config";
import { errorMessage } from "./errors";
import { formatDay, formatMoney, formatNumber } from "./format";

function jwt(claims: object): string {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return `${b64({ alg: "HS256" })}.${b64(claims)}.signature`;
}

describe("client key guard", () => {
  it("accepts publishable and anon keys only", () => {
    expect(isClientKey("sb_publishable_abc")).toBe(true);
    expect(isClientKey(jwt({ role: "anon" }))).toBe(true);
    expect(isClientKey("sb_secret_abc")).toBe(false);
    expect(isClientKey(jwt({ role: "service_role" }))).toBe(false);
    expect(isClientKey("not-a-key")).toBe(false);
    expect(isClientKey("a.%%%.b")).toBe(false);
  });

  it("reports why the backend is unavailable", () => {
    const base = { playStoreUrl: "", privacyPolicyUrl: "", legalName: "", contactEmail: "" };
    expect(backendState({ ...base, supabaseUrl: "", supabaseAnonKey: "" })).toBe("missing");
    expect(backendState({ ...base, supabaseUrl: "https://x.supabase.co", supabaseAnonKey: "sb_secret_x" })).toBe("unsafe_key");
    expect(backendState({ ...base, supabaseUrl: "https://x.supabase.co", supabaseAnonKey: "sb_publishable_x" })).toBe("ready");
  });
});

describe("formatting", () => {
  it("formats micros as Turkish currency", () => {
    expect(formatMoney(49_990_000, "TRY")).toBe("₺49,99");
    expect(formatMoney(0, "TRY")).toBe("₺0,00");
    expect(formatMoney(4_500_000, "USD")).toBe("$4,50");
  });

  it("formats numbers and chart days", () => {
    expect(formatNumber(12345)).toBe("12.345");
    expect(formatDay("2026-09-27")).toBe("27 Eyl");
  });
});

describe("error messages", () => {
  it("maps backend codes, network failures and unknown errors", () => {
    expect(errorMessage(new Error("admin_required"))).toBe("Bu işlem için yetkin yok ya da iki adımlı doğrulama süresi doldu. Yeniden giriş yap.");
    expect(errorMessage(new Error("Invalid login credentials"))).toBe("E-posta ya da şifre hatalı.");
    expect(errorMessage(new TypeError("Failed to fetch"))).toContain("Sunucuya ulaşılamadı");
    expect(errorMessage(new Error("Edge Function returned: no_recipients"))).toBe("Bu gruba uyan alıcı yok.");
    expect(errorMessage(new Error("duplicate key value violates unique constraint"))).toBe("Beklenmeyen bir hata oluştu.");
    expect(errorMessage(null)).toBe("Beklenmeyen bir hata oluştu.");
  });
});

import { niceTicks } from "@/components/admin/DailyBars";

describe("chart ticks", () => {
  it("uses whole, round steps that cover the maximum", () => {
    expect(niceTicks(1)).toEqual([0, 1, 2]);
    expect(niceTicks(7)).toEqual([0, 5, 10]);
    expect(niceTicks(40)).toEqual([0, 20, 40]);
    expect(niceTicks(130)).toEqual([0, 100, 200]);
  });
});

describe("deadlines", () => {
  const now = Date.parse("2026-10-02T10:00:00Z");
  it("counts down and reports overdue", async () => {
    const { timeLeft } = await import("./format");
    expect(timeLeft("2026-10-03T08:30:00Z", now)).toEqual({ overdue: false, label: "22 sa 30 dk kaldı" });
    expect(timeLeft("2026-10-05T12:00:00Z", now)).toEqual({ overdue: false, label: "3 gün 2 sa kaldı" });
    expect(timeLeft("2026-10-02T09:00:00Z", now)).toEqual({ overdue: true, label: "1 sa 0 dk gecikti" });
  });
});

describe("csv export", () => {
  it("quotes, keeps Turkish text and flattens nested values", async () => {
    const { toCsv } = await import("./compliance-api");
    const csv = toCsv([{ a: "İçerik, \"alıntı\"", b: { x: 1 } }, { a: "düz", c: null }]);
    expect(csv.startsWith("﻿")).toBe(true);
    expect(csv).toBe("﻿a,b,c\r\n\"İçerik, \"\"alıntı\"\"\",\"{\"\"x\"\":1}\",\r\ndüz,,");
  });
});
