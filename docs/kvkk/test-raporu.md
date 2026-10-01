# KVKK Uyum — Test Raporu

Tarih: 2026-10-01. Sonuç sözcükleri: **PASS** (çalıştı, geçti) · **FAILED** · **NOT RUN** (çalıştırılamadı; nedeni yazılı).
Görevdeki "Security Rules / Firebase Emulator" testlerinin karşılığı, gerçek migration'ları boş bir PostgreSQL'e
uygulayıp Supabase rolleriyle (`anon`, `authenticated`, `service_role`, JWT claim'leri) çalışan SQL testleridir.

## 1. Veritabanı (RLS + RPC) — `scripts/test-db.sh`

Yerel (PostgreSQL 16) ve CI `database` job'ı: **PASS — 20/20 test dosyası** (001–020).

KVKK testleri (her biri önce kırmızı, sonra yeşil görüldü):

| Test | Kanıtladığı |
|---|---|
| `018_compliance_foundation` | Ayarlar yalnızca yetkiliyle değişir ve loglanır · hukuki metin sürümleri değiştirilemez/silinemez, yayımlamak compliance + MFA + gerekçe ister · kayıtta 18 yaş sunucuda da reddedilir, doğum tarihi saklanmaz, her onay sürüm + SHA-256 ile yazılır, isteğe bağlı rıza reddedilince kayıt olur · metin güncellenince yeniden kabul istenir · rıza geri çekme anında etkili ve loglu · **log tablolarına `anon`/`authenticated`/`service_role` okuyamaz/yazamaz; tablo sahibi bile UPDATE/DELETE/TRUNCATE yapamaz** · erişim logları (giriş, çıkış, başarısız giriş e-posta özeti, şifre sıfırlama; Auth denetim kaydından kopya) · roller birbirinin alanına giremez, MFA'sız (aal1) hiçbir yönetici işlemi çalışmaz, gerekçesiz değişiklik reddedilir · **zincirde oynanan satır "Doğrula" ile tespit edilir; süre dolunca silme zinciri bozmaz** · yönetici Storage'dan öğrenci belgesi okuyamaz |
| `019_data_subject_rights` | md.11 başvurusu, numara, 30 gün sayacı, yanıt bildirimi · ihlal kaydı + 72 saat · soyad gizleme ve diğerlerine dönen adlar · hassas kategori etiketi reddi, eşleşmeye itiraz · içerik kaldırma bildirimi, itiraz, kararın geri alınması · **yönetici özel mesajı yalnızca şikâyet bağlamında, gerekçeyle ve loglanarak görür** · girişsiz telif bildirimi ve oran sınırı · 30 günlük geri alınabilir silme, grup mesajlarının anonimleşmesi, grubun devri, 5651 kayıtlarının profilden ayrılması · belge/dışa aktarma/hareketsiz hesap/çökme raporu imhası ve `deletion_logs` · dışa aktarmada kişinin tüm verisi, günde 3 sınırı · tüm bu işlemlerden sonra zincirler sağlam |
| `020_compliance_schedules` | Zamanlamayı yalnızca compliance kurar; pg_cron yoksa açık hata döner |
| Güncellenen: 003, 006, 008, 011, 016 | Belge erişimi artık yalnızca `admin-document` ile; yeni bildirim türleri; anon'a açık fonksiyonlar listesi; soyad maskesi |

## 2. Edge Function'lar (Deno)

`deno check` + `deno test --allow-read supabase/functions/`: **PASS — 44 test** (yerel ve CI).

KVKK ile ilgili: `export-my-data/logic` (dosya içeriği, okunabilir özet, kısa süreli bağlantı), `retention-run/logic`
(imha listesi, hata durumunda yarıda kalmama), `_shared/user-files` (kişinin tüm bucket'lardaki dosyaları),
`submit-student-document/logic` (SHA-256), `submit-course-note/logic` (hak beyanı zorunlu),
`_shared/logging` (**fonksiyon kaynaklarında console çağrılarına e-posta/token/içerik geçmediği taraması + `redact()`**),
`_shared/fcm` (kampanya kanalı, yeni bildirim metinleri).

## 3. Web (Next.js) — `web/`

`npm run lint`, `typecheck`, `vitest`, `build`: **PASS** (vitest 4 dosya / 16 test: gerekçe başlığı kodlaması,
Markdown güvenli dönüşümü, hata mesajları). Yerel ve CI.

## 4. Android

CI `android` job'ı: `testDebugUnitTest lintRelease assembleDebug bundleRelease`.

| Test | Kanıtladığı |
|---|---|
| `UseCaseTest` (AgePolicy, SignUpForm) | 18 yaş sınırı (18. doğum günü dahil); 18 altı, doğum tarihi yok, aydınlatma okunmadı ya da koşullar kabul edilmediyse istek backend'e gitmez; seçilen rızalar kayıtla iletilir |
| `LegalMarkdownTest` | Hukuki metin dönüşümü |
| `PersonalDataDetectorTest` | TC kimlik (sağlama haneleriyle), TR IBAN (boşluklu/boşluksuz), cep telefonu yazımları; Türkçe katlama ve tam kelime eşleşmesi veritabanıyla aynı |
| `NoPersonalDataInLogsTest` | Kaynaklardaki `Log.*` çağrılarına e-posta, telefon, mesaj/belge içeriği geçmez |
| `FunctionErrorTest` | Yeni KVKK hata kodları Türkçe mesaja çevrilir |

Sonuç: **PASS** (CI `6402e65`).

## 5. Sır taraması

`scripts/scan-secrets.py` (izlenen dosyalar) ve `--history` (tüm git geçmişi): **PASS — 0 bulgu** (yerel, 2026-10-01).
Tarayıcının gerçek anahtar yakaladığı ayrıca denendi. CI'da `secret-scan` job'ı.

## 6. Canlı ortam (salt okunur) — `scripts/live-smoke.sh`

CI `live-smoke`: **PASS** — log tablolarına anon/kullanıcı anahtarıyla erişim yok, hukuki metinler herkese açık okunur,
KVKK RPC'leri oturum ister, yeni Edge Function'lar oturumsuz çağrıyı reddeder.

## 7. Uçtan uca akışlar

| Akış | Durum | Not |
|---|---|---|
| Kayıt (yaş, onaylar) → `consent_logs` | Veritabanı düzeyinde PASS (018, gerçek `auth.users` tetikleyicisi); cihazda **NOT RUN** | Gerçek cihaz/hesap gerekir |
| Giriş → log + metin güncellemesi | Veritabanı düzeyinde PASS; cihazda **NOT RUN** | — |
| Hesap silme → 30 gün → imha | Veritabanı düzeyinde PASS (019, süre ileri alınarak); canlıda **NOT RUN** | pg_cron zamanlaması kurulunca ilk gece çalışır |
| Verilerimi indir | Mantık PASS (Deno), veritabanı PASS (019); canlıda **NOT RUN** | — |
| Yönetici MFA girişi | **NOT RUN** | Rol atanmış gerçek hesap + TOTP uygulaması gerekir |

## CI sonucu

CI `6402e65` (çalıştırma 105, 2026-10-01): **PASS** — tüm job'lar yeşil:

| Job | Sonuç |
|---|---|
| Database migrations and SQL tests (001–020) | PASS |
| Edge Function type check and tests (44) | PASS |
| Web lint, types, tests, build | PASS |
| Leaked secret scan (dosyalar + geçmiş) | PASS |
| Live Supabase checks (read-only) | PASS |
| Android: testDebugUnitTest, lintRelease, assembleDebug, bundleRelease, imza kontrolü | PASS |
| Test APK yayını (`apk-build-105`) | PASS |

Web üretim dağıtımı (Vercel) aynı commit'ten: READY.
