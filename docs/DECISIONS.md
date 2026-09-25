# Kararlar

Biçim: `Dn — karar — gerekçe / kaynak`

- **D1 — Android uygulaması sıfırdan yazılır.** — Kullanıcı kararı (2026-09-25). `kamp-sag-` ve `kamp-sagiweb` yalnızca referans.
- **D2 — Kimlik doğrulama Supabase Auth.** — Kullanıcı kararı (2026-09-25); Bölüm 0.5'teki "Firebase Authentication" şartının yerine geçer.
- **D3 — AI sağlayıcı OpenAI (analiz + embedding), yalnızca backend üzerinden.** — Kullanıcı kararı (2026-09-25); Bölüm 0.8/0.9'daki "Gemini" şartının yerine geçer. Anahtar Edge Function secret'ıdır.
- **D4 — Kod yazım desenleri `whered-d-put` projesinden alınır** (config ortamdan, yapılandırılmamış build "bağlı değil" ekranı gösterir, `AppError`/`AppResult`/`safeCall`, CI ile build doğrulama). — Kullanıcı talebi (2026-09-25).
- **D5 — Build doğrulaması GitHub Actions'ta.** — Bu cloud ortamı Android SDK'yı (`dl.google.com`) indiremiyor; CI indirebiliyor.
- **D6 — Hesap yaşam döngüsü `profiles.account_status`:** `PROFILE_INCOMPLETE → DOCUMENT_REQUIRED → PENDING_REVIEW → APPROVED | REJECTED`, ayrıca `SUSPENDED`. Yalnızca backend değiştirir; istemcinin `profiles` tablosuna yazma politikası yoktur, profil `complete_profile` RPC'siyle yazılır.
- **D7 — Profil bilgileri doğrulama başladıktan sonra kilitlenir** (`profile_locked`). — Belge bu bilgilere göre onaylanır; onay sonrası üniversite değişirse doğrulama anlamsızlaşır.
- **D8 — Üniversite listesi veritabanından gelir ve uygulamada gömülü değildir** (Bölüm 0.15). Liste boşsa boş durum gösterilir. Listeyi doldurmak yönetici işidir (service role); resmi kaynaktan (YÖK) veri yüklenmesi dış yapılandırmadır.
- **D9 — Paket adı `com.kampusagi.android`.** — Play'de kalıcıdır; kullanıcı farklı bir paket adı isterse ilk yayından ÖNCE değiştirilmelidir.
