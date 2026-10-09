# Çerez ve Üçüncü Taraf SDK Politikası

Metin tarihi: 9 Ekim 2026.

## Mobil uygulamadaki üçüncü taraf yazılım bileşenleri (SDK)

| SDK | Sağlayıcı | Amaç | Topladığı veri | Rıza |
|---|---|---|---|---|
| supabase-kt | Supabase Inc. | Giriş, veritabanı, dosya, gerçek zamanlı güncelleme | Oturum belirteci, gönderdiğin istekler, IP adresi (sunucu tarafında) | Gerekmez (hizmetin sunulması) |
| Firebase Cloud Messaging | Google LLC | Anlık bildirim | FCM belirteci, uygulama örneği kimliği | Gerekmez (işlemsel bildirim); kampanya bildirimleri için ayrı açık rıza |
| Google Play Billing | Google LLC | Premium abonelik satın alma | Satın alma bilgileri | Gerekmez (sözleşmenin ifası) |
| AndroidX / Jetpack, Kotlin Coroutines, Hilt, Ktor | Google, JetBrains | Uygulama altyapısı | Kişisel veri toplamaz | — |

**Kullanılmayanlar:** Firebase Analytics, Google Analytics, Crashlytics, reklam SDK'ları ve reklam kimliği. Çökme raporları kendi backend'imize hesap kimliği, cihaz modeli, sürüm ve hata sınıfı/çağrı yığını ile gönderilir. Hesap kimliği kişisel veridir; bu kayıtlar anonim değildir. Ad, e-posta, mesaj içeriği ve ham hata mesajları rapora eklenmez. Yeni veri işleme amaçları veya SDK'lar eklenirse ilgili metinler güncellenir; gerekli durumlarda işleme başlamadan ayrı rıza alınır.

## Web sitesi

- Web sitesi ve yönetim paneli Vercel üzerinde barındırılır. Ziyaret sırasında IP adresi ve tarayıcı bilgisi barındırma sağlayıcısının sunucu kayıtlarında işlenir.
- Web sitesinde **reklam veya analitik çerezi kullanılmaz.**
- Yönetim paneline ve hesap silme sayfasına giriş yapıldığında, oturumu sürdürmek için zorunlu oturum verisi tarayıcının yerel depolamasında tutulur (zorunlu, rıza gerektirmez). Çıkış yapıldığında silinir.
