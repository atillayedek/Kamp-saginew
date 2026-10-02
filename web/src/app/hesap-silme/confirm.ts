export const CONFIRM_WORD = "SİL";

/** "sil", "SİL", "SIL" (non-Turkish keyboard / caps lock) all count; dotted and dotless I are folded together. */
export function isDeleteConfirmation(text: string): boolean {
  return text.trim().toLocaleUpperCase("tr-TR").replace(/İ/g, "I") === "SIL";
}
