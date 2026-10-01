// Maps backend errors to Turkish messages. Database functions raise stable
// snake_case codes (SQLSTATE P0001); Edge Functions answer {"error": "<code>"}.

const MESSAGES: Record<string, string> = {
  admin_required: "Bu işlem için yetkin yok ya da iki adımlı doğrulama süresi doldu. Yeniden giriş yap.",
  audit_reason_required: "Bu işlem için en az 3 karakterlik bir gerekçe gerekli.",
  document_destroyed: "Belge saklama süresi dolduğu için imha edilmiş; yalnızca karar ve belge özeti duruyor.",
  verification_not_found: "Doğrulama kaydı bulunamadı.",
  unknown_log: "Bilinmeyen kayıt türü.",
  invalid_setting_value: "Değer geçersiz: süreler 0 veya daha büyük tam sayı, metinler 1–2000 karakter olmalı.",
  compliance_setting_missing: "Ayar bulunamadı.",
  legal_document_not_found: "Hukuki metin bulunamadı.",
  invalid_legal_document: "Başlık 3–200, metin en az 50 karakter olmalı.",
  request_not_found: "Başvuru bulunamadı.",
  invalid_breach: "Tespit zamanı geçmişte olmalı, açıklama en az 10 karakter.",
  breach_not_found: "İhlal kaydı bulunamadı.",
  appeal_not_found: "İtiraz bulunamadı ya da zaten karara bağlanmış.",
  invalid_appeal: "Karar açıklaması en az 3 karakter olmalı.",
  notice_not_found: "Bildirim bulunamadı ya da zaten sonuçlanmış.",
  statement_required: "Doğruluk ve yetki beyanını onaylaman gerekiyor.",
  invalid_notice: "Bilgiler eksik ya da hatalı: ad en az 3, eser açıklaması en az 10 karakter ve geçerli bir e-posta gerekli.",
  pg_cron_unavailable: "Bu projede pg_cron eklentisi bulunmuyor; Supabase desteğine danış.",
  pg_cron_not_enabled: "pg_cron açılamadı. Supabase → Database → Extensions → pg_cron'u aç, sonra tekrar dene.",
  rate_limited: "Kısa sürede çok fazla istek gönderildi. Biraz sonra tekrar dene.",
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
  confirmation_required: "Silmeyi onaylamak için kutuya SİL yaz.",
  account_deletion_failed: "Hesap silinemedi. Biraz sonra tekrar dene; sorun sürerse bize yaz.",
  push_not_configured: "Anlık bildirim yapılandırılmamış (Supabase secret FCM_SERVICE_ACCOUNT).",
  invalid_functions_url: "Proje adresi okunamadı. Edge Function ortamını kontrol et.",
  push_failed: "Firebase'e bağlanılamadı. Service account anahtarını kontrol et.",
  announcement_already_pushed: "Bu duyuru telefonlara zaten gönderildi.",
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
