import { assertEquals } from "jsr:@std/assert@1";
import { generateKeyPair, SignJWT } from "npm:jose@6";
import { verifyGooglePush } from "./auth.ts";

Deno.test("RTDN checks signature, sender, verification, audience and expiration", async () => {
  const keys = await generateKeyPair("RS256");
  const sender = "push@example.iam.gserviceaccount.com", audience = "https://backend/functions/v1/play-rtdn";
  const make = (email: string, verified: boolean, aud: string, expires: string) => new SignJWT({ email, email_verified: verified })
    .setProtectedHeader({ alg: "RS256" }).setIssuer("https://accounts.google.com").setAudience(aud)
    .setIssuedAt().setExpirationTime(expires).sign(keys.privateKey);
  const valid = await make(sender, true, audience, "5m");
  assertEquals(await verifyGooglePush(valid, audience, sender, keys.publicKey), true);
  assertEquals(await verifyGooglePush(valid, "wrong-audience", sender, keys.publicKey), false);
  assertEquals(await verifyGooglePush(await make("other", true, audience, "5m"), audience, sender, keys.publicKey), false);
  assertEquals(await verifyGooglePush(await make(sender, false, audience, "5m"), audience, sender, keys.publicKey), false);
  assertEquals(await verifyGooglePush(await make(sender, true, audience, "-1m"), audience, sender, keys.publicKey), false);
  const other = await generateKeyPair("RS256");
  assertEquals(await verifyGooglePush(valid, audience, sender, other.publicKey), false);
});
