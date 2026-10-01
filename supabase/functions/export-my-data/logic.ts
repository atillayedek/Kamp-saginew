// Pure parts of the personal data export: the readable HTML version of the JSON.

const SECTION_TITLES: Record<string, string> = {
  account: "Hesap",
  profile: "Profil",
  student_verifications: "Öğrenci doğrulama",
  posts: "Gönderiler",
  comments: "Yorumlar",
  post_likes: "Beğeniler",
  saved_posts: "Kaydedilenler",
  poll_votes: "Anket oyları",
  event_attendance: "Etkinlik katılımları",
  requirements: "İhtiyaç ilanları",
  match_objections: "Eşleşme itirazları",
  course_notes: "Ders notları",
  groups: "Gruplar ve kanallar",
  group_messages: "Grup ve kanal mesajları",
  conversations: "Sohbetler (gönderdiğin mesajlar)",
  notifications: "Bildirimler",
  blocked_users: "Engellediğin kişiler",
  reports_made: "Yaptığın şikâyetler",
  premium: "Premium ve satın almalar",
  devices: "Cihazlar",
  activity_days: "Uygulamayı açtığın günler",
  crash_reports: "Hata raporları",
  consents: "Onay ve rıza geçmişi",
  access_logs: "Giriş kayıtları",
  data_subject_requests: "KVKK başvuruların",
};

export function escapeHtml(value: string): string {
  return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

function renderValue(value: unknown): string {
  if (value === null || value === undefined) return "<span class=\"muted\">—</span>";
  if (Array.isArray(value)) {
    if (value.length === 0) return "<span class=\"muted\">Kayıt yok</span>";
    if (value.every((v) => typeof v !== "object" || v === null)) return escapeHtml(value.map(String).join(", "));
    return renderTable(value as unknown[]);
  }
  if (typeof value === "object") return renderObject(value as Record<string, unknown>);
  return escapeHtml(String(value));
}

function renderObject(value: Record<string, unknown>): string {
  const rows = Object.entries(value).map(([k, v]) => `<tr><th>${escapeHtml(k)}</th><td>${renderValue(v)}</td></tr>`);
  return `<table class="kv">${rows.join("")}</table>`;
}

function renderTable(rows: unknown[]): string {
  const keys = [...new Set(rows.flatMap((r) => (r && typeof r === "object" ? Object.keys(r as object) : [])))];
  const head = keys.map((k) => `<th>${escapeHtml(k)}</th>`).join("");
  const body = rows.map((r) => {
    const record = (r ?? {}) as Record<string, unknown>;
    return `<tr>${keys.map((k) => `<td>${renderValue(record[k])}</td>`).join("")}</tr>`;
  }).join("");
  return `<div class="scroll"><table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>`;
}

/** A self-contained, readable page of everything in the export (no scripts, no external resources). */
export function renderExportHtml(data: Record<string, unknown>): string {
  const sections = Object.entries(data)
    .filter(([key]) => key !== "generated_at")
    .map(([key, value]) => `<section><h2>${escapeHtml(SECTION_TITLES[key] ?? key)}</h2>${renderValue(value)}</section>`)
    .join("\n");
  return `<!doctype html>
<html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>KampüsAğı — Kişisel verilerin</title>
<style>
body{font-family:system-ui,sans-serif;margin:0 auto;max-width:960px;padding:16px;color:#1b1f24;background:#fff}
h1{font-size:22px}h2{font-size:17px;margin-top:28px;border-bottom:1px solid #d0d7de;padding-bottom:4px}
table{border-collapse:collapse;width:100%;font-size:13px}th,td{border:1px solid #d0d7de;padding:4px 6px;text-align:left;vertical-align:top;word-break:break-word}
th{background:#f3f6fa}.kv th{width:30%}.muted{color:#6e7781}.scroll{overflow-x:auto}
</style></head><body>
<h1>KampüsAğı — Kişisel verilerin</h1>
<p class="muted">Oluşturulma: ${escapeHtml(String(data.generated_at ?? ""))}. KVKK md.11 kapsamında hazırlanmıştır. Makine tarafından okunabilir sürüm aynı paketteki JSON dosyasıdır.</p>
${sections}
</body></html>
`;
}
