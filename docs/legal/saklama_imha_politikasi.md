# Kişisel Veri Saklama ve İmha Politikası

Metin tarihi: 9 Ekim 2026.

Bu politika, Kişisel Verilerin Silinmesi, Yok Edilmesi veya Anonim Hale Getirilmesi Hakkında Yönetmelik uyarınca hazırlanmıştır.

## 1. Kayıt ortamları

Tüm kişisel veriler elektronik ortamda tutulur: Supabase PostgreSQL veritabanı, Supabase Storage (dosyalar), Supabase Auth (giriş bilgileri), Firebase Cloud Messaging (yalnızca bildirim belirteci üzerinden gönderim), Resend (gönderilen e-postaların kayıtları), Vercel (web sunucu kayıtları). Kâğıt ortamda kişisel veri tutulmaz.

## 2. Saklama süreleri

Aşağıdaki süreler 9 Ekim 2026 tarihinde canlı sistemin herkese açık uyum ayarlarından doğrulanan sürelerdir. Süre değişikliği, gerekçesi ve ilgili metnin yeni sürümüyle birlikte yönetilir; amaç ortadan kalkınca kanuni saklama gereği bulunmayan veriler gereksiz yere tutulmaz.

| Veri | Saklama süresi | İmha yöntemi |
|---|---|---|
| Öğrenci belgesi (PDF) | Onay veya red kararından sonra en geç 30 gün | Dosyanın Storage'dan kalıcı silinmesi; kalan kayıtta yalnızca sonuç, tarih, doğrulayan ve SHA-256 özeti |
| Doğum tarihi | Saklanmaz (yalnızca kayıt anında yaş kontrolü) | — |
| Hesap ve profil verileri, içerikler, mesajlar | Üyelik süresince; silme talebinden sonra 30 günlük geri alma süresi sonunda | Bağlı kayıtlar silinir; birebir sohbetler karşı taraf için de silinir. Grup/kanal mesajlarının hesap bağlantısı kaldırılır; metinde bulunan kişisel bilgiler için ayrıca inceleme gerekir |
| Hareketsiz hesaplar | 2 yıl giriş yapılmazsa e-posta ile uyarı, 30 gün sonra | Kalıcı silme |
| 5651 trafik kayıtları (giriş, çıkış, başarısız giriş, şifre sıfırlama; IP, cihaz, zaman) | 2 yıl | Kalıcı silme (zincir başlangıç özeti imha kaydında tutulur) |
| Onay, aydınlatma ve rıza kayıtları | 10 yıl (ispat yükümlülüğü, genel zamanaşımı) | Kalıcı silme |
| Yönetici işlem kayıtları | 10 yıl | Kalıcı silme |
| İmha kayıtları | 10 yıl | Kalıcı silme |
| Uygulama hata raporları | 180 gün | Kalıcı silme |
| Veri dışa aktarma dosyası | 7 gün | Kalıcı silme |
| Yanıtlanmış veya reddedilmiş veri sahibi başvuruları | Alınma tarihinden itibaren 3650 gün | Kalıcı silme |
| Veri ihlali ve telif bildirimleri | [İHLAL VE TELİF KAYDI SAKLAMA SÜRESİ] | Gerekli deliller ayrılarak süre sonunda silme; hukuki uyuşmazlık varsa yalnız ilgili kayıtlar sınırlı erişimle tutulur |
| Satın alma kayıtları | [SATIN ALMA KAYDI SAKLAMA SÜRESİ] | Hesap silinince kullanıcı bağlantısı kaldırılır. Sipariş numarası ve işlem bilgisi kalabilir; bu işlem tek başına tam anonimleştirme değildir |
| Bildirim belirteci | Çıkış yapılınca veya FCM geçersiz bildirince | Kalıcı silme |

## 3. Periyodik imha

Uygulama günlük imha işi için zamanlama ve işlem kaydı mekanizması içerir. İşletmeci bu zamanlamanın etkinliğini ve başarılı çalışmasını yönetim panelinden izler; başarısız işlemleri tekrar çalıştırır. Kayıtlar veri kategorisi, gerekçe, yöntem ve kayıt sayısını içerir; silinen içeriğin kendisi imha kaydına eklenmez. Veri sahibinin silme talebine verilecek yanıt ve yapılacak işlem, periyodik imha zamanlamasından bağımsız olarak mevzuatın gerektirdiği sürelerde değerlendirilir.

## 4. Yedekler

Veritabanı yedekleri barındırma sağlayıcısı (Supabase) tarafından alınır ve sağlayıcının yedek saklama süresi ([YEDEK SAKLAMA SÜRESİ]) sonunda kendiliğinden silinir. Yedekten geri dönüş yapılırsa, geri dönüş anından sonra imha edilmiş olması gereken veriler bir sonraki otomatik imha çalışmasında yeniden imha edilir.

## 5. Teknik ve idari tedbirler

Satır düzeyinde erişim kuralları, rol bazlı yönetici yetkileri, yöneticiler için iki adımlı doğrulama, değiştirilemez ve hash zinciriyle korunan işlem kayıtları, TLS ile şifreli iletim, sağlayıcı düzeyinde şifreli depolama, gizli anahtarların yalnızca sunucu ortam değişkenlerinde tutulması.

## 6. Sorumlular

Politikanın uygulanmasından [SORUMLU KİŞİ / BİRİM] sorumludur.
