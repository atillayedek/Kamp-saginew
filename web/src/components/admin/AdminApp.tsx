"use client";

import type { Session, SupabaseClient } from "@supabase/supabase-js";
import {
  Building2,
  CreditCard,
  FileCheck,
  Flag,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  Mail,
  Menu,
  Moon,
  Sun,
  Users,
  X,
} from "lucide-react";
import { useEffect, useState } from "react";
import { backendState } from "@/lib/config";
import { errorMessage } from "@/lib/errors";
import { supabase } from "@/lib/supabase";
import { BroadcastView } from "./BroadcastView";
import { DocumentsView } from "./DocumentsView";
import { Button, ErrorBox, inputClass, Loading } from "./ui";
import { OverviewView, PurchasesView, ReportsView, UniversitiesView, UsersView } from "./views";

const TABS = [
  { id: "overview", label: "Genel bakış", icon: LayoutDashboard },
  { id: "documents", label: "Belge onayları", icon: FileCheck },
  { id: "users", label: "Kullanıcılar", icon: Users },
  { id: "purchases", label: "Satın almalar", icon: CreditCard },
  { id: "email", label: "Toplu e-posta", icon: Mail },
  { id: "reports", label: "Şikayetler", icon: Flag },
  { id: "universities", label: "Üniversiteler", icon: Building2 },
] as const;

type TabId = typeof TABS[number]["id"];

function isAdmin(session: Session | null): boolean {
  return session?.user.app_metadata?.role === "admin";
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
    // A signed-in account without the admin role is signed out immediately.
    function accept(next: Session | null) {
      if (next && !isAdmin(next)) {
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
  if (!session || !isAdmin(session)) return <SignIn client={client} denied={denied} onAttempt={() => setDenied(false)} />;
  return <Shell client={client} email={session.user.email ?? ""} />;
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

function Shell({ client, email }: { client: SupabaseClient; email: string }) {
  const [tab, setTab] = useState<TabId>("overview");
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
      {TABS.map(({ id, label, icon: Icon }) => (
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
        {tab === "email" ? <BroadcastView client={client} adminEmail={email} /> : null}
        {tab === "reports" ? <ReportsView client={client} /> : null}
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
