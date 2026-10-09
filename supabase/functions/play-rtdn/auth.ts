import { createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from "npm:jose@6";

const googleKeys = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));

export async function verifyGooglePush(token: string, audience: string, sender: string, key: CryptoKey | JWTVerifyGetKey = googleKeys): Promise<boolean> {
  try {
    const { payload } = await jwtVerify(token, key, {
      algorithms: ["RS256"], issuer: ["https://accounts.google.com", "accounts.google.com"], audience,
      requiredClaims: ["exp", "iat", "email", "email_verified"],
    });
    return payload.email === sender && payload.email_verified === true;
  } catch {
    return false;
  }
}
