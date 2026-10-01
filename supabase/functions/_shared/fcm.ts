// Firebase Cloud Messaging (HTTP v1). Authorisation comes from
// google-auth.ts with the firebase.messaging scope.

import { accessToken, type ServiceAccount } from "./google-auth.ts";

type Fetch = typeof fetch;

export const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
export const OPEN_NOTIFICATIONS_ACTION = "com.kampusagi.android.OPEN_NOTIFICATIONS";

export function fcmAccessToken(fetchFn: Fetch, account: ServiceAccount, nowSeconds: number) {
  return accessToken(fetchFn, account, FCM_SCOPE, nowSeconds);
}

export type SendResult = "sent" | "unregistered" | "failed";

export interface PushMessage {
  title: string;
  body: string;
  /** String-only data the app uses to open the right screen. */
  data: Record<string, string>;
}

export async function sendToDevice(
  fetchFn: Fetch,
  projectId: string,
  token: string,
  deviceToken: string,
  message: PushMessage,
): Promise<SendResult> {
  const response = await fetchFn(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: "POST",
    headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      message: {
        token: deviceToken,
        notification: { title: message.title, body: message.body },
        data: message.data,
        android: {
          priority: "HIGH",
          // Matches the intent filter on MainActivity: a tap opens the notification list.
          notification: { channel_id: "kampusagi_default", click_action: OPEN_NOTIFICATIONS_ACTION },
        },
      },
    }),
  });
  if (response.ok) {
    await response.body?.cancel();
    return "sent";
  }
  const text = await response.text();
  // The token is no longer valid for this app: stop sending to it.
  if (response.status === 404 || text.includes("UNREGISTERED") || text.includes("registration-token-not-registered")) {
    return "unregistered";
  }
  return "failed";
}

/** Sends one message to many devices, a few at a time; results follow the token order. */
export async function sendToDevices(
  fetchFn: Fetch,
  projectId: string,
  token: string,
  deviceTokens: string[],
  message: PushMessage,
  concurrency = 20,
): Promise<SendResult[]> {
  const results: SendResult[] = new Array(deviceTokens.length);
  let next = 0;
  async function worker() {
    while (next < deviceTokens.length) {
      const i = next++;
      try {
        results[i] = await sendToDevice(fetchFn, projectId, token, deviceTokens[i], message);
      } catch (error) {
        console.error("FCM request failed", error instanceof Error ? error.message : String(error));
        results[i] = "failed";
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, deviceTokens.length) }, worker));
  return results;
}

/** Push text of an admin announcement, cut to what fits a notification. */
export function announcementText(title: string, body: string): { title: string; body: string } {
  const cut = (text: string, max: number) => (text.length > max ? text.slice(0, max - 1).trimEnd() + "…" : text);
  return { title: cut(title.trim(), 65), body: cut(body.trim(), 240) };
}

/** Turkish push text. Message previews are not included: the lock screen is not private. */
export function pushText(kind: string, actorName: string | null): { title: string; body: string } | null {
  const who = actorName ?? "Bir öğrenci";
  switch (kind) {
    case "NEW_MESSAGE":
      return { title: "Yeni mesaj", body: `${who} sana mesaj gönderdi.` };
    case "NEW_COMMENT":
      return { title: "Yeni yorum", body: `${who} gönderine yorum yaptı.` };
    case "VERIFICATION_APPROVED":
      return { title: "Hesabın onaylandı", body: "Öğrenci doğrulaman tamamlandı. KampüsAğı'na hoş geldin!" };
    case "VERIFICATION_REJECTED":
      return { title: "Belgen onaylanmadı", body: "Nedenini görmek ve yeni belge yüklemek için uygulamayı aç." };
    default:
      return null;
  }
}

/** Constant-time comparison for the webhook secret. */
export function safeEqual(a: string, b: string): boolean {
  const x = new TextEncoder().encode(a), y = new TextEncoder().encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}
