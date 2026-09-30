"use client";

import type { SupabaseClient } from "@supabase/supabase-js";
import { Copy, Search } from "lucide-react";
import { useState } from "react";
import { adminApi, type AdminUser } from "@/lib/admin-api";
import { errorMessage } from "@/lib/errors";
import { formatDate, formatDateTime, formatNumber } from "@/lib/format";
import { DailyBars } from "./DailyBars";
import { Badge, Button, Card, Empty, ErrorBox, inputClass, Loading, PageHeader, Stat, useAsync } from "./ui";

type Props = { client: SupabaseClient };

function percent(part: number, whole: number): string {
  return whole > 0 ? `%${Math.round((part / whole) * 100)}` : "–";
}

// Activity -------------------------------------------------------------------------------

export function ActivityView({ client }: Props) {
  const stats = useAsync(() => adminApi.activity(client), "activity");
  const s = stats.data;
  return (
    <>
      <PageHeader
        title="Aktiflik"
        description="Uygulamayı açan hesaplar gün bazında sayılır (İstanbul saatiyle). Başka hiçbir kullanım verisi toplanmaz."
        action={<Button variant="secondary" onClick={stats.reload} busy={stats.loading}>Yenile</Button>}
      />
      {stats.error ? <ErrorBox message={stats.error} onRetry={stats.reload} /> : null}
      {!s && stats.loading ? <Loading /> : null}
      {s ? (
        <div className="space-y-6">
          {s.first_day ? (
            <p className="text-sm text-ink-2">Kayıt {formatDate(s.first_day)} tarihinde başladı; daha önceki günler için veri yok.</p>
          ) : (
            <p className="text-sm text-ink-2">Henüz kayıtlı aktiflik yok. Veriler bu sürümü kullanan uygulamalardan gelmeye başlar.</p>
          )}
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Stat label="Bugün aktif" value={formatNumber(s.today)} />
            <Stat label="Son 7 gün aktif" value={formatNumber(s.last_7_days)} />
            <Stat label="Son 30 gün aktif" value={formatNumber(s.last_30_days)} />
            <Stat
              label="Geri dönen yeni kullanıcılar"
              value={percent(s.retention.returned, s.retention.cohort)}
              hint={`8–60 gün önce kaydolan ${formatNumber(s.retention.cohort)} kişiden; 7+ gün sonra dönen ${percent(
                s.retention.returned_after_7_days,
                s.retention.cohort,
              )}`}
            />
          </div>
          <div className="grid gap-6 lg:grid-cols-2">
            <Card title="Günlük aktif öğrenci">
              <DailyBars label="Aktif" points={s.daily.map((d) => ({ day: d.day, value: d.active }))} />
            </Card>
            <Card title="Son 7 günde en aktif üniversiteler">
              {s.top_universities.length === 0 ? (
                <Empty>Henüz veri yok.</Empty>
              ) : (
                <ul className="divide-y divide-line">
                  {s.top_universities.map((u) => (
                    <li key={u.name} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                      <span className="truncate">{u.name}</span>
                      <span className="font-medium tabular-nums">{formatNumber(u.active)}</span>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </div>
        </div>
      ) : null}
    </>
  );
}

// Announcements ----------------------------------------------------------------------------

function defaultEnd(): string {
  const date = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function AnnouncementsView({ client }: Props) {
  const list = useAsync(() => adminApi.announcements(client), "announcements");
  const universities = useAsync(() => adminApi.universityOptions(client), "university-options");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [universityId, setUniversityId] = useState("");
  const [endsAt, setEndsAt] = useState(defaultEnd);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [endingId, setEndingId] = useState<string | null>(null);
  // Status badges compare against the time the page was opened.
  const [now] = useState(() => Date.now());

  async function create(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await adminApi.createAnnouncement(client, title.trim(), body.trim(), universityId || null, new Date(endsAt).toISOString());
      setTitle("");
      setBody("");
      list.reload();
    } catch (err) {
      console.error(err);
      setError(errorMessage(err));
    }
    setBusy(false);
  }

  async function end(id: string) {
    setEndingId(id);
    setError(null);
    try {
      await adminApi.endAnnouncement(client, id);
      list.reload();
    } catch (err) {
      console.error(err);
      setError(errorMessage(err));
    }
    setEndingId(null);
  }

  return (
    <>
      <PageHeader
        title="Uygulama içi duyuru"
        description="Duyuru, uygulamayı açık olan öğrencilere anında, diğerlerine uygulamayı açtıklarında akışın en üstünde gösterilir. Öğrenci kapatabilir."
      />
      <div className="grid gap-6 lg:grid-cols-[minmax(0,420px)_1fr]">
        <Card title="Yeni duyuru">
          <form onSubmit={create} className="space-y-4">
            <label className="block">
              <span className="mb-1.5 block text-sm font-medium">Başlık</span>
              <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={80} required className={inputClass} />
            </label>
            <label className="block">
              <span className="mb-1.5 block text-sm font-medium">Metin</span>
              <textarea value={body} onChange={(e) => setBody(e.target.value)} maxLength={1000} rows={4} required className={inputClass} />
              <span className="mt-1 block text-xs text-ink-3">{body.length} / 1000</span>
            </label>
            <label className="block">
              <span className="mb-1.5 block text-sm font-medium">Kime</span>
              <select value={universityId} onChange={(e) => setUniversityId(e.target.value)} className={inputClass}>
                <option value="">Tüm onaylı öğrenciler</option>
                {(universities.data ?? []).map((u) => (
                  <option key={u.id} value={u.id}>{u.name} ({u.city})</option>
                ))}
              </select>
            </label>
            <label className="block">
              <span className="mb-1.5 block text-sm font-medium">Bitiş</span>
              <input type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} required className={inputClass} />
              <span className="mt-1 block text-xs text-ink-3">En fazla 90 gün sonra.</span>
            </label>
            {error ? <ErrorBox message={error} /> : null}
            {universities.error ? <ErrorBox message={universities.error} onRetry={universities.reload} /> : null}
            <Button type="submit" busy={busy} disabled={title.trim().length < 2 || body.trim().length < 2}>Yayınla</Button>
          </form>
        </Card>
        <Card title="Duyurular">
          {list.error ? <ErrorBox message={list.error} onRetry={list.reload} /> : null}
          {!list.data && list.loading ? <Loading /> : null}
          {list.data && list.data.length === 0 ? <Empty>Henüz duyuru yok.</Empty> : null}
          <ul className="divide-y divide-line">
            {(list.data ?? []).map((a) => {
              const active = new Date(a.ends_at).getTime() > now && new Date(a.starts_at).getTime() <= now;
              return (
                <li key={a.id} className="space-y-1 py-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">{a.title}</span>
                    {active ? <Badge tone="good">Yayında</Badge> : <Badge>Bitti</Badge>}
                    <Badge tone="brand">{a.university_name ?? "Tüm öğrenciler"}</Badge>
                  </div>
                  <p className="text-sm text-ink-2 whitespace-pre-line">{a.body}</p>
                  <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-ink-3">
                    <span>
                      {formatDateTime(a.created_at)} → {formatDateTime(a.ends_at)} · {formatNumber(a.dismissed_count)} kişi kapattı
                    </span>
                    {active ? (
                      <Button variant="secondary" onClick={() => end(a.id)} busy={endingId === a.id}>Şimdi bitir</Button>
                    ) : null}
                  </div>
                </li>
              );
            })}
          </ul>
        </Card>
      </div>
    </>
  );
}

// Premium gifts and promo codes -------------------------------------------------------------------

export function PremiumView({ client }: Props) {
  const plans = useAsync(() => adminApi.plans(client), "plans");
  const grants = useAsync(() => adminApi.grants(client), "grants");
  const codes = useAsync(() => adminApi.promoCodes(client), "promo-codes");
  const activePlans = (plans.data ?? []).filter((p) => p.is_active);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [now] = useState(() => Date.now());

  return (
    <>
      <PageHeader
        title="Premium hediye ve promosyon"
        description="Hediye ve kodlar Google Play dışında Premium verir; ücret alınmaz, gelir tablosuna girmez. Süreler üst üste eklenir."
      />
      {plans.error ? <ErrorBox message={plans.error} onRetry={plans.reload} /> : null}
      {plans.data && activePlans.length === 0 ? (
        <ErrorBox message="Aktif bir Premium planı yok. Hediye ve kod için önce subscription_plans tablosuna aktif bir plan eklenmeli (docs/DEPLOYMENT.md)." />
      ) : null}
      {error ? <div className="mb-4"><ErrorBox message={error} /></div> : null}
      {notice ? <p className="mb-4 rounded-xl bg-good-soft px-4 py-3 text-sm text-good">{notice}</p> : null}
      <div className="grid gap-6 lg:grid-cols-2">
        <GrantForm
          client={client}
          plans={activePlans}
          onDone={(message) => {
            setError(null);
            setNotice(message);
            grants.reload();
          }}
          onError={setError}
        />
        <PromoForm
          client={client}
          plans={activePlans}
          onDone={(message) => {
            setError(null);
            setNotice(message);
            codes.reload();
          }}
          onError={setError}
        />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <Card title="Promosyon kodları">
          {codes.error ? <ErrorBox message={codes.error} onRetry={codes.reload} /> : null}
          {!codes.data && codes.loading ? <Loading /> : null}
          {codes.data && codes.data.length === 0 ? <Empty>Henüz kod yok.</Empty> : null}
          <ul className="divide-y divide-line">
            {(codes.data ?? []).map((c) => {
              const expired = c.expires_at !== null && new Date(c.expires_at).getTime() <= now;
              const usable = !c.disabled_at && !expired && c.redemption_count < c.max_redemptions;
              return (
                <li key={c.id} className="flex flex-wrap items-center justify-between gap-2 py-3 text-sm">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <code className="rounded bg-surface-2 px-1.5 py-0.5 font-mono text-sm">{c.code}</code>
                      <button
                        onClick={() => navigator.clipboard.writeText(c.code).catch((err: unknown) => console.error(err))}
                        aria-label="Kodu kopyala"
                        className="text-ink-3 hover:text-ink"
                      >
                        <Copy className="size-3.5" aria-hidden />
                      </button>
                      {usable ? <Badge tone="good">Geçerli</Badge> : <Badge>{c.disabled_at ? "Kapatıldı" : expired ? "Süresi doldu" : "Tükendi"}</Badge>}
                    </div>
                    <p className="text-xs text-ink-3">
                      {c.plan_name} · {c.days} gün · {formatNumber(c.redemption_count)}/{formatNumber(c.max_redemptions)} kullanım
                      {c.expires_at ? ` · son ${formatDate(c.expires_at)}` : ""}
                    </p>
                  </div>
                  {usable ? (
                    <Button
                      variant="secondary"
                      onClick={async () => {
                        try {
                          await adminApi.disablePromoCode(client, c.id);
                          codes.reload();
                        } catch (err) {
                          console.error(err);
                          setError(errorMessage(err));
                        }
                      }}
                    >
                      Kapat
                    </Button>
                  ) : null}
                </li>
              );
            })}
          </ul>
        </Card>
        <Card title="Son hediyeler">
          {grants.error ? <ErrorBox message={grants.error} onRetry={grants.reload} /> : null}
          {!grants.data && grants.loading ? <Loading /> : null}
          {grants.data && grants.data.length === 0 ? <Empty>Henüz hediye yok.</Empty> : null}
          <ul className="divide-y divide-line">
            {(grants.data ?? []).map((g) => {
              const active = !g.revoked_at && new Date(g.expires_at).getTime() > now;
              return (
                <li key={g.id} className="flex flex-wrap items-center justify-between gap-2 py-3 text-sm">
                  <div className="min-w-0 space-y-1">
                    <p className="truncate font-medium">{g.full_name ?? g.email} {g.username ? <span className="text-ink-3">@{g.username}</span> : null}</p>
                    <p className="text-xs text-ink-3">
                      {g.source === "PROMO" ? `Kod ${g.promo_code ?? ""}` : "Yönetici hediyesi"} · {g.plan_name} · {formatDate(g.expires_at)} tarihine kadar
                      {g.note ? ` · ${g.note}` : ""}
                    </p>
                  </div>
                  {active ? (
                    <Button
                      variant="ghost"
                      onClick={async () => {
                        try {
                          await adminApi.revokeGrant(client, g.id);
                          grants.reload();
                        } catch (err) {
                          console.error(err);
                          setError(errorMessage(err));
                        }
                      }}
                    >
                      Geri al
                    </Button>
                  ) : (
                    <Badge>{g.revoked_at ? "Geri alındı" : "Süresi doldu"}</Badge>
                  )}
                </li>
              );
            })}
          </ul>
        </Card>
      </div>
    </>
  );
}

function PlanSelect({ plans, value, onChange }: { plans: { id: string; name: string }[]; value: string; onChange: (id: string) => void }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium">Plan</span>
      <select value={value} onChange={(e) => onChange(e.target.value)} className={inputClass} required>
        <option value="" disabled>Plan seç</option>
        {plans.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
      </select>
    </label>
  );
}

function GrantForm({ client, plans, onDone, onError }: {
  client: SupabaseClient;
  plans: { id: string; name: string }[];
  onDone: (message: string) => void;
  onError: (message: string) => void;
}) {
  const [search, setSearch] = useState("");
  const [results, setResults] = useState<AdminUser[] | null>(null);
  const [user, setUser] = useState<AdminUser | null>(null);
  const [planId, setPlanId] = useState("");
  const [days, setDays] = useState(30);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);

  async function find(e: React.FormEvent) {
    e.preventDefault();
    try {
      setResults(await adminApi.users(client, search.trim(), null, 10, 0));
    } catch (err) {
      console.error(err);
      onError(errorMessage(err));
    }
  }

  async function grant() {
    if (!user || !planId) return;
    setBusy(true);
    try {
      const expires = await adminApi.grantPremium(client, user.id, planId, days, note.trim() || null);
      onDone(`${user.full_name ?? user.email} için Premium ${formatDate(expires)} tarihine kadar açıldı.`);
      setUser(null);
      setNote("");
    } catch (err) {
      console.error(err);
      onError(errorMessage(err));
    }
    setBusy(false);
  }

  return (
    <Card title="Kişiye Premium hediye et">
      <div className="space-y-4">
        <form onSubmit={find} className="flex gap-2">
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="E-posta, ad veya kullanıcı adı"
            className={inputClass}
          />
          <Button type="submit" variant="secondary"><Search className="size-4" aria-hidden /> Bul</Button>
        </form>
        {results && !user ? (
          results.length === 0 ? <Empty>Kullanıcı bulunamadı.</Empty> : (
            <ul className="max-h-56 divide-y divide-line overflow-auto rounded-lg border border-line">
              {results.map((u) => (
                <li key={u.id}>
                  <button onClick={() => setUser(u)} className="w-full px-3 py-2 text-left text-sm hover:bg-surface-2">
                    {u.full_name ?? u.email} <span className="text-ink-3">{u.username ? `@${u.username} · ` : ""}{u.email}</span>
                    {u.is_premium ? <span className="ml-2"><Badge tone="brand">Premium</Badge></span> : null}
                  </button>
                </li>
              ))}
            </ul>
          )
        ) : null}
        {user ? (
          <div className="space-y-4">
            <div className="flex items-center justify-between rounded-lg bg-surface-2 px-3 py-2 text-sm">
              <span>{user.full_name ?? user.email} <span className="text-ink-3">{user.email}</span></span>
              <button onClick={() => setUser(null)} className="text-xs font-medium text-ink-2 hover:text-ink">Değiştir</button>
            </div>
            <PlanSelect plans={plans} value={planId} onChange={setPlanId} />
            <label className="block">
              <span className="mb-1.5 block text-sm font-medium">Gün</span>
              <input type="number" min={1} max={365} value={days} onChange={(e) => setDays(Number(e.target.value))} className={inputClass} />
            </label>
            <label className="block">
              <span className="mb-1.5 block text-sm font-medium">Not (isteğe bağlı)</span>
              <input value={note} onChange={(e) => setNote(e.target.value)} maxLength={200} className={inputClass} />
            </label>
            <Button onClick={grant} busy={busy} disabled={!planId || days < 1 || days > 365}>Hediye et</Button>
          </div>
        ) : null}
      </div>
    </Card>
  );
}

function PromoForm({ client, plans, onDone, onError }: {
  client: SupabaseClient;
  plans: { id: string; name: string }[];
  onDone: (message: string) => void;
  onError: (message: string) => void;
}) {
  const [code, setCode] = useState("");
  const [planId, setPlanId] = useState("");
  const [days, setDays] = useState(30);
  const [maxRedemptions, setMaxRedemptions] = useState(100);
  const [expiresAt, setExpiresAt] = useState("");
  const [busy, setBusy] = useState(false);

  async function create(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      const created = await adminApi.createPromoCode(
        client,
        code.trim() || null,
        planId,
        days,
        maxRedemptions,
        expiresAt ? new Date(expiresAt).toISOString() : null,
      );
      onDone(`Kod oluşturuldu: ${created}. Öğrenciler uygulamada Premium ekranından kullanabilir.`);
      setCode("");
    } catch (err) {
      console.error(err);
      onError(errorMessage(err));
    }
    setBusy(false);
  }

  return (
    <Card title="Promosyon kodu oluştur">
      <form onSubmit={create} className="space-y-4">
        <label className="block">
          <span className="mb-1.5 block text-sm font-medium">Kod (boş bırakılırsa rastgele üretilir)</span>
          <input
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            maxLength={32}
            placeholder="ör. KAMPUS-2026"
            className={`${inputClass} font-mono`}
          />
        </label>
        <PlanSelect plans={plans} value={planId} onChange={setPlanId} />
        <div className="grid grid-cols-2 gap-3">
          <label className="block">
            <span className="mb-1.5 block text-sm font-medium">Gün</span>
            <input type="number" min={1} max={365} value={days} onChange={(e) => setDays(Number(e.target.value))} className={inputClass} />
          </label>
          <label className="block">
            <span className="mb-1.5 block text-sm font-medium">Kullanım sınırı</span>
            <input
              type="number"
              min={1}
              max={100000}
              value={maxRedemptions}
              onChange={(e) => setMaxRedemptions(Number(e.target.value))}
              className={inputClass}
            />
          </label>
        </div>
        <label className="block">
          <span className="mb-1.5 block text-sm font-medium">Son kullanma (isteğe bağlı)</span>
          <input type="datetime-local" value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)} className={inputClass} />
        </label>
        <p className="text-xs text-ink-3">Her öğrenci bir kodu bir kez kullanabilir. Kod kısa ve tahmin edilebilir olursa herkes kullanabilir; özel kodlar için boş bırak.</p>
        <Button type="submit" busy={busy} disabled={!planId}>Oluştur</Button>
      </form>
    </Card>
  );
}
