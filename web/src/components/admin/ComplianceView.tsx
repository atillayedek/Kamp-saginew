"use client";

import type { SupabaseClient } from "@supabase/supabase-js";
import { Download, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { adminApi } from "@/lib/admin-api";
import { withReason } from "@/lib/audit";
import {
  type Breach,
  type ChainCheck,
  complianceApi,
  type DataSubjectRequest,
  type LegalDocument,
  type LogTable,
  toCsv,
} from "@/lib/compliance-api";
import { errorMessage } from "@/lib/errors";
import { formatDateTime, formatNumber, timeLeft } from "@/lib/format";
import { Badge, Button, Card, Empty, ErrorBox, inputClass, Loading, PageHeader, Stat, useAsync, useReason } from "./ui";

type Props = { client: SupabaseClient };

const SECTIONS = [
  { id: "dashboard", label: "Gösterge paneli" },
  { id: "requests", label: "Başvurular" },
  { id: "breaches", label: "İhlal kayıtları" },
  { id: "logs", label: "Kayıtlar (log)" },
  { id: "legal", label: "Hukuki metinler" },
  { id: "retention", label: "İmha" },
  { id: "objections", label: "Eşleşme itirazları" },
  { id: "settings", label: "Ayarlar" },
] as const;

type SectionId = typeof SECTIONS[number]["id"];

export const DSR_TYPE_LABELS: Record<string, string> = {
  access: "Bilgi talebi",
  purpose: "İşleme amacı",
  third_parties: "Aktarılan taraflar",
  rectification: "Düzeltme",
  erasure: "Silme",
  notify_third_parties: "Üçüncü kişilere bildirim",
  objection_automated: "Otomatik analize itiraz",
  compensation: "Zararın giderilmesi",
  other: "Diğer",
};

const DSR_STATUS: Record<DataSubjectRequest["status"], { label: string; tone: "warn" | "brand" | "good" | "bad" }> = {
  RECEIVED: { label: "Alındı", tone: "warn" },
  IN_PROGRESS: { label: "İşlemde", tone: "brand" },
  ANSWERED: { label: "Yanıtlandı", tone: "good" },
  REJECTED: { label: "Reddedildi", tone: "bad" },
};

const LOG_LABELS: Record<LogTable, string> = {
  consent_logs: "Rıza ve aydınlatma",
  access_logs: "Erişim (5651)",
  admin_audit_logs: "Yönetici işlemleri",
  deletion_logs: "İmha",
};

const DOC_LABELS: Record<string, string> = {
  aydinlatma_metni: "Aydınlatma Metni",
  gizlilik_politikasi: "Gizlilik Politikası",
  kullanim_kosullari: "Kullanım Koşulları",
  topluluk_kurallari: "Topluluk Kuralları",
  acik_riza_pazarlama_eposta: "Açık rıza: e-posta kampanyaları",
  acik_riza_pazarlama_bildirim: "Açık rıza: kampanya bildirimleri",
  cerez_sdk_politikasi: "Çerez ve SDK Politikası",
  telif_politikasi: "Telif ve İçerik Kaldırma",
  premium_on_bilgilendirme: "Premium Ön Bilgilendirme",
  mesafeli_sozlesme: "Mesafeli Sözleşme",
  saklama_imha_politikasi: "Saklama ve İmha Politikası",
  basvuru_formu: "Başvuru Formu",
  cocuk_guvenligi: "Çocuk Güvenliği Standartları",
};

function download(name: string, content: string, type: string) {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}

function toIso(local: string): string | null {
  return local ? new Date(local).toISOString() : null;
}

export function ComplianceView({ client }: Props) {
  const [section, setSection] = useState<SectionId>("dashboard");
  return (
    <>
      <PageHeader
        title="KVKK ve Uyum"
        description="Bu bölümdeki her görüntüleme, dışa aktarma ve değişiklik gerekçesiyle değiştirilemez yönetici kayıtlarına yazılır."
      />
      <div className="mb-5 flex flex-wrap gap-2" role="tablist">
        {SECTIONS.map((s) => (
          <Button key={s.id} role="tab" aria-selected={section === s.id} variant={section === s.id ? "primary" : "secondary"} onClick={() => setSection(s.id)}>
            {s.label}
          </Button>
        ))}
      </div>
      {section === "dashboard" ? <Dashboard client={client} onOpen={setSection} /> : null}
      {section === "requests" ? <Requests client={client} /> : null}
      {section === "breaches" ? <Breaches client={client} /> : null}
      {section === "logs" ? <Logs client={client} /> : null}
      {section === "legal" ? <Legal client={client} /> : null}
      {section === "retention" ? <Retention client={client} /> : null}
      {section === "objections" ? <Objections client={client} /> : null}
      {section === "settings" ? <Settings client={client} /> : null}
    </>
  );
}

// Dashboard ---------------------------------------------------------------------------------

function Dashboard({ client, onOpen }: Props & { onOpen: (id: SectionId) => void }) {
  const config = useAsync(() => complianceApi.publicConfig(client), "public-config");
  const requests = useAsync(() => complianceApi.requests(client, null), "dsr-all");
  const breaches = useAsync(() => complianceApi.breaches(client), "breaches");
  const retention = useAsync(() => complianceApi.retentionStatus(client), "retention-status");
  const overview = useAsync(() => adminApi.overview(client), "overview");
  const legal = useAsync(() => complianceApi.legalStats(client), "legal-stats");
  const [now] = useState(() => Date.now());
  const warnDays = Number(config.data?.dsr_warning_days ?? 7);

  const open = (requests.data ?? []).filter((r) => r.status === "RECEIVED" || r.status === "IN_PROGRESS");
  const overdue = open.filter((r) => new Date(r.due_at).getTime() < now);
  const soon = open.filter((r) => {
    const left = new Date(r.due_at).getTime() - now;
    return left >= 0 && left < warnDays * 86_400_000;
  });
  const openBreaches = (breaches.data ?? []).filter((b) => !b.closed_at);

  return (
    <div className="space-y-6">
      {[config, requests, breaches, retention, overview, legal].map((a, i) => (a.error ? <ErrorBox key={i} message={a.error} onRetry={a.reload} /> : null))}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label="Açık veri sahibi başvurusu" value={formatNumber(open.length)}
          hint={overdue.length > 0 ? `${overdue.length} başvurunun süresi geçti` : soon.length > 0 ? `${soon.length} başvuruya ${warnDays} günden az kaldı` : "Süresi yaklaşan yok"}
          tone={overdue.length > 0 ? "bad" : soon.length > 0 ? "warn" : undefined} />
        <Stat label="Açık ihlal kaydı" value={formatNumber(openBreaches.length)}
          hint={openBreaches.find((b) => !b.reported_to_board_at) ? "Kurul bildirimi bekleyen var" : "—"}
          tone={openBreaches.find((b) => !b.reported_to_board_at) ? "bad" : undefined} />
        <Stat label="Onay bekleyen belge" value={overview.data ? formatNumber(overview.data.pending_verifications) : "…"} />
        <Stat label="İmhayı bekleyen belge" value={retention.data ? formatNumber(retention.data.documents_awaiting_destruction) : "…"}
          hint={`Karardan sonra en geç ${String(config.data?.document_retention_days ?? "…")} gün`} />
      </div>

      {openBreaches.length > 0 ? (
        <Card title="Kurul bildirimi sayacı (72 saat)">
          <ul className="space-y-2 text-sm">
            {openBreaches.map((b) => {
              const left = timeLeft(b.board_deadline, now);
              return (
                <li key={b.id} className="flex flex-wrap items-center gap-2">
                  {b.reported_to_board_at ? <Badge tone="good">Bildirildi</Badge> : <Badge tone={left.overdue ? "bad" : "warn"}>{left.label}</Badge>}
                  <span className="truncate">{b.description}</span>
                </li>
              );
            })}
          </ul>
        </Card>
      ) : null}

      {[...overdue, ...soon].length > 0 ? (
        <Card title="Süresi yaklaşan / geçen başvurular" action={<Button variant="ghost" onClick={() => onOpen("requests")}>Tümü</Button>}>
          <ul className="space-y-2 text-sm">
            {[...overdue, ...soon].map((r) => {
              const left = timeLeft(r.due_at, now);
              return (
                <li key={r.id} className="flex flex-wrap items-center gap-2">
                  <Badge tone={left.overdue ? "bad" : "warn"}>{left.label}</Badge>
                  <span className="font-medium">{r.request_no}</span>
                  <span className="text-ink-2">{DSR_TYPE_LABELS[r.type] ?? r.type}</span>
                </li>
              );
            })}
          </ul>
        </Card>
      ) : null}

      <Card title="Aktif hukuki metinler ve kabul oranları" action={<Button variant="ghost" onClick={() => onOpen("legal")}>Yönet</Button>}>
        {legal.loading && !legal.data ? <Loading /> : null}
        <div className="scroll overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-left text-ink-3"><tr><th className="py-1">Metin</th><th>Sürüm</th><th>Yayın</th><th>Onay / bilgilendirme</th></tr></thead>
            <tbody>
              {(legal.data ?? []).map((d) => (
                <tr key={d.doc_type} className="border-t border-line">
                  <td className="py-1.5">{DOC_LABELS[d.doc_type] ?? d.doc_type}</td>
                  <td>v{d.version}</td>
                  <td>{formatDateTime(d.published_at)}</td>
                  <td>{d.kind === "notice" || d.kind === "agreement" || d.kind === "consent"
                    ? `${formatNumber(d.acknowledged)} / ${formatNumber(d.users)} (%${d.users ? Math.round((100 * d.acknowledged) / d.users) : 0})`
                    : `${formatNumber(d.acknowledged)} satın almada`}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <ChainCard client={client} />
    </div>
  );
}

function ChainCard({ client }: Props) {
  const ask = useReason();
  const [result, setResult] = useState<ChainCheck[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function verify() {
    const reason = await ask("Hash zincirini doğrula", { initial: "Periyodik bütünlük kontrolü", confirmLabel: "Doğrula" });
    if (!reason) return;
    setBusy(true);
    setError(null);
    try {
      setResult(await withReason(reason, () => complianceApi.verifyChains(client)));
    } catch (e) {
      console.error(e);
      setError(errorMessage(e));
    }
    setBusy(false);
  }

  return (
    <Card title="Kayıt bütünlüğü (SHA-256 zinciri)" action={<Button onClick={verify} busy={busy}><ShieldCheck className="size-4" aria-hidden /> Doğrula</Button>}>
      <p className="text-sm text-ink-2">Her kayıt bir öncekinin özetini taşır. Sonradan değiştirilen ya da araya eklenen bir kayıt zinciri bozar.</p>
      {error ? <div className="mt-3"><ErrorBox message={error} /></div> : null}
      {result ? (
        <ul className="mt-3 space-y-1 text-sm">
          {result.map((r) => (
            <li key={r.table_name} className="flex flex-wrap items-center gap-2">
              {r.ok && r.head_matches ? <Badge tone="good">Sağlam</Badge> : <Badge tone="bad">BOZULMUŞ</Badge>}
              <span className="font-medium">{LOG_LABELS[r.table_name]}</span>
              <span className="text-ink-3">{formatNumber(r.rows_checked)} kayıt</span>
              {r.first_bad_seq ? <span className="text-bad">ilk bozuk kayıt #{r.first_bad_seq}</span> : null}
              {!r.head_matches ? <span className="text-bad">son kayıt eksik/silinmiş</span> : null}
            </li>
          ))}
        </ul>
      ) : null}
    </Card>
  );
}

// Data subject requests ---------------------------------------------------------------------

function Requests({ client }: Props) {
  const ask = useReason();
  const [status, setStatus] = useState<string>("");
  const list = useAsync(() => complianceApi.requests(client, status || null), `dsr-${status}`);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [now] = useState(() => Date.now());
  const [form, setForm] = useState({ type: "access", details: "", channel: "email", email: "", receivedAt: "" });

  async function update(r: DataSubjectRequest, next: "IN_PROGRESS" | "ANSWERED" | "REJECTED") {
    const summary = answers[r.id]?.trim() ?? "";
    if (next !== "IN_PROGRESS" && summary.length < 3) {
      setError("Yanıt özeti yaz (başvurana gösterilir).");
      return;
    }
    const reason = await ask(`${r.request_no}: ${next === "IN_PROGRESS" ? "işleme al" : next === "ANSWERED" ? "yanıtla" : "reddet"}`, {
      initial: next === "IN_PROGRESS" ? "Başvuru incelemeye alındı" : summary,
      confirmLabel: "Kaydet",
    });
    if (!reason) return;
    setError(null);
    try {
      await withReason(reason, () => complianceApi.updateRequest(client, r.id, next, next === "IN_PROGRESS" ? null : summary));
      list.reload();
    } catch (e) {
      console.error(e);
      setError(errorMessage(e));
    }
  }

  async function record(e: React.FormEvent) {
    e.preventDefault();
    const reason = await ask("Kanal dışından gelen başvuruyu kaydet", { initial: `${form.channel} ile gelen başvuru`, confirmLabel: "Kaydet" });
    if (!reason) return;
    setError(null);
    try {
      await withReason(reason, () => complianceApi.recordRequest(client, form.type, form.details, form.channel, form.email, toIso(form.receivedAt)));
      setForm({ ...form, details: "", email: "", receivedAt: "" });
      list.reload();
    } catch (err) {
      console.error(err);
      setError(errorMessage(err));
    }
  }

  return (
    <div className="space-y-6">
      {error ? <ErrorBox message={error} /> : null}
      <div className="flex flex-wrap items-center gap-3">
        <select className={`${inputClass} w-auto`} value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Duruma göre süz">
          <option value="">Tümü</option>
          {Object.entries(DSR_STATUS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
        </select>
        <Button variant="secondary" onClick={list.reload} busy={list.loading}>Yenile</Button>
      </div>
      {list.error ? <ErrorBox message={list.error} onRetry={list.reload} /> : null}
      {list.loading && !list.data ? <Loading /> : null}
      {list.data && list.data.length === 0 ? <Card><Empty>Başvuru yok.</Empty></Card> : null}
      {(list.data ?? []).map((r) => {
        const open = r.status === "RECEIVED" || r.status === "IN_PROGRESS";
        const left = timeLeft(r.due_at, now);
        return (
          <Card key={r.id}>
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-semibold">{r.request_no}</span>
              <Badge tone={DSR_STATUS[r.status].tone}>{DSR_STATUS[r.status].label}</Badge>
              <Badge>{DSR_TYPE_LABELS[r.type] ?? r.type}</Badge>
              {open ? <Badge tone={left.overdue ? "bad" : "warn"}>{left.label}</Badge> : null}
              <span className="text-xs text-ink-3">
                {formatDateTime(r.received_at)} · {r.channel} · {r.username ? `@${r.username}` : r.requester_email ?? "—"}
              </span>
            </div>
            <p className="mt-3 text-sm whitespace-pre-wrap">{r.details}</p>
            {r.response_summary ? <p className="mt-2 text-sm text-ink-2">Yanıt: {r.response_summary}</p> : null}
            {open ? (
              <div className="mt-4 space-y-2">
                <textarea
                  rows={3}
                  className={`${inputClass} resize-y`}
                  placeholder="Yanıt özeti (başvurana uygulamada gösterilir)"
                  value={answers[r.id] ?? ""}
                  onChange={(e) => setAnswers((a) => ({ ...a, [r.id]: e.target.value }))}
                />
                <div className="flex flex-wrap gap-2">
                  {r.status === "RECEIVED" ? <Button variant="secondary" onClick={() => update(r, "IN_PROGRESS")}>İşleme al</Button> : null}
                  <Button onClick={() => update(r, "ANSWERED")}>Yanıtla</Button>
                  <Button variant="danger" onClick={() => update(r, "REJECTED")}>Reddet</Button>
                </div>
              </div>
            ) : null}
          </Card>
        );
      })}

      <Card title="E-posta, KEP veya posta ile gelen başvuruyu kaydet">
        <form className="grid gap-3 sm:grid-cols-2" onSubmit={record}>
          <select className={inputClass} value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })} aria-label="Talep türü">
            {Object.entries(DSR_TYPE_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
          <select className={inputClass} value={form.channel} onChange={(e) => setForm({ ...form, channel: e.target.value })} aria-label="Kanal">
            <option value="email">E-posta</option>
            <option value="kep">KEP</option>
            <option value="mail">Posta / elden</option>
          </select>
          <input className={inputClass} type="email" required placeholder="Başvuranın e-postası" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          <label className="text-sm text-ink-2">
            Geliş zamanı (boşsa şimdi)
            <input className={inputClass} type="datetime-local" value={form.receivedAt} onChange={(e) => setForm({ ...form, receivedAt: e.target.value })} />
          </label>
          <textarea className={`${inputClass} sm:col-span-2`} rows={3} required minLength={10} placeholder="Talep" value={form.details} onChange={(e) => setForm({ ...form, details: e.target.value })} />
          <div className="sm:col-span-2"><Button type="submit">Kaydet (30 gün sayacı başlar)</Button></div>
        </form>
      </Card>
    </div>
  );
}

// Breaches -------------------------------------------------------------------------------------

const EMPTY_BREACH = {
  id: null as string | null,
  detected_at: "",
  description: "",
  categories: "",
  affected: "",
  measures: "",
  reported: "",
  notified: "",
  closed: false,
};

function toLocal(iso: string | null): string {
  if (!iso) return "";
  const d = new Date(iso);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

function Breaches({ client }: Props) {
  const ask = useReason();
  const list = useAsync(() => complianceApi.breaches(client), "breaches");
  const [form, setForm] = useState(EMPTY_BREACH);
  const [error, setError] = useState<string | null>(null);
  const [now] = useState(() => Date.now());

  function edit(b: Breach) {
    setForm({
      id: b.id,
      detected_at: toLocal(b.detected_at),
      description: b.description,
      categories: b.affected_data_categories.join(", "),
      affected: b.affected_user_count?.toString() ?? "",
      measures: b.measures_taken ?? "",
      reported: toLocal(b.reported_to_board_at),
      notified: toLocal(b.users_notified_at),
      closed: b.closed_at !== null,
    });
    window.scrollTo({ top: document.body.scrollHeight, behavior: "smooth" });
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    const reason = await ask(form.id ? "İhlal kaydını güncelle" : "İhlal kaydı aç", { confirmLabel: "Kaydet" });
    if (!reason) return;
    setError(null);
    try {
      await withReason(reason, () => complianceApi.saveBreach(client, {
        id: form.id,
        detected_at: toIso(form.detected_at)!,
        description: form.description,
        affected_data_categories: form.categories.split(",").map((c) => c.trim()).filter(Boolean),
        affected_user_count: form.affected ? Number(form.affected) : null,
        measures_taken: form.measures || null,
        reported_to_board_at: toIso(form.reported),
        users_notified_at: toIso(form.notified),
        closed: form.closed,
      }));
      setForm(EMPTY_BREACH);
      list.reload();
    } catch (err) {
      console.error(err);
      setError(errorMessage(err));
    }
  }

  return (
    <div className="space-y-6">
      {error ? <ErrorBox message={error} /> : null}
      {list.error ? <ErrorBox message={list.error} onRetry={list.reload} /> : null}
      {list.loading && !list.data ? <Loading /> : null}
      {list.data && list.data.length === 0 ? <Card><Empty>İhlal kaydı yok.</Empty></Card> : null}
      {(list.data ?? []).map((b) => {
        const left = timeLeft(b.board_deadline, now);
        return (
          <Card key={b.id}>
            <div className="flex flex-wrap items-center gap-2">
              {b.closed_at ? <Badge>Kapatıldı</Badge> : <Badge tone="bad">Açık</Badge>}
              {b.reported_to_board_at
                ? <Badge tone="good">Kurul&apos;a bildirildi {formatDateTime(b.reported_to_board_at)}</Badge>
                : <Badge tone={left.overdue ? "bad" : "warn"}>Kurul bildirimi: {left.label}</Badge>}
              {b.users_notified_at ? <Badge tone="good">Kişiler bilgilendirildi</Badge> : null}
              <span className="text-xs text-ink-3">Tespit: {formatDateTime(b.detected_at)}</span>
            </div>
            <p className="mt-3 text-sm whitespace-pre-wrap">{b.description}</p>
            <p className="mt-2 text-sm text-ink-2">
              Kategoriler: {b.affected_data_categories.join(", ") || "—"} · Etkilenen: {b.affected_user_count ?? "bilinmiyor"}
            </p>
            {b.measures_taken ? <p className="mt-1 text-sm text-ink-2 whitespace-pre-wrap">Tedbirler: {b.measures_taken}</p> : null}
            <Button className="mt-3" variant="secondary" onClick={() => edit(b)}>Düzenle</Button>
          </Card>
        );
      })}
      <Card title={form.id ? "İhlal kaydını güncelle" : "Yeni ihlal kaydı"}>
        <form className="grid gap-3 sm:grid-cols-2" onSubmit={save}>
          <label className="text-sm text-ink-2">Tespit zamanı
            <input required className={inputClass} type="datetime-local" value={form.detected_at} onChange={(e) => setForm({ ...form, detected_at: e.target.value })} />
          </label>
          <label className="text-sm text-ink-2">Etkilenen kişi sayısı
            <input className={inputClass} type="number" min={0} value={form.affected} onChange={(e) => setForm({ ...form, affected: e.target.value })} />
          </label>
          <textarea required minLength={10} rows={3} className={`${inputClass} sm:col-span-2`} placeholder="Ne oldu?" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          <input className={`${inputClass} sm:col-span-2`} placeholder="Etkilenen veri kategorileri (virgülle)" value={form.categories} onChange={(e) => setForm({ ...form, categories: e.target.value })} />
          <textarea rows={2} className={`${inputClass} sm:col-span-2`} placeholder="Alınan tedbirler" value={form.measures} onChange={(e) => setForm({ ...form, measures: e.target.value })} />
          <label className="text-sm text-ink-2">Kurul&apos;a bildirim zamanı
            <input className={inputClass} type="datetime-local" value={form.reported} onChange={(e) => setForm({ ...form, reported: e.target.value })} />
          </label>
          <label className="text-sm text-ink-2">Kişilere bildirim zamanı
            <input className={inputClass} type="datetime-local" value={form.notified} onChange={(e) => setForm({ ...form, notified: e.target.value })} />
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={form.closed} onChange={(e) => setForm({ ...form, closed: e.target.checked })} /> Kapatıldı
          </label>
          <div className="flex gap-2 sm:col-span-2">
            <Button type="submit">Kaydet</Button>
            {form.id ? <Button type="button" variant="ghost" onClick={() => setForm(EMPTY_BREACH)}>Vazgeç</Button> : null}
          </div>
        </form>
      </Card>
    </div>
  );
}

// Logs --------------------------------------------------------------------------------------------

function Logs({ client }: Props) {
  const ask = useReason();
  const [table, setTable] = useState<LogTable>("consent_logs");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [userId, setUserId] = useState("");
  const [action, setAction] = useState("");
  const [rows, setRows] = useState<Record<string, unknown>[] | null>(null);
  const [selected, setSelected] = useState<Record<string, unknown> | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const uuidOk = !userId || /^[0-9a-f-]{36}$/i.test(userId.trim());

  async function load(e: React.FormEvent) {
    e.preventDefault();
    const reason = await ask(`${LOG_LABELS[table]} kayıtlarını görüntüle`, { confirmLabel: "Görüntüle" });
    if (!reason) return;
    setBusy(true);
    setError(null);
    try {
      setRows(await withReason(reason, () =>
        complianceApi.logs(client, table, toIso(from), toIso(to), userId.trim() || null, action.trim() || null)));
      setSelected(null);
    } catch (err) {
      console.error(err);
      setError(errorMessage(err));
    }
    setBusy(false);
  }

  async function exportRows(format: "csv" | "json") {
    if (!rows) return;
    const reason = await ask(`${rows.length} kaydı ${format.toUpperCase()} olarak dışa aktar`, { confirmLabel: "Dışa aktar" });
    if (!reason) return;
    try {
      await withReason(reason, () => complianceApi.recordAction(client, `export.${table}`, table, null, {
        rows: rows.length,
        format,
        from: toIso(from),
        to: toIso(to),
        user_id: userId.trim() || null,
        action: action.trim() || null,
      }));
      const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, "-");
      if (format === "csv") download(`${table}-${stamp}.csv`, toCsv(rows), "text/csv;charset=utf-8");
      else download(`${table}-${stamp}.json`, JSON.stringify(rows, null, 2), "application/json");
    } catch (err) {
      console.error(err);
      setError(errorMessage(err));
    }
  }

  const columns = rows && rows.length > 0 ? Object.keys(rows[0]).filter((k) => !["prev_hash", "hash", "details"].includes(k)).slice(0, 8) : [];

  return (
    <div className="space-y-4">
      <Card>
        <form className="grid gap-3 sm:grid-cols-2 lg:grid-cols-6" onSubmit={load}>
          <select className={inputClass} value={table} onChange={(e) => { setTable(e.target.value as LogTable); setRows(null); }} aria-label="Kayıt türü">
            {(Object.keys(LOG_LABELS) as LogTable[]).map((t) => <option key={t} value={t}>{LOG_LABELS[t]}</option>)}
          </select>
          <input className={inputClass} type="datetime-local" value={from} onChange={(e) => setFrom(e.target.value)} aria-label="Başlangıç" />
          <input className={inputClass} type="datetime-local" value={to} onChange={(e) => setTo(e.target.value)} aria-label="Bitiş" />
          <input className={inputClass} placeholder="Kullanıcı kimliği (uid)" value={userId} onChange={(e) => setUserId(e.target.value)} />
          <input className={inputClass} placeholder="İşlem türü (ör. login, accepted)" value={action} onChange={(e) => setAction(e.target.value)} />
          <Button type="submit" busy={busy} disabled={!uuidOk}>Getir</Button>
        </form>
      </Card>
      {error ? <ErrorBox message={error} /> : null}
      {rows ? (
        <Card
          title={`${formatNumber(rows.length)} kayıt (en yeni üstte, en fazla 500)`}
          action={rows.length > 0 ? (
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => exportRows("csv")}><Download className="size-4" aria-hidden /> CSV</Button>
              <Button variant="secondary" onClick={() => exportRows("json")}><Download className="size-4" aria-hidden /> JSON</Button>
            </div>
          ) : null}
        >
          {rows.length === 0 ? <Empty>Kayıt yok.</Empty> : (
            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead className="text-left text-ink-3"><tr>{columns.map((c) => <th key={c} className="px-2 py-1">{c}</th>)}</tr></thead>
                <tbody>
                  {rows.map((r) => (
                    <tr key={String(r.seq)} className="cursor-pointer border-t border-line hover:bg-surface-2" onClick={() => setSelected(r)}>
                      {columns.map((c) => <td key={c} className="max-w-56 truncate px-2 py-1">{r[c] === null ? "—" : String(r[c])}</td>)}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      ) : null}
      {selected ? (
        <Card title={`Kayıt #${String(selected.seq)}`} action={<Button variant="ghost" onClick={() => setSelected(null)}>Kapat</Button>}>
          <pre className="overflow-x-auto text-xs whitespace-pre-wrap break-all">{JSON.stringify(selected, null, 2)}</pre>
        </Card>
      ) : null}
    </div>
  );
}

// Legal texts --------------------------------------------------------------------------------------

function Legal({ client }: Props) {
  const ask = useReason();
  const stats = useAsync(() => complianceApi.legalStats(client), "legal-stats");
  const [doc, setDoc] = useState<LegalDocument | null>(null);
  const [draft, setDraft] = useState<{ title: string; content: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);

  async function open(type: string) {
    setError(null);
    setNote(null);
    try {
      const loaded = await complianceApi.legalDocument(client, type);
      setDoc(loaded);
      setDraft(null);
    } catch (e) {
      console.error(e);
      setError(errorMessage(e));
    }
  }

  async function publish() {
    if (!doc || !draft) return;
    const reason = await ask(`${DOC_LABELS[doc.doc_type] ?? doc.doc_type}: yeni sürümü yayınla`, {
      hint: "Gerekçe (zorunlu), ör. avukat onay tarihi. Eski sürüm silinmez; zorunlu metinler kullanıcılara bir sonraki girişte yeniden onaylatılır.",
      confirmLabel: "Yayınla",
    });
    if (!reason) return;
    setError(null);
    try {
      const version = await withReason(reason, () => complianceApi.publishLegal(client, doc.doc_type, draft.title, draft.content));
      setNote(`Sürüm ${version} yayınlandı.`);
      await open(doc.doc_type);
      stats.reload();
    } catch (e) {
      console.error(e);
      setError(errorMessage(e));
    }
  }

  return (
    <div className="space-y-4">
      {error ? <ErrorBox message={error} /> : null}
      {note ? <p className="rounded-lg bg-good-soft px-4 py-3 text-sm text-good">{note}</p> : null}
      <Card title="Metinler">
        {stats.error ? <ErrorBox message={stats.error} onRetry={stats.reload} /> : null}
        <ul className="divide-y divide-line text-sm">
          {(stats.data ?? []).map((d) => (
            <li key={d.doc_type} className="flex flex-wrap items-center justify-between gap-2 py-2">
              <span>{DOC_LABELS[d.doc_type] ?? d.doc_type} <span className="text-ink-3">v{d.version} · {formatDateTime(d.published_at)}</span></span>
              <Button variant="secondary" onClick={() => open(d.doc_type)}>Aç</Button>
            </li>
          ))}
        </ul>
      </Card>
      {doc ? (
        <Card title={`${doc.title} — v${doc.version}`} action={draft ? null : <Button onClick={() => setDraft({ title: doc.title, content: doc.content })}>Yeni sürüm hazırla</Button>}>
          <p className="mb-3 break-all text-xs text-ink-3">SHA-256: {doc.content_sha256}</p>
          {draft ? (
            <div className="space-y-3">
              <input className={inputClass} value={draft.title} onChange={(e) => setDraft({ ...draft, title: e.target.value })} aria-label="Başlık" />
              <textarea className={`${inputClass} font-mono text-xs`} rows={24} value={draft.content} onChange={(e) => setDraft({ ...draft, content: e.target.value })} aria-label="Metin (Markdown)" />
              <div className="flex gap-2">
                <Button onClick={publish} disabled={draft.content === doc.content && draft.title === doc.title}>Yayınla</Button>
                <Button variant="ghost" onClick={() => setDraft(null)}>Vazgeç</Button>
              </div>
            </div>
          ) : (
            <pre className="max-h-[60vh] overflow-auto text-xs whitespace-pre-wrap">{doc.content}</pre>
          )}
        </Card>
      ) : null}
    </div>
  );
}

// Retention -----------------------------------------------------------------------------------------

function Retention({ client }: Props) {
  const ask = useReason();
  const status = useAsync(() => complianceApi.retentionStatus(client), "retention-status");
  const report = useAsync(() => complianceApi.retentionReport(client, 90), "retention-report");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const s = status.data;
  const ready = s && s.pg_cron_installed && s.pg_net_installed && s.functions_url_set;

  async function setup() {
    const reason = await ask("Otomatik imhayı etkinleştir", { initial: "İlk kurulum" });
    if (!reason) return;
    setBusy(true);
    setError(null);
    try {
      await withReason(reason, () => adminApi.pushSetup(client));
      status.reload();
    } catch (e) {
      console.error(e);
      setError(errorMessage(e));
    }
    setBusy(false);
  }

  return (
    <div className="space-y-4">
      {error ? <ErrorBox message={error} /> : null}
      <Card title="Otomatik imha (her gün 03:17 UTC)">
        {status.error ? <ErrorBox message={status.error} onRetry={status.reload} /> : null}
        {s ? (
          <div className="space-y-3 text-sm">
            <div className="flex flex-wrap gap-2">
              <Badge tone={s.pg_cron_installed ? "good" : "bad"}>pg_cron {s.pg_cron_installed ? "var" : "yok"}</Badge>
              <Badge tone={s.pg_net_installed ? "good" : "bad"}>pg_net {s.pg_net_installed ? "var" : "yok"}</Badge>
              <Badge tone={s.functions_url_set ? "good" : "warn"}>{s.functions_url_set ? "Fonksiyon adresi kayıtlı" : "Fonksiyon adresi yok"}</Badge>
            </div>
            {!s.pg_cron_installed ? <p className="text-ink-2">Supabase → Database → Extensions → pg_cron&apos;u aç; ardından migration&apos;daki zamanlama bir sonraki dağıtımda kurulur.</p> : null}
            {!s.functions_url_set ? <Button onClick={setup} busy={busy}>Etkinleştir</Button> : null}
            {s.last_run ? (
              <p className={s.last_run.error ? "text-bad" : "text-ink-2"}>
                Son çalışma: {formatDateTime(s.last_run.finished_at ?? s.last_run.started_at)}
                {s.last_run.error ? ` — hata: ${s.last_run.error}` : " — başarılı"}
              </p>
            ) : <p className="text-ink-3">{ready ? "Henüz çalışmadı." : "Kurulum tamamlanınca her gün çalışır."}</p>}
          </div>
        ) : null}
      </Card>
      <Card title="Periyodik imha raporu (son 90 gün)">
        {report.error ? <ErrorBox message={report.error} onRetry={report.reload} /> : null}
        {report.data && report.data.length === 0 ? <Empty>Bu dönemde imha yok.</Empty> : null}
        {report.data && report.data.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="text-left text-ink-3"><tr><th className="py-1">Gün</th><th>Veri</th><th>Gerekçe</th><th>Yöntem</th><th>Adet</th></tr></thead>
              <tbody>
                {report.data.map((r, i) => (
                  <tr key={i} className="border-t border-line">
                    <td className="py-1">{r.day}</td><td>{r.data_category}</td><td>{r.reason}</td><td>{r.method}</td><td>{formatNumber(r.item_count)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </Card>
    </div>
  );
}

function Objections({ client }: Props) {
  const list = useAsync(() => complianceApi.matchObjections(client), "match-objections");
  return (
    <Card title="Otomatik eşleşmeye itirazlar (KVKK md.11/1-g)">
      {list.error ? <ErrorBox message={list.error} onRetry={list.reload} /> : null}
      {list.loading && !list.data ? <Loading /> : null}
      {list.data && list.data.length === 0 ? <Empty>İtiraz yok.</Empty> : null}
      <ul className="divide-y divide-line text-sm">
        {(list.data ?? []).map((o, i) => (
          <li key={i} className="py-2">
            <span className="font-medium">@{o.username}</span>: “{o.requirement_title}” ↔ “{o.matched_title}”
            <span className="text-ink-3"> · {formatDateTime(o.created_at)}</span>
            {o.reason ? <p className="text-ink-2">{o.reason}</p> : null}
          </li>
        ))}
      </ul>
    </Card>
  );
}

// Settings -------------------------------------------------------------------------------------------

function Settings({ client }: Props) {
  const ask = useReason();
  const list = useAsync(() => complianceApi.settings(client), "compliance-settings");
  const [values, setValues] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);

  async function save(key: string, current: unknown) {
    const raw = values[key];
    if (raw === undefined) return;
    let value: unknown;
    if (typeof current === "number") value = Number(raw);
    else if (typeof current === "boolean") value = raw === "true";
    else value = raw;
    if (typeof current === "number" && (!Number.isInteger(value) || (value as number) < 0)) {
      setError("Geçerli bir tam sayı gir.");
      return;
    }
    const reason = await ask(`${key} ayarını değiştir`, {
      hint: "Gerekçe (zorunlu), ör. avukat görüşü. Süre değişirse ilgili hukuki metnin yeni sürümünü de yayınla.",
      confirmLabel: "Kaydet",
    });
    if (!reason) return;
    setError(null);
    try {
      await withReason(reason, () => complianceApi.setSetting(client, key, value));
      setValues((v) => {
        const next = { ...v };
        delete next[key];
        return next;
      });
      list.reload();
    } catch (e) {
      console.error(e);
      setError(errorMessage(e));
    }
  }

  return (
    <Card title="Yasal süreler ve sınırlar (tek kaynak)">
      {error ? <div className="mb-3"><ErrorBox message={error} /></div> : null}
      {list.error ? <ErrorBox message={list.error} onRetry={list.reload} /> : null}
      {list.loading && !list.data ? <Loading /> : null}
      <ul className="divide-y divide-line">
        {(list.data ?? []).map((s) => (
          <li key={s.key} className="grid gap-2 py-3 sm:grid-cols-[1fr_280px_auto] sm:items-center">
            <div>
              <p className="font-mono text-xs">{s.key}</p>
              <p className="text-sm text-ink-2">{s.description}</p>
            </div>
            {typeof s.value === "boolean" ? (
              <select className={inputClass} value={values[s.key] ?? String(s.value)} onChange={(e) => setValues((v) => ({ ...v, [s.key]: e.target.value }))}>
                <option value="true">Açık</option>
                <option value="false">Kapalı</option>
              </select>
            ) : typeof s.value === "number" ? (
              <input className={inputClass} type="number" min={0} value={values[s.key] ?? String(s.value)} onChange={(e) => setValues((v) => ({ ...v, [s.key]: e.target.value }))} />
            ) : (
              <textarea className={inputClass} rows={3} value={values[s.key] ?? String(s.value)} onChange={(e) => setValues((v) => ({ ...v, [s.key]: e.target.value }))} />
            )}
            <Button variant="secondary" disabled={values[s.key] === undefined} onClick={() => save(s.key, s.value)}>Kaydet</Button>
          </li>
        ))}
      </ul>
    </Card>
  );
}
