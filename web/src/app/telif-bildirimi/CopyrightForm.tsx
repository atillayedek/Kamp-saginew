"use client";

import { createClient } from "@supabase/supabase-js";
import { useState, type FormEvent } from "react";
import { Button, ErrorBox, inputClass } from "@/components/admin/ui";
import { backendState, config } from "@/lib/config";
import { errorMessage } from "@/lib/errors";

/** Copyright notices from right holders; no account needed (FSEK, 5651). */
export function CopyrightForm() {
  const [form, setForm] = useState({ name: "", email: "", organization: "", work: "", location: "", statement: false });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reference, setReference] = useState<string | null>(null);

  if (backendState() !== "ready") {
    return (
      <p className="rounded-lg border border-warn/40 bg-warn-soft px-4 py-3 text-sm text-warn">
        Form henüz yapılandırılmadı. Bildirimini aşağıdaki iletişim adresine gönderebilirsin.
      </p>
    );
  }
  if (reference) {
    return (
      <p className="rounded-lg border border-line bg-surface px-4 py-3 text-sm">
        Bildirimin alındı. Kayıt numarası: <strong className="font-mono">{reference}</strong>. İnceleme sonucu e-posta adresine bildirilir.
      </p>
    );
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    const client = createClient(config.supabaseUrl, config.supabaseAnonKey, {
      auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
    });
    const { data, error: rpcError } = await client.rpc("submit_copyright_notice", {
      p_claimant_name: form.name,
      p_claimant_email: form.email,
      p_organization: form.organization || null,
      p_work_description: form.work,
      p_content_location: form.location,
      p_statement_confirmed: form.statement,
    });
    if (rpcError) {
      console.error(rpcError);
      setError(errorMessage(rpcError));
    } else {
      setReference(String(data).slice(0, 8).toUpperCase());
    }
    setBusy(false);
  }

  const field = (key: keyof typeof form) => ({
    value: form[key] as string,
    onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setForm({ ...form, [key]: e.target.value }),
  });

  return (
    <form onSubmit={submit} className="space-y-3 rounded-2xl border border-line bg-surface p-5">
      <label className="block text-sm"><span className="text-ink-2">Ad soyad</span>
        <input className={`${inputClass} mt-1`} required minLength={3} maxLength={200} autoComplete="name" {...field("name")} />
      </label>
      <label className="block text-sm"><span className="text-ink-2">E-posta</span>
        <input className={`${inputClass} mt-1`} type="email" required maxLength={254} autoComplete="email" {...field("email")} />
      </label>
      <label className="block text-sm"><span className="text-ink-2">Temsil ettiğin kurum (varsa)</span>
        <input className={`${inputClass} mt-1`} maxLength={200} {...field("organization")} />
      </label>
      <label className="block text-sm"><span className="text-ink-2">Hak sahibi olduğun eser</span>
        <textarea className={`${inputClass} mt-1`} rows={3} required minLength={10} maxLength={4000} {...field("work")} />
      </label>
      <label className="block text-sm"><span className="text-ink-2">İhlal eden içeriğin uygulamadaki yeri (ders kodu, not başlığı, paylaşan kullanıcı adı…)</span>
        <textarea className={`${inputClass} mt-1`} rows={3} required minLength={3} maxLength={2000} {...field("location")} />
      </label>
      <label className="flex items-start gap-2 text-sm">
        <input type="checkbox" className="mt-1" checked={form.statement} onChange={(e) => setForm({ ...form, statement: e.target.checked })} required />
        <span>Bildirimdeki bilgilerin doğru olduğunu ve eserin hak sahibi ya da hak sahibinin yetkili temsilcisi olduğumu beyan ederim.</span>
      </label>
      {error ? <ErrorBox message={error} /> : null}
      <Button type="submit" busy={busy} disabled={!form.statement}>Bildirimi gönder</Button>
    </form>
  );
}
