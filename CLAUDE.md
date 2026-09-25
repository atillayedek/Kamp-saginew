# KampüsAğı Android — Çalışma Kuralları

Önce `docs/PHASE1_AUDIT.md`, `docs/DECISIONS.md` ve kullanıcının Bölüm 0 production kurallarını oku. Bölüm 0 her şeyin üstündedir;
kullanıcının `docs/DECISIONS.md`'deki açık kararları Bölüm 0'ın ilgili maddesini revize eder.

## Yığın (kullanıcı kararı, 2026-09-25)

- Android: Kotlin, Jetpack Compose + Material 3, Hilt, Coroutines/Flow, Navigation Compose (type-safe), supabase-kt.
- Backend: Supabase — PostgreSQL + RLS, Supabase Auth, Storage, Edge Functions. Migration'lar `supabase/migrations/`.
- AI: OpenAI — yalnızca backend (Edge Function) üzerinden; `OPENAI_API_KEY` backend secret'ıdır, Android'e asla girmez.
  Embedding OpenAI embedding modeliyle üretilir ve pgvector'a yazılır.
- Sıfırdan yazıldı; `kamp-sag-` / `kamp-sagiweb` yalnızca referanstır, kod kopyalanmaz.

Kod yazım mantığı, aynı hesaptaki `atillayedek/whered-d-put` (WhereDidIPutIt) projesindeki desenlerden alınmıştır.

## Mimari

- Tek modül, Clean Architecture + MVVM, gereksiz soyutlama yok:
  `core/` (config, Hilt modülleri, design system, util) · `domain/` (model, repository arayüzleri, saf mantık) ·
  `data/` (uzak istemci + DTO, Room cache, DataStore, repository implementasyonları, sync) ·
  `presentation/` (Compose ekranları + ViewModel, type-safe Navigation Compose).
- Room yalnızca yerel cache / offline içindir; kaynak gerçek backend'dir (Bölüm 0.6).
- Uzun süren/yeniden denenebilir yazmalar WorkManager (network constraint + exponential backoff) ile yapılır.

## Konfigürasyon ve sırlar

- Tüm yapılandırma önce ortam değişkeninden, sonra git'e girmeyen `local.properties` / `keystore.properties`
  dosyasından okunur (`config("NAME")` yardımcı fonksiyonu, `app/build.gradle.kts`). Repoya hiçbir değer commit edilmez.
- Yalnızca istemciye açık değerler `BuildConfig`'e girer (proje URL'si, publishable/anon key, Firebase istemci yapılandırması).
  Service account, Gemini anahtarı, Play Developer API anahtarı yalnızca backend secret'tır.
- `AppConfig.isBackendConfigured` false ise uygulama **"Henüz bağlı değil / kurulum gerekli"** ekranını gösterir.
  Sahte veri, sahte giriş veya sahte başarı gösterilmez (Bölüm 0.2).
- Release imzası yalnızca keystore sağlanmışsa yapılandırılır; yoksa release build imzasız üretilir ve bu raporlanır.

## Hata yönetimi

- `domain/model/AppError` — UI'ın açıklayabildiği hata türleri; ham exception, HTTP kodu, SQL hatası data katmanından çıkmaz.
- `AppResult<T>` = `Success` / `Failure(AppError)`; repository'ler bunu döner.
- `data/remote/ErrorMapping.kt`: `Throwable.toAppError()`, `Throwable.isTransient()` (yeniden denenebilir mi),
  `safeCall {}` — `CancellationException`'ı asla yutmayan `runCatching`.
- Boş `catch {}` yasak (Bölüm 0.17). En iyi gayretle yapılan işlemler bile hatayı loglar/raporlar ve UI state'e yansıtır.
- ViewModel tek bir `UiState` data class'ı `StateFlow` olarak açar (`stateIn(WhileSubscribed(5_000))`);
  loading / empty / error durumları state'te açıkça modellenir.

## Build doğrulaması

- Bu cloud ortamında Android SDK / Google Maven erişimi yoktur. Build doğrulaması **GitHub Actions** üzerinde yapılır:
  `.github/workflows/android.yml` → `./gradlew testDebugUnitTest lintRelease assembleDebug bundleRelease`.
- Workflow secret'ların **var olup olmadığını** raporlar, değerlerini asla yazdırmaz.
- Bir faz yalnızca CI'daki gerçek sonuç yeşilse COMPLETE yazılabilir; çalışmayan adım `NOT RUN`, başarısız adım `FAILED`.

## Veritabanı testleri

- `scripts/test-db.sh` tüm migration'ları boş bir PostgreSQL'e uygular ve `supabase/tests/[0-9]*.sql` testlerini çalıştırır.
  `supabase/tests/_supabase_harness.sql` yalnızca test için Supabase rollerini, varsayılan yetkileri ve `auth` şemasını taklit eder.
- Her RLS/RPC değişikliği önce başarısız olan bir SQL testiyle gelir. CI'da `database` job'ı aynı betiği çalıştırır.
- İstemciye dönen veritabanı hataları `SQLSTATE P0001` + sabit snake_case mesajdır (ör. `username_taken`);
  `ErrorMapping.kt` bunları `AppError`'a çevirir.

## Test

- Saf domain mantığı (parser, politika, skor normalizasyonu) `src/test` altında JUnit ile test edilir.
- Test double'lar yalnızca `src/test/` ve `src/androidTest/` altında (Bölüm 0.23).

## Dil

- Kullanıcıya görünen metinler `strings.xml` (tr varsayılan); kod tanımlayıcıları İngilizce.
