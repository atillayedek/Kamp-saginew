# Hukuki metinlerin yayın bilgileri — 9 Ekim 2026

Kullanıcının talebiyle `docs/legal/` içindeki 13 metin yeniden düzenlendi ve standart taslak/avukat onayı başlığı kaldırıldı. Bu işlem herhangi bir avukat onayı, işletmeci kimliği doğrulaması veya canlı yayın yapıldığı anlamına gelmez. Metin tarihi revizyon tarihidir; yürürlük veya veritabanında yayın tarihi değildir.

Doğrulanan iletişim kanalı mevcut deployment belgesinde tanımlı `iletisim@tsdmrdigitalstudio.com.tr`; marka KampüsAğı ve web adresi `https://kampusagi.tsdmrdigitalstudio.com.tr` olarak kullanıldı. TSDMR Digital Studio marka adı, gerçek veri sorumlusu/ticari unvan yerine konmadı.

## Tamamlanacak gerçek bilgiler

| Alan | Gereken bilgi |
|---|---|
| VERİ SORUMLUSU UNVANI | İşletmecinin tam gerçek kişi adı veya ticari unvanı; şahıs/şirket durumu |
| ADRES | Tebligat ve tüketici başvuruları için gerçek adres |
| MERSİS NO / VERGİ DAİRESİ VE NO | İşletmenin durumuna göre mevcut kayıtlar; uygulanmayan alanlar doğrulama sonrası kaldırılır |
| KEP / VERBİS KAYIT NO | Varsa gerçek kayıtlar; mevcut değilse varmış gibi yazılmaz |
| İLETİŞİM TELEFONU | Ön bilgilendirme ve sözleşme iletişim telefonu |
| SUPABASE BÖLGESİ | Projenin gerçek veri barındırma bölgesi |
| YURT DIŞI AKTARIM DAYANAĞI | Her ilgili alıcı için gerçekten tesis edilmiş hukuki güvence ve kapsamı; imzalanmamış sözleşme imzalanmış gibi yazılmaz |
| YEDEK SAKLAMA SÜRESİ | Sağlayıcı planına ve etkin yedek/PITR ayarına göre gerçek süre |
| İHLAL VE TELİF KAYDI SAKLAMA SÜRESİ | Onaylanmış süre ve bu süreyi uygulayan imha işlemi; mevcut kodda zaman bazlı otomatik temizleme yok |
| SATIN ALMA KAYDI SAKLAMA SÜRESİ | Onaylanmış muhasebe/ispat süresi ve imha işlemi; mevcut kod kullanıcı bağlantısını kaldırıyor, süre dolumunda otomatik temizlemiyor |
| SORUMLU KİŞİ / BİRİM | İmha işlemlerini izleyecek ve taleplere yanıt verecek gerçek sorumlu |

## İçerik düzeltmeleri

- Çökme kayıtlarındaki hesap kimliği kişisel veri olarak açıklandı; anonim veya kişisel veri içermez iddiası kaldırıldı.
- İnsan tarafından görülen öğrenci belgesindeki ek bilgilerin hiç okunmadığı iddiası düzeltildi; OCR ile veri çıkarımı yapılmadığı ayrı belirtildi.
- Mesajların uçtan uca şifrelenmediği, şikâyet bağlamının sınırlı yönetici incelemesi ve kullanıcılar arasındaki görünürlük açıklandı.
- Silme talebinin 30 günlük geri alma süresinde içerik görünürlüğü, birebir sohbet silinmesi, grup/kanal mesajlarında kalan metin ve aboneliğin ayrıca iptal edilmesi açıklandı.
- KKTC kurumları üyelik kapsamına eklendi. Bu değişiklik KKTC mevzuatına uygunluk onayı değildir.
- Pazarlama rızası yurt dışı aktarımın genel izni olarak kullanılmadı; işletim sistemi bildirim izniyle ayrımı açıklandı.
- Abonelik cayma/iade bölümleri genel kural, uygulanabilir istisna ve saklı tüketici haklarıyla düzenlendi; ödeme/sözleşme onayı ile başarılı hizmet aktivasyonu ayrıldı.
- Canlı `public_compliance_config` üzerinden 30/730/3650/180/7 günlük mevcut süreler doğrulandı. Tespit edilmemiş yedek süresi veya çalışmayan imha işlemi garantisi yazılmadı.

## Yayın

Eski migration `20261002100100_legal_documents_v1.sql` değiştirilmedi. Önceden yayımlanmış metinler onay kanıtıdır; kaynak dosyası değişikliği canlı veritabanındaki metni değiştirmez. Eksik bilgiler tamamlandıktan sonra yönetim paneli → KVKK ve Uyum → Hukuki metinler üzerinden yeni sürümler yayımlanır. Tarih/sürüm/hash ve önceki onaylar korunur. Canlı `scripts/verify-production.py` tekrar çalıştırılarak sonuç doğrulanır. Yer tutuculu dosyalar yayına alınmaz; taslak başlığı kaldırılması production kontrolünü geçmez.

## Kullanılan resmi kaynaklar

- [KVKK aydınlatma yükümlülüğü](https://www.kvkk.gov.tr/Icerik/2033/Aydinlatma-Yukumlulugu-)
- [KVKK başvurusu ve zorunlu bilgiler hakkında Kurul kararı](https://www.kvkk.gov.tr/Icerik/7287/2021-1262)
- [Standart sözleşme bildirim modülü](https://www.kvkk.gov.tr/Icerik/8043/Standart-Sozlesme-Bildirim-Modulu-Hakkinda-Kamuoyu-Duyurusu)
- [Ticaret Bakanlığı mesafeli sözleşme bilgilendirmesi](https://tuketici.ticaret.gov.tr/yayinlar/tuketici-bilgi-rehberi/mesafeli-sozlesmeler-hakkinda-bilgilendirme)
