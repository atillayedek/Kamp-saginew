# KampüsAğı Gizlilik Politikası

Metin tarihi: 9 Ekim 2026.

Bu politika KampüsAğı Android uygulamasında ve https://kampusagi.tsdmrdigitalstudio.com.tr adresindeki web sitesinde kişisel verilerinin nasıl işlendiğini açıklar. Aydınlatma Metni işleme amaçlarını ve hukuki sebepleri ayrıntılı olarak belirtir. Pazarlama rızaları bu belgelerden ayrı ve isteğe bağlıdır.

- Veri sorumlusu: [VERİ SORUMLUSU UNVANI], [ADRES]
- İletişim: iletisim@tsdmrdigitalstudio.com.tr · KEP: [KEP]

## 1. Topladığımız veriler

- **Hesap:** e-posta adresi, şifre (yalnızca şifrelenmiş özeti, Supabase Auth tarafından), ad soyad, kullanıcı adı, üniversite, bölüm.
- **Yaş:** doğum tarihin yalnızca 18 yaş kontrolü için kullanılır, saklanmaz.
- **Öğrenci doğrulama:** öğrenci belgesi (PDF). Belge yalnızca yetkili doğrulama personeline, kısa süreli ve her açılışı kayıt altına alınan bir bağlantıyla gösterilir; karar verildikten sonra en geç 30 gün içinde silinir. Kalıcı olarak yalnızca doğrulandığın, tarih, doğrulayan yetkili ve belgenin SHA-256 özeti tutulur.
- **Profil:** biyografi, profil fotoğrafı, soyad görünürlük tercihi.
- **İçerik:** gönderiler, fotoğraflar, anketler, etkinlikler, ilanlar, yorumlar, beğeniler, ihtiyaç ilanları, ders notları, birebir/grup/kanal mesajları.
- **Kayıtlar (log):** giriş, çıkış, başarısız giriş ve şifre sıfırlama zamanı, IP adresi, cihaz modeli, işletim sistemi ve uygulama sürümü; onay/rıza kayıtları; şikâyet ve engellemeler.
- **Hata raporları:** uygulama çöktüğünde hesap kimliği, cihaz modeli, Android sürümü, uygulama sürümü ve hata sınıfı/çağrı yığını işlenir. Hesap kimliği kişisel veridir; raporlar anonim değildir. Ad, e-posta, mesaj içeriği ve ham hata mesajları rapora eklenmez.
- **Bildirim belirteci:** anlık bildirim göndermek için Firebase Cloud Messaging belirteci.
- **Satın alma:** Google Play abonelik bilgisi (ürün, süre, sipariş numarası, fiyat). Kart bilgilerine erişmiyoruz.
- **Kullanım:** yalnızca uygulamayı hangi gün açtığın bilgisi (günde tek kayıt, başka davranış verisi toplanmaz).

**Toplamadıklarımız:** konum (GPS), rehber, mikrofon, kamera kaydı (fotoğraf yalnızca senin seçtiğin görseldir), reklam kimliği. Platform'da reklam yoktur; kullanıcıları başka uygulama ve sitelerde **izlemiyoruz** (App Tracking Transparency kapsamında takip yapılmaz). Analitik veya reklam SDK'sı kullanılmaz.

## 2. Kullanım amaçları

Hizmeti sunmak, öğrenciliği doğrulamak, hesabını ve topluluğu korumak, yasal yükümlülükleri (5651, 6698, 6502 sayılı Kanunlar) yerine getirmek, şikâyetleri incelemek ve yalnızca açık rızan varsa kampanya iletileri göndermek.

## 3. Paylaşım ve yurt dışı aktarım

Verilerini satmayız. Supabase veritabanı, kimlik doğrulama, dosya depolama ve sunucu hizmetlerini; Firebase Cloud Messaging bildirim teslimini; Google Play ödeme ve abonelik doğrulamasını; Resend e-posta gönderimini; Vercel web barındırmasını; Cloudflare alan adı ve iletişim e-postası yönlendirmesini sağlar. Veri kategorileri ve alıcılar Aydınlatma Metni'nde açıklanır. Supabase proje bölgesi: [SUPABASE BÖLGESİ]. Yurt dışına aktarım için kullanılan hukuki güvence ve kapsamı: [YURT DIŞI AKTARIM DAYANAĞI]. Hizmet sağlayıcının veri işleme sözleşmesi tek başına Türkiye'deki aktarım şartlarının sağlandığı anlamına gelmez. Yetkili makamların hukuka uygun talepleri saklıdır.

## 4. Güvenlik

Bağlantılar HTTPS (TLS) ile korunur. Veritabanında satır düzeyinde erişim ve yönetim panelinde rol/MFA kontrolleri uygulanır. Belge erişimi ve yönetici incelemeleri gerekçe ve işlem kaydıyla sınırlandırılır. Mesajlaşma uçtan uca şifreli değildir; içerik sunucuda saklanır. Yöneticiler özel mesajlara genel erişim sağlayamaz; şikâyet edilen mesajın sınırlı bağlamı inceleme amacıyla açılabilir. Hiçbir teknik sistem mutlak güvenlik sağlamaz. Hesabının yetkisiz kullanıldığını fark edersen iletişim adresimize bildir.

## 5. Saklama ve silme

Saklama süreleri Kişisel Veri Saklama ve İmha Politikası'ndadır. **Ayarlar → Gizlilik ve KVKK → Hesabımı sil** veya https://kampusagi.tsdmrdigitalstudio.com.tr/hesap-silme adresinden silme talebi oluşturabilirsin. 30 günlük geri alma süresinde hesap kullanımı silmeden vazgeçme işlemiyle sınırlanır; mevcut içeriklerin diğer kullanıcılara görünmeye devam edebilir. Süre sonunda hesap ve bağlı veriler imha işlemine alınır. Birebir sohbetler karşı taraf için de silinir; grup/kanal mesajlarının hesap bağlantısı kaldırılır. Mesaj metninde kendi yazdığın kişisel bilgiler varsa hesap bağlantısının kaldırılması bu bilgileri otomatik temizlemez; ayrıca silme talebine konu edebilirsin. İspat ve kanuni yükümlülükler için saklanan kayıtlar ayrı sürelerine tabidir. Yedeklerin imhası politikada ayrıca açıklanır.

Hesap silme Google Play aboneliğini iptal etmez. Yeni ücret alınmasını önlemek için aboneliğini Google Play'den ayrıca iptal etmelisin.

## 6. Hakların

KVKK md.11 kapsamındaki hakların Aydınlatma Metni'nde yazılıdır. Verilerini uygulamadan **Verilerimi indir** ile indirebilir, rızalarını **Rızalarım** ekranından geri alabilir, **Başvuru oluştur** ile başvuru yapabilirsin. Kişisel Verileri Koruma Kurulu'na şikâyet hakkın saklıdır.

## 7. Çocuklar

Platform 18 yaşından küçükler için değildir; 18 yaşından küçük olduğu anlaşılan hesaplar kapatılır.

## 8. Değişiklikler

Bu politikanın her sürümü saklanır; önemli değişikliklerde uygulamaya bir sonraki girişinde bilgilendirilirsin.
