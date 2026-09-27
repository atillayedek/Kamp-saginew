"use client";

import type { SupabaseClient } from "@supabase/supabase-js";
import { Megaphone, Send, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { adminApi, type BroadcastResult } from "@/lib/admin-api";
import { errorMessage } from "@/lib/errors";
import { AUDIENCE_LABELS, formatDateTime, formatNumber } from "@/lib/format";
import { Badge, Button, Card, Empty, ErrorBox, inputClass, Loading, PageHeader, useAsync } from "./ui";

const MAX_SUBJECT = 150;
const MAX_BODY = 20000;

type Step = { kind: "edit" } | { kind: "confirm"; count: number } | { kind: "done"; result: BroadcastResult };

export function BroadcastView({ client, adminEmail }: { client: SupabaseClient; adminEmail: string }) {
  const [subject, setSubject] = useState("");
  const [body, setBody] = useState("");
  const [audience, setAudience] = useState("ALL");
  const [marketing, setMarketing] = useState(false);
  const [step, setStep] = useState<Step>({ kind: "edit" });
  const [busy, setBusy] = useState<"count" | "test" | "send" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [testSent, setTestSent] = useState(false);
  const history = useAsync(() => adminApi.broadcasts(client), "broadcasts");

  const valid = subject.trim().length > 0 && subject.length <= MAX_SUBJECT && !/[\r\n]/.test(subject) &&
    body.trim().length > 0 && body.length <= MAX_BODY;

  async function run<T>(kind: "count" | "test" | "send", action: () => Promise<T>): Promise<T | null> {
    setBusy(kind);
    setError(null);
    try {
      return await action();
    } catch (e) {
      console.error(e);
      setError(errorMessage(e));
      return null;
    } finally {
      setBusy(null);
    }
  }

  async function prepare() {
    const count = await run("count", () => adminApi.audienceSize(client, audience, marketing));
    if (count !== null) setStep({ kind: "confirm", count });
  }

  async function sendTest() {
    setTestSent(false);
    const result = await run("test", () =>
      adminApi.sendBroadcast(client, { subject, body, audience, marketing, test_only: true }));
    if (result) setTestSent(true);
  }

  async function send() {
    const result = await run("send", () =>
      adminApi.sendBroadcast(client, { subject, body, audience, marketing, test_only: false }));
    if (result) {
      setStep({ kind: "done", result });
      history.reload();
    }
  }

  function reset() {
    setSubject("");
    setBody("");
    setTestSent(false);
    setStep({ kind: "edit" });
  }

  return (
    <>
      <PageHeader title="Toplu e-posta" description="Resend üzerinden öğrencilere duyuru ya da kampanya e-postası gönder." />
      <div className="grid gap-6 xl:grid-cols-[3fr_2fr]">
        <Card title="Yeni e-posta">
          {step.kind === "done" ? (
            <div className="space-y-4">
              <div className={`rounded-xl px-4 py-3 text-sm ${step.result.failed_count === 0 ? "bg-good-soft text-good" : "bg-warn-soft text-warn"}`}>
                {formatNumber(step.result.sent_count)} / {formatNumber(step.result.recipient_count)} alıcıya gönderildi.
                {step.result.failed_count > 0 ? ` ${formatNumber(step.result.failed_count)} gönderim başarısız oldu; ayrıntılar Supabase fonksiyon loglarında.` : ""}
              </div>
              <Button onClick={reset}>Yeni e-posta yaz</Button>
            </div>
          ) : (
            <form
              className="space-y-5"
              onSubmit={(e) => {
                e.preventDefault();
                if (valid) void prepare();
              }}
            >
              <fieldset className="grid gap-3 sm:grid-cols-2" disabled={step.kind === "confirm"}>
                <legend className="mb-2 text-sm font-medium">Tür</legend>
                <TypeOption
                  checked={!marketing}
                  onChange={() => setMarketing(false)}
                  icon={<ShieldCheck className="size-4" aria-hidden />}
                  title="Hizmet duyurusu"
                  text="Bakım, kural değişikliği, güvenlik gibi hizmetle ilgili bilgiler. Askıdakiler hariç seçilen gruba gider."
                />
                <TypeOption
                  checked={marketing}
                  onChange={() => setMarketing(true)}
                  icon={<Megaphone className="size-4" aria-hidden />}
                  title="Pazarlama / kampanya"
                  text="Yalnızca e-posta iznini açmış kişilere gider ve abonelikten çıkma bağlantısı içerir (6563 sayılı Kanun)."
                />
              </fieldset>

              <label className="block">
                <span className="mb-1.5 block text-sm font-medium">Alıcılar</span>
                <select
                  value={audience}
                  onChange={(e) => setAudience(e.target.value)}
                  disabled={step.kind === "confirm"}
                  className={inputClass}
                >
                  {Object.entries(AUDIENCE_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>{label}</option>
                  ))}
                </select>
              </label>

              <label className="block">
                <span className="mb-1.5 flex justify-between text-sm font-medium">
                  Konu <span className="font-normal text-ink-3 tabular-nums">{subject.length}/{MAX_SUBJECT}</span>
                </span>
                <input
                  value={subject}
                  onChange={(e) => setSubject(e.target.value.replace(/[\r\n]/g, ""))}
                  maxLength={MAX_SUBJECT}
                  disabled={step.kind === "confirm"}
                  className={inputClass}
                  placeholder="Örn. Yeni özellik: topluluk etkinlikleri"
                />
              </label>

              <label className="block">
                <span className="mb-1.5 flex justify-between text-sm font-medium">
                  Metin <span className="font-normal text-ink-3 tabular-nums">{formatNumber(body.length)}/{formatNumber(MAX_BODY)}</span>
                </span>
                <textarea
                  value={body}
                  onChange={(e) => setBody(e.target.value)}
                  maxLength={MAX_BODY}
                  rows={10}
                  disabled={step.kind === "confirm"}
                  className={`${inputClass} resize-y leading-relaxed`}
                  placeholder={"Merhaba,\n\nParagrafları boş bir satırla ayır."}
                />
                <span className="mt-1 block text-xs text-ink-3">Düz metin olarak gönderilir; boş satırlar paragraf olur.</span>
              </label>

              {error ? <ErrorBox message={error} /> : null}
              {testSent ? (
                <p className="rounded-lg bg-good-soft px-4 py-2.5 text-sm text-good">Test e-postası {adminEmail} adresine gönderildi.</p>
              ) : null}

              {step.kind === "confirm" ? (
                <div className="rounded-xl border border-line bg-surface-2 p-4">
                  {step.count === 0 ? (
                    <p className="text-sm">Bu seçime uyan alıcı yok.</p>
                  ) : (
                    <p className="text-sm">
                      Bu e-posta <span className="font-semibold">{formatNumber(step.count)}</span> kişiye
                      {marketing ? " (pazarlama izni olanlar)" : ""} gönderilecek. Gönderim geri alınamaz.
                    </p>
                  )}
                  <div className="mt-3 flex flex-wrap gap-2">
                    {step.count > 0 ? (
                      <Button type="button" onClick={send} busy={busy === "send"}>
                        <Send className="size-4" aria-hidden /> Şimdi gönder
                      </Button>
                    ) : null}
                    <Button type="button" variant="ghost" disabled={busy === "send"} onClick={() => setStep({ kind: "edit" })}>
                      Düzenlemeye dön
                    </Button>
                  </div>
                </div>
              ) : (
                <div className="flex flex-wrap gap-2">
                  <Button type="submit" disabled={!valid} busy={busy === "count"}>Gönderime hazırla</Button>
                  <Button type="button" variant="secondary" disabled={!valid || busy !== null} busy={busy === "test"} onClick={sendTest}>
                    Kendime test gönder
                  </Button>
                </div>
              )}
            </form>
          )}
        </Card>

        <Card title="Gönderim geçmişi">
          {history.error ? <ErrorBox message={history.error} onRetry={history.reload} /> : null}
          {history.loading && !history.data ? <Loading /> : null}
          {history.data && history.data.length === 0 ? <Empty>Henüz toplu e-posta gönderilmedi.</Empty> : null}
          <ul className="divide-y divide-line">
            {history.data?.map((b) => (
              <li key={b.id} className="py-3 first:pt-0">
                <div className="flex items-start justify-between gap-3">
                  <p className="font-medium">{b.subject}</p>
                  <Badge tone={b.status === "SENT" ? "good" : b.status === "SENDING" ? "warn" : "bad"}>
                    {{ SENT: "Gönderildi", SENDING: "Gönderiliyor", PARTIAL: "Kısmen", FAILED: "Başarısız" }[b.status]}
                  </Badge>
                </div>
                <p className="mt-1 text-xs text-ink-3">
                  {formatDateTime(b.created_at)} · {AUDIENCE_LABELS[b.audience] ?? b.audience}
                  {b.marketing ? " · pazarlama" : " · duyuru"} · {formatNumber(b.sent_count)}/{formatNumber(b.recipient_count)}
                  {b.sent_by_email ? ` · ${b.sent_by_email}` : ""}
                </p>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </>
  );
}

function TypeOption({ checked, onChange, icon, title, text }: {
  checked: boolean;
  onChange: () => void;
  icon: React.ReactNode;
  title: string;
  text: string;
}) {
  return (
    <label
      className={`flex cursor-pointer gap-3 rounded-xl border p-4 transition ${checked ? "border-brand bg-brand-soft" : "border-line hover:bg-surface-2"}`}
    >
      <input type="radio" name="kind" checked={checked} onChange={onChange} className="mt-1 accent-[var(--brand)]" />
      <span>
        <span className="flex items-center gap-1.5 text-sm font-medium">{icon} {title}</span>
        <span className="mt-1 block text-xs leading-relaxed text-ink-2">{text}</span>
      </span>
    </label>
  );
}
