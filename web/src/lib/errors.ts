// Maps backend errors to Turkish messages. Database functions raise stable
// snake_case codes (SQLSTATE P0001); Edge Functions answer {"error": "<code>"}.

const MESSAGES: Record<string, string> = {
  admin_required: "Bu işlem için yönetici yetkisi gerekiyor.",
  not_authenticated: "Oturumun sona erdi. Yeniden giriş yap.",
  invalid_page: "Geçersiz sayfa isteği.",
  invalid_audience: "Geçersiz alıcı grubu.",
  invalid_request: "Form eksik ya da hatalı. Konu ve metni kontrol et.",
  invalid_action: "Bu şikayet için bu işlem yapılamaz.",
  report_not_found: "Şikayet bulunamadı ya da zaten çözülmüş.",
  user_not_suspended: "Bu hesap askıda değil.",
  rejection_reason_required: "Reddetmek için 3–500 karakterlik bir gerekçe yaz.",
  verification_not_pending: "Bu belge zaten incelenmiş.",
  invalid_decision: "Geçersiz karar.",
  document_unavailable: "Belge açılamadı. Dosya silinmiş olabilir.",
  no_recipients: "Bu gruba uyan alıcı yok.",
  email_not_configured:
    "E-posta gönderimi yapılandırılmamış (RESEND_API_KEY / RESEND_FROM; pazarlama için PUBLIC_SITE_URL ve UNSUBSCRIBE_SECRET).",
  email_sender_invalid:
    "RESEND_FROM geçersiz. Şu biçimde olmalı: KampüsAğı <duyuru@alanadi> ya da duyuru@alanadi (tırnak yok, alan adı Resend'de doğrulanmış olmalı).",
  email_send_failed: "E-posta gönderilemedi. Resend ayarlarını kontrol et.",
  invalid_link: "Bu bağlantı geçersiz ya da bozulmuş.",
  invalid_announcement: "Başlık 2–80, metin 2–1000 karakter olmalı; bitiş en fazla 90 gün sonra.",
  announcement_not_found: "Duyuru bulunamadı ya da zaten bitmiş.",
  plan_not_available: "Seçilen plan aktif değil. Önce subscription_plans tablosunda aktif bir plan olmalı.",
  invalid_grant: "Süre 1–365 gün, not en fazla 200 karakter olmalı.",
  grant_not_found: "Hediye bulunamadı ya da zaten geri alınmış.",
  user_not_found: "Kullanıcı bulunamadı.",
  invalid_promo_code: "Kod 4–32 karakter (A–Z, 0–9, -), süre 1–365 gün, kullanım 1–100.000 olmalı; bitiş gelecekte olmalı.",
  promo_code_taken: "Bu kod zaten var.",
  promo_code_not_found: "Kod bulunamadı ya da zaten kapatılmış.",
  server_error: "Sunucu hatası. Biraz sonra tekrar dene.",
  "Invalid login credentials": "E-posta ya da şifre hatalı.",
  "Email not confirmed": "Bu e-posta adresi henüz onaylanmamış.",
};

const NETWORK_HINTS = ["Failed to fetch", "NetworkError", "Load failed", "fetch failed"];

export function errorMessage(error: unknown): string {
  const raw = error instanceof Error ? error.message : typeof error === "string" ? error : "";
  if (MESSAGES[raw]) return MESSAGES[raw];
  if (NETWORK_HINTS.some((hint) => raw.includes(hint))) return "Sunucuya ulaşılamadı. İnternet bağlantını kontrol et.";
  for (const [code, message] of Object.entries(MESSAGES)) {
    if (/^[a-z_]+$/.test(code) && raw.includes(code)) return message;
  }
  return "Beklenmeyen bir hata oluştu.";
}
