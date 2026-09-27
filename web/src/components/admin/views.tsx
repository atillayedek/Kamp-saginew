"use client";

import type { SupabaseClient } from "@supabase/supabase-js";
import { ChevronLeft, ChevronRight, Search } from "lucide-react";
import { useMemo, useState } from "react";
import { adminApi, type ReportAction } from "@/lib/admin-api";
import { errorMessage } from "@/lib/errors";
import {
  formatDate,
  formatDateTime,
  formatMoney,
  formatNumber,
  REPORT_REASON_LABELS,
  REPORT_TARGET_LABELS,
  STATUS_LABELS,
} from "@/lib/format";
import { DailyBars } from "./DailyBars";
import {
  Badge,
  Button,
  Card,
  Empty,
  ErrorBox,
  inputClass,
  Loading,
  PageHeader,
  Stat,
  statusTone,
  useAsync,
} from "./ui";

type Props = { client: SupabaseClient };

// Overview --------------------------------------------------------------------------

export function OverviewView({ client, onNavigate }: Props & { onNavigate: (tab: string) => void }) {
  const overview = useAsync(() => adminApi.overview(client), "overview");
  const o = overview.data;

  return (
    <>
      <PageHeader
        title="Genel bakış"
        description="Tüm sayılar canlı veritabanından okunur."
        action={<Button variant="secondary" onClick={overview.reload} busy={overview.loading}>Yenile</Button>}
      />
      {overview.error ? <ErrorBox message={overview.error} onRetry={overview.reload} /> : null}
      {!o && overview.loading ? <Loading /> : null}
      {o ? (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Stat
              label="Toplam kullanıcı"
              value={formatNumber(o.total_users)}
              hint={`Son 7 gün +${formatNumber(o.new_users_7_days)} · son 30 gün +${formatNumber(o.new_users_30_days)}`}
            />
            <Stat
              label="Onaylı öğrenci"
              value={formatNumber(o.status_counts.APPROVED ?? 0)}
              hint={`${formatNumber(o.pending_verifications)} belge incelemede`}
              tone={o.pending_verifications > 0 ? "warn" : undefined}
            />
            <Stat
              label="Aktif premium"
              value={formatNumber(o.active_premium)}
              hint={`Toplam ${formatNumber(o.purchase_count)} doğrulanmış satın alma`}
            />
            <Stat
              label="Açık şikayet"
              value={formatNumber(o.open_reports)}
              hint={o.open_reports > 0 ? "İnceleme bekliyor" : "Bekleyen şikayet yok"}
              tone={o.open_reports > 0 ? "bad" : undefined}
            />
          </div>

          <RevenueCards revenue={o.revenue} withoutPrice={o.purchases_without_price} />

          <div className="grid gap-6 lg:grid-cols-2">
            <Card title="Günlük yeni kayıt">
              <DailyBars label="Yeni kayıt" points={o.daily.map((d) => ({ day: d.day, value: d.signups }))} />
            </Card>
            <Card title="Günlük satın alma">
              <DailyBars label="Satın alma" points={o.daily.map((d) => ({ day: d.day, value: d.purchases }))} />
            </Card>
          </div>

          <div className="grid gap-6 lg:grid-cols-2">
            <Card title="Hesap durumları">
              <ul className="divide-y divide-line">
                {Object.keys(STATUS_LABELS).map((status) => (
                  <li key={status} className="flex items-center justify-between py-2.5 text-sm">
                    <Badge tone={statusTone(status)}>{STATUS_LABELS[status]}</Badge>
                    <span className="tabular-nums font-medium">{formatNumber(o.status_counts[status] ?? 0)}</span>
                  </li>
                ))}
              </ul>
            </Card>
            <Card title="Yapılacaklar">
              <ul className="space-y-3 text-sm">
                <li className="flex items-center justify-between gap-3">
                  <span className="text-ink-2">İnceleme bekleyen belgeler</span>
                  <Button variant="secondary" onClick={() => onNavigate("documents")}>
                    {formatNumber(o.pending_verifications)} belgeyi incele
                  </Button>
                </li>
                <li className="flex items-center justify-between gap-3">
                  <span className="text-ink-2">Açık şikayetler</span>
                  <Button variant="secondary" onClick={() => onNavigate("reports")}>
                    {formatNumber(o.open_reports)} şikayeti incele
                  </Button>
                </li>
                <li className="flex items-center justify-between gap-3">
                  <span className="text-ink-2">Pazarlama e-postasına izin verenler</span>
                  <span className="font-medium tabular-nums">{formatNumber(o.marketing_opt_in)}</span>
                </li>
                <li className="flex items-center justify-between gap-3">
                  <span className="text-ink-2">Toplu e-posta</span>
                  <Button variant="secondary" onClick={() => onNavigate("email")}>E-posta yaz</Button>
                </li>
              </ul>
            </Card>
          </div>
        </div>
      ) : null}
    </>
  );
}

function RevenueCards({ revenue, withoutPrice }: {
  revenue: Record<string, { total_micros: number; last_30_days_micros: number }>;
  withoutPrice: number;
}) {
  const currencies = Object.keys(revenue).sort();
  return (
    <Card title="Kazanılan para (brüt)">
      {currencies.length === 0 ? (
        <Empty>Henüz fiyatı doğrulanmış bir satın alma yok.</Empty>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {currencies.map((currency) => (
            <div key={currency} className="rounded-xl bg-surface-2 p-4">
              <p className="text-xs font-medium text-ink-2">{currency}</p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">{formatMoney(revenue[currency].total_micros, currency)}</p>
              <p className="mt-1 text-xs text-ink-3">
                Son 30 gün: {formatMoney(revenue[currency].last_30_days_micros, currency)}
              </p>
            </div>
          ))}
        </div>
      )}
      <p className="mt-4 text-xs leading-relaxed text-ink-3">
        Google Play&apos;in doğruladığı satın almaların liste fiyatıdır; Google komisyonu ve vergiler düşülmemiştir.
        Kesin tutarlar için Play Console finans raporlarına bak.
        {withoutPrice > 0 ? ` ${formatNumber(withoutPrice)} satın almada Google fiyat bilgisi göndermedi, toplama katılmadı.` : ""}
      </p>
    </Card>
  );
}

// Users -----------------------------------------------------------------------------

const PAGE = 25;

export function UsersView({ client }: Props) {
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<string>("");
  const [page, setPage] = useState(0);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const users = useAsync(
    () => adminApi.users(client, search, status || null, PAGE, page * PAGE),
    `${search}|${status}|${page}`,
  );
  const total = users.data?.[0]?.total_count ?? 0;

  async function reinstate(id: string) {
    setBusyId(id);
    setActionError(null);
    try {
      await adminApi.reinstate(client, id);
      users.reload();
    } catch (error) {
      console.error(error);
      setActionError(errorMessage(error));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <>
      <PageHeader title="Kullanıcılar" description="E-posta, kullanıcı adı ya da ada göre ara; duruma göre filtrele." />
      <form
        className="mb-4 flex flex-wrap gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          setSearch(query.trim());
        }}
      >
        <div className="relative min-w-60 flex-1">
          <Search className="pointer-events-none absolute left-3 top-2.5 size-4 text-ink-3" aria-hidden />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Ara…"
            aria-label="Kullanıcı ara"
            className={`${inputClass} pl-9`}
          />
        </div>
        <select
          value={status}
          onChange={(e) => {
            setPage(0);
            setStatus(e.target.value);
          }}
          aria-label="Durum filtresi"
          className={`${inputClass.replace("w-full ", "")} w-full sm:w-56`}
        >
          <option value="">Tüm durumlar</option>
          {Object.entries(STATUS_LABELS).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
        <Button type="submit">Ara</Button>
      </form>
      {actionError ? <div className="mb-4"><ErrorBox message={actionError} /></div> : null}
      <Card>
        {users.error ? <ErrorBox message={users.error} onRetry={users.reload} /> : null}
        {users.loading && !users.data ? <Loading /> : null}
        {users.data && users.data.length === 0 ? <Empty>Bu aramaya uyan kullanıcı yok.</Empty> : null}
        {users.data && users.data.length > 0 ? (
          <div className="-mx-5 -my-5 overflow-x-auto">
            <table className="w-full min-w-[760px] text-sm">
              <thead className="bg-surface-2 text-left text-xs text-ink-2">
                <tr>
                  <th className="px-5 py-2.5 font-medium">Kullanıcı</th>
                  <th className="px-3 py-2.5 font-medium">Üniversite</th>
                  <th className="px-3 py-2.5 font-medium">Durum</th>
                  <th className="px-3 py-2.5 font-medium">Kayıt</th>
                  <th className="px-5 py-2.5 text-right font-medium">İşlem</th>
                </tr>
              </thead>
              <tbody>
                {users.data.map((u) => (
                  <tr key={u.id} className="border-t border-line align-top">
                    <td className="px-5 py-3">
                      <p className="font-medium">{u.full_name ?? "—"}</p>
                      <p className="text-xs text-ink-3">
                        {u.username ? `@${u.username} · ` : ""}{u.email}
                      </p>
                    </td>
                    <td className="px-3 py-3 text-ink-2">
                      {u.university_name ?? "—"}
                      {u.department ? <p className="text-xs text-ink-3">{u.department}</p> : null}
                    </td>
                    <td className="px-3 py-3">
                      <div className="flex flex-wrap gap-1.5">
                        <Badge tone={statusTone(u.account_status)}>{STATUS_LABELS[u.account_status] ?? u.account_status}</Badge>
                        {u.is_premium ? <Badge tone="brand">Premium</Badge> : null}
                        {u.marketing_opt_in ? <Badge>E-posta izni</Badge> : null}
                      </div>
                    </td>
                    <td className="px-3 py-3 whitespace-nowrap text-ink-2">{formatDate(u.created_at)}</td>
                    <td className="px-5 py-3 text-right">
                      {u.account_status === "SUSPENDED" ? (
                        <Button variant="secondary" busy={busyId === u.id} disabled={busyId !== null} onClick={() => reinstate(u.id)}>
                          Askıyı kaldır
                        </Button>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </Card>
      <Pager page={page} total={total} onPage={setPage} />
    </>
  );
}

function Pager({ page, total, onPage }: { page: number; total: number; onPage: (page: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / PAGE));
  return (
    <div className="mt-4 flex items-center justify-between text-sm text-ink-2">
      <span>{formatNumber(total)} kayıt</span>
      <div className="flex items-center gap-2">
        <Button variant="ghost" disabled={page === 0} onClick={() => onPage(page - 1)} aria-label="Önceki sayfa">
          <ChevronLeft className="size-4" aria-hidden />
        </Button>
        <span className="tabular-nums">{page + 1} / {pages}</span>
        <Button variant="ghost" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)} aria-label="Sonraki sayfa">
          <ChevronRight className="size-4" aria-hidden />
        </Button>
      </div>
    </div>
  );
}

// Purchases -------------------------------------------------------------------------

export function PurchasesView({ client }: Props) {
  const [page, setPage] = useState(0);
  const overview = useAsync(() => adminApi.overview(client), "purchases-overview");
  const purchases = useAsync(() => adminApi.purchases(client, PAGE, page * PAGE), `purchases|${page}`);
  const total = purchases.data?.[0]?.total_count ?? 0;

  return (
    <>
      <PageHeader title="Satın almalar ve gelir" description="Google Play ile sunucuda doğrulanan abonelik satın almaları." />
      <div className="space-y-6">
        {overview.error ? <ErrorBox message={overview.error} onRetry={overview.reload} /> : null}
        {overview.data ? (
          <>
            <div className="grid gap-4 sm:grid-cols-3">
              <Stat label="Toplam satın alma" value={formatNumber(overview.data.purchase_count)} />
              <Stat label="Son 30 gün" value={formatNumber(overview.data.purchases_30_days)} />
              <Stat label="Aktif premium" value={formatNumber(overview.data.active_premium)} />
            </div>
            <RevenueCards revenue={overview.data.revenue} withoutPrice={overview.data.purchases_without_price} />
          </>
        ) : null}
        <Card title="Satın alma kayıtları">
          {purchases.error ? <ErrorBox message={purchases.error} onRetry={purchases.reload} /> : null}
          {purchases.loading && !purchases.data ? <Loading /> : null}
          {purchases.data && purchases.data.length === 0 ? <Empty>Henüz satın alma yok.</Empty> : null}
          {purchases.data && purchases.data.length > 0 ? (
            <div className="-mx-5 -my-5 overflow-x-auto">
              <table className="w-full min-w-[720px] text-sm">
                <thead className="bg-surface-2 text-left text-xs text-ink-2">
                  <tr>
                    <th className="px-5 py-2.5 font-medium">Tarih</th>
                    <th className="px-3 py-2.5 font-medium">Kullanıcı</th>
                    <th className="px-3 py-2.5 font-medium">Plan</th>
                    <th className="px-3 py-2.5 font-medium">Sipariş</th>
                    <th className="px-3 py-2.5 font-medium">Dönem sonu</th>
                    <th className="px-5 py-2.5 text-right font-medium">Tutar</th>
                  </tr>
                </thead>
                <tbody>
                  {purchases.data.map((p) => (
                    <tr key={p.id} className="border-t border-line">
                      <td className="px-5 py-3 whitespace-nowrap text-ink-2">{formatDateTime(p.created_at)}</td>
                      <td className="px-3 py-3">
                        {p.user_id ? (
                          <>
                            <p className="font-medium">{p.username ? `@${p.username}` : "—"}</p>
                            <p className="text-xs text-ink-3">{p.email}</p>
                          </>
                        ) : (
                          <span className="text-ink-3">Silinmiş hesap</span>
                        )}
                      </td>
                      <td className="px-3 py-3">{p.plan_name}</td>
                      <td className="px-3 py-3 font-mono text-xs text-ink-3">{p.order_id ?? "—"}</td>
                      <td className="px-3 py-3 whitespace-nowrap text-ink-2">{formatDate(p.period_ends_at)}</td>
                      <td className="px-5 py-3 text-right tabular-nums font-medium">
                        {p.amount_micros !== null && p.currency ? formatMoney(p.amount_micros, p.currency) : "Fiyat yok"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
        </Card>
        <Pager page={page} total={total} onPage={setPage} />
      </div>
    </>
  );
}

// Universities ------------------------------------------------------------------

export function UniversitiesView({ client }: Props) {
  const stats = useAsync(() => adminApi.universities(client), "universities");
  const [filter, setFilter] = useState("");
  const rows = useMemo(() => {
    const q = filter.trim().toLocaleLowerCase("tr");
    return (stats.data ?? []).filter(
      (u) => !q || u.name.toLocaleLowerCase("tr").includes(q) || u.city.toLocaleLowerCase("tr").includes(q),
    );
  }, [stats.data, filter]);
  const cities = useMemo(() => {
    const map = new Map<string, { students: number; approved: number; premium: number }>();
    for (const u of stats.data ?? []) {
      const c = map.get(u.city) ?? { students: 0, approved: 0, premium: 0 };
      c.students += u.students;
      c.approved += u.approved;
      c.premium += u.premium;
      map.set(u.city, c);
    }
    return [...map.entries()].sort((a, b) => b[1].students - a[1].students);
  }, [stats.data]);

  return (
    <>
      <PageHeader title="Üniversite istatistikleri" description="En az bir kayıtlı öğrencisi olan üniversiteler ve iller." />
      {stats.error ? <ErrorBox message={stats.error} onRetry={stats.reload} /> : null}
      {stats.loading && !stats.data ? <Loading /> : null}
      {stats.data ? (
        <div className="grid gap-6 xl:grid-cols-[2fr_1fr]">
          <Card
            title={`Üniversiteler (${formatNumber(stats.data.length)})`}
            action={
              <input
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
                placeholder="Üniversite ya da il ara"
                aria-label="Üniversite ara"
                className={`${inputClass} max-w-56 py-1.5`}
              />
            }
          >
            {rows.length === 0 ? (
              <Empty>Henüz üniversite seçmiş öğrenci yok.</Empty>
            ) : (
              <div className="-mx-5 -my-5 max-h-[640px] overflow-auto">
                <table className="w-full min-w-[560px] text-sm">
                  <thead className="sticky top-0 bg-surface-2 text-left text-xs text-ink-2">
                    <tr>
                      <th className="px-5 py-2.5 font-medium">Üniversite</th>
                      <th className="px-3 py-2.5 text-right font-medium">Öğrenci</th>
                      <th className="px-3 py-2.5 text-right font-medium">Onaylı</th>
                      <th className="px-5 py-2.5 text-right font-medium">Premium</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((u) => (
                      <tr key={u.university_id} className="border-t border-line">
                        <td className="px-5 py-2.5">
                          <p className="font-medium">{u.name}</p>
                          <p className="text-xs text-ink-3">{u.city}</p>
                        </td>
                        <td className="px-3 py-2.5 text-right tabular-nums">{formatNumber(u.students)}</td>
                        <td className="px-3 py-2.5 text-right tabular-nums">{formatNumber(u.approved)}</td>
                        <td className="px-5 py-2.5 text-right tabular-nums">{formatNumber(u.premium)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
          <Card title={`İller (${formatNumber(cities.length)})`}>
            {cities.length === 0 ? (
              <Empty>Veri yok.</Empty>
            ) : (
              <ul className="divide-y divide-line text-sm">
                {cities.map(([city, c]) => (
                  <li key={city} className="flex items-center justify-between gap-3 py-2.5">
                    <span className="font-medium">{city}</span>
                    <span className="text-xs text-ink-2 tabular-nums">
                      {formatNumber(c.students)} öğrenci · {formatNumber(c.approved)} onaylı · {formatNumber(c.premium)} premium
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      ) : null}
    </>
  );
}

// Reports -------------------------------------------------------------------------

const ACTION_LABELS: Record<ReportAction, string> = {
  DISMISS: "Reddet (sorun yok)",
  REMOVE_CONTENT: "İçeriği kaldır",
  SUSPEND_USER: "Kullanıcıyı askıya al",
};

export function ReportsView({ client }: Props) {
  const reports = useAsync(() => adminApi.openReports(client), "reports");
  const [busy, setBusy] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<{ id: string; action: ReportAction } | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  async function resolve(id: string, action: ReportAction) {
    setBusy(id);
    setActionError(null);
    setConfirm(null);
    try {
      await adminApi.resolveReport(client, id, action);
      reports.reload();
    } catch (error) {
      console.error(error);
      setActionError(errorMessage(error));
    } finally {
      setBusy(null);
    }
  }

  return (
    <>
      <PageHeader
        title="Şikayetler"
        description="Açık şikayetler, en eskisi üstte. Aynı içerik için gelen tüm şikayetler birlikte kapanır."
        action={<Button variant="secondary" onClick={reports.reload} busy={reports.loading}>Yenile</Button>}
      />
      {actionError ? <div className="mb-4"><ErrorBox message={actionError} /></div> : null}
      {reports.error ? <ErrorBox message={reports.error} onRetry={reports.reload} /> : null}
      {reports.loading && !reports.data ? <Loading /> : null}
      {reports.data && reports.data.length === 0 ? (
        <Card><Empty>Açık şikayet yok.</Empty></Card>
      ) : null}
      <div className="space-y-4">
        {reports.data?.map((r) => (
          <Card key={r.report_id}>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="flex flex-wrap items-center gap-2">
                <Badge tone="bad">{REPORT_REASON_LABELS[r.reason] ?? r.reason}</Badge>
                <Badge>{REPORT_TARGET_LABELS[r.target_kind] ?? r.target_kind}</Badge>
                {r.report_count > 1 ? <Badge tone="warn">{formatNumber(r.report_count)} şikayet</Badge> : null}
              </div>
              <span className="text-xs text-ink-3">{formatDateTime(r.created_at)}</span>
            </div>
            {r.target_excerpt ? (
              <blockquote className="mt-4 rounded-lg border-l-4 border-line bg-surface-2 px-4 py-3 text-sm whitespace-pre-wrap">
                {r.target_excerpt}
              </blockquote>
            ) : null}
            {r.details ? <p className="mt-3 text-sm text-ink-2">Şikayet notu: {r.details}</p> : null}
            <p className="mt-3 text-sm text-ink-2">
              Şikayet edilen: <span className="font-medium text-ink">{r.target_full_name ?? "—"}</span>
              {r.target_username ? ` (@${r.target_username})` : ""}{" "}
              <Badge tone={statusTone(r.target_account_status)}>
                {STATUS_LABELS[r.target_account_status] ?? r.target_account_status}
              </Badge>
              {r.reporter_username ? <span className="text-ink-3"> · şikayet eden @{r.reporter_username}</span> : null}
            </p>
            <div className="mt-4 flex flex-wrap gap-2">
              {(Object.keys(ACTION_LABELS) as ReportAction[])
                .filter((a) => !(a === "REMOVE_CONTENT" && r.target_kind === "USER"))
                .map((action) => (
                  <Button
                    key={action}
                    variant={action === "DISMISS" ? "secondary" : "danger"}
                    busy={busy === r.report_id && confirm === null}
                    disabled={busy !== null}
                    onClick={() => (action === "DISMISS" ? resolve(r.report_id, action) : setConfirm({ id: r.report_id, action }))}
                  >
                    {ACTION_LABELS[action]}
                  </Button>
                ))}
            </div>
            {confirm?.id === r.report_id ? (
              <div className="mt-4 flex flex-wrap items-center gap-3 rounded-lg bg-bad-soft px-4 py-3 text-sm text-bad">
                <span className="flex-1">
                  {confirm.action === "SUSPEND_USER"
                    ? "Hesap askıya alınacak ve bu kişiyle ilgili tüm açık şikayetler kapanacak. Emin misin?"
                    : "İçerik kaldırılacak. Emin misin?"}
                </span>
                <Button variant="danger" onClick={() => resolve(r.report_id, confirm.action)}>Evet, uygula</Button>
                <Button variant="ghost" onClick={() => setConfirm(null)}>Vazgeç</Button>
              </div>
            ) : null}
          </Card>
        ))}
      </div>
    </>
  );
}
