# PHASE 1 — PROJECT AUDIT & PRODUCTION GAP ANALYSIS

Tarih: 2026-09-25
Kapsam: `atillayedek/Kamp-saginew` (hedef repo) + aynı hesaptaki iki ilgili repo salt-okunur incelendi:
`atillayedek/kamp-sag-` (Android + Supabase) ve `atillayedek/kamp-sagiweb` (Web + Firebase).
Bu belgede kod yazılmadı; yalnızca mevcut durum ve production kurallarına (Bölüm 0) göre açıklar raporlanır.

---

## 1. Repo gerçeği

| Repo | İçerik | Son commit |
|---|---|---|
| `Kamp-saginew` (hedef) | **Tamamen boş.** Hiç commit, dal veya dosya yok. | — |
| `kamp-sag-` | Android (Kotlin, Compose, Hilt, Clean Architecture) + Supabase (Postgres, RLS, 41 migration, 7 Edge Function, 9 SQL test) | `8325aa4 first commit` |
| `kamp-sagiweb` | Next.js web + Firebase (Firestore, Cloud Functions, Rules testleri) + `packages/contracts` | `a894779 Faz 8: Keşfet sekmesi` |

**Sonuç:** Hedef repoda denetlenecek Android kodu yok. En ileri Android kod tabanı `kamp-sag-` içindeki `KampusAgi-Android/`.

---

## 2. Mimari uyumsuzluk (Bölüm 0 kurallarına göre)

Bölüm 0 şu yığını zorunlu kılıyor: **Firebase Auth + PostgreSQL + pgvector + Gemini (backend) + Gemini Embedding + FCM + Firebase Storage + App Check + Crashlytics + Google Play Billing (sunucu doğrulamalı).**

| Kural | `kamp-sag-` (Android + Supabase) | `kamp-sagiweb` (Web + Firebase) |
|---|---|---|
| 0.5 Firebase Authentication | ❌ Supabase Auth (e-posta + Google) | ✅ Firebase Auth (e-posta/şifre, "geçici" notlu) |
| 0.6 PostgreSQL + gerçek migration | ✅ Postgres, 41 migration, FK/index/RLS | ❌ Firestore (PostgreSQL yok) |
| 0.7 Firebase (FCM/Storage/App Check/Crashlytics) | ❌ Hiçbiri yok (Supabase Storage, Realtime bildirim) | Kısmi (Storage + Rules); FCM/App Check/Crashlytics yok |
| 0.8 Gemini (backend üzerinden) | ❌ Claude (`parse-need`, Anthropic API) | ❌ Claude (Cloud Functions) |
| 0.9 Gemini Embedding + pgvector eşleşme | ❌ pgvector kolonu (1536) + HNSW index var ama **embedding üretilmiyor**; skor kural tabanlı | ❌ pgvector yok; skor kural tabanlı |
| 0.10 PDF → backend → storage → admin review | ✅ `submit-student-document` + `review_student_verification` RPC + SQL test | ✅ Callable + moderatör paneli |
| 0.11 Realtime chat (delivered/read/typing gerçek) | ✅ Supabase Realtime, `last_read_at`, presence/broadcast | Faz 9+ (henüz yok) |
| 0.12 FCM | ❌ Yok (bilinçli karar D4) | ❌ Yok |
| 0.13 Play Billing + sunucu doğrulama | ✅ `PlayBillingGateway` + `verify-purchase` (Play Developer API, acknowledge, entitlement tablosu) | Web — uygulanamaz |
| 0.14 Backend authorization | ✅ RLS her tabloda, testli | ✅ Rules, testli |
| 0.1 / 0.2 Mock/Fake yasağı | ✅ `src/main` temiz (aşağıda) | ❌ `functions/src/ai/fake.ts` production kaynak ağacında (`createFakeNeedExtractor`, emulator'de devreye giriyor) |

**Hiçbir mevcut kod tabanı Bölüm 0 yığınına birebir uymuyor.** Bu bir kullanıcı kararıdır (bkz. §6 KARAR-1).

---

## 3. `kamp-sag-` Android — otomatik tarama sonuçları (`app/src/main`)

| Kontrol | Sonuç |
|---|---|
| `mock/fake/dummy/sample/demo/stub/lorem` (src/main + supabase/functions) | Bulunamadı. Tek eşleşme: text-field `placeholder` parametresi (UI ipucu metni — veri değil). |
| `TODO/FIXME/IMPLEMENT LATER/NOT IMPLEMENTED/TEMP` | Bulunamadı. ("geçici" kelimesi iki yorumda "geçici ağ hatası" anlamında.) |
| `isPremium = true / debugPremium / premiumUser` | Bulunamadı. |
| Test double'lar | Yalnızca `src/test/.../testutil/*Fakes.kt` (0.23'e uygun). |
| Boş `catch` (0.17 ihlali) | **3 adet:** `ChatViewModel.kt:229` (yeniden bağlanma döngüsü, yorumlu), `ChatViewModel.kt:292` (`markRead`, boş), `SupabaseRequirementRepository.kt:83` (`recompute-matches` hatası yutuluyor). Hata loglanmıyor/raporlanmıyor → **düzeltilmeli.** |
| `@Preview` + örnek veri | `src/main` içinde ~37 dosyada Preview; 0.22'ye göre izinli ancak release'te R8 ile elendiği doğrulanmalı (veya `debug` source set'e taşınmalı). |
| Hardcoded config | `gradle.properties` içinde Supabase URL + publishable key (publishable key istemci için tasarlanmıştır, sır değil). `ANDROID_PACKAGE_NAME` varsayılanı `verify-purchase` içinde. |
| Hardcoded üniversite listesi | Yok; `universities` tablosundan (`seed.sql` ile tohumlanıyor — içeriği production verisi olarak ayrıca doğrulanmalı). |

---

## 4. Doğrulama (bu oturumda)

| Doğrulama | Sonuç | Neden |
|---|---|---|
| `./gradlew clean assembleDebug lint` | **NOT RUN** | Ortamda Android SDK yok; `dl.google.com` (SDK + Google Maven) ağ politikası tarafından engelli (HTTP 403). |
| Unit / Roborazzi testleri | **NOT RUN** | Aynı neden (AGP, Google Maven gerektiriyor). |
| Supabase canlı proje (`ggphcgapgwrcdumfldsc`) | **NOT VERIFIED** | `*.supabase.co` ağ politikası tarafından engelli (403); ayrıca bu proje bağlı Supabase hesabında listelenmiyor (hesapta yalnızca `WhereDidIPutIt`, `Rotamuse` var). |
| Supabase SQL testleri (`supabase/tests/001–009`) | **NOT RUN** | Canlı DB erişimi yok. |
| Firebase projesi | **NOT VERIFIED** | Hiçbir repoda gerçek `google-services.json` / Firebase projesi yok. |
| Gemini | **NOT VERIFIED** | Hiçbir kod tabanında Gemini entegrasyonu yok. |

Önceki oturum notlarında (`kamp-sag-/docs/PROGRESS.md`) "275 test ✓" yazıyor; **bu oturumda yeniden çalıştırılmadığı için PASS kabul edilmemiştir.**

---

## 5. Production gap listesi (öncelik sırasıyla)

| # | Açık | Kural | Tür |
|---|---|---|---|
| G1 | Hedef repo boş; kod tabanı seçilmedi / taşınmadı | — | Karar |
| G2 | Auth Supabase Auth → Firebase Auth'a geçmeli (veya kural revize edilmeli) | 0.5 | Mimari |
| G3 | AI sağlayıcı Claude → Gemini (backend) | 0.8 | Mimari |
| G4 | Embedding üretimi yok; pgvector sorgusu kullanılmıyor; skor kural tabanlı | 0.9 | Eksik özellik |
| G5 | FCM yok (bildirimler yalnızca uygulama içi Realtime) | 0.7, 0.12 | Eksik özellik |
| G6 | Crashlytics yok (`ErrorReporter` = Logcat) | 0.7 | Eksik özellik |
| G7 | App Check yok | 0.7 | Eksik özellik |
| G8 | Firebase Storage yok (Supabase Storage) | 0.7 | Mimari |
| G9 | 3 sessiz `catch` bloğu | 0.17 | Kod düzeltmesi |
| G10 | Preview örnek verisinin release'e girmediği doğrulanmadı | 0.22 | Doğrulama |
| G11 | Release imzası / AAB yok | 0.18 | Dış yapılandırma |
| G12 | Play Console ürünleri, RTDN (Pub/Sub) yok | 0.13 | Dış yapılandırma |
| G13 | Gizlilik politikası URL'si, hesap silme web sayfası yok | Play politikası | Dış yapılandırma |
| G14 | Bu ortamda build doğrulaması yapılamıyor | 0.18 | Ortam |

---

## 6. Kullanıcı kararı gerektirenler

**KARAR-1 — Temel kod tabanı ve yığın.** Seçenekler:
- (a) `kamp-sag-` Android kodunu `Kamp-saginew`'e taşı; Supabase Postgres + pgvector kalır; **Auth → Firebase Auth** (Supabase third-party auth ile Firebase JWT kabul edilebilir, RLS korunur), **Claude → Gemini**, FCM/Crashlytics/App Check eklenir. *(Önerilen: en çok çalışan, testli production kodu burada; PostgreSQL şartını zaten karşılıyor.)*
- (b) Sıfırdan yeni Android + ayrı backend (ör. Cloud Run + Cloud SQL Postgres) — en uzun yol.
- (c) Bölüm 0'ı mevcut yığına göre revize et (Supabase Auth / Claude kalır).

**KARAR-2 — AI sağlayıcı:** Kural 0.8 Gemini diyor; mevcut iki repo da Claude kullanıyor ve önceki bir kararınız "Claude yalnızca backend'den" idi. Hangisi geçerli?

**KARAR-3 — FCM:** Önceki notlarda 2026-09-16'da "FCM'yi iptal et" kararı var; Bölüm 0 FCM'yi zorunlu tutuyor. Bölüm 0 geçerli kabul edildi — onay?

---

## 7. BLOCKED — EXTERNAL CONFIGURATION REQUIRED

| REQUIRED | WHY | WHERE | SECURITY |
|---|---|---|---|
| Ağ erişimi: `dl.google.com`, `*.supabase.co` (ve Firebase/Gemini için `*.googleapis.com`) | Android SDK/Google Maven indirme, build/lint/test; backend doğrulama | Cloud ortam ayarları → Network access | Yalnızca gereken alan adları allowlist'e |
| Supabase projesi `ggphcgapgwrcdumfldsc` erişimi (veya yeni proje) | Migration/test/Edge Function doğrulama | Supabase MCP bağlantısı / proje sahibi | Service role key yalnızca Edge Function secret |
| Firebase projesi + `google-services.json` (production) | Auth, FCM, Storage, App Check, Crashlytics | `app/google-services.json` (repo dışı / CI secret) | Service account JSON yalnızca backend secret |
| Gemini API anahtarı | AI analiz + embedding | Backend secret (`GEMINI_API_KEY`) | Android'e asla konmaz |
| Google Play Console: paket adı, abonelik ürün ID'leri, Play Developer API service account | Billing + sunucu doğrulama | `subscription_plans.play_product_id`, `GOOGLE_PLAY_SERVICE_ACCOUNT` secret | Service account yalnızca backend |
| Upload keystore | Release AAB imzası | CI secret / `KEYSTORE_PATH` ortam değişkeni | Repo'ya girmez |
| Gizlilik politikası URL'si, hesap silme talep URL'si | Play Data Safety | Play Console + uygulama içi bağlantı | — |
