"use client";

import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { Button, ErrorBox } from "@/components/admin/ui";
import { unsubscribe } from "@/lib/admin-api";
import { errorMessage } from "@/lib/errors";
import { supabase } from "@/lib/supabase";

/** The person confirms with a click, so link scanners in mail clients cannot unsubscribe them. */
export function Unsubscribe() {
  const params = useSearchParams();
  const userId = params.get("u") ?? "";
  const signature = params.get("s") ?? "";
  const [state, setState] = useState<"idle" | "busy" | "done">("idle");
  const [error, setError] = useState<string | null>(null);
  const client = supabase();

  async function confirm() {
    if (!client) return;
    setState("busy");
    setError(null);
    try {
      await unsubscribe(client, userId, signature);
      setState("done");
    } catch (e) {
      console.error(e);
      setError(errorMessage(e));
      setState("idle");
    }
  }

  return (
    <div className="w-full max-w-md rounded-2xl border border-line bg-surface p-6">
      <h1 className="text-lg font-semibold">E-posta aboneliği</h1>
      {!client ? (
        <p className="mt-3 text-sm text-ink-2">Bu sayfa henüz yapılandırılmadı. Uygulamadaki Ayarlar → E-posta izinleri bölümünden de çıkabilirsin.</p>
      ) : !userId || !signature ? (
        <p className="mt-3 text-sm text-ink-2">Bağlantı eksik. E-postadaki bağlantıyı tam olarak açtığından emin ol.</p>
      ) : state === "done" ? (
        <p className="mt-3 rounded-lg bg-good-soft px-4 py-3 text-sm text-good">
          Kampanya e-postalarından çıktın. Hizmetle ilgili zorunlu duyurular gelmeye devam eder.
        </p>
      ) : (
        <>
          <p className="mt-3 text-sm leading-relaxed text-ink-2">
            KampüsAğı kampanya ve yenilik e-postalarını artık almak istemiyorsan onayla. İznini istediğin zaman uygulamadaki
            Ayarlar ekranından yeniden açabilirsin.
          </p>
          {error ? <div className="mt-4"><ErrorBox message={error} /></div> : null}
          <Button className="mt-5 w-full" busy={state === "busy"} onClick={confirm}>Abonelikten çık</Button>
        </>
      )}
    </div>
  );
}
