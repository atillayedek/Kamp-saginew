# Kişisel Veri Envanteri

> Kod tabanından çıkarılmıştır (2 Ekim 2026). Sürelerin tek kaynağı `public.compliance_settings` tablosudur
> (yönetim paneli → KVKK ve Uyum → Ayarlar); buradaki süreler varsayılan değerlerdir. Hukuki sebepler
> **avukat onayına tabidir** (bkz. `acik-sorular.md`).

## Önemli not: gerçek mimari

Görev tanımı Firebase (Firestore, Storage, Functions, Security Rules), iOS ve `api.kampusagi.com` varsayıyordu.
Projede bunlar yoktur (kullanıcı kararı D1/D2, 2026-09-25):

| Görevdeki varsayım | Projedeki gerçek karşılık |
|---|---|
| Firestore koleksiyonları | Supabase PostgreSQL tabloları (`supabase/migrations/`) |
| Security Rules + Emulator testleri | Satır düzeyi güvenlik (RLS), `REVOKE`/`GRANT`, tetikleyiciler + `supabase/tests/*.sql` (`scripts/test-db.sh`, CI `database` job'ı) |
| Cloud Functions | Supabase Edge Functions (`supabase/functions/`) + veritabanı fonksiyonları + `pg_cron` |
| Custom claims | `auth.users.raw_app_meta_data` (`role: "admin"` = superadmin, `roles: [...]`) — yalnızca service role yazabilir |
| Firebase Auth MFA | Supabase Auth TOTP MFA (oturum `aal2`) |
| Firebase Hosting admin paneli | `web/` Next.js, Vercel (`/admin`) |
| iOS (SwiftUI) | Yok. App Store belgesi ileride için hazırlandı (`docs/store/app-store-privacy.md`). |
| `api.kampusagi.com` etkinlik backend'i | Yok. Etkinlikler Supabase'de `post_events`/`event_attendees` tablolarındadır. |
| Firebase Analytics / Crashlytics | Kullanılmıyor. Çökme raporları kendi tablomuzda (`client_errors`). Firebase yalnızca FCM için. |

## Barındırma ve bölgeler

| Sistem | Ne tutar | Bölge | Yurt dışı |
|---|---|---|---|
| Supabase (proje `kbyiaqitiukuthjdkwwd`) | Veritabanı, Auth, Storage, Edge Functions, Realtime | **Tespit edilemedi** — bu ortamın Supabase yönetim API'sine erişimi yok. Dashboard → Project Settings → General → Region'dan okunmalı (`acik-sorular.md` S1) | Evet |
| Firebase Cloud Messaging (proje `kampusaginew`) | Bildirim teslimi (veri saklamaz; belirteç Supabase'de) | Küresel (Google) | Evet |
| Google Play | Satın alma, uygulama dağıtımı | Google | Evet |
| Resend | Gönderilen e-postalar ve teslim kayıtları | `eu-west-1` (İrlanda), alan adı `mail.tsdmrdigitalstudio.com.tr` | Evet (AB) |
| Vercel | Web sitesi + yönetim paneli; erişim kayıtları | Fonksiyon bölgesi ayarlanmadı (varsayılan) | Evet |
| Cloudflare | DNS, `iletisim@` e-posta yönlendirmesi | Küresel | Evet |
| GitHub | Kaynak kod, CI (kişisel veri yok) | ABD | — |

## Envanter

Erişim sütunu: **Kişi** = verinin sahibi; **Öğrenci** = görünürlük kurallarına göre diğer onaylı öğrenciler;
**V/M/U/S** = yönetici rolleri verifier / moderator / compliance / superadmin (MFA zorunlu, her okuma ve
değişiklik `admin_audit_logs`'a yazılır); **Servis** = Edge Function'lar (service role).

### 1. Kimlik ve iletişim

| Alan | Yer | Amaç | Hukuki sebep | Saklama | Erişim | Yurt dışı |
|---|---|---|---|---|---|---|
| E-posta | `auth.users.email`, `profiles.email` | Giriş, doğrulama, şifre sıfırlama, hizmet e-postası | md.5/2-c | Üyelik + silme sonrası geri alma süresi (30 g) | Kişi; M/S (kullanıcı listesi, okuma loglanır); Servis | Evet |
| Şifre özeti | `auth.users.encrypted_password` | Giriş | md.5/2-c | Üyelik | Supabase Auth | Evet |
| Ad soyad | `profiles.full_name` | Hesap, diğer öğrencilere gösterim | md.5/2-c | Üyelik | Kişi; Öğrenci (soyad varsayılan gizli: `public_name` "Ayşe Y."); V/M/S | Evet |
| Soyad görünürlük tercihi | `profiles.show_full_name` | Veri minimizasyonu | md.5/2-c | Üyelik | Kişi | Evet |
| Kullanıcı adı | `profiles.username` | Profil, arama | md.5/2-c | Üyelik | Kişi, Öğrenci, V/M/S | Evet |
| Doğum tarihi | Kayıt formu → `auth.users.raw_user_meta_data.kvkk.birth_date` | 18 yaş kontrolü | md.5/2-c | **Saklanmaz**: `kvkk_before_signup` tetikleyicisi kontrol edip aynı işlemde siler | — | — |

### 2. Öğrenci doğrulama

| Alan | Yer | Amaç | Hukuki sebep | Saklama | Erişim | Yurt dışı |
|---|---|---|---|---|---|---|
| Öğrenci belgesi (PDF) | Storage `student-documents/<uid>/...` | Öğrencilik doğrulaması | md.5/2-c, md.5/2-f | Karardan sonra en geç `document_retention_days` (30 g); `retention-run` siler, `deletion_logs`'a yazar | Kişi (yükleme); yalnızca V/S, `admin-document` üzerinden 5 dk'lık imzalı bağlantıyla, gerekçe zorunlu, her açılış loglanır | Evet |
| Belge SHA-256 | `student_verifications.document_sha256` | Kararın sonradan ispatı | md.5/2-e/f | 10 yıl / hesapla birlikte | V/S | Evet |
| Doğrulama kararı, tarih, doğrulayan, ret gerekçesi | `student_verifications` | Doğrulama | md.5/2-c | Hesapla birlikte (CASCADE) | Kişi, V/S | Evet |
| Üniversite, bölüm | `profiles.university_id`, `department` | Doğrulama, topluluk kapsamı | md.5/2-c | Üyelik | Kişi, Öğrenci, V/M/S | Evet |

Belge içeriği (T.C. kimlik no, fotoğraf, adres) okunmaz, OCR yapılmaz, ayrıca kaydedilmez.

### 3. Profil ve içerik

| Alan | Yer | Amaç | Hukuki sebep | Saklama | Erişim |
|---|---|---|---|---|---|
| Biyografi, profil fotoğrafı | `profiles.bio`, Storage `avatars/` | Profil | md.5/2-c | Üyelik | Kişi, Öğrenci |
| Gönderiler, fotoğraflar, anket/etkinlik/ilan | `posts`, `post_media` (+ Storage `post-media/`), `polls`, `poll_options`, `post_events`, `post_listings` | Akış | md.5/2-c | Kişi silene kadar / hesapla | Kişi, Öğrenci (görünürlük kuralı), M (yalnızca şikâyet alıntısı) |
| Yorumlar, beğeniler, oylar, katılımlar, kaydedilenler | `comments`, `post_likes`, `poll_votes`, `event_attendees`, `saved_posts` | Akış | md.5/2-c | Hesapla | Kişi, Öğrenci (kaydedilenler yalnızca kişi) |
| Konu etiketleri (#) | `post_tags` (gönderi metninden türetilir, katlanmış anahtar) | Etiket sayfası, popüler etiketler | md.5/2-c | Gönderiyle birlikte (CASCADE) | Yalnızca fonksiyonlar; Öğrenci gönderiyi görebiliyorsa |
| Kişi etiketleri (@) | `post_mentions`, `comment_mentions` (+ `notifications` `MENTIONED`) | Etiketlenen kişiye bildirim | md.5/2-c | Gönderi/yorum ya da hesap silinince (CASCADE) | Yalnızca fonksiyonlar (API kapalı); etiketlenen kişi bildirimini görür |
| İhtiyaç ilanları (başlık, açıklama, etiket, yer metni, tarih) | `requirements` | Eşleşme | md.5/2-c | Kişi kapatana/silene kadar / hesapla | Kişi, eşleşen öğrenciler |
| Eşleşme itirazları | `match_objections` | md.11/1-g | md.5/2-ç | Hesapla | Kişi (etkisi), U/S |
| Ders notları (PDF) + telif beyanı zamanı | `course_notes` (+ Storage `course-notes/`) | Not paylaşımı | md.5/2-c | Kişi silene kadar / hesapla | Kişi, aynı üniversitedeki Öğrenci, M (arama loglanır) |
| Grup/kanal üyelikleri ve mesajları | `groups`, `group_members`, `group_messages` (+ Storage `group-media/`) | Gruplar | md.5/2-c | Hesap silinince mesaj **anonimleştirilir** (`sender_id = null`), kurulan grup devredilir | Grup üyeleri; M (yalnızca şikâyet + sınırlı bağlam, gerekçeli, loglanır) |

### 4. Mesajlar

| Alan | Yer | Amaç | Hukuki sebep | Saklama | Erişim |
|---|---|---|---|---|---|
| Birebir mesajlar, okundu bilgisi | `conversations`, `conversation_members`, `messages` | Mesajlaşma | md.5/2-c | Hesapla (CASCADE) | Yalnızca sohbet üyeleri. Yöneticiler okuyamaz; şikâyet edilen mesaj için `admin_report_context` öncesi/sonrası `report_context_messages` (5) mesajı gerekçeyle açar ve loglar |

### 5. İşlem güvenliği ve loglar

| Alan | Yer | Amaç | Hukuki sebep | Saklama | Erişim |
|---|---|---|---|---|---|
| Giriş/çıkış/kayıt/şifre sıfırlama: zaman, IP | `access_logs` (`source=auth_server`, Supabase Auth denetim kaydından 5 dakikada bir kopyalanır) | 5651, güvenlik | md.5/2-a/ç | `access_log_retention_days` (730 g); hesap silinince profilden ayrılır | Kişi (kendi kayıtları), U/S (görüntüleme loglanır) |
| Uygulama girişi/çıkışı: IP, cihaz modeli, OS, uygulama sürümü | `access_logs` (`source=app`) | Aynı | Aynı | Aynı | Aynı |
| Başarısız giriş: e-posta **SHA-256**, IP, cihaz | `access_logs` (`login_failed`) | Güvenlik | md.5/2-f | Aynı | U/S |
| Rıza/aydınlatma kayıtları: belge türü, sürüm, SHA-256, işlem, kanal, IP, user-agent, uygulama sürümü | `consent_logs` | İspat | md.5/2-ç/e | `consent_log_retention_days` (3650 g) | Kişi (kendi geçmişi), U/S |
| Yönetici işlemleri: yönetici, roller, işlem, hedef, gerekçe, IP, değişen alanlar | `admin_audit_logs` | Hesap verebilirlik | md.5/2-ç/f | `admin_audit_retention_days` (3650 g) | U/S |
| İmha kayıtları (içerik değil, meta veri) | `deletion_logs` | İmha ispatı | md.5/2-ç | `deletion_log_retention_days` (3650 g) | U/S |
| Engellemeler | `user_blocks` | Güvenlik | md.5/2-c | Hesapla | Kişi |
| Şikâyetler (+ şikâyet anındaki içerik alıntısı) | `reports` | Moderasyon, 5651 | md.5/2-ç/f | Hesapla (şikâyet edenin) | M/S |
| Moderasyon itirazları | `moderation_appeals` | Hakkaniyet | md.5/2-c/f | Hesapla | Kişi, M/S |
| Çökme raporları (anonim uid, cihaz modeli, SDK, sürüm, hata yığını) | `client_errors` | Hata giderme | md.5/2-f | `client_error_retention_days` (180 g) | Servis (dashboard) |
| Uygulamayı açtığı günler | `user_activity_days` | Hareketsiz hesap tespiti, istatistik | md.5/2-f | Hesapla | S (toplu sayılar) |

Tüm log tabloları **eklemeye açık, değiştirilemez**: API rolleri (`anon`, `authenticated`, `service_role`) hiç
yazamaz/okuyamaz; yazma yalnızca `SECURITY DEFINER` fonksiyonlarıyla; `UPDATE`/`DELETE`/`TRUNCATE` tetikleyiciyle
reddedilir (yalnızca saklama süresi dolan satırlar `purge_log` ile silinir ve zincir başlangıcı `deletion_logs`'a
yazılır). Her satır `prev_hash` + `hash` (SHA-256 zinciri) taşır; panelde "Doğrula" düğmesi zinciri yeniden hesaplar.

### 6. Cihaz ve bildirim

| Alan | Yer | Amaç | Hukuki sebep | Saklama | Erişim |
|---|---|---|---|---|---|
| FCM belirteci, platform | `device_tokens` | Bildirim | md.5/2-c | Çıkışta / FCM geçersiz deyince / hesapla | Servis |
| Bildirimler | `notifications` | Uygulama içi bildirim | md.5/2-c | Hesapla | Kişi |
| Duyuru kapatmaları | `announcement_dismissals` | Duyuru | md.5/2-c | Hesapla | Kişi, S (sayı) |

### 7. Ödeme ve abonelik

| Alan | Yer | Amaç | Hukuki sebep | Saklama | Erişim |
|---|---|---|---|---|---|
| Play satın alma jetonu, plan, bitiş | `entitlements` | Premium | md.5/2-c | Hesapla | Servis, S |
| Sipariş no, fiyat, para birimi, dönem | `purchase_events` | Muhasebe, tüketici mevzuatı | md.5/2-ç | 10 yıl; hesap silinince `user_id` boşaltılır (kişisiz) | S |
| Hediye/promosyon kayıtları | `premium_grants`, `promo_redemptions` | Premium | md.5/2-c | Hesapla | S |
| Ön bilgilendirme/mesafeli sözleşme onayı | `consent_logs` (`channel=purchase`) | 6502 ispatı | md.5/2-ç | 3650 g | U/S |

### 8. Pazarlama

| Alan | Yer | Amaç | Hukuki sebep | Saklama | Erişim |
|---|---|---|---|---|---|
| E-posta kampanya izni | `profiles.marketing_opt_in(_at)` + `consent_logs` | Ticari ileti | **Açık rıza** (md.5/1), 6563 | Geri alınana / hesapla | Kişi, S |
| Bildirim kampanya izni | `profiles.marketing_push_opt_in` + `consent_logs` | Ticari ileti | **Açık rıza** | Aynı | Kişi, S |
| Gönderilen toplu e-postalar | `email_broadcasts` | Hizmet duyurusu / kampanya | md.5/2-c / açık rıza | Hesapla değil, kalıcı (içerik kişisel veri içermez) | S |

### 9. Başvurular ve uyum kayıtları

| Alan | Yer | Amaç | Hukuki sebep | Saklama | Erişim |
|---|---|---|---|---|---|
| Veri sahibi başvuruları (no, tür, metin, durum, yanıt, e-posta) | `data_subject_requests` | md.11/13 | md.5/2-ç | Yanıtlanandan sonra `deletion_log_retention_days` (3650 g) | Kişi, U/S |
| Veri dışa aktarma dosyaları | Storage `data-exports/` + `data_exports` | md.11 | md.5/2-ç | `data_export_retention_days` (7 g) | Kişi (imzalı bağlantı, 60 dk) |
| İhlal kayıtları | `breach_register` | md.12/5 | md.5/2-ç | Süresiz (avukata soruldu, S9) | U/S |
| Telif bildirimleri (hak sahibinin adı, e-postası, IP) | `copyright_notices` | FSEK, 5651 | md.5/2-e | Süresiz (S10) | M/S |

### 10. Web sitesi ve panel

| Alan | Yer | Amaç | Hukuki sebep | Saklama |
|---|---|---|---|---|
| Ziyaretçi IP, user-agent | Vercel erişim kayıtları | Barındırma | md.5/2-f | Vercel'in süresi (S5) |
| Panel oturumu | Tarayıcı `localStorage` (`kampusagi-admin`) | Oturum | md.5/2-c | Çıkış/30 dk hareketsizlik |
| Hesap silme sayfası oturumu | Bellekte, saklanmaz | Silme talebi | md.5/2-c | Sayfa kapanınca |

Web sitesinde analitik/reklam çerezi yoktur.

## Özel nitelikli veriler (md.6)

İstenmez ve alan yoktur. `sensitive_terms` tablosundaki kelimeleri içeren ihtiyaç **etiketleri** veritabanında
reddedilir (`sensitive_tag`); serbest metinlerde uygulama uyarır, şikâyet kategorisi "Nefret / ayrımcılık" bağlıdır.
Konum izni (GPS) istenmez; yer bilgisi elle yazılan metindir.
