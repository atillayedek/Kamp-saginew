// Every staff action carries a reason (KVKK accountability). The database refuses staff
// changes without one and records it in admin_audit_logs; the panel sends it in the
// x-audit-reason header, base64 encoded because HTTP headers are ASCII only.

let activeReason: string | null = null;

export const MIN_REASON = 3;
export const MAX_REASON = 1000;

export function encodeReason(reason: string): string {
  const bytes = new TextEncoder().encode(reason);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

export function decodeReason(encoded: string): string {
  return new TextDecoder().decode(Uint8Array.from(atob(encoded), (c) => c.charCodeAt(0)));
}

export function isValidReason(reason: string): boolean {
  const trimmed = reason.trim();
  return trimmed.length >= MIN_REASON && trimmed.length <= MAX_REASON;
}

/**
 * Runs `call` with `reason` attached to the requests it starts. The API helpers read the
 * reason synchronously when a request starts, so `call` must start its request right away.
 */
export function withReason<T>(reason: string, call: () => Promise<T>): Promise<T> {
  const previous = activeReason;
  activeReason = reason.trim();
  try {
    return call();
  } finally {
    activeReason = previous;
  }
}

/** Headers for the request being started now. */
export function auditHeaders(): Record<string, string> {
  return activeReason ? { "x-audit-reason": encodeReason(activeReason) } : {};
}
