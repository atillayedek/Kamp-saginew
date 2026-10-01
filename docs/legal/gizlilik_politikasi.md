# KampüsAğı Gizlilik Politikası

> **TASLAK — AVUKAT ONAYI GEREKLİ.**

Bu politika Google Play ve App Store gizlilik politikası şartları için hazırlanmıştır ve KampüsAğı Aydınlatma Metni ile birlikte okunmalıdır. İkisi arasında fark varsa Aydınlatma Metni esas alınır.

- Veri sorumlusu: [VERİ SORUMLUSU UNVANI], [ADRES]
- İletişim: [İLETİŞİM E-POSTASI] · KEP: [KEP]

## 1. Topladığımız veriler

- **Hesap:** e-posta adresi, şifre (yalnızca şifrelenmiş özeti, Supabase Auth tarafından), ad soyad, kullanıcı adı, üniversite, bölüm.
- **Yaş:** doğum tarihin yalnızca 18 yaş kontrolü için kullanılır, saklanmaz.
- **Öğrenci doğrulama:** öğrenci belgesi (PDF). Belge yalnızca yetkili doğrulama personeline, kısa süreli ve her açılışı kayıt altına alınan bir bağlantıyla gösterilir; karar verildikten sonra en geç 30 gün içinde silinir. Kalıcı olarak yalnızca doğrulandığın, tarih, doğrulayan yetkili ve belgenin SHA-256 özeti tutulur.
- **Profil:** biyografi, profil fotoğrafı, soyad görünürlük tercihi.
- **İçerik:** gönderiler, fotoğraflar, anketler, etkinlikler, ilanlar, yorumlar, beğeniler, ihtiyaç ilanları, ders notları, birebir/grup/kanal mesajları.
- **Kayıtlar (log):** giriş, çıkış, başarısız giriş ve şifre sıfırlama zamanı, IP adresi, cihaz modeli, işletim sistemi ve uygulama sürümü; onay/rıza kayıtları; şikâyet ve engellemeler.
- **Hata raporları:** uygulama çöktüğünde cihaz modeli, Android sürümü, uygulama sürümü ve hata yığını. Ad, e-posta veya mesaj içeriği hata raporlarına yazılmaz.
- **Bildirim belirteci:** anlık bildirim göndermek için Firebase Cloud Messaging belirteci.
- **Satın alma:** Google Play abonelik bilgisi (ürün, süre, sipariş numarası, fiyat). Kart bilgilerine erişmiyoruz.
- **Kullanım:** yalnızca uygulamayı hangi gün açtığın bilgisi (günde tek kayıt, başka davranış verisi toplanmaz).

**Toplamadıklarımız:** konum (GPS), rehber, mikrofon, kamera kaydı (fotoğraf yalnızca senin seçtiğin görseldir), reklam kimliği. Platform'da reklam yoktur; kullanıcıları başka uygulama ve sitelerde **izlemiyoruz** (App Tracking Transparency kapsamında takip yapılmaz). Analitik veya reklam SDK'sı kullanılmaz.

## 2. Kullanım amaçları

Hizmeti sunmak, öğrenciliği doğrulamak, hesabını ve topluluğu korumak, yasal yükümlülükleri (5651, 6698, 6502 sayılı Kanunlar) yerine getirmek, şikâyetleri incelemek ve yalnızca açık rızan varsa kampanya iletileri göndermek.

## 3. Paylaşım ve yurt dışı aktarım

Verilerin satılmaz. Hizmet sağlayıcılarımız: Supabase (veritabanı ve dosya depolama, [SUPABASE BÖLGESİ]), Google Firebase Cloud Messaging (bildirim), Google Play (ödeme), Resend (e-posta, İrlanda), Vercel (web sitesi), Cloudflare (alan adı ve e-posta yönlendirme). Bu sağlayıcılar yurt dışında bulunduğundan aktarım KVKK md.9 uyarınca standart sözleşmelerle yapılır. Yetkili makamların hukuka uygun talepleri saklıdır.

## 4. Güvenlik

Tüm bağlantılar HTTPS (TLS) ile şifrelenir; veriler barındırma sağlayıcısında şifreli diskte saklanır. Veritabanında satır düzeyinde erişim kuralları uygulanır; her kullanıcı yalnızca görmesine izin verilen veriye erişir. Yönetici hesapları iki adımlı doğrulama ile korunur, rollere ayrılmıştır (belge doğrulama, içerik moderasyonu, KVKK) ve her yönetici işlemi gerekçesiyle, değiştirilemez şekilde kayıt altına alınır. Yöneticiler özel mesajlarını okuyamaz; yalnızca şikâyet edilen bir mesaj ve yakın bağlamı, kayıt altına alınarak incelenebilir.

## 5. Saklama ve silme

Saklama süreleri Kişisel Veri Saklama ve İmha Politikası'ndadır. **Hesabını uygulamadan (Ayarlar → Gizlilik ve KVKK → Hesabımı sil) veya web sitemizdeki hesap silme sayfasından silebilirsin.** 30 günlük geri alma süresinden sonra hesabın ve içeriklerin kalıcı olarak silinir; yasal saklama zorunluluğu olan kayıtlar profilinden ayrılarak yasal süre boyunca tutulur.

## 6. Hakların

KVKK md.11 kapsamındaki hakların Aydınlatma Metni'nde yazılıdır. Verilerini uygulamadan **Verilerimi indir** ile indirebilir, rızalarını **Rızalarım** ekranından geri alabilir, **Başvuru oluştur** ile başvuru yapabilirsin. Kişisel Verileri Koruma Kurulu'na şikâyet hakkın saklıdır.

## 7. Çocuklar

Platform 18 yaşından küçükler için değildir; 18 yaşından küçük olduğu anlaşılan hesaplar kapatılır.

## 8. Değişiklikler

Bu politikanın her sürümü saklanır; önemli değişikliklerde uygulamaya bir sonraki girişinde bilgilendirilirsin.
