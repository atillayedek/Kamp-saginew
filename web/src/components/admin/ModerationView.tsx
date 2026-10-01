"use client";

import type { SupabaseClient } from "@supabase/supabase-js";
import { AlertTriangle, MessagesSquare } from "lucide-react";
import { useState } from "react";
import { adminApi, type Report, type ReportAction } from "@/lib/admin-api";
import { withReason } from "@/lib/audit";
import { complianceApi, type ContextMessage, type CourseNoteHit } from "@/lib/compliance-api";
import { errorMessage } from "@/lib/errors";
import {
  formatDateTime,
  formatNumber,
  REPORT_REASON_LABELS,
  REPORT_TARGET_LABELS,
  STATUS_LABELS,
  timeLeft,
  URGENT_REPORT_REASONS,
} from "@/lib/format";
import { Badge, Button, Card, Empty, ErrorBox, inputClass, Loading, PageHeader, statusTone, useAsync, useReason } from "./ui";

type Props = { client: SupabaseClient };

const ACTION_LABELS: Record<ReportAction, string> = {
  DISMISS: "Reddet (sorun yok)",
  REMOVE_CONTENT: "İçeriği kaldır",
  SUSPEND_USER: "Kullanıcıyı askıya al",
};

const SECTIONS = [
  { id: "reports", label: "Şikâyetler" },
  { id: "appeals", label: "İtirazlar" },
  { id: "copyright", label: "Telif bildirimleri" },
] as const;

/** Reports (urgent ones first, with their deadline), appeals against decisions and copyright notices. */
export function ModerationView({ client }: Props) {
  const [section, setSection] = useState<typeof SECTIONS[number]["id"]>("reports");
  return (
    <>
      <PageHeader
        title="Moderasyon"
        description="Kişilik hakkı ihlali ve kişisel veri ifşası şikâyetleri acildir ve üstte gösterilir. Her karar gerekçesiyle kayıt altına alınır; içerik sahibine bildirilir ve itiraz edebilir."
      />
      <div className="mb-5 flex flex-wrap gap-2" role="tablist">
        {SECTIONS.map((s) => (
          <Button key={s.id} role="tab" aria-selected={section === s.id} variant={section === s.id ? "primary" : "secondary"} onClick={() => setSection(s.id)}>
            {s.label}
          </Button>
        ))}
      </div>
      {section === "reports" ? <Reports client={client} /> : null}
      {section === "appeals" ? <Appeals client={client} /> : null}
      {section === "copyright" ? <Copyright client={client} /> : null}
    </>
  );
}

function urgentFirst(reports: Report[]): Report[] {
  return [...reports].sort((a, b) => {
    const ua = URGENT_REPORT_REASONS.has(a.reason) ? 0 : 1;
    const ub = URGENT_REPORT_REASONS.has(b.reason) ? 0 : 1;
    return ua - ub || a.created_at.localeCompare(b.created_at);
  });
}

function Reports({ client }: Props) {
  const ask = useReason();
  const reports = useAsync(() => adminApi.openReports(client), "reports");
  const config = useAsync(() => complianceApi.publicConfig(client), "public-config");
  const urgentHours = Number(config.data?.urgent_report_hours ?? 24);
  const [busy, setBusy] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [contexts, setContexts] = useState<Record<string, ContextMessage[]>>({});
  const [now] = useState(() => Date.now());

  async function resolve(report: Report, action: ReportAction) {
    const reason = await ask(`${ACTION_LABELS[action]}`, {
      hint: action === "DISMISS"
        ? "Gerekçe (zorunlu). Kayıt altına alınır."
        : "Gerekçe (zorunlu). Kayıt altına alınır; içerik sahibine şikâyet kategorisiyle birlikte bildirilir ve itiraz edebilir.",
      confirmLabel: "Uygula",
    });
    if (!reason) return;
    setBusy(report.report_id);
    setActionError(null);
    try {
      await withReason(reason, () => adminApi.resolveReport(client, report.report_id, action));
      reports.reload();
    } catch (error) {
      console.error(error);
      setActionError(errorMessage(error));
    } finally {
      setBusy(null);
    }
  }

  async function showContext(report: Report) {
    const reason = await ask("Mesaj bağlamını görüntüle", {
      hint: "Özel mesajlar yalnızca şikâyet incelemesi için ve gerekçeyle açılabilir. Bu görüntüleme kayıt altına alınır.",
      confirmLabel: "Görüntüle",
    });
    if (!reason) return;
    setActionError(null);
    try {
      const messages = await withReason(reason, () => complianceApi.reportContext(client, report.report_id));
      setContexts((previous) => ({ ...previous, [report.report_id]: messages }));
    } catch (error) {
      console.error(error);
      setActionError(errorMessage(error));
    }
  }

  return (
    <>
      <div className="mb-4 flex justify-end">
        <Button variant="secondary" onClick={reports.reload} busy={reports.loading}>Yenile</Button>
      </div>
      {actionError ? <div className="mb-4"><ErrorBox message={actionError} /></div> : null}
      {reports.error ? <ErrorBox message={reports.error} onRetry={reports.reload} /> : null}
      {reports.loading && !reports.data ? <Loading /> : null}
      {reports.data && reports.data.length === 0 ? <Card><Empty>Açık şikâyet yok.</Empty></Card> : null}
      <div className="space-y-4">
        {urgentFirst(reports.data ?? []).map((r) => {
          const urgent = URGENT_REPORT_REASONS.has(r.reason);
          const deadline = urgent ? timeLeft(new Date(r.created_at).getTime() + urgentHours * 3_600_000, now) : null;
          const context = contexts[r.report_id];
          return (
            <Card key={r.report_id} className={urgent ? "border-bad/50" : ""}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex flex-wrap items-center gap-2">
                  {urgent ? (
                    <Badge tone="bad"><AlertTriangle className="mr-1 size-3" aria-hidden />Acil</Badge>
                  ) : null}
                  <Badge tone="bad">{REPORT_REASON_LABELS[r.reason] ?? r.reason}</Badge>
                  <Badge>{REPORT_TARGET_LABELS[r.target_kind] ?? r.target_kind}</Badge>
                  {r.report_count > 1 ? <Badge tone="warn">{formatNumber(r.report_count)} şikâyet</Badge> : null}
                  {deadline ? <Badge tone={deadline.overdue ? "bad" : "warn"}>{deadline.label}</Badge> : null}
                </div>
                <span className="text-xs text-ink-3">{formatDateTime(r.created_at)}</span>
              </div>
              {r.target_excerpt ? (
                <blockquote className="mt-4 rounded-lg border-l-4 border-line bg-surface-2 px-4 py-3 text-sm whitespace-pre-wrap">
                  {r.target_excerpt}
                </blockquote>
              ) : null}
              {r.details ? <p className="mt-3 text-sm text-ink-2">Şikâyet notu: {r.details}</p> : null}
              <p className="mt-3 text-sm text-ink-2">
                Şikâyet edilen: <span className="font-medium text-ink">{r.target_full_name ?? "—"}</span>
                {r.target_username ? ` (@${r.target_username})` : ""}{" "}
                <Badge tone={statusTone(r.target_account_status)}>{STATUS_LABELS[r.target_account_status] ?? r.target_account_status}</Badge>
                {r.reporter_username ? <span className="text-ink-3"> · şikâyet eden @{r.reporter_username}</span> : null}
              </p>
              {r.target_kind === "MESSAGE" || r.target_kind === "GROUP_MESSAGE" ? (
                context ? (
                  <div className="mt-4 space-y-1 rounded-lg border border-line p-3 text-sm">
                    {context.length === 0 ? <p className="text-ink-3">Mesaj artık yok; yalnızca şikâyetteki alıntı var.</p> : null}
                    {context.map((m) => (
                      <p key={m.message_id} className={m.is_reported ? "rounded bg-bad-soft px-2 py-1" : "px-2 py-1"}>
                        <span className="font-medium">@{m.sender_username ?? "silinmiş"}</span>{" "}
                        <span className="text-xs text-ink-3">{formatDateTime(m.created_at)}</span>
                        <br />
                        <span className="whitespace-pre-wrap">{m.body ?? "[fotoğraf]"}</span>
                      </p>
                    ))}
                  </div>
                ) : (
                  <Button className="mt-4" variant="ghost" onClick={() => showContext(r)}>
                    <MessagesSquare className="size-4" aria-hidden /> Mesaj bağlamını gör (kayıt altına alınır)
                  </Button>
                )
              ) : null}
              <div className="mt-4 flex flex-wrap gap-2">
                {(Object.keys(ACTION_LABELS) as ReportAction[])
                  .filter((a) => !(a === "REMOVE_CONTENT" && r.target_kind === "USER"))
                  .map((action) => (
                    <Button
                      key={action}
                      variant={action === "DISMISS" ? "secondary" : "danger"}
                      busy={busy === r.report_id}
                      disabled={busy !== null}
                      onClick={() => resolve(r, action)}
                    >
                      {ACTION_LABELS[action]}
                    </Button>
                  ))}
              </div>
            </Card>
          );
        })}
      </div>
    </>
  );
}

function Appeals({ client }: Props) {
  const ask = useReason();
  const appeals = useAsync(() => complianceApi.appeals(client), "appeals");
  const [error, setError] = useState<string | null>(null);

  async function decide(id: string, reverse: boolean) {
    const note = await ask(reverse ? "İtirazı kabul et (karar geri alınır)" : "İtirazı reddet (karar sürer)", {
      hint: "Kişiye gösterilecek açıklama. Gerekçe olarak da kaydedilir.",
      confirmLabel: reverse ? "Kabul et" : "Reddet",
    });
    if (!note) return;
    setError(null);
    try {
      await withReason(note, () => complianceApi.decideAppeal(client, id, reverse, note));
      appeals.reload();
    } catch (e) {
      console.error(e);
      setError(errorMessage(e));
    }
  }

  return (
    <>
      {error ? <div className="mb-4"><ErrorBox message={error} /></div> : null}
      {appeals.error ? <ErrorBox message={appeals.error} onRetry={appeals.reload} /> : null}
      {appeals.loading && !appeals.data ? <Loading /> : null}
      {appeals.data && appeals.data.length === 0 ? <Card><Empty>İtiraz yok.</Empty></Card> : null}
      <div className="space-y-4">
        {appeals.data?.map((a) => (
          <Card key={a.id}>
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone={a.status === "OPEN" ? "warn" : a.status === "REVERSED" ? "good" : "neutral"}>
                {a.status === "OPEN" ? "Bekliyor" : a.status === "REVERSED" ? "Kabul edildi" : "Reddedildi"}
              </Badge>
              <Badge>{REPORT_TARGET_LABELS[a.target_kind] ?? a.target_kind}</Badge>
              <Badge tone="bad">{REPORT_REASON_LABELS[a.reason] ?? a.reason}</Badge>
              <span className="text-xs text-ink-3">@{a.username ?? "—"} · {formatDateTime(a.created_at)}</span>
            </div>
            {a.excerpt ? <blockquote className="mt-3 rounded-lg bg-surface-2 px-3 py-2 text-sm whitespace-pre-wrap">{a.excerpt}</blockquote> : null}
            <p className="mt-3 text-sm whitespace-pre-wrap"><span className="font-medium">İtiraz:</span> {a.body}</p>
            {a.decision_note ? <p className="mt-2 text-sm text-ink-2">Karar: {a.decision_note}</p> : null}
            {a.status === "OPEN" ? (
              <div className="mt-4 flex flex-wrap gap-2">
                <Button onClick={() => decide(a.id, true)}>Kabul et, kararı geri al</Button>
                <Button variant="secondary" onClick={() => decide(a.id, false)}>Reddet</Button>
              </div>
            ) : null}
          </Card>
        ))}
      </div>
    </>
  );
}

function Copyright({ client }: Props) {
  const ask = useReason();
  const notices = useAsync(() => complianceApi.copyrightNotices(client), "copyright");
  const [noteIds, setNoteIds] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);

  async function resolve(id: string, status: "REMOVED" | "REJECTED") {
    const noteId = noteIds[id] ?? null;
    const note = await ask(status === "REMOVED" ? "İçeriği kaldır" : "Bildirimi reddet", {
      hint: "Karar açıklaması (zorunlu). Kaydedilir; içerik kaldırılırsa yükleyene bildirilir.",
      confirmLabel: "Uygula",
    });
    if (!note) return;
    setError(null);
    try {
      await withReason(note, () => complianceApi.resolveCopyright(client, id, status, note, status === "REMOVED" ? noteId : null));
      notices.reload();
    } catch (e) {
      console.error(e);
      setError(errorMessage(e));
    }
  }

  return (
    <>
      {error ? <div className="mb-4"><ErrorBox message={error} /></div> : null}
      {notices.error ? <ErrorBox message={notices.error} onRetry={notices.reload} /> : null}
      {notices.loading && !notices.data ? <Loading /> : null}
      {notices.data && notices.data.length === 0 ? <Card><Empty>Telif bildirimi yok.</Empty></Card> : null}
      <div className="space-y-4">
        {notices.data?.map((n) => (
          <Card key={n.id}>
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone={n.status === "OPEN" ? "warn" : n.status === "REMOVED" ? "bad" : "neutral"}>
                {n.status === "OPEN" ? "Açık" : n.status === "REMOVED" ? "Kaldırıldı" : "Reddedildi"}
              </Badge>
              <span className="text-xs text-ink-3">{formatDateTime(n.created_at)}</span>
            </div>
            <p className="mt-3 text-sm">
              <span className="font-medium">{n.claimant_name}</span>
              {n.organization ? ` (${n.organization})` : ""} · <a className="text-brand hover:underline" href={`mailto:${n.claimant_email}`}>{n.claimant_email}</a>
            </p>
            <p className="mt-2 text-sm whitespace-pre-wrap"><span className="font-medium">Eser:</span> {n.work_description}</p>
            <p className="mt-1 text-sm whitespace-pre-wrap"><span className="font-medium">İçeriğin yeri:</span> {n.content_location}</p>
            {n.resolution_note ? <p className="mt-2 text-sm text-ink-2">Karar: {n.resolution_note}</p> : null}
            {n.status === "OPEN" ? (
              <NotePicker
                client={client}
                selected={noteIds[n.id] ?? null}
                onSelect={(id) => setNoteIds((previous) => ({ ...previous, [n.id]: id }))}
                onRemove={() => resolve(n.id, "REMOVED")}
                onReject={() => resolve(n.id, "REJECTED")}
              />
            ) : null}
          </Card>
        ))}
      </div>
    </>
  );
}

function NotePicker({ client, selected, onSelect, onRemove, onReject }: {
  client: SupabaseClient;
  selected: string | null;
  onSelect: (id: string) => void;
  onRemove: () => void;
  onReject: () => void;
}) {
  const [query, setQuery] = useState("");
  const [hits, setHits] = useState<CourseNoteHit[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function search(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      setHits(await complianceApi.searchNotes(client, query));
    } catch (err) {
      console.error(err);
      setError(errorMessage(err));
    }
  }

  return (
    <div className="mt-4 space-y-3 rounded-lg border border-line p-3">
      <form onSubmit={search} className="flex flex-wrap gap-2">
        <input
          className={`${inputClass} min-w-56 flex-1`}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Ders notu ara: başlık, ders kodu, kullanıcı adı"
        />
        <Button type="submit" variant="secondary" disabled={query.trim().length < 2}>Ara</Button>
      </form>
      {error ? <ErrorBox message={error} /> : null}
      {hits && hits.length === 0 ? <p className="text-sm text-ink-3">Not bulunamadı.</p> : null}
      {hits && hits.length > 0 ? (
        <ul className="space-y-1 text-sm">
          {hits.map((h) => (
            <li key={h.id}>
              <label className="flex items-center gap-2">
                <input type="radio" checked={selected === h.id} onChange={() => onSelect(h.id)} disabled={h.deleted_at !== null} />
                <span>
                  <span className="font-medium">{h.course_code}</span> · {h.title} · @{h.author_username ?? "—"}
                  {h.deleted_at ? <span className="text-ink-3"> (kaldırılmış)</span> : null}
                </span>
              </label>
            </li>
          ))}
        </ul>
      ) : null}
      <div className="flex flex-wrap gap-2">
        <Button variant="danger" disabled={!selected} onClick={onRemove}>Seçili notu kaldır</Button>
        <Button variant="secondary" onClick={onReject}>Bildirimi reddet</Button>
      </div>
    </div>
  );
}
