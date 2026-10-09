import { assertEquals } from "jsr:@std/assert@1";
import { parseNotification, subscriptionExpiry } from "./logic.ts";

const packageName = "com.kampusagi.android";
const envelope = (data: unknown) => ({ message: { data: btoa(JSON.stringify(data)) } });
Deno.test("RTDN validates package, event time and purchase token", () => {
  const data = { packageName, eventTimeMillis: "1791540000000", subscriptionNotification: { purchaseToken: "purchase-token-123" } };
  assertEquals(parseNotification(envelope(data), packageName), { token: "purchase-token-123", eventTime: 1791540000000 });
  assertEquals(parseNotification(envelope({ ...data, packageName: "other" }), packageName), null);
  assertEquals(parseNotification(envelope({ ...data, eventTimeMillis: "invalid" }), packageName), null);
  assertEquals(parseNotification({ message: { data: "bad" } }, packageName), null);
  assertEquals(parseNotification(envelope({ packageName, eventTimeMillis: "1791540000000", testNotification: {} }), packageName), { token: null, eventTime: 1791540000000 });
});
Deno.test("RTDN reconciles current paid access and revocation", () => {
  const now = new Date("2026-10-09T10:00:00Z");
  const sub = { subscriptionState: "SUBSCRIPTION_STATE_ACTIVE", externalAccountIdentifiers: { obfuscatedExternalAccountId: "user" }, lineItems: [{ productId: "plus", expiryTime: "2026-11-09T10:00:00Z" }] };
  assertEquals(subscriptionExpiry(sub, "plus", "user", now), "2026-11-09T10:00:00.000Z");
  assertEquals(subscriptionExpiry({ ...sub, subscriptionState: "SUBSCRIPTION_STATE_CANCELED" }, "plus", "user", now), "2026-11-09T10:00:00.000Z");
  assertEquals(subscriptionExpiry({ ...sub, subscriptionState: "SUBSCRIPTION_STATE_EXPIRED" }, "plus", "user", now), null);
  assertEquals(subscriptionExpiry(sub, "plus", "other", now), null);
});
