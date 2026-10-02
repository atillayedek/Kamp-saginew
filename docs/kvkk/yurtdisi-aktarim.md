# Yurt Dışına Aktarım Kontrol Listesi (KVKK md.9)

> Bu belge hukuki adımların listesidir; sözleşmelerin imzalanması ve Kurul'a bildirim veri sorumlusunun işidir.
> KVKK md.9 (7499 sayılı Kanun'la değişik, 1 Haziran 2024'ten itibaren) ve Kişisel Verilerin Yurt Dışına Aktarılmasına
> İlişkin Usul ve Esaslar Hakkında Yönetmelik esas alınmıştır. **Avukat onayı gerekir.**

## Genel adımlar (her alıcı için)

1. Alıcının ülkesi için Kurul'un **yeterlilik kararı** var mı? (2 Ekim 2026 itibarıyla yayımlanmış bir yeterlilik kararı
   tespit edilmedi → uygun güvence gerekir; avukat güncel durumu doğrulamalı.)
2. Uygun güvence olarak Kurul'un ilan ettiği **standart sözleşme** (veri sorumlusu → veri işleyen modülü) imzalanır.
3. Standart sözleşme imzalandıktan sonra **5 iş günü içinde** Kurum'a bildirilir.
4. Aydınlatma Metni'nde alıcı, ülke, amaç ve hukuki dayanak yazılır (yazıldı, yer tutucular avukat onayıyla doldurulacak).
5. Alıcının alt işleyenleri (sub-processors) ve veri merkezi bölgeleri dosyalanır.

## Alıcılar

| # | Alıcı | Rol | Aktarılan veri | Amaç | Ülke/bölge | Standart sözleşme imzalandı | Kurum'a bildirim (≤5 iş günü) |
|---|---|---|---|---|---|---|---|
| 1 | Supabase Inc. | Veri işleyen | Platform'daki tüm veriler (DB, Auth, Storage, Edge Functions, Realtime) | Barındırma ve işleme | [SUPABASE BÖLGESİ] (AWS) | [ ] | [ ] |
| 2 | Google LLC / Google Ireland Ltd. — Firebase Cloud Messaging | Veri işleyen | FCM belirteci, bildirim başlığı/metni (içerik içermez) | Anlık bildirim | ABD/AB | [ ] | [ ] |
| 3 | Google Ireland Ltd. / Google LLC — Google Play (Billing, Developer API) | Bağımsız veri sorumlusu (satın alma) / veri işleyen (doğrulama) | Satın alma jetonu, sipariş, ürün | Abonelik | AB/ABD | [ ] (Google Play Developer Distribution Agreement + avukat değerlendirmesi) | [ ] |
| 4 | Resend Inc. | Veri işleyen | E-posta adresi, e-posta içeriği | Hizmet ve kampanya e-postası | `eu-west-1` (İrlanda); şirket ABD | [ ] | [ ] |
| 5 | Vercel Inc. | Veri işleyen | Web ziyaretçi IP/UA; panel oturumları (yöneticiler) | Web sitesi ve panel barındırma | ABD/AB (edge) | [ ] | [ ] |
| 6 | Cloudflare Inc. | Veri işleyen | `iletisim@` adresine gelen e-postalar; DNS sorguları | E-posta yönlendirme, DNS | Küresel | [ ] | [ ] |
| 7 | GitHub Inc. | — | Kişisel veri aktarılmaz (kaynak kod, CI) | Geliştirme | ABD | Gerekmez (kişisel veri yok) | — |
| 8 | Gmail (yönlendirme hedefi, `atilla…@gmail.com`) | Veri işleyen (iletişim kutusu) | Başvuru ve iletişim e-postaları | İletişim | ABD/AB | [ ] — kurumsal e-posta ile değiştirilmesi önerilir (S12) | [ ] |

## Açık rıza

7499 sayılı değişiklikten sonra açık rıza yalnızca **arızi** aktarımlar için kullanılabilir. Bu Platform'daki aktarımlar
düzenli ve süreklidir; açık rızaya dayandırılmamış, standart sözleşmeye dayandırılmıştır. Bu nedenle kayıt ekranında
yurt dışı aktarım için açık rıza kutusu **yoktur** (`acik-sorular.md` S3).

## Bölge değişikliği

Supabase projesinin bölgesi bu ortamdan okunamadı. Türkiye'ye en yakın bölge (ör. `eu-central-1` Frankfurt) değilse
taşımak **veri göçü** gerektirir (yeni proje + dump/restore + Storage kopyası + Auth kullanıcıları). Görev tanımı gereği
yapılmadı; `acik-sorular.md` S1'de raporlandı.
