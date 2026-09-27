// Turkish formatting for numbers, money and dates in the admin panel.

const numberFormat = new Intl.NumberFormat("tr-TR");

export function formatNumber(value: number): string {
  return numberFormat.format(value);
}

/** Micros (1/1,000,000 of the unit) as money, e.g. 49990000 TRY → "₺49,99". */
export function formatMoney(micros: number, currency: string): string {
  try {
    return new Intl.NumberFormat("tr-TR", { style: "currency", currency }).format(micros / 1_000_000);
  } catch {
    return `${(micros / 1_000_000).toFixed(2)} ${currency}`;
  }
}

export function formatDate(iso: string): string {
  return new Intl.DateTimeFormat("tr-TR", { day: "2-digit", month: "short", year: "numeric" }).format(new Date(iso));
}

export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat("tr-TR", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(iso));
}

/** "2026-09-27" → "27 Eyl" for chart axes. */
export function formatDay(day: string): string {
  const [y, m, d] = day.split("-").map(Number);
  return new Intl.DateTimeFormat("tr-TR", { day: "numeric", month: "short" }).format(new Date(Date.UTC(y, m - 1, d)));
}

export const STATUS_LABELS: Record<string, string> = {
  PROFILE_INCOMPLETE: "Profil eksik",
  DOCUMENT_REQUIRED: "Belge bekleniyor",
  PENDING_REVIEW: "İncelemede",
  APPROVED: "Onaylı",
  REJECTED: "Reddedildi",
  SUSPENDED: "Askıda",
};

export const AUDIENCE_LABELS: Record<string, string> = {
  ALL: "Tüm aktif kullanıcılar",
  APPROVED: "Onaylı öğrenciler",
  PENDING_REVIEW: "Belgesi incelemede olanlar",
  DOCUMENT_REQUIRED: "Belge yüklemeyenler",
  PREMIUM: "Premium aboneler",
};

export const REPORT_REASON_LABELS: Record<string, string> = {
  SPAM: "Spam",
  HARASSMENT: "Taciz",
  INAPPROPRIATE: "Uygunsuz içerik",
  FAKE_PROFILE: "Sahte profil",
  OTHER: "Diğer",
};

export const REPORT_TARGET_LABELS: Record<string, string> = {
  POST: "Gönderi",
  COMMENT: "Yorum",
  MESSAGE: "Mesaj",
  USER: "Kullanıcı",
};
