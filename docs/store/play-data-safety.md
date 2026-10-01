# Google Play — Veri güvenliği formu yanıtları

> Kaynak: `docs/kvkk/veri-envanteri.md`. Envanter değişirse bu dosya ve Play Console formu birlikte güncellenir.
> Gizlilik politikası URL'si: `<WEBSITE_URL>/gizlilik` (veritabanındaki yürürlükteki sürüm). Hukuki metinler avukat
> onayına kadar "TASLAK" ibaresi taşır (`acik-sorular.md` S0b).

## Genel sorular

| Soru | Yanıt | Dayanak |
|---|---|---|
| Uygulama, gerekli kullanıcı veri türlerini topluyor veya paylaşıyor mu? | **Evet** | Envanter |
| Toplanan tüm veriler aktarım sırasında şifreleniyor mu? | **Evet** | Tüm trafik HTTPS (Supabase, FCM, Play) |
| Kullanıcıların verilerinin silinmesini isteme yolu var mı? | **Evet** | Uygulama: Ayarlar → Gizlilik ve KVKK → Hesabı sil (30 gün geri alma); web: `<WEBSITE_URL>/hesap-silme` |
| Veriler üçüncü taraflarla paylaşılıyor mu? | **Hayır** | Supabase, Firebase Cloud Messaging, Resend, Vercel **hizmet sağlayıcı** (Play tanımında paylaşım sayılmaz). Reklam ağı, veri satışı, analitik SDK'sı yok |
| Uygulama Families politikasına tabi mi? | Hayır — hedef kitle 18+ | Kayıtta doğum tarihiyle 18 yaş kontrolü (tarih saklanmaz) |
| Bağımsız güvenlik incelemesi (MASA) | Hayır (yapılmadı) | — |

## Toplanan veri türleri

Hepsi: **Toplanıyor**, **Paylaşılmıyor**, **Geçici olarak işlenmiyor** (aksi yazılmadıkça).

| Play veri türü | Ne | Zorunlu / isteğe bağlı | Amaçlar |
|---|---|---|---|
| Kişisel bilgiler → Ad | Ad soyad (diğer öğrencilere soyad varsayılan olarak baş harf) | Zorunlu | Uygulama işlevselliği, Hesap yönetimi |
| Kişisel bilgiler → E-posta adresi | Hesap e-postası | Zorunlu | Hesap yönetimi, Uygulama işlevselliği, Geliştirici iletişimi, Pazarlama (**yalnızca ayrı açık rızayla**) |
| Kişisel bilgiler → Kullanıcı kimlikleri | Kullanıcı adı, hesap kimliği | Zorunlu | Uygulama işlevselliği, Hesap yönetimi, Dolandırıcılığı önleme/güvenlik |
| Kişisel bilgiler → Diğer bilgiler | Üniversite, bölüm, biyografi | Üniversite zorunlu | Uygulama işlevselliği |
| Finansal bilgiler → Satın alma geçmişi | Premium sipariş no, plan, fiyat, satın alma jetonu | İsteğe bağlı | Uygulama işlevselliği, Hesap yönetimi |
| Mesajlar → Diğer uygulama içi mesajlar | Birebir, grup ve kanal mesajları | İsteğe bağlı | Uygulama işlevselliği |
| Fotoğraflar ve videolar → Fotoğraflar | Profil, gönderi ve grup fotoğrafları | İsteğe bağlı | Uygulama işlevselliği |
| Dosyalar ve dokümanlar | Öğrenci belgesi PDF (karardan sonra en geç 30 günde imha, yalnızca SHA-256 kalır), ders notu PDF | Belge zorunlu | Uygulama işlevselliği, Dolandırıcılığı önleme/güvenlik |
| Uygulama etkinliği → Diğer kullanıcı tarafından oluşturulan içerik | Gönderi, yorum, anket, etkinlik, ilan, ihtiyaç metni | İsteğe bağlı | Uygulama işlevselliği |
| Uygulama etkinliği → Uygulama etkileşimleri | Uygulamayı açtığı gün (günde tek kayıt) | Zorunlu | Analiz (yalnızca toplu sayı), Hesap yönetimi (hareketsiz hesap tespiti) |
| Uygulama bilgileri ve performans → Kilitlenme günlükleri | Çökme türü ve yığın izi (180 gün) | Zorunlu | Analiz |
| Uygulama bilgileri ve performans → Diğer | Uygulama sürümü, Android sürümü, cihaz modeli | Zorunlu | Analiz, Dolandırıcılığı önleme/güvenlik |
| Cihaz veya diğer kimlikler | FCM bildirim belirteci | Zorunlu (bildirim için) | Uygulama işlevselliği |

Notlar:
- **IP adresi** giriş/çıkış kayıtlarında (5651, 2 yıl) ve rıza kayıtlarında tutulur. Play'de ayrı veri türü yoktur;
  "Uygulama bilgileri ve performans → Diğer" ve "Dolandırıcılığı önleme/güvenlik" amacıyla beyan edilir. IP'den konum
  çıkarılmaz, bu nedenle "Konum" işaretlenmez.
- Başarısız girişlerde e-posta yalnızca SHA-256 özeti olarak tutulur.
- Doğum tarihi **toplanmaz** (yalnızca kayıt anında 18 yaş kontrolü yapılır, aynı işlemde silinir).

## Toplanmayanlar

Konum (kesin/yaklaşık), rehber, takvim, sağlık ve fitness, ses, müzik, video, web geçmişi, yüklü uygulamalar,
reklam kimliği, SMS/arama kaydı, ırk/etnik köken, siyasi/dini görüş, cinsel yönelim, kredi kartı/banka bilgisi
(ödemeyi Google Play alır).

## İzinler (manifest ile tutarlılık)

| İzin | Neden | Kullanıcıya açıklama |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Backend, çevrimdışı yeniden deneme | — |
| `POST_NOTIFICATIONS` (Android 13+) | Mesaj/eşleşme/karar bildirimleri | İzin isteğinden önce gerekçe penceresi; kampanya bildirimi ayrı kanalda ve ayrı açık rızayla |
| `com.android.vending.BILLING` (Play Billing kütüphanesinin manifestinden gelir) | Premium | — |

Kamera, konum, rehber, mikrofon izni **yoktur**; fotoğraf/PDF sistem seçicisiyle alınır.
