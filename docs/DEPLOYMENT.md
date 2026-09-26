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
- **Database → Extensions:** `vector` (migration zaten `extensions` şemasında açar).

## 2. Edge Function secret'ları

| Secret | Kullanım | Not |
|---|---|---|
| `OPENAI_API_KEY` | analyze/publish-requirement | Yalnızca backend. Android'e asla girmez. |
| `OPENAI_CHAT_MODEL` | Yapılandırılmış ihtiyaç analizi | JSON schema (strict) destekleyen bir sohbet modeli. |
| `OPENAI_EMBEDDING_MODEL` | Eşleşme vektörü | 1536 boyut üretebilen bir model (ör. `text-embedding-3-small`). |

`SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY` Supabase tarafından fonksiyonlara otomatik verilir.

```bash
supabase secrets set OPENAI_API_KEY=... OPENAI_CHAT_MODEL=... OPENAI_EMBEDDING_MODEL=...
```

### Çökme raporları

Ek yapılandırma gerekmez. Raporlar **Dashboard → Table Editor → `client_errors`** tablosunda görünür (sürüm, cihaz,
istisna türü, yığın izi). Gizlilik politikasında çökme verilerinin (cihaz modeli, Android sürümü, hata kaydı) hesapla
ilişkilendirilerek saklandığı belirtilmelidir.

### Bildirimler

FCM kullanılmaz (D30); ek yapılandırma gerekmez. Canlıda eski `dispatch-push` Edge Function'ı kaldıysa
**Dashboard → Edge Functions → dispatch-push → Delete** ile silinir (artık kullanılmıyor).

### Premium (Google Play Billing)

1. Play Console'da abonelik ürün(ler)ini oluştur (ör. `kampusagi.plus`) ve temel planı/fiyatı tanımla.
2. Play Console → Kurulum → API erişimi: bir Google Cloud service account'u bağla, "Finansal verileri görüntüleme" ve "Siparişleri yönetme" izinlerini ver; JSON anahtarını `GOOGLE_PLAY_SERVICE_ACCOUNT` secret'ı yap. `ANDROID_PACKAGE_NAME=com.kampusagi.android`.
3. `supabase functions deploy verify-purchase`
4. Planı veritabanına ekle (ürün sahibi kararı; örnek değil, şablon):
   ```sql
   insert into public.subscription_plans (play_product_id, name, description, is_active,
       ai_analyze_daily, ai_publish_daily, max_active_requirements)
   values ('<play ürün kimliği>', '<ad>', '<açıklama>', true, <n>, <n>, <n>);
   ```
5. Billing yalnızca Play'den (iç test kanalı dahil) kurulan sürümde çalışır.

### Hesap silme ve gizlilik (Google Play zorunlulukları)

1. `delete-account` Edge Function'ı deploy edilmiş olmalı (yukarıda). Ek secret gerekmez; `SUPABASE_SERVICE_ROLE_KEY` Supabase tarafından sağlanır.
2. **Gizlilik politikası:** herkese açık bir `https://` sayfası yayınla ve adresini `PRIVACY_POLICY_URL` olarak ver
   (GitHub → Settings → Variables → Actions → `PRIVACY_POLICY_URL`, yerelde `local.properties`). Aynı adres Play Console → Uygulama içeriği → Gizlilik politikası alanına girilir.
   Politikada şikayet edilen mesajların yöneticilere gösterildiği belirtilmelidir.
3. **Web'den hesap silme talebi:** Play Console, uygulamayı kurmadan da silme talebi yapılabilen bir web adresi ister (Veri güvenliği → Hesap silme). Bu repo bir web sayfası içermez; ürün sahibinin bu sayfayı sağlaması gerekir.

## 3. İlk yönetici

Supabase Dashboard → Authentication → kullanıcı → **app_metadata**: `{"role": "admin"}` (yalnızca service role/Dashboard yazabilir).
Kullanıcı yeniden giriş yaptığında (yeni JWT) uygulamada "Yönetim" ekranı (doğrulamalar + şikayetler) görünür.

## 4. Android build

GitHub repo secret'ları (istemciye açık değerler): `SUPABASE_URL`, `SUPABASE_ANON_KEY`.
Yerelde aynı adlarla `local.properties` veya ortam değişkeni kullanılabilir.

Release imzası: `keystore.properties` veya ortam değişkenleri
`KAMPUSAGI_KEYSTORE_FILE`, `KAMPUSAGI_KEYSTORE_PASSWORD`, `KAMPUSAGI_KEY_ALIAS`, `KAMPUSAGI_KEY_PASSWORD`.
Sağlanmazsa release AAB imzasız üretilir ve Play'e yüklenemez.

CI'da imzalı AAB için GitHub repo secret'ları: `KAMPUSAGI_KEYSTORE_BASE64` (`base64 -w0 upload.jks` çıktısı),
`KAMPUSAGI_KEYSTORE_PASSWORD`, `KAMPUSAGI_KEY_ALIAS`, `KAMPUSAGI_KEY_PASSWORD`. İş akışı keystore'u yalnızca
runner'ın geçici dizinine açar, varlığını raporlar, içeriğini yazdırmaz ve `jarsigner -verify` ile imzayı doğrular.
Play App Signing kullanılır: bu anahtar yükleme anahtarıdır. `versionCode` CI'da iş akışı çalıştırma numarasıdır
(`KAMPUSAGI_VERSION_CODE`), yerel build'lerde 1'dir.
