# KampüsAğı yayın incelemesi — 2026-10-09

**Yayın kararı: BLOCKED — production-ready onayı verilmedi.**

Depo önce indirildi; Android/Kotlin/Compose istemcisi, Supabase Auth/Postgres/RLS/Storage/Edge Functions, Next.js yönetim ve yasal sayfaları, mevcut kararlar ve yayın iş akışı incelendi. iOS bu Play Store incelemesinin derleme kapsamına alınmadı. İnceleme mutlak hatasızlık garantisi değildir.

## Düzeltilen bulgular

| Bulgu | Değişiklik | Doğrulama |
|---|---|---|
| İptal edilmiş fakat süresi bitmemiş Play aboneliği reddediliyordu | `play.ts` CANCELED durumunda ödenmiş süreyi korur; süre ve hesap/ürün denetimleri devam eder | Yeni test önce başarısız, düzeltmeden sonra başarılı |
| Yenileme, iade ve ödeme bekletme durumu sunucuda otomatik güncellenmiyordu | Yeni `play-rtdn` fonksiyonu Google OIDC imza/issuer/audience/expiry/e-posta denetiminden sonra Play API'den güncel durumu okur. Yalnızca mevcut hesaba bağlı token güncellenir; eski/tekrarlanan olaylar yok sayılır | RSA imza, yanlış audience/gönderen/süre, abonelik durumu ve SQL yetki/idempotency testleri |
| Acknowledge hatası satın alma başarısı gibi dönüyordu | `verify-purchase` 502 döner; RTDN bağlı satın almalar için acknowledge işlemini de yeniden dener | Edge Functions tür kontrolü; mevcut acknowledge API testleri. Gerçek Play testi henüz yapılmadı |
| Hatalı Markdown pipe satırı sonsuz döngü yaratabiliyordu | Android ve web okuyucuları satırı normal metin olarak tüketir | İki platformda regresyon testi |
| Release lint üç enum serializer için R8 obfuscation riski bildirdi | `PostCategory`, `GroupKind`, `GroupRole` `@Keep` ile korundu | Son commit için GitHub CI release lint ve küçültülmüş derleme PASS |
| Exception mesajları Logcat ve çökme raporlarına hassas bilgi taşıyabiliyordu | 39 Logcat çağrısından ham exception çıkarıldı; raporlarda mesaj yerine sınıf ve stack frame tutulur; eski raporlar da yüklemeden önce temizlenir | Gizlilik taraması ve çökme raporu testleri |
| Küçük sıkıştırılmış görseller büyük bitmap bellek tahsisi yaratabiliyordu | Profil ve gönderi fotoğraflarında çözünürlük önceden okunur ve örnekleme uygulanır | Büyük/taşma sınırına yakın boyutlar için örnekleme testi |
| Çıkıştan sonra tamamlanan görsel indirmeleri önbelleği geri doldurabiliyordu | Oturum nesli değişince eski sonuçlar atılır; avatar önbelleği 128 görselle sınırlanır | Kod incelemesi; cihazda hesap değiştirme testi bekliyor |
| CI eksik yapılandırılmış ve imzasız AAB üretebiliyordu | `KAMPUSAGI_REQUIRE_PRODUCTION=true` HTTPS/backend/Firebase/imza/sürüm değerlerini zorunlu kılar. Manuel CI `production` seçeneği canlı yasal sayfaları da kontrol eder | Normal tanı derlemesi korunur; production kontrolü mevcut taslak metinlerde FAIL verir |
| APK yalnızca Android job'ına bağlı yayımlanıyordu | Yayımlama tüm CI job'larına ve varsayılan dala bağlandı | İş akışı incelemesi |
| Web bağımlılık açıkları | Next.js/eslint-config-next 16.4.0; sharp >=0.35.5 ve source-map-js >=1.2.2 overrides | Yeniden lint/tür/test/build ve npm audit |

## Doğrulama sonuçları

| Kontrol | Sonuç |
|---|---|
| SQL migration'ları + 001–022 test dosyaları | PASS — temiz, izole PostgreSQL/pgvector Docker veritabanında |
| Edge Functions | PASS — 48 test ve tüm index.ts dosyalarında `deno check` |
| Web | PASS — lint, TypeScript, 22 test ve Next.js production build |
| Web runtime bağımlılıkları (`npm audit --omit=dev`) | PASS — 0 bulgu |
| Web tüm bağımlılıklar | 5 yüksek bulgu — tek yamalanmamış `braces` açığının ESLint/fast-glob zinciri; yalnız geliştirme bağımlılığı |
| Dosya ve Git geçmişi sır taraması | PASS — 0 bulgu |
| Canlı Supabase `scripts/live-smoke.sh` | PASS — istemci anahtarı, e-posta onayı, anonim/RPC erişimi, fonksiyon kimlik denetimi, sahte abonelikten çıkma, özel log tabloları |
| Canlı dört yasal/hesap silme sayfası | HTTP 200; hesap silme sayfası kurulu. Gizlilik/koşullar/çocuk güvenliği metinleri taslak |
| Canlı production kontrolü | FAIL — 13 aktif metin taslak/yer tutucu içeriyor; RTDN endpoint deployment/server yapılandırması hazır değil |
| Android test/lint/APK/AAB | 97 yerel birim testi PASS, lint 0 error/fatal; son enum koruma düzeltmesi dahil GitHub CI test/lint/APK/R8 AAB PASS |
| Production yapılandırma negatif testi | PASS — yerelde eksik Firebase yapılandırması derlemeyi durdurdu |
| Yerel AAB arm64 native ELF hizalaması | PASS — iki kütüphanenin PT_LOAD hizalaması en az 16 KB; cihazda 16 KB çalışma testi yapılmadı |
| Telefon/emülatör testi | NOT RUN — bağlı telefon yok; mevcut AVD Windows hardware acceleration hatasıyla açılmadı |
| Gerçek Play Billing/push/hesap silme uçtan uca | NOT RUN — gerçek cihaz/hesap/Play iç test gerekir |

Kod commit'i `afbe0ad36d5d302e8c755d6585bb5fb776c628d3` için [CI 37881624591](https://github.com/atillayedek/Kamp-saginew/actions/runs/37881624591) ve [CI 37881614345](https://github.com/atillayedek/Kamp-saginew/actions/runs/37881614345) başarılı tamamlandı. Altı kontrol grubu geçti. Android build 6m 26s sürdü; signature kontrolü `jar is unsigned` bildirdi. Bu başarılı tanı CI sonucu imzalı production build onayı değildir. Değişiklikler [taslak PR #2](https://github.com/atillayedek/Kamp-saginew/pull/2) içinde; birleştirme veya canlı deployment yapılmadı.

## Yayın öncesi tamamlanması gerekenler

1. **Yükleme imzası:** GitHub secret listesinde `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GOOGLE_SERVICES_JSON` mevcut; `KAMPUSAGI_KEYSTORE_BASE64`, `KAMPUSAGI_KEYSTORE_PASSWORD`, `KAMPUSAGI_KEY_ALIAS`, `KAMPUSAGI_KEY_PASSWORD` yok. Kullanılacak mevcut upload anahtarını koruyarak bunları tanımla. Yeni anahtar mevcut Play anahtarı yerine gelişigüzel kullanılmamalı.
2. **Hukuki metinler:** gerçek şirket/adres/iletişim/süre bilgileri ve onaylı metinlerin yeni sürümleri yönetim panelinden yayımlanmalı. Taslak ibaresini kaldırmak tek başına onay değildir. `scripts/verify-production.py` tekrar PASS olmalı.
3. **Yeni backend kodu:** `20261009100000_billing_reconciliation.sql` migration'ı; `play-rtdn` ve değişen `verify-purchase` deploy edilmeli. Pub/Sub OIDC push + Play topic + iki RTDN secret'ı kurulmalı; ayrıntılar `docs/DEPLOYMENT.md`. Yerel test canlı deploy kanıtı değildir.
4. **İç test:** gerçek cihazda kayıt/e-posta doğrulama/profil/PDF/admin onayı; gönderi/fotoğraf/yorum/şikâyet/engelleme; sohbet/grup; push; veri dışa aktarma/silme talebi/iptal ve imha zamanlaması doğrulanmalı. Play test satın alması/yenileme/iptal/iade testleri yapılmalı.
5. **Play Console:** Veri güvenliği, hedef kitle, çocuk güvenliği irtibatı, uygulama erişimi ve gerçek ekran görüntüleri mevcut mağaza belgeleri ile uygulamanın gerçek davranışına göre doğrulanmalı. Console ayarları bu oturumda görülmedi.
6. **Son yayın çıktısı:** yeni commit için tüm CI job'ları geçtikten sonra manuel `production=true` build çalıştırılmalı; imzalı AAB'nin signature doğrulaması ve sürüm numarası kontrol edilmeli. Yerel imzasız AAB yükleme dosyası değildir.

## Kalan geliştirme bağımlılığı riski

`braces <=3.0.3` için yayımlanmış yamalı sürüm yoktur. `eslint-config-next → @next/eslint-plugin-next → fast-glob → micromatch → braces` zinciri lint sırasında kullanılır; production runtime audit'inde yer almaz. Güvenilmeyen glob kalıpları lint yapılandırmasına alınmamalı; upstream düzeltmesi çıktığında zincir güncellenmelidir. Açık gizlenmedi veya uyumsuz eski Next.js lint yapılandırmasına zorla dönülmedi.

Kaynaklar: [braces güvenlik duyurusu](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm), [Google abonelik yaşam döngüsü](https://developer.android.com/google/play/billing/lifecycle/subscriptions), [Google RTDN](https://developer.android.com/google/play/billing/rtdn-reference), [Pub/Sub OIDC doğrulaması](https://docs.cloud.google.com/pubsub/docs/authenticate-push-subscriptions), [Play hesap silme gereklilikleri](https://support.google.com/googleplay/android-developer/answer/13327111).

## Yerel derleme

Windows'ta çalışma klasöründeki Türkçe karakterler AGP'nin path kontrolünü tetikledi. Klasör taşınmadan `subst K: <çalışma klasörü>` ile ASCII yol kullanıldı; JDK 17 ve mevcut Android SDK 36 ile derleme başlatıldı. `local.properties` yalnızca sitede zaten yayımlanmış istemciye açık backend yapılandırmasını içerir, git'e girmez. Firebase/keystore yerel olarak alınmadı; GitHub secret değerleri okunmadı veya yazdırılmadı.
