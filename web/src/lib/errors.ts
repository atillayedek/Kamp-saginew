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
  email_send_failed: "E-posta gönderilemedi. Resend ayarlarını kontrol et.",
  invalid_link: "Bu bağlantı geçersiz ya da bozulmuş.",
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
