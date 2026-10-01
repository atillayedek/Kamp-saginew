"use client";

import type { SupabaseClient } from "@supabase/supabase-js";
import { ShieldCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { errorMessage } from "@/lib/errors";
import { Button, ErrorBox, inputClass, Loading } from "./ui";

type Step =
  | { kind: "loading" }
  | { kind: "challenge"; factorId: string }
  | { kind: "enroll"; factorId: string; qr: string; secret: string }
  | { kind: "error"; message: string };

/**
 * Staff actions need an MFA (aal2) session. Staff with an authenticator app enter the
 * 6-digit code; first-timers scan a QR code to enrol (TOTP), then confirm with a code.
 */
export function MfaGate({ client, email }: { client: SupabaseClient; email: string }) {
  const [step, setStep] = useState<Step>({ kind: "loading" });
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    (async () => {
      const { data, error: listError } = await client.auth.mfa.listFactors();
      if (listError) throw listError;
      const verified = data.totp.find((f) => f.status === "verified");
      if (verified) return { kind: "challenge", factorId: verified.id } as Step;
      // Unfinished enrolments would block a new one with the same name.
      for (const factor of data.all.filter((f) => f.factor_type === "totp" && f.status !== "verified")) {
        const { error: removeError } = await client.auth.mfa.unenroll({ factorId: factor.id });
        if (removeError) throw removeError;
      }
      const { data: enrolled, error: enrollError } = await client.auth.mfa.enroll({
        factorType: "totp",
        friendlyName: `KampüsAğı yönetim ${new Date().toISOString().slice(0, 10)}`,
      });
      if (enrollError) throw enrollError;
      return { kind: "enroll", factorId: enrolled.id, qr: enrolled.totp.qr_code, secret: enrolled.totp.secret } as Step;
    })().then(
      (next) => active && setStep(next),
      (e: unknown) => {
        console.error(e);
        if (active) setStep({ kind: "error", message: errorMessage(e) });
      },
    );
    return () => {
      active = false;
    };
  }, [client]);

  async function verify(e: React.FormEvent) {
    e.preventDefault();
    if (step.kind !== "challenge" && step.kind !== "enroll") return;
    setBusy(true);
    setError(null);
    const { error: verifyError } = await client.auth.mfa.challengeAndVerify({ factorId: step.factorId, code: code.trim() });
    if (verifyError) {
      console.error(verifyError);
      setError("Kod doğrulanamadı. Uygulamadaki güncel 6 haneli kodu gir.");
      setBusy(false);
    }
    // On success the session is upgraded to aal2 and the panel opens by itself.
  }

  return (
    <div className="w-full max-w-sm space-y-4 rounded-2xl border border-line bg-surface p-6 shadow-sm">
      <div className="flex items-center gap-2">
        <ShieldCheck className="size-5 text-brand" aria-hidden />
        <h1 className="text-lg font-semibold">İki adımlı doğrulama</h1>
      </div>
      <p className="text-sm text-ink-2">{email}</p>
      {step.kind === "loading" ? <Loading /> : null}
      {step.kind === "error" ? <ErrorBox message={step.message} /> : null}
      {step.kind === "enroll" ? (
        <div className="space-y-3 text-sm text-ink-2">
          <p>Yönetici hesapları iki adımlı doğrulama ile korunur. Google Authenticator, Microsoft Authenticator veya benzeri bir uygulamayla bu kodu tara:</p>
          {/* eslint-disable-next-line @next/next/no-img-element -- data: URL from Supabase Auth */}
          <img src={step.qr} alt="İki adımlı doğrulama QR kodu" className="mx-auto size-48 rounded-lg bg-white p-2" />
          <p>Tarayamıyorsan anahtarı elle gir: <code className="break-all font-mono text-xs text-ink">{step.secret}</code></p>
        </div>
      ) : null}
      {step.kind === "challenge" || step.kind === "enroll" ? (
        <form onSubmit={verify} className="space-y-3">
          <label className="block">
            <span className="mb-1.5 block text-sm font-medium">Doğrulama kodu</span>
            <input
              inputMode="numeric"
              autoComplete="one-time-code"
              pattern="[0-9]{6}"
              maxLength={6}
              required
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
              className={`${inputClass} text-center font-mono text-lg tracking-widest`}
            />
          </label>
          {error ? <ErrorBox message={error} /> : null}
          <Button type="submit" busy={busy} className="w-full" disabled={code.length !== 6}>Doğrula</Button>
        </form>
      ) : null}
      <Button variant="ghost" className="w-full" onClick={() => client.auth.signOut().catch((e: unknown) => console.error(e))}>Çıkış yap</Button>
    </div>
  );
}
