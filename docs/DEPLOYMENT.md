# Dağıtım (Supabase + Android)

Bu belge, kodun canlı ortamda çalışması için **kullanıcının sağlaması gereken** yapılandırmayı listeler.
Hiçbir değer repoya yazılmaz.

## 1. Supabase projesi

**Durum (2026-09-26):** `KampusAgi` projesi (`kbyiaqitiukuthjdkwwd`, eu-central-1, free plan) oluşturuldu.
11 migration uygulandı (repo dosya sürümleri canlı migration geçmişiyle aynıdır), 6 Edge Function deploy edildi,
Supabase güvenlik/performans danışmanı çalıştırıldı ve bulgular `20260926070413_hardening` ile giderildi.
Ücretsiz plandaki 2 aktif proje sınırı nedeniyle `WhereDidIPutIt2` projesi kullanıcı onayıyla **duraklatıldı**
(veri korunur; Dashboard'dan geri açılabilir). Aşağıdaki Dashboard ayarları ve secret'lar henüz **yapılmadı**.
Android build için: `SUPABASE_URL=https://kbyiaqitiukuthjdkwwd.supabase.co`, `SUPABASE_ANON_KEY` = Dashboard → Project Settings → API
(istemciye açık publishable/anon anahtar) — GitHub repo secret'ı olarak girilir.

> **Uyarı:** `SUPABASE_ANON_KEY` yalnızca **publishable** (`sb_publishable_...`) veya eski **anon** JWT anahtarı olabilir.
> **Secret / service_role** anahtarı APK'ya gömülür ve RLS'i herkes için devre dışı bırakır. Gradle build'i bu durumda
> bilerek hata verir, CI `live-smoke` işi de anahtarın rolünü kontrol eder.

```bash
supabase link --project-ref <proje-ref>
supabase db push                       # supabase/migrations/* (üniversite listesi dahil)
supabase functions deploy submit-student-document
supabase functions deploy analyze-requirement
supabase functions deploy publish-requirement
supabase functions deploy delete-account
```

- **Auth → URL Configuration:** Redirect URL'lere `kampusagi://auth-callback` ve `kampusagi://auth-callback?type=recovery` eklenir.
- **Auth → Email:** "Confirm email" açık olmalı.
- **Database → Extensions:** `vector` ilk migration'larda açılır; D54'ten sonra hiçbir tablo kullanmaz.

## 2. Edge Function secret'ları

İhtiyaç eşleştirmesi yapay zekâ kullanmaz (D54); OpenAI secret'ı gerekmez. `SUPABASE_URL`, `SUPABASE_ANON_KEY`,
`SUPABASE_SERVICE_ROLE_KEY` Supabase tarafından fonksiyonlara otomatik verilir.

**Eski fonksiyonları kaldır:** `analyze-requirement` ve `publish-requirement` repodan silindi; canlıda deploy edilmiş
kalmışlarsa Dashboard → Edge Functions'tan sil. Varsa `OPENAI_API_KEY`, `OPENAI_CHAT_MODEL`, `OPENAI_EMBEDDING_MODEL`
secret'larını da sil ve OpenAI anahtarını OpenAI panelinden iptal et.

### Çökme raporları

Ek yapılandırma gerekmez. Raporlar **Dashboard → Table Editor → `client_errors`** tablosunda görünür (sürüm, cihaz,
istisna türü, yığın izi). Gizlilik politikasında çökme verilerinin (cihaz modeli, Android sürümü, hata kaydı) hesapla
ilişkilendirilerek saklandığı belirtilmelidir.

### Bildirimler (push, FCM — D55)

Firebase projesi: `kampusaginew`, Android uygulaması `com.kampusagi.android`.

1. **Android build (GitHub):** Settings → Secrets and variables → Actions → New repository secret
   `GOOGLE_SERVICES_JSON` = Firebase'den indirilen `google-services.json` dosyasının **içeriği**. Build yalnızca istemciye açık
   değerleri (proje kimliği, uygulama kimliği, API anahtarı, gönderici numarası) okur. Yerelde dosyayı `app/google-services.json`
   olarak koy (git'e girmez). Yoksa uygulama derlenir, push kapalı kalır ve bildirimler yalnızca uygulama açıkken Realtime ile gelir.
2. **Edge Function secret'ı (Supabase → Edge Functions → Secrets):** `FCM_SERVICE_ACCOUNT` = Firebase → Proje ayarları →
   Hizmet hesapları → "Yeni özel anahtar oluştur" ile inen JSON'un tamamı. Gizli anahtardır; sohbete/repoya yazılmaz.
3. **Admin paneli → Duyurular → "Telefon bildirimleri" → "Bildirimleri etkinleştir"** (bir kez). Bu, projenin fonksiyon adresini
   veritabanına kaydeder; bundan sonra veritabanı her yeni bildirimi pg_net ile `dispatch-push`'a kendisi gönderir (D56).
   Webhook veya elle üretilen gizli değer gerekmez: doğrulama değerini veritabanı üretir ve yalnızca sunucu tarafında kalır.
   Kart "pg_net yok" derse Supabase → Database → Extensions → pg_net açılır.
4. `admin-push` yalnızca yöneticiye açıktır; aktif bir duyuru "Telefonlara gönder" ile hedefindeki onaylı öğrencilere **bir kez** gönderilir.
5. Kilit ekranında mesaj içeriği gösterilmez ("X sana mesaj gönderdi"). FCM'nin tanımadığı cihaz belirteçleri otomatik silinir.

### Premium (Google Play Billing)

1. Play Console'da abonelik ürün(ler)ini oluştur (ör. `kampusagi.plus`) ve temel planı/fiyatı tanımla. Kullanıcı kararı (D45): aylık temel plan **100 TL** (Türkiye fiyatı; diğer ülkeler için Play'in önerdiği karşılıkları onayla). Uygulama fiyatı Play'den okur, koda fiyat yazılmaz.
2. Play Console → Kurulum → API erişimi: bir Google Cloud service account'u bağla, "Finansal verileri görüntüleme" ve "Siparişleri yönetme" izinlerini ver; JSON anahtarını `GOOGLE_PLAY_SERVICE_ACCOUNT` secret'ı yap. `ANDROID_PACKAGE_NAME=com.kampusagi.android`.
3. `supabase functions deploy verify-purchase`
4. Planı veritabanına ekle (ürün sahibi kararı; örnek değil, şablon):
   ```sql
   insert into public.subscription_plans (play_product_id, name, description, is_active, max_active_requirements)
   values ('<play ürün kimliği>', '<ad>', '<açıklama>', true, <aktif ilan sınırı, ücretsiz 20>);
   ```
5. Billing yalnızca Play'den (iç test kanalı dahil) kurulan sürümde çalışır.
6. Aktif bir plan satırı olmadan admin panelindeki Premium hediye ve promosyon kodu da çalışmaz (plan limitleri bu satırdan gelir). Hediye/kod Play'den bağımsızdır, ücret alınmaz.

### Hesap silme ve gizlilik (Google Play zorunlulukları)

1. `delete-account` Edge Function'ı deploy edilmiş olmalı (yukarıda). Ek secret gerekmez; `SUPABASE_SERVICE_ROLE_KEY` Supabase tarafından sağlanır.
2. **Yasal sayfalar web sitesindedir** (`web/`): `/gizlilik` (KVKK aydınlatma + gizlilik politikası), `/kullanim-kosullari`,
   `/cocuk-guvenligi` (CSAE standartları), `/hesap-silme` (uygulamasız silme: e-posta + şifre ile giriş, `delete-account` çağrısı,
   oturum tarayıcıda saklanmaz). Vercel'de `NEXT_PUBLIC_LEGAL_NAME` (veri sorumlusu adı) ve `NEXT_PUBLIC_CONTACT_EMAIL` tanımlanmazsa
   sayfalar bunu açıkça uyarı olarak gösterir; Play'e göndermeden önce ikisi de dolu olmalı.
3. **Uygulamadaki bağlantılar:** GitHub → Settings → Variables → Actions → `WEBSITE_URL` (ör. `https://kampusagi-nine.vercel.app`).
   Build bundan `PRIVACY_POLICY_URL` (`/gizlilik`, ayrıca verilirse o kazanır) ve `TERMS_URL` (`/kullanim-kosullari`) üretir;
   kayıt ekranındaki 18 yaş / koşullar onay kutusu bu sayfaları açar. Yerelde `local.properties` → `WEBSITE_URL=...`.
4. Play Console'a girilecek tüm metinler, Veri güvenliği / içerik derecelendirmesi yanıtları ve yayın sırası: `docs/play-store/STORE_LISTING.md`.
   Simge ve öne çıkan grafik aynı klasörde. Ekran görüntüleri gerçek cihazdan alınır.

### Toplu e-posta (Resend) — admin paneli

Supabase Dashboard → Edge Functions → Secrets:

| Secret | Değer |
|---|---|
| `RESEND_API_KEY` | Resend API anahtarı (yalnızca gönderme izni yeterli) |
| `RESEND_FROM` | Resend'de doğrulanmış bir alan adından gönderici, ör. `KampüsAğı <duyuru@alanadi>` |
| `PUBLIC_SITE_URL` | Web sitesinin adresi (abonelikten çıkma sayfası `/abonelik-iptal` burada) |
| `UNSUBSCRIBE_SECRET` | En az 32 karakterlik rastgele değer (`openssl rand -hex 32`); değişirse eski bağlantılar geçersiz olur |

Eksikse `admin-broadcast` `email_not_configured` döner ve panel bunu açıkça yazar. Pazarlama gönderimi için dört secret da gerekir.
Resend hesabında şu an doğrulanmış tek alan adı başka bir ürüne ait; KampüsAğı için kendi alan adını Resend'de doğrula.

## 3. Web sitesi (Vercel)

- Alan adı: `https://kampusagi.tsdmrdigitalstudio.com.tr` (Cloudflare'de `kampusagi` CNAME → `cname.vercel-dns.com`, DNS only). `kampusagi-nine.vercel.app` da çalışır.
- Veri sorumlusu `TSDMR Digital Studio`, iletişim `iletisim@tsdmrdigitalstudio.com.tr` (Vercel env). Toplu e-posta `mail.tsdmrdigitalstudio.com.tr` alt alan adından gider (Resend, SPF/DKIM/DMARC doğrulandı).

- Proje: `kampusagi` (Vercel), kök dizin `web`, framework Next.js.
- Ortam değişkenleri: `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY` (yalnızca `sb_publishable_…`; gizli anahtar
  konursa site "kurulum gerekli" gösterir), `NEXT_PUBLIC_LEGAL_NAME`, `NEXT_PUBLIC_CONTACT_EMAIL` (yasal sayfalar için gerekli), isteğe bağlı `NEXT_PUBLIC_PLAY_STORE_URL`.
- Admin paneli `/admin`. Landing page'de bağlantısı yoktur, arama motorlarına kapalıdır.
- Supabase Auth → URL Configuration'a site adresini ekle (şifre sıfırlama bağlantıları için).

## 4. Yöneticiler, MFA ve KVKK kurulumu

Roller Supabase Dashboard → Authentication → kullanıcı → **app_metadata**'ya yazılır (yalnızca service role/Dashboard yazabilir):

| app_metadata | Rol | Görür |
|---|---|---|
| `{"role": "admin"}` | superadmin | Her şey |
| `{"roles": ["verifier"]}` | Belge doğrulama | Doğrulamalar (belge 5 dk'lık bağlantıyla) |
| `{"roles": ["moderator"]}` | İçerik | Moderasyon, itirazlar, telif bildirimleri |
| `{"roles": ["compliance"]}` | KVKK | Loglar, başvurular, ihlaller, hukuki metinler, imha, ayarlar |

Roller birleştirilebilir (`["verifier","moderator"]`). Kişi `/admin`'e girince TOTP (Google Authenticator vb.) kurar; her
girişte kod ister. 30 dakika hareketsizlikte çıkış yapılır. Her işlem için kısa bir gerekçe istenir ve loglanır.
Uygulamada yönetim ekranı yoktur; ayarlarda "Yönetim paneli" bağlantısı web paneline gider.

KVKK ilk kurulum (compliance ya da superadmin):
1. Supabase → Database → Extensions → **pg_cron** ve **pg_net** açık olmalı.
2. Panel → KVKK ve Uyum → İmha → **Zamanlamayı kur** (erişim kaydı kopyalama her 5 dk, imha her gün 03:17 UTC) ve
   (fonksiyon adresi kayıtlı değilse) **Etkinleştir**.
3. Hukuki metinlerdeki yer tutucuları avukatla doldur → panel → Hukuki metinler → yeni sürüm yayımla (eski sürümler kalır,
   kullanıcılar bir sonraki girişte yeni sürümü görür/kabul eder).
4. Saklama süreleri: panel → Ayarlar (`docs/kvkk/acik-sorular.md` S0c). Süre değişirse ilgili metnin yeni sürümünü yayımla.

## 5. Android build

GitHub repo secret'ları (istemciye açık değerler): `SUPABASE_URL`, `SUPABASE_ANON_KEY`.
Yerelde aynı adlarla `local.properties` veya ortam değişkeni kullanılabilir.

Release imzası: `keystore.properties` veya ortam değişkenleri
`KAMPUSAGI_KEYSTORE_FILE`, `KAMPUSAGI_KEYSTORE_PASSWORD`, `KAMPUSAGI_KEY_ALIAS`, `KAMPUSAGI_KEY_PASSWORD`.
Sağlanmazsa release AAB imzasız üretilir ve Play'e yüklenemez.

Yükleme anahtarını kendi bilgisayarında `bash scripts/create-upload-keystore.sh` ile oluştur; betik secret değerlerini
nereye gireceğini yazar, hiçbir şeyi repoya koymaz. CI'da imzalı AAB için GitHub repo secret'ları: `KAMPUSAGI_KEYSTORE_BASE64` (`base64 -w0 upload.jks` çıktısı),
`KAMPUSAGI_KEYSTORE_PASSWORD`, `KAMPUSAGI_KEY_ALIAS`, `KAMPUSAGI_KEY_PASSWORD`. İş akışı keystore'u yalnızca
runner'ın geçici dizinine açar, varlığını raporlar, içeriğini yazdırmaz ve `jarsigner -verify` ile imzayı doğrular.
Play App Signing kullanılır: bu anahtar yükleme anahtarıdır. `versionCode` CI'da iş akışı çalıştırma numarasıdır
(`KAMPUSAGI_VERSION_CODE`), yerel build'lerde 1'dir.
