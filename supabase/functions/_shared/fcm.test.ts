import { assertEquals } from "jsr:@std/assert@1";
import { announcementText, pushText, safeEqual, sendToDevice, sendToDevices } from "./fcm.ts";

/** Test double for fetch. */
function scriptedFetch(status: number, body: string) {
  const calls: { url: string; init: RequestInit }[] = [];
  const fn = (input: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(input), init: init ?? {} });
    return Promise.resolve(new Response(body, { status }));
  };
  return { fn: fn as typeof fetch, calls };
}

Deno.test("send maps FCM answers", async () => {
  const message = { title: "Yeni mesaj", body: "Ali sana mesaj gönderdi.", data: { kind: "NEW_MESSAGE" } };
  const ok = scriptedFetch(200, "{}");
  assertEquals(await sendToDevice(ok.fn, "proj", "tok", "device", message), "sent");
  assertEquals(ok.calls[0].url, "https://fcm.googleapis.com/v1/projects/proj/messages:send");
  const sent = JSON.parse(ok.calls[0].init.body as string);
  assertEquals(sent.message.token, "device");
  assertEquals(sent.message.data, { kind: "NEW_MESSAGE" });
  assertEquals(sent.message.android.notification.click_action, "com.kampusagi.android.OPEN_NOTIFICATIONS");
  assertEquals((ok.calls[0].init.headers as Record<string, string>)["Authorization"], "Bearer tok");

  assertEquals(await sendToDevice(scriptedFetch(404, '{"error":{"status":"NOT_FOUND"}}').fn, "p", "t", "d", message), "unregistered");
  assertEquals(await sendToDevice(scriptedFetch(400, '{"error":{"details":[{"errorCode":"UNREGISTERED"}]}}').fn, "p", "t", "d", message), "unregistered");
  assertEquals(await sendToDevice(scriptedFetch(503, "unavailable").fn, "p", "t", "d", message), "failed");
});

Deno.test("push texts never include message content", () => {
  assertEquals(pushText("NEW_MESSAGE", "Ayşe Yılmaz"), { title: "Yeni mesaj", body: "Ayşe Yılmaz sana mesaj gönderdi." });
  assertEquals(pushText("NEW_COMMENT", null)?.body, "Bir öğrenci gönderine yorum yaptı.");
  // Moderation and KVKK outcomes never carry the content or the reason on the lock screen.
  for (const kind of ["CONTENT_REMOVED", "ACCOUNT_SUSPENDED", "APPEAL_DECIDED", "DSR_ANSWERED"]) {
    assertEquals(typeof pushText(kind, "Ayşe")?.title, "string");
    assertEquals(pushText(kind, "Ayşe")?.body.includes("Ayşe"), false);
  }
  assertEquals(pushText("VERIFICATION_APPROVED", null)?.title, "Hesabın onaylandı");
  assertEquals(pushText("UNKNOWN", null), null);
});

Deno.test("secret comparison", () => {
  assertEquals(safeEqual("abc", "abc"), true);
  assertEquals(safeEqual("abc", "abd"), false);
  assertEquals(safeEqual("abc", "abcd"), false);
  assertEquals(safeEqual("", ""), true);
});

Deno.test("many devices: every token gets a result in order", async () => {
  const message = { title: "Duyuru", body: "Metin", data: { kind: "ANNOUNCEMENT" } };
  const fn = ((input: string | URL | Request, init?: RequestInit) => {
    const token = JSON.parse(init?.body as string).message.token as string;
    if (token === "gone") return Promise.resolve(new Response('{"error":{"status":"NOT_FOUND"}}', { status: 404 }));
    if (token === "boom") return Promise.reject(new Error("network"));
    return Promise.resolve(new Response("{}", { status: 200 }));
  }) as typeof fetch;
  assertEquals(await sendToDevices(fn, "p", "t", ["a", "gone", "b", "boom"], message, 2), ["sent", "unregistered", "sent", "failed"]);
  assertEquals(await sendToDevices(fn, "p", "t", [], message), []);
});

Deno.test("announcement text is cut to notification size", () => {
  assertEquals(announcementText(" Bakım ", " Yarın 02:00 "), { title: "Bakım", body: "Yarın 02:00" });
  const long = announcementText("x".repeat(100), "y".repeat(500));
  assertEquals(long.title.length, 65);
  assertEquals(long.body.length, 240);
  assertEquals(long.body.endsWith("…"), true);
});
