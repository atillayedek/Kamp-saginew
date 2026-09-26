// Google Play Developer API: verify a subscription purchase token on the
// server and acknowledge it. `fetchFn` is injectable for tests.

type Fetch = typeof fetch;

export const PLAY_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications";

export type PlayCheck =
  | { ok: true; expiresAt: string; needsAcknowledge: boolean }
  | { ok: false; code: "purchase_not_active" | "purchase_not_for_account" | "product_mismatch" };

/**
 * Decides whether a subscriptionsv2 response entitles `userId` to
 * `productId` now. The app sets obfuscatedAccountId to the user's id when it
 * launches the purchase, so a token bought by one account cannot unlock another.
 */
export function evaluateSubscription(data: unknown, productId: string, userId: string, now: Date): PlayCheck {
  const sub = (data ?? {}) as {
    subscriptionState?: string;
    acknowledgementState?: string;
    externalAccountIdentifiers?: { obfuscatedExternalAccountId?: string };
    lineItems?: { productId?: string; expiryTime?: string }[];
  };
  if (sub.externalAccountIdentifiers?.obfuscatedExternalAccountId !== userId) {
    return { ok: false, code: "purchase_not_for_account" };
  }
  if (sub.subscriptionState !== "SUBSCRIPTION_STATE_ACTIVE" && sub.subscriptionState !== "SUBSCRIPTION_STATE_IN_GRACE_PERIOD") {
    return { ok: false, code: "purchase_not_active" };
  }
  const item = (sub.lineItems ?? []).find((line) => line.productId === productId);
  if (!item) return { ok: false, code: "product_mismatch" };
  const expiry = item.expiryTime ? Date.parse(item.expiryTime) : NaN;
  if (Number.isNaN(expiry) || expiry <= now.getTime()) return { ok: false, code: "purchase_not_active" };
  return {
    ok: true,
    expiresAt: new Date(expiry).toISOString(),
    needsAcknowledge: sub.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING",
  };
}

export class PlayApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

export async function fetchSubscription(
  fetchFn: Fetch,
  accessToken: string,
  packageName: string,
  purchaseToken: string,
): Promise<unknown> {
  const url = `${BASE}/${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
  const response = await fetchFn(url, { headers: { "Authorization": `Bearer ${accessToken}` } });
  if (!response.ok) {
    await response.body?.cancel();
    throw new PlayApiError(response.status, `subscriptionsv2.get returned HTTP ${response.status}`);
  }
  return await response.json();
}

/** Unacknowledged purchases are refunded by Google after three days. */
export async function acknowledgeSubscription(
  fetchFn: Fetch,
  accessToken: string,
  packageName: string,
  productId: string,
  purchaseToken: string,
): Promise<void> {
  const url = `${BASE}/${encodeURIComponent(packageName)}/purchases/subscriptions/${encodeURIComponent(productId)}` +
    `/tokens/${encodeURIComponent(purchaseToken)}:acknowledge`;
  const response = await fetchFn(url, {
    method: "POST",
    headers: { "Authorization": `Bearer ${accessToken}`, "Content-Type": "application/json" },
    body: "{}",
  });
  await response.body?.cancel();
  if (!response.ok) throw new PlayApiError(response.status, `acknowledge returned HTTP ${response.status}`);
}
