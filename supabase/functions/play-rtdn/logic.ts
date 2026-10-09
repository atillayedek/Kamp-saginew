import { evaluateSubscription } from "../_shared/play.ts";

export function parseNotification(body: unknown, packageName: string): { token: string | null; eventTime: number } | null {
  try {
    const envelope = body as { message?: { data?: unknown } };
    if (typeof envelope?.message?.data !== "string" || envelope.message.data.length > 32768) return null;
    const data = JSON.parse(atob(envelope.message.data));
    if (data.packageName !== packageName) return null;
    const eventTime = Number(data.eventTimeMillis);
    if (!Number.isSafeInteger(eventTime) || eventTime <= 0) return null;
    if (data.testNotification) return { token: null, eventTime };
    const token = data.subscriptionNotification?.purchaseToken ?? data.voidedPurchaseNotification?.purchaseToken;
    if (typeof token !== "string" || token.length < 10 || token.length > 4096) return null;
    return { token, eventTime };
  } catch {
    return null;
  }
}

/** Only Google's current subscription response decides access, not the event type. */
export function subscriptionExpiry(data: unknown, productId: string, userId: string, now: Date): string | null {
  const check = evaluateSubscription(data, productId, userId, now);
  return check.ok ? check.expiresAt : null;
}
