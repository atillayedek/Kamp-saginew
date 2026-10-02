import { describe, expect, it } from "vitest";
import { auditHeaders, decodeReason, encodeReason, isValidReason, withReason } from "./audit";

describe("audit reason", () => {
  it("round-trips Turkish text through an ASCII header", () => {
    const reason = "Şikâyet: kişisel veri ifşası (ğüşiöç İ)";
    const encoded = encodeReason(reason);
    expect(encoded).toMatch(/^[A-Za-z0-9+/=]+$/);
    expect(decodeReason(encoded)).toBe(reason);
  });

  it("is attached only while the call starts", async () => {
    expect(auditHeaders()).toEqual({});
    const seen = await withReason("  Belge geçerli  ", async () => auditHeaders());
    expect(decodeReason(seen["x-audit-reason"])).toBe("Belge geçerli");
    expect(auditHeaders()).toEqual({});
  });

  it("needs at least three characters", () => {
    expect(isValidReason("  ab ")).toBe(false);
    expect(isValidReason("abc")).toBe(true);
    expect(isValidReason("x".repeat(1001))).toBe(false);
  });
});
