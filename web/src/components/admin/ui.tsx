"use client";

import { AlertTriangle, Loader2, RefreshCw } from "lucide-react";
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { isValidReason, MAX_REASON } from "@/lib/audit";
import { errorMessage } from "@/lib/errors";

export interface Async<T> {
  data: T | null;
  error: string | null;
  loading: boolean;
  reload: () => void;
}

/** Loads on mount and whenever `key` changes; errors become Turkish messages. */
export function useAsync<T>(load: () => Promise<T>, key: string): Async<T> {
  const [nonce, setNonce] = useState(0);
  const token = `${key}#${nonce}`;
  const [result, setResult] = useState<{ token: string; data: T | null; error: string | null } | null>(null);
  const loadRef = useRef(load);

  useEffect(() => {
    loadRef.current = load;
  });

  useEffect(() => {
    let active = true;
    loadRef.current().then(
      (data) => {
        if (active) setResult({ token, data, error: null });
      },
      (error: unknown) => {
        console.error(error);
        if (active) setResult((previous) => ({ token, data: previous?.data ?? null, error: errorMessage(error) }));
      },
    );
    return () => {
      active = false;
    };
  }, [token]);

  const loading = result?.token !== token;
  return {
    data: result?.data ?? null,
    error: loading ? null : result.error,
    loading,
    reload: () => setNonce((n) => n + 1),
  };
}

export function Card({ title, action, children, className = "" }: {
  title?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`rounded-2xl border border-line bg-surface ${className}`}>
      {title ? (
        <div className="flex items-center justify-between gap-3 border-b border-line px-5 py-3.5">
          <h2 className="text-sm font-semibold">{title}</h2>
          {action}
        </div>
      ) : null}
      <div className="p-5">{children}</div>
    </section>
  );
}

export function Stat({ label, value, hint, tone }: {
  label: string;
  value: string;
  hint?: string;
  tone?: "warn" | "bad";
}) {
  return (
    <div className="rounded-2xl border border-line bg-surface p-5">
      <p className="text-sm text-ink-2">{label}</p>
      <p className="mt-2 text-3xl font-semibold tabular-nums tracking-tight">{value}</p>
      {hint ? (
        <p className={`mt-1 text-xs ${tone === "bad" ? "text-bad" : tone === "warn" ? "text-warn" : "text-ink-3"}`}>
          {hint}
        </p>
      ) : null}
    </div>
  );
}

const BADGE_TONES = {
  neutral: "bg-surface-2 text-ink-2",
  brand: "bg-brand-soft text-brand",
  good: "bg-good-soft text-good",
  warn: "bg-warn-soft text-warn",
  bad: "bg-bad-soft text-bad",
} as const;

export function Badge({ tone = "neutral", children }: { tone?: keyof typeof BADGE_TONES; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium ${BADGE_TONES[tone]}`}>
      {children}
    </span>
  );
}

export function statusTone(status: string): keyof typeof BADGE_TONES {
  switch (status) {
    case "APPROVED":
      return "good";
    case "PENDING_REVIEW":
    case "DOCUMENT_REQUIRED":
      return "warn";
    case "SUSPENDED":
    case "REJECTED":
      return "bad";
    default:
      return "neutral";
  }
}

export function Button({ variant = "primary", busy = false, className = "", children, disabled, ...props }:
  React.ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "secondary" | "danger" | "ghost"; busy?: boolean }) {
  const styles = {
    primary: "bg-brand text-white hover:bg-brand-strong",
    secondary: "border border-line bg-surface text-ink hover:bg-surface-2",
    danger: "bg-bad text-white hover:opacity-90",
    ghost: "text-ink-2 hover:bg-surface-2 hover:text-ink",
  }[variant];
  return (
    <button
      {...props}
      disabled={disabled || busy}
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-3.5 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50 ${styles} ${className}`}
    >
      {busy ? <Loader2 className="size-4 animate-spin" aria-hidden /> : null}
      {children}
    </button>
  );
}

export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-wrap items-center gap-3 rounded-xl border border-bad/30 bg-bad-soft px-4 py-3 text-sm text-bad">
      <AlertTriangle className="size-4 shrink-0" aria-hidden />
      <span className="flex-1">{message}</span>
      {onRetry ? (
        <button onClick={onRetry} className="inline-flex items-center gap-1 font-medium underline-offset-2 hover:underline">
          <RefreshCw className="size-3.5" aria-hidden /> Tekrar dene
        </button>
      ) : null}
    </div>
  );
}

export function Loading({ label = "Yükleniyor…" }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 py-8 text-sm text-ink-3" role="status">
      <Loader2 className="size-4 animate-spin" aria-hidden /> {label}
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="py-8 text-center text-sm text-ink-3">{children}</p>;
}

export function PageHeader({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {description ? <p className="mt-1 text-sm text-ink-2">{description}</p> : null}
      </div>
      {action}
    </div>
  );
}

export const inputClass =
  "w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink placeholder:text-ink-3 focus:border-brand focus:outline-none";

type AskReason = (title: string, options?: { hint?: string; initial?: string; confirmLabel?: string }) => Promise<string | null>;

const ReasonContext = createContext<AskReason | null>(null);

/**
 * Asks the staff member why they are doing something. The answer is sent with the request
 * (withReason) and stored in the audit log; cancelling aborts the action.
 */
export function useReason(): AskReason {
  const ask = useContext(ReasonContext);
  if (!ask) throw new Error("useReason outside ReasonProvider");
  return ask;
}

interface ReasonRequest {
  title: string;
  hint?: string;
  initial: string;
  confirmLabel: string;
  resolve: (value: string | null) => void;
}

export function ReasonProvider({ children }: { children: ReactNode }) {
  const [request, setRequest] = useState<ReasonRequest | null>(null);
  const [text, setText] = useState("");

  const ask = useCallback<AskReason>((title, options) => new Promise((resolve) => {
    setText(options?.initial ?? "");
    setRequest({ title, hint: options?.hint, initial: options?.initial ?? "", confirmLabel: options?.confirmLabel ?? "Devam et", resolve });
  }), []);

  function close(value: string | null) {
    request?.resolve(value);
    setRequest(null);
  }

  return (
    <ReasonContext.Provider value={ask}>
      {children}
      {request ? (
        <div className="fixed inset-0 z-50 grid place-items-center p-4" role="dialog" aria-modal="true" aria-labelledby="reason-title">
          <div className="absolute inset-0 bg-black/40" onClick={() => close(null)} />
          <form
            className="relative w-full max-w-md space-y-4 rounded-2xl border border-line bg-surface p-5 shadow-lg"
            onSubmit={(e) => {
              e.preventDefault();
              if (isValidReason(text)) close(text.trim());
            }}
          >
            <h2 id="reason-title" className="text-base font-semibold">{request.title}</h2>
            <label className="block">
              <span className="mb-1.5 block text-sm text-ink-2">
                {request.hint ?? "Gerekçe (zorunlu). İşlemle birlikte değiştirilemez kayıtlara yazılır."}
              </span>
              <textarea
                autoFocus
                rows={3}
                maxLength={MAX_REASON}
                value={text}
                onChange={(e) => setText(e.target.value)}
                className={`${inputClass} resize-y`}
              />
            </label>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="ghost" onClick={() => close(null)}>Vazgeç</Button>
              <Button type="submit" disabled={!isValidReason(text)}>{request.confirmLabel}</Button>
            </div>
          </form>
        </div>
      ) : null}
    </ReasonContext.Provider>
  );
}
