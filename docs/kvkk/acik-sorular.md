# Açık Sorular (avukata / ürün sahibine)

Kod tarafında karar verilemeyen, uydurulmaması gereken her şey. Her madde kodda nerede beklediğiyle birlikte yazıldı.

## Veri sorumlusu ve metinler

- **S0 — Veri sorumlusu kimliği.** Unvan, adres, MERSİS/vergi no, KEP, VERBİS no metinlerde `[VERİ SORUMLUSU UNVANI]`,
  `[ADRES]`, `[MERSİS NO]`, `[VERGİ DAİRESİ VE NO]`, `[KEP]`, `[VERBİS KAYIT NO]` olarak bırakıldı. "TSDMR Digital Studio"
  web sitesinde gösterilen ad olarak verildi; tüzel kişi mi, şahıs işletmesi mi ve tam unvanı nedir? İletişim adresi
  olarak `iletisim@tsdmrdigitalstudio.com.tr` verildi; metinlere avukat onayıyla yazılmalı. Doldurulmuş metinler yönetim
  paneli → KVKK ve Uyum → Hukuki metinler'den **yeni sürüm** olarak yayımlanır (eski sürümler ispat için kalır).
- **S0b — "TASLAK — AVUKAT ONAYI GEREKLİ" ibaresi** şu an yayındaki metinlerde görünür (Play Store gizlilik politikası
  URL'si dahil). Avukat onayından sonra ibaresiz yeni sürüm yayımlanmalı.
- **S0c — Saklama süreleri** (`compliance_settings`): rıza ve yönetici kayıtları için 10 yıl (TBK md.146 genel
  zamanaşımı) seçildi; 5651 trafik kayıtları için 2 yıl (yer sağlayıcı yükümlülüğü 1–2 yıl, üst sınır alındı); belge 30 gün;
  silme geri alma 30 gün; hareketsiz hesap 2 yıl + 30 gün; çökme raporu 180 gün; dışa aktarma 7 gün. Onaylanmalı. Ayar
  değişirse ilgili metinlerin yeni sürümü yayımlanmalı (metinler sayıyı açıkça yazar).

## Mimari ve teknik

- **S1 — Supabase bölgesi** tespit edilemedi (bu ortamın Supabase yönetim API'sine izni yok). Dashboard → Project
  Settings → General → Region. AB dışındaysa taşıma veri göçü gerektirir; yapılmadı.
- **S2 — Görev tanımındaki Firebase/iOS/api.kampusagi.com** projede yok; gereksinimler Supabase/Android/Next.js
  karşılıklarıyla uygulandı (`veri-envanteri.md`, ilk tablo). iOS uygulaması ve ayrı etkinlik API'si yapılacaksa aynı
  log standardı (`access_logs`/`admin_audit_logs` RPC'leri) kullanılmalı.
- **S3 — Yurt dışı aktarım için açık rıza** istenmedi; 7499 sonrası düzenli aktarımlar standart sözleşmeye dayanır. Avukat
  teyit etmeli.
- **S4 — App Check / Play Integrity.** Supabase'de Firebase App Check karşılığı yok. Şu an: RLS, oran sınırları, Auth'un
  kendi sınırları. Play Integrity API ile Edge Function'larda doğrulama eklenebilir (Google Cloud projesi ve servis hesabı
  gerekir). Karar: ürün sahibi.
- **S5 — Vercel erişim kayıtları** ve **Supabase sağlayıcı logları/yedekleri** için saklama süreleri sağlayıcının
  planına bağlı (`saklama_imha_politikasi.md` `[YEDEK SAKLAMA SÜRESİ]`). Plan seviyeleri kontrol edilmeli.
- **S6 — 5651 port bilgisi.** Mobil operatörlerin CGNAT kullanımı nedeniyle kaynak port istenebilir; Supabase/Cloudflare
  kaynak portu iletmiyor, `access_logs.port` boş kalıyor. Gerekli mi, nasıl sağlanacak?
- **S7 — Silme talebinin geri alma süresi boyunca içerik görünürlüğü.** Şu an hesap silme talebinden sonra 30 gün
  boyunca kişinin içerikleri diğerlerine görünür; kişi uygulamayı kullanamaz (yalnızca "vazgeç" ekranı). İstenirse
  bu sürede içerik gizlenebilir.
- **S8 — Birebir sohbetler** hesap silinince karşı taraf için de silinir (sohbet tablosu iki kişiye bağlı). Grup/kanal
  mesajları "Silinmiş kullanıcı" olarak kalır. Birebir mesajların da anonimleştirilerek karşı tarafta kalması istenir mi?
- **S9 — İhlal kaydı ve S10 — telif bildirimi** için saklama süresi belirlenmedi (şu an süresiz). Avukat belirlemeli.
- **S11 — Mesafeli satış.** Ödemeyi Google Play alıyor; satıcı sıfatı ve fatura (e-arşiv) yükümlülüğü avukat/mali
  müşavirle netleşmeli. Premium fiyatı metinde değil, Play ödeme penceresinde gösteriliyor.
- **S12 — İletişim kutusu Gmail'e yönleniyor.** KVKK başvuruları kişisel veri içerir; kurumsal e-posta (veya KEP)
  önerilir; Gmail kullanılacaksa yurt dışı aktarım listesine eklendi.

## Ticari ileti

- **S13 — İYS (İleti Yönetim Sistemi).** E-posta ile ticari ileti (kampanya) gönderilecekse 6563 ve Ticari İletişim ve
  Ticari Elektronik İletiler Hakkında Yönetmelik uyarınca hizmet sağlayıcının **İYS'ye kaydı** ve onayların İYS'ye
  yüklenmesi gerekir (muafiyet eşiği/şartları avukatla teyit edilmeli). Kodda onaylar `consent_logs`'ta kanıtlı tutuluyor;
  İYS entegrasyonu yapılmadı. Kayıt yapılana kadar panelden **kampanya e-postası gönderilmemeli**.
- **S14 — Anlık bildirim kampanyaları** İYS kapsamında mı? (Genel görüş: hayır, ancak açık rıza ve ret yolu yine
  zorunlu — uygulandı.) Teyit edilmeli.

## Yaş ve doğrulama

- **S15 — Yaş beyanı** doğum tarihiyle alınıyor ve doğrulanmıyor (belge yaş içermeyebilir). Yeterli mi?
- **S16 — VERBİS kaydı** yükümlülüğü (çalışan sayısı / bilanço eşikleri, yurt dışı yerleşik olmama) veri sorumlusu
  bilgileri netleşince değerlendirilmeli.

## Operasyon

- **S17 — Yönetici rolleri** atanmalı: Supabase Dashboard → Authentication → kullanıcı → `app_metadata`
  (`{"role":"admin"}` = superadmin; ya da `{"roles":["verifier"]}`, `["moderator"]`, `["compliance"]`). İlk girişte
  web paneli TOTP kurulumu ister.
- **S18 — pg_cron** projede açık değilse (Dashboard → Database → Extensions) erişim kaydı kopyalama ve günlük imha
  zamanlanmaz; panel → KVKK → İmha durumu gösterir. Açılınca panel → KVKK → İmha → "Zamanlamayı kur"
  (bkz. DEPLOYMENT.md §4).
