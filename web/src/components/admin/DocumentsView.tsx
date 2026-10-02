"use client";

import type { SupabaseClient } from "@supabase/supabase-js";
import { Check, ExternalLink, FileText, X } from "lucide-react";
import { useState } from "react";
import { adminApi, type PendingVerification } from "@/lib/admin-api";
import { withReason } from "@/lib/audit";
import { errorMessage } from "@/lib/errors";
import { formatDateTime, formatNumber } from "@/lib/format";
import { Button, Card, Empty, ErrorBox, inputClass, Loading, PageHeader, useAsync, useReason } from "./ui";

const MIN_REASON = 3;
const MAX_REASON = 500;

/**
 * Student document review: open the uploaded PDF, then approve or reject with a
 * reason. The database applies the decision to the document and the profile
 * together (review_student_verification), only for verifiers in an MFA session.
 * Opening a document and every decision are recorded with their reason.
 */
export function DocumentsView({ client }: { client: SupabaseClient }) {
  const pending = useAsync(() => adminApi.pendingVerifications(client), "verifications");

  return (
    <>
      <PageHeader
        title="Belge onayları"
        description="Öğrenci belgesini açıp incele, sonra onayla ya da gerekçesiyle reddet. En eski başvuru üstte."
        action={<Button variant="secondary" onClick={pending.reload} busy={pending.loading}>Yenile</Button>}
      />
      {pending.error ? <ErrorBox message={pending.error} onRetry={pending.reload} /> : null}
      {pending.loading && !pending.data ? <Loading /> : null}
      {pending.data && pending.data.length === 0 ? <Card><Empty>İnceleme bekleyen belge yok.</Empty></Card> : null}
      {pending.data && pending.data.length > 0 ? (
        <p className="mb-4 text-sm text-ink-2">{formatNumber(pending.data.length)} belge inceleme bekliyor.</p>
      ) : null}
      <div className="space-y-4">
        {pending.data?.map((v) => (
          <ReviewCard key={v.verification_id} client={client} item={v} onDone={pending.reload} />
        ))}
      </div>
    </>
  );
}

function ReviewCard({ client, item, onDone }: { client: SupabaseClient; item: PendingVerification; onDone: () => void }) {
  const ask = useReason();
  const [url, setUrl] = useState<string | null>(null);
  const [opening, setOpening] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState<"approve" | "reject" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const reasonValid = reason.trim().length >= MIN_REASON && reason.trim().length <= MAX_REASON;

  async function open() {
    const why = await ask("Öğrenci belgesini aç", {
      initial: "Öğrenci doğrulama incelemesi",
      hint: "Gerekçe (zorunlu). Belgenin her açılışı gerekçesiyle kayıt altına alınır.",
      confirmLabel: "Belgeyi aç",
    });
    if (!why) return;
    setOpening(true);
    setError(null);
    try {
      setUrl(await withReason(why, () => adminApi.documentUrl(client, item.verification_id)));
    } catch (e) {
      console.error(e);
      setError(errorMessage(e));
    } finally {
      setOpening(false);
    }
  }

  async function decide(approve: boolean) {
    const why = await ask(approve ? "Belgeyi onayla" : "Belgeyi reddet", {
      initial: approve ? "Belge geçerli; ad, üniversite ve bölüm profille eşleşiyor." : `Ret: ${reason.trim()}`,
      confirmLabel: approve ? "Onayla" : "Reddet",
    });
    if (!why) return;
    setBusy(approve ? "approve" : "reject");
    setError(null);
    try {
      await withReason(why, () => adminApi.reviewVerification(client, item.verification_id, approve, approve ? null : reason.trim()));
      onDone();
    } catch (e) {
      console.error(e);
      setError(errorMessage(e));
      setBusy(null);
    }
  }

  return (
    <Card>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-semibold">{item.full_name ?? "—"}</p>
          <p className="text-sm text-ink-2">
            {item.username ? `@${item.username} · ` : ""}{item.email}
          </p>
          <p className="mt-1 text-sm text-ink-2">
            {item.university_name ?? "Üniversite seçilmemiş"}
            {item.department ? ` · ${item.department}` : ""}
          </p>
        </div>
        <span className="text-xs text-ink-3">Gönderildi: {formatDateTime(item.submitted_at)}</span>
      </div>

      <div className="mt-4">
        {url ? (
          <div className="space-y-2">
            <iframe
              src={url}
              title={`${item.full_name ?? item.email} öğrenci belgesi`}
              className="h-[70vh] min-h-96 w-full rounded-lg border border-line bg-surface-2"
            />
            <a
              href={url}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 text-sm font-medium text-brand hover:underline"
            >
              <ExternalLink className="size-4" aria-hidden /> Yeni sekmede aç (bağlantı 5 dakika geçerli)
            </a>
          </div>
        ) : (
          <Button variant="secondary" onClick={open} busy={opening}>
            <FileText className="size-4" aria-hidden /> Belgeyi aç
          </Button>
        )}
      </div>

      {rejecting ? (
        <div className="mt-4 space-y-3 rounded-xl border border-line bg-surface-2 p-4">
          <label className="block">
            <span className="mb-1.5 flex justify-between text-sm font-medium">
              Ret gerekçesi (öğrenci görür)
              <span className="font-normal tabular-nums text-ink-3">{reason.length}/{MAX_REASON}</span>
            </span>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={MAX_REASON}
              rows={3}
              className={`${inputClass} resize-y`}
              placeholder="Örn. Belge okunmuyor, lütfen güncel ve net bir öğrenci belgesi yükle."
            />
          </label>
          <div className="flex flex-wrap gap-2">
            <Button variant="danger" disabled={!reasonValid || busy !== null} busy={busy === "reject"} onClick={() => decide(false)}>
              <X className="size-4" aria-hidden /> Reddet
            </Button>
            <Button variant="ghost" disabled={busy !== null} onClick={() => setRejecting(false)}>Vazgeç</Button>
          </div>
        </div>
      ) : (
        <div className="mt-4 flex flex-wrap gap-2">
          <Button disabled={!url || busy !== null} busy={busy === "approve"} onClick={() => decide(true)}>
            <Check className="size-4" aria-hidden /> Onayla
          </Button>
          <Button variant="secondary" disabled={busy !== null} onClick={() => setRejecting(true)}>
            <X className="size-4" aria-hidden /> Reddet…
          </Button>
          {!url ? <span className="self-center text-xs text-ink-3">Onaylamadan önce belgeyi aç.</span> : null}
        </div>
      )}
      {error ? <div className="mt-4"><ErrorBox message={error} /></div> : null}
    </Card>
  );
}
