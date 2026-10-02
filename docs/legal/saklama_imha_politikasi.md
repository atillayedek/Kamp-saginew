# Kişisel Veri Saklama ve İmha Politikası

> **TASLAK — AVUKAT ONAYI GEREKLİ.**

Bu politika, Kişisel Verilerin Silinmesi, Yok Edilmesi veya Anonim Hale Getirilmesi Hakkında Yönetmelik uyarınca hazırlanmıştır.

## 1. Kayıt ortamları

Tüm kişisel veriler elektronik ortamda tutulur: Supabase PostgreSQL veritabanı, Supabase Storage (dosyalar), Supabase Auth (giriş bilgileri), Firebase Cloud Messaging (yalnızca bildirim belirteci üzerinden gönderim), Resend (gönderilen e-postaların kayıtları), Vercel (web sunucu kayıtları). Kâğıt ortamda kişisel veri tutulmaz.

## 2. Saklama süreleri

Süreler yönetim panelindeki uyum ayarlarından okunur; aşağıdaki değerler varsayılanlardır. Ayar değiştiğinde bu politikanın yeni sürümü yayımlanır.

| Veri | Saklama süresi | İmha yöntemi |
|---|---|---|
| Öğrenci belgesi (PDF) | Onay veya red kararından sonra en geç 30 gün | Dosyanın Storage'dan kalıcı silinmesi; kalan kayıtta yalnızca sonuç, tarih, doğrulayan ve SHA-256 özeti |
| Doğum tarihi | Saklanmaz (yalnızca kayıt anında yaş kontrolü) | — |
| Hesap ve profil verileri, içerikler, mesajlar | Üyelik süresince; silme talebinden sonra 30 günlük geri alma süresi sonunda | Kalıcı silme; grup ve kanal mesajları "Silinmiş kullanıcı" olarak anonimleştirme |
| Hareketsiz hesaplar | 2 yıl giriş yapılmazsa e-posta ile uyarı, 30 gün sonra | Kalıcı silme |
| 5651 trafik kayıtları (giriş, çıkış, başarısız giriş, şifre sıfırlama; IP, cihaz, zaman) | 2 yıl | Kalıcı silme (zincir başlangıç özeti imha kaydında tutulur) |
| Onay, aydınlatma ve rıza kayıtları | 10 yıl (ispat yükümlülüğü, genel zamanaşımı) | Kalıcı silme |
| Yönetici işlem kayıtları | 10 yıl | Kalıcı silme |
| İmha kayıtları | 10 yıl | Kalıcı silme |
| Uygulama hata raporları | 180 gün | Kalıcı silme |
| Veri dışa aktarma dosyası | 7 gün | Kalıcı silme |
| Veri sahibi başvuruları ve ihlal kayıtları | 10 yıl | Kalıcı silme |
| Satın alma kayıtları | Vergi Usul Kanunu ve Türk Ticaret Kanunu uyarınca 10 yıl; hesap silinince kişiden ayrılır | Anonimleştirme |
| Bildirim belirteci | Çıkış yapılınca veya FCM geçersiz bildirince | Kalıcı silme |

## 3. Periyodik imha

Otomatik imha işi **her gün** çalışır (yasal üst sınır olan 6 aylık periyodik imha süresinden çok daha sık). Her imha işlemi; hangi veri kategorisinin, hangi gerekçeyle (kullanıcı talebi, saklama süresi doldu, belge kararı, hareketsiz hesap), hangi yöntemle ve kaç kayıt olarak imha edildiği bilgisiyle, silinen içeriğin kendisi olmadan, değiştirilemez imha kayıtlarına yazılır. İmha raporu yönetim panelinde görüntülenir.

## 4. Yedekler

Veritabanı yedekleri barındırma sağlayıcısı (Supabase) tarafından alınır ve sağlayıcının yedek saklama süresi ([YEDEK SAKLAMA SÜRESİ]) sonunda kendiliğinden silinir. Yedekten geri dönüş yapılırsa, geri dönüş anından sonra imha edilmiş olması gereken veriler bir sonraki otomatik imha çalışmasında yeniden imha edilir.

## 5. Teknik ve idari tedbirler

Satır düzeyinde erişim kuralları, rol bazlı yönetici yetkileri, yöneticiler için iki adımlı doğrulama, değiştirilemez ve hash zinciriyle korunan işlem kayıtları, TLS ile şifreli iletim, sağlayıcı düzeyinde şifreli depolama, gizli anahtarların yalnızca sunucu ortam değişkenlerinde tutulması.

## 6. Sorumlular

Politikanın uygulanmasından [SORUMLU KİŞİ / BİRİM] sorumludur.
