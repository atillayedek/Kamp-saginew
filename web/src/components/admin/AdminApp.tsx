"use client";

import type { Session, SupabaseClient } from "@supabase/supabase-js";
import {
  Activity,
  Building2,
  CreditCard,
  Gift,
  FileCheck,
  Flag,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  Mail,
  Megaphone,
  Menu,
  Moon,
  Scale,
  Sun,
  Users,
  X,
} from "lucide-react";
import { useEffect, useState } from "react";
import { complianceApi, type StaffAccess, type StaffRole } from "@/lib/compliance-api";
import { backendState } from "@/lib/config";
import { errorMessage } from "@/lib/errors";
import { supabase } from "@/lib/supabase";
import { BroadcastView } from "./BroadcastView";
import { ComplianceView } from "./ComplianceView";
import { DocumentsView } from "./DocumentsView";
import { ActivityView, AnnouncementsView, PremiumView } from "./GrowthViews";
import { MfaGate } from "./MfaGate";
import { ModerationView } from "./ModerationView";
import { Button, ErrorBox, inputClass, Loading, ReasonProvider } from "./ui";
import { OverviewView, PurchasesView, UniversitiesView, UsersView } from "./views";

// Each section is shown only to the roles whose database functions it calls; superadmin sees all.
const TABS = [
  { id: "overview", label: "Genel bakış", icon: LayoutDashboard, roles: ["verifier", "moderator", "compliance"] },
  { id: "documents", label: "Belge onayları", icon: FileCheck, roles: ["verifier"] },
  { id: "reports", label: "Moderasyon", icon: Flag, roles: ["moderator"] },
  { id: "users", label: "Kullanıcılar", icon: Users, roles: ["moderator"] },
  { id: "compliance", label: "KVKK ve Uyum", icon: Scale, roles: ["compliance"] },
  { id: "purchases", label: "Satın almalar", icon: CreditCard, roles: [] },
  { id: "premium", label: "Premium hediye", icon: Gift, roles: [] },
  { id: "activity", label: "Aktiflik", icon: Activity, roles: [] },
  { id: "announcements", label: "Uygulama duyurusu", icon: Megaphone, roles: [] },
  { id: "email", label: "Toplu e-posta", icon: Mail, roles: [] },
  { id: "universities", label: "Üniversiteler", icon: Building2, roles: [] },
] as const satisfies readonly { id: string; label: string; icon: unknown; roles: readonly StaffRole[] }[];

type TabId = typeof TABS[number]["id"];

const STAFF_ROLES: StaffRole[] = ["superadmin", "verifier", "moderator", "compliance"];

/** Claims in the JWT only decide whether to try; the database decides what is allowed. */
function hasStaffClaim(session: Session | null): boolean {
  const meta = session?.user.app_metadata as { role?: unknown; roles?: unknown } | undefined;
  if (meta?.role === "admin") return true;
  return Array.isArray(meta?.roles) && meta.roles.some((r) => STAFF_ROLES.includes(r as StaffRole));
}

export function visibleTabs(roles: StaffRole[]) {
  return TABS.filter((t) => roles.includes("superadmin") || t.roles.some((r) => roles.includes(r as StaffRole)));
}

export function AdminApp() {
  const state = backendState();
  const client = supabase();
  if (state !== "ready" || !client) return <NotConfigured reason={state === "unsafe_key" ? "unsafe_key" : "missing"} />;
  return <AdminGate client={client} />;
}

function AdminGate({ client }: { client: SupabaseClient }) {
  const [session, setSession] = useState<Session | null | undefined>(undefined);
  const [denied, setDenied] = useState(false);

  useEffect(() => {
    let active = true;
    // A signed-in account without a staff role is signed out immediately.
    function accept(next: Session | null) {
      if (next && !hasStaffClaim(next)) {
        setDenied(true);
        setSession(null);
        client.auth.signOut().catch((error: unknown) => console.error(error));
        return;
      }
      setSession(next);
    }
    client.auth.getSession().then(({ data, error }) => {
      if (error) console.error(error);
      if (active) accept(data.session);
    });
    const { data } = client.auth.onAuthStateChange((_event, next) => {
      if (active) accept(next);
    });
    return () => {
      active = false;
      data.subscription.unsubscribe();
    };
  }, [client]);

  if (session === undefined) return <Centered><Loading label="Oturum kontrol ediliyor…" /></Centered>;
  if (!session || !hasStaffClaim(session)) return <SignIn client={client} denied={denied} onAttempt={() => setDenied(false)} />;
  return <StaffGate client={client} email={session.user.email ?? ""} sessionKey={session.access_token} />;
}

/** Reads the roles and MFA rule from the database, asks for the second factor, then opens the panel. */
function StaffGate({ client, email, sessionKey }: { client: SupabaseClient; email: string; sessionKey: string }) {
  const [access, setAccess] = useState<{ key: string; value: StaffAccess | null; error: string | null } | null>(null);

  useEffect(() => {
    let active = true;
    complianceApi.access(client).then(
      (value) => active && setAccess({ key: sessionKey, value, error: null }),
      (error: unknown) => {
        console.error(error);
        if (active) setAccess({ key: sessionKey, value: null, error: errorMessage(error) });
      },
    );
    return () => {
      active = false;
    };
  }, [client, sessionKey]);

  if (!access || access.key !== sessionKey) return <Centered><Loading label="Yetkiler kontrol ediliyor…" /></Centered>;
  if (access.error || !access.value) {
    return <Centered><div className="max-w-md"><ErrorBox message={access.error ?? "Yetkiler okunamadı."} /></div></Centered>;
  }
  if (access.value.roles.length === 0) {
    return (
      <Centered>
        <div className="max-w-md space-y-3">
          <ErrorBox message="Bu hesabın yönetim paneli rolü yok." />
          <Button variant="secondary" onClick={() => client.auth.signOut().catch((e: unknown) => console.error(e))}>Çıkış yap</Button>
        </div>
      </Centered>
    );
  }
  if (access.value.mfa_required && !access.value.mfa_satisfied) {
    return <Centered><MfaGate client={client} email={email} /></Centered>;
  }
  return (
    <ReasonProvider>
      <Shell client={client} email={email} roles={access.value.roles} idleMinutes={access.value.idle_minutes} />
    </ReasonProvider>
  );
}

/** Signs out after `minutes` without keyboard, mouse, touch or scroll activity. */
function useIdleSignOut(client: SupabaseClient, minutes: number) {
  useEffect(() => {
    if (!Number.isFinite(minutes) || minutes <= 0) return;
    let timer = window.setTimeout(expire, minutes * 60_000);
    function expire() {
      client.auth.signOut().catch((error: unknown) => console.error(error));
      try {
        sessionStorage.setItem("kampusagi-admin-idle", "1");
      } catch (error) {
        console.warn("Idle note not saved", error);
      }
    }
    function reset() {
      window.clearTimeout(timer);
      timer = window.setTimeout(expire, minutes * 60_000);
    }
    const events = ["mousemove", "mousedown", "keydown", "touchstart", "scroll", "visibilitychange"] as const;
    for (const e of events) window.addEventListener(e, reset, { passive: true });
    return () => {
      window.clearTimeout(timer);
      for (const e of events) window.removeEventListener(e, reset);
    };
  }, [client, minutes]);
}

function Centered({ children }: { children: React.ReactNode }) {
  return <div className="grid min-h-dvh place-items-center px-4">{children}</div>;
}

function Logo() {
  return (
    <div className="flex items-center gap-2 font-semibold">
      <span className="grid size-8 place-items-center rounded-lg bg-brand text-white">
        <GraduationCap className="size-4" aria-hidden />
      </span>
      KampüsAğı <span className="text-ink-3 font-normal">Yönetim</span>
    </div>
  );
}

function SignIn({ client, denied, onAttempt }: { client: SupabaseClient; denied: boolean; onAttempt: () => void }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [idle] = useState(() => {
    try {
      const flagged = sessionStorage.getItem("kampusagi-admin-idle") === "1";
      sessionStorage.removeItem("kampusagi-admin-idle");
      return flagged;
    } catch {
      return false;
    }
  });

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    onAttempt();
    setBusy(true);
    setError(null);
    const { error: signInError } = await client.auth.signInWithPassword({ email: email.trim(), password });
    if (signInError) {
      console.error(signInError);
      setError(errorMessage(signInError));
    }
    setBusy(false);
  }

  return (
    <Centered>
      <div className="w-full max-w-sm">
        <div className="mb-8 flex justify-center"><Logo /></div>
        <form onSubmit={submit} className="space-y-4 rounded-2xl border border-line bg-surface p-6 shadow-sm">
          <h1 className="text-lg font-semibold">Yönetici girişi</h1>
          <label className="block">
            <span className="mb-1.5 block text-sm font-medium">E-posta</span>
            <input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} className={inputClass} />
          </label>
          <label className="block">
            <span className="mb-1.5 block text-sm font-medium">Şifre</span>
            <input
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className={inputClass}
            />
          </label>
          {idle ? <ErrorBox message="Hareketsiz kaldığın için oturum kapatıldı." /> : null}
          {denied ? <ErrorBox message="Bu hesabın yönetici yetkisi yok." /> : null}
          {error ? <ErrorBox message={error} /> : null}
          <Button type="submit" busy={busy} className="w-full">Giriş yap</Button>
        </form>
      </div>
    </Centered>
  );
}

function readTheme(): string {
  try {
    const stored = localStorage.getItem("kampusagi-admin-theme");
    if (stored === "dark" || stored === "light") return stored;
  } catch (error) {
    console.warn("Theme preference unavailable", error);
  }
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

/** Only used after sign-in, which happens in the browser, so reading storage on first render is safe. */
function useTheme(): [string, () => void] {
  const [theme, setTheme] = useState(readTheme);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);
  function toggle() {
    const next = theme === "dark" ? "light" : "dark";
    setTheme(next);
    try {
      localStorage.setItem("kampusagi-admin-theme", next);
    } catch (error) {
      console.warn("Theme preference not saved", error);
    }
  }
  return [theme, toggle];
}

function Shell({ client, email, roles, idleMinutes }: { client: SupabaseClient; email: string; roles: StaffRole[]; idleMinutes: number }) {
  const tabs = visibleTabs(roles);
  const [tab, setTab] = useState<TabId>(tabs[0]?.id ?? "overview");
  useIdleSignOut(client, idleMinutes);
  const [menuOpen, setMenuOpen] = useState(false);
  const [theme, toggleTheme] = useTheme();
  const [signOutError, setSignOutError] = useState<string | null>(null);

  async function signOut() {
    const { error } = await client.auth.signOut();
    if (error) {
      console.error(error);
      setSignOutError(errorMessage(error));
    }
  }

  function go(id: string) {
    setTab(id as TabId);
    setMenuOpen(false);
    window.scrollTo({ top: 0 });
  }

  const nav = (
    <nav className="space-y-1" aria-label="Yönetim menüsü">
      {tabs.map(({ id, label, icon: Icon }) => (
        <button
          key={id}
          onClick={() => go(id)}
          aria-current={tab === id ? "page" : undefined}
          className={`flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition ${
            tab === id ? "bg-brand-soft text-brand" : "text-ink-2 hover:bg-surface-2 hover:text-ink"
          }`}
        >
          <Icon className="size-4" aria-hidden /> {label}
        </button>
      ))}
    </nav>
  );

  const footer = (
    <div className="space-y-2 border-t border-line pt-4">
      <p className="truncate px-3 text-xs text-ink-3" title={email}>{email}</p>
      <p className="px-3 text-xs text-ink-3">Rol: {roles.join(", ")} · {idleMinutes} dk hareketsizlikte çıkış</p>
      <button onClick={toggleTheme} className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm text-ink-2 hover:bg-surface-2 hover:text-ink">
        {theme === "dark" ? <Sun className="size-4" aria-hidden /> : <Moon className="size-4" aria-hidden />}
        {theme === "dark" ? "Açık tema" : "Koyu tema"}
      </button>
      <button onClick={signOut} className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm text-ink-2 hover:bg-surface-2 hover:text-ink">
        <LogOut className="size-4" aria-hidden /> Çıkış yap
      </button>
      {signOutError ? <p className="px-3 text-xs text-bad">{signOutError}</p> : null}
    </div>
  );

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[240px_1fr]">
      <aside className="sticky top-0 hidden h-dvh flex-col justify-between border-r border-line bg-surface p-4 lg:flex">
        <div className="space-y-6">
          <div className="px-2 pt-1"><Logo /></div>
          {nav}
        </div>
        {footer}
      </aside>

      <header className="sticky top-0 z-20 flex items-center justify-between border-b border-line bg-surface px-4 py-3 lg:hidden">
        <Logo />
        <button onClick={() => setMenuOpen(true)} aria-label="Menüyü aç" className="rounded-lg p-2 hover:bg-surface-2">
          <Menu className="size-5" aria-hidden />
        </button>
      </header>
      {menuOpen ? (
        <div className="fixed inset-0 z-30 lg:hidden" role="dialog" aria-modal="true">
          <div className="absolute inset-0 bg-black/40" onClick={() => setMenuOpen(false)} />
          <div className="absolute inset-y-0 left-0 flex w-72 flex-col justify-between bg-surface p-4">
            <div className="space-y-6">
              <div className="flex items-center justify-between px-2 pt-1">
                <Logo />
                <button onClick={() => setMenuOpen(false)} aria-label="Menüyü kapat" className="rounded-lg p-2 hover:bg-surface-2">
                  <X className="size-5" aria-hidden />
                </button>
              </div>
              {nav}
            </div>
            {footer}
          </div>
        </div>
      ) : null}

      <main className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-8 sm:py-8">
        {tab === "overview" ? <OverviewView client={client} onNavigate={go} /> : null}
        {tab === "documents" ? <DocumentsView client={client} /> : null}
        {tab === "users" ? <UsersView client={client} /> : null}
        {tab === "purchases" ? <PurchasesView client={client} /> : null}
        {tab === "premium" ? <PremiumView client={client} /> : null}
        {tab === "activity" ? <ActivityView client={client} /> : null}
        {tab === "announcements" ? <AnnouncementsView client={client} /> : null}
        {tab === "email" ? <BroadcastView client={client} adminEmail={email} /> : null}
        {tab === "reports" ? <ModerationView client={client} /> : null}
        {tab === "compliance" ? <ComplianceView client={client} /> : null}
        {tab === "universities" ? <UniversitiesView client={client} /> : null}
      </main>
    </div>
  );
}

function NotConfigured({ reason }: { reason: "missing" | "unsafe_key" }) {
  return (
    <Centered>
      <div className="max-w-md rounded-2xl border border-line bg-surface p-6">
        <Logo />
        <h1 className="mt-6 text-lg font-semibold">Henüz bağlı değil — kurulum gerekli</h1>
        <p className="mt-2 text-sm leading-relaxed text-ink-2">
          {reason === "unsafe_key"
            ? "NEXT_PUBLIC_SUPABASE_ANON_KEY bir istemci anahtarı değil. Yalnızca sb_publishable_… ya da anon anahtarı kullanılabilir; gizli anahtar asla tarayıcıya konmaz."
            : "Bu site NEXT_PUBLIC_SUPABASE_URL ve NEXT_PUBLIC_SUPABASE_ANON_KEY olmadan derlendi. Vercel proje ayarlarına ekleyip yeniden deploy et."}
        </p>
      </div>
    </Centered>
  );
}
