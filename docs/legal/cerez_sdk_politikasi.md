# Çerez ve Üçüncü Taraf SDK Politikası

> **TASLAK — AVUKAT ONAYI GEREKLİ.**

## Mobil uygulamadaki üçüncü taraf yazılım bileşenleri (SDK)

| SDK | Sağlayıcı | Amaç | Topladığı veri | Rıza |
|---|---|---|---|---|
| supabase-kt | Supabase Inc. | Giriş, veritabanı, dosya, gerçek zamanlı güncelleme | Oturum belirteci, gönderdiğin istekler, IP adresi (sunucu tarafında) | Gerekmez (hizmetin sunulması) |
| Firebase Cloud Messaging | Google LLC | Anlık bildirim | FCM belirteci, uygulama örneği kimliği | Gerekmez (işlemsel bildirim); kampanya bildirimleri için ayrı açık rıza |
| Google Play Billing | Google LLC | Premium abonelik satın alma | Satın alma bilgileri | Gerekmez (sözleşmenin ifası) |
| AndroidX / Jetpack, Kotlin Coroutines, Hilt, Ktor | Google, JetBrains | Uygulama altyapısı | Kişisel veri toplamaz | — |

**Kullanılmayanlar:** Firebase Analytics, Google Analytics, Crashlytics, reklam SDK'ları ve reklam kimliği. Çökme raporları kendi sunucumuza, kişisel veri içermeden (ad, e-posta, mesaj içeriği yok; yalnızca anonim kullanıcı kimliği, cihaz modeli, sürüm ve hata yığını) gönderilir. İleride analitik bir araç eklenirse varsayılan olarak kapalı olacak ve yalnızca ayrı açık rızanla açılacaktır.

## Web sitesi

- Web sitesi ve yönetim paneli Vercel üzerinde barındırılır. Ziyaret sırasında IP adresi ve tarayıcı bilgisi barındırma sağlayıcısının sunucu kayıtlarında işlenir.
- Web sitesinde **reklam veya analitik çerezi kullanılmaz.**
- Yönetim paneline ve hesap silme sayfasına giriş yapıldığında, oturumu sürdürmek için zorunlu oturum verisi tarayıcının yerel depolamasında tutulur (zorunlu, rıza gerektirmez). Çıkış yapıldığında silinir.
