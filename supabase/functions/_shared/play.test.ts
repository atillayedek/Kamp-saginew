import { assertEquals, assertRejects } from "jsr:@std/assert@1";
import { acknowledgeSubscription, evaluateSubscription, fetchSubscription, moneyToMicros, PlayApiError } from "./play.ts";

const now = new Date("2026-09-26T12:00:00Z");
const USER = "0b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c10";
const active = {
  subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
  acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
  externalAccountIdentifiers: { obfuscatedExternalAccountId: USER },
  lineItems: [{ productId: "kampusagi.plus", expiryTime: "2026-10-26T12:00:00Z" }],
};

Deno.test("active subscription for this account and product is accepted", () => {
  assertEquals(evaluateSubscription(active, "kampusagi.plus", USER, now), {
    ok: true, expiresAt: "2026-10-26T12:00:00.000Z", needsAcknowledge: true, orderId: null, price: null,
  });
  const acknowledged = { ...active, acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED" };
  assertEquals(evaluateSubscription(acknowledged, "kampusagi.plus", USER, now).ok, true);
  const grace = { ...active, subscriptionState: "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" };
  assertEquals(evaluateSubscription(grace, "kampusagi.plus", USER, now).ok, true);
});

Deno.test("order id and recurring price are passed on for the revenue ledger", () => {
  const priced = {
    ...active,
    latestOrderId: "GPA.old",
    lineItems: [{
      productId: "kampusagi.plus",
      expiryTime: "2026-10-26T12:00:00Z",
      latestSuccessfulOrderId: "GPA.1234-5678..1",
      autoRenewingPlan: { recurringPrice: { currencyCode: "TRY", units: "49", nanos: 990000000 } },
    }],
  };
  const check = evaluateSubscription(priced, "kampusagi.plus", USER, now);
  assertEquals(check.ok && check.orderId, "GPA.1234-5678..1");
  assertEquals(check.ok && check.price, { amountMicros: 49_990_000, currency: "TRY" });
  const legacy = { ...active, latestOrderId: "GPA.legacy" };
  const legacyCheck = evaluateSubscription(legacy, "kampusagi.plus", USER, now);
  assertEquals(legacyCheck.ok && legacyCheck.orderId, "GPA.legacy");
});

Deno.test("malformed prices are dropped, never guessed", () => {
  assertEquals(moneyToMicros({ currencyCode: "USD", units: "4", nanos: 500000000 }), { amountMicros: 4_500_000, currency: "USD" });
  assertEquals(moneyToMicros({ currencyCode: "EUR", nanos: 990000000 }), { amountMicros: 990_000, currency: "EUR" });
  assertEquals(moneyToMicros({ currencyCode: "usd", units: "4" }), null);
  assertEquals(moneyToMicros({ currencyCode: "USD", units: "-4" }), null);
  assertEquals(moneyToMicros({ currencyCode: "USD", units: "abc" }), null);
  assertEquals(moneyToMicros({ currencyCode: "USD", units: "1", nanos: 1e9 }), null);
  assertEquals(moneyToMicros({ units: "4" }), null);
  assertEquals(moneyToMicros(null), null);
});

Deno.test("everything else is refused", () => {
  assertEquals(evaluateSubscription({ ...active, externalAccountIdentifiers: { obfuscatedExternalAccountId: "someone-else" } }, "kampusagi.plus", USER, now),
    { ok: false, code: "purchase_not_for_account" });
  assertEquals(evaluateSubscription({ ...active, externalAccountIdentifiers: undefined }, "kampusagi.plus", USER, now),
    { ok: false, code: "purchase_not_for_account" });
  assertEquals(evaluateSubscription({ ...active, subscriptionState: "SUBSCRIPTION_STATE_EXPIRED" }, "kampusagi.plus", USER, now),
    { ok: false, code: "purchase_not_active" });
  assertEquals(evaluateSubscription({ ...active, subscriptionState: "SUBSCRIPTION_STATE_PENDING" }, "kampusagi.plus", USER, now),
    { ok: false, code: "purchase_not_active" });
  assertEquals(evaluateSubscription(active, "kampusagi.pro", USER, now), { ok: false, code: "product_mismatch" });
  assertEquals(evaluateSubscription({ ...active, lineItems: [{ productId: "kampusagi.plus", expiryTime: "2026-09-26T11:59:59Z" }] }, "kampusagi.plus", USER, now),
    { ok: false, code: "purchase_not_active" });
  assertEquals(evaluateSubscription(null, "kampusagi.plus", USER, now), { ok: false, code: "purchase_not_for_account" });
});

/** Test double for fetch. */
function scriptedFetch(status: number, body: string) {
  const calls: { url: string; init: RequestInit }[] = [];
  const fn = (input: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(input), init: init ?? {} });
    return Promise.resolve(new Response(body, { status }));
  };
  return { fn: fn as typeof fetch, calls };
}

Deno.test("API calls use the right endpoints and surface HTTP errors", async () => {
  const ok = scriptedFetch(200, JSON.stringify(active));
  assertEquals(await fetchSubscription(ok.fn, "tok", "com.kampusagi.android", "a/b"), active);
  assertEquals(ok.calls[0].url,
    "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.kampusagi.android/purchases/subscriptionsv2/tokens/a%2Fb");

  const missing = scriptedFetch(404, "{}");
  const error = await assertRejects(() => fetchSubscription(missing.fn, "tok", "com.kampusagi.android", "x"), PlayApiError);
  assertEquals(error.status, 404);

  const ack = scriptedFetch(200, "");
  await acknowledgeSubscription(ack.fn, "tok", "com.kampusagi.android", "kampusagi.plus", "tkn");
  assertEquals(ack.calls[0].url,
    "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.kampusagi.android/purchases/subscriptions/kampusagi.plus/tokens/tkn:acknowledge");
  assertEquals(ack.calls[0].init.method, "POST");
  await assertRejects(() => acknowledgeSubscription(scriptedFetch(500, "").fn, "t", "p", "s", "x"), PlayApiError);
});
