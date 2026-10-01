"use client";

import { createClient } from "@supabase/supabase-js";
import { useState, type FormEvent } from "react";
import { Button, ErrorBox, inputClass } from "@/components/admin/ui";
import { invoke } from "@/lib/admin-api";
import { backendState, config } from "@/lib/config";
import { errorMessage } from "@/lib/errors";
import { CONFIRM_WORD, isDeleteConfirmation } from "./confirm";

/**
 * Signs in with a throw-away client (no stored session), calls the same
 * delete-account function the app uses (which schedules the deletion after the
 * grace period), then signs out.
 */
export function DeleteAccountForm() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmText, setConfirmText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [scheduledFor, setScheduledFor] = useState<string | null>(null);

  if (backendState() !== "ready") {
    return (
      <p className="rounded-lg border border-warn/40 bg-warn-soft px-4 py-3 text-sm text-warn">
        Web üzerinden silme henüz yapılandırılmadı. Uygulamadan silebilir ya da aşağıdaki adrese yazabilirsin.
      </p>
    );
  }

  if (scheduledFor) {
    return (
      <p className="rounded-lg border border-line bg-surface px-4 py-3 text-sm text-ink">
        Silme talebin alındı. Hesabın ve verilerin{" "}
        <strong>{new Date(scheduledFor).toLocaleDateString("tr-TR", { day: "numeric", month: "long", year: "numeric" })}</strong>{" "}
        tarihinde kalıcı olarak silinecek. O güne kadar uygulamaya giriş yapıp vazgeçebilirsin.
      </p>
    );
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!isDeleteConfirmation(confirmText)) {
      setError(errorMessage("confirmation_required"));
      return;
    }
    setBusy(true);
    setError(null);
    const client = createClient(config.supabaseUrl, config.supabaseAnonKey, {
      auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
    });
    try {
      const { error: signInError } = await client.auth.signInWithPassword({ email: email.trim(), password });
      if (signInError) throw new Error(signInError.message);
      const result = await invoke<{ scheduled_for: string }>(client, "delete-account", { confirm: "DELETE" });
      setPassword("");
      setScheduledFor(result.scheduled_for);
      const { error: signOutError } = await client.auth.signOut({ scope: "local" });
      if (signOutError) console.error(signOutError);
    } catch (e) {
      console.error(e);
      setError(errorMessage(e));
      // The session is in memory only; drop it so nothing lingers after a failure.
      const { error: signOutError } = await client.auth.signOut({ scope: "local" });
      if (signOutError) console.error(signOutError);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-3 rounded-2xl border border-line bg-surface p-5">
      <label className="block text-sm">
        <span className="text-ink-2">E-posta</span>
        <input className={`${inputClass} mt-1`} type="email" autoComplete="email" required value={email}
          onChange={(e) => setEmail(e.target.value)} />
      </label>
      <label className="block text-sm">
        <span className="text-ink-2">Şifre</span>
        <input className={`${inputClass} mt-1`} type="password" autoComplete="current-password" required value={password}
          onChange={(e) => setPassword(e.target.value)} />
      </label>
      <label className="block text-sm">
        <span className="text-ink-2">Onaylamak için <strong>{CONFIRM_WORD}</strong> yaz</span>
        <input className={`${inputClass} mt-1`} required value={confirmText} onChange={(e) => setConfirmText(e.target.value)} />
      </label>
      {error ? <ErrorBox message={error} /> : null}
      <Button type="submit" variant="danger" busy={busy} disabled={!email || !password || !confirmText}>
        Hesabımın silinmesini iste
      </Button>
    </form>
  );
}
