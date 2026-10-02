# App Store — Gizlilik etiketi (App Privacy) taslağı

> Projede şu an **iOS uygulaması yoktur** (`docs/kvkk/acik-sorular.md` S2). Bu dosya, bir iOS sürümü aynı backend
> ve aynı veri envanteriyle yapılırsa App Store Connect → App Privacy formuna girilecek yanıtlardır.

## Takip (Tracking)

- Uygulama kullanıcıyı başka şirketlerin uygulama/web sitelerinde **takip etmez**; reklam kimliği (IDFA) okunmaz,
  veri aracısıyla paylaşım yoktur → **"Data Used to Track You": yok**. **App Tracking Transparency (ATT) penceresi
  gerekmez** ve gösterilmez.
- Analitik/reklam SDK'sı yoktur. Push için APNs/FCM kullanılır (hizmet sağlayıcı).

## Data Linked to You (kimliğe bağlı veriler)

| Kategori → tür | Ne | Amaç (App Store adlarıyla) |
|---|---|---|
| Contact Info → Name | Ad soyad | App Functionality |
| Contact Info → Email Address | Hesap e-postası | App Functionality, Developer's Advertising or Marketing (**yalnızca açık rızayla**) |
| User Content → Photos or Videos | Profil/gönderi/grup fotoğrafları | App Functionality |
| User Content → Emails or Text Messages | Uygulama içi mesajlar | App Functionality |
| User Content → Other User Content | Gönderi, yorum, ders notu, ihtiyaç ilanı, öğrenci belgesi (30 günde imha) | App Functionality |
| Identifiers → User ID | Hesap kimliği, kullanıcı adı | App Functionality |
| Identifiers → Device ID | Bildirim belirteci | App Functionality |
| Purchases → Purchase History | Premium siparişleri | App Functionality |
| Usage Data → Product Interaction | Uygulamayı açtığı gün | Analytics |
| Diagnostics → Crash Data | Çökme yığını | App Functionality |
| Diagnostics → Other Diagnostic Data | Uygulama/OS sürümü, cihaz modeli, IP (giriş kayıtları, 5651) | App Functionality (güvenlik) |
| Other Data → Other Data Types | Üniversite, bölüm, biyografi; rıza kayıtları | App Functionality |

## Data Not Linked to You

Yok (çökme raporları anonim kimlik taşır ama kayıt kişiye bağlanabildiği için "Linked" olarak beyan edildi).

## Data Not Collected

Health & Fitness, Financial Info (kart bilgisi App Store'da), Location, Sensitive Info, Contacts, Browsing History,
Search History (arama sorguları saklanmaz), Audio Data.

## Diğer App Store gereksinimleri

- Uygulama içinden hesap silme: var (Ayarlar → Gizlilik ve KVKK → Hesabı sil).
- Kullanıcı içeriği: şikâyet, engelleme, 24 saatte inceleme hedefi (topluluk kuralları) — Guideline 1.2 karşılanır.
- Abonelik: ön bilgilendirme ve mesafeli sözleşme ekranı, "Aboneliği yönet" bağlantısı (iOS'ta App Store aboneliklerine).
