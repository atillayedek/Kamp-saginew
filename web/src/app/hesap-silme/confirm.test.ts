import { describe, expect, it } from "vitest";
import { isDeleteConfirmation } from "./confirm";

describe("isDeleteConfirmation", () => {
  it("accepts every way of typing the word", () => {
    for (const text of ["SİL", "sil", "SIL", " Sil ", "sıl"]) expect(isDeleteConfirmation(text)).toBe(true);
  });
  it("rejects anything else", () => {
    for (const text of ["", "SILL", "delete", "si l"]) expect(isDeleteConfirmation(text)).toBe(false);
  });
});
