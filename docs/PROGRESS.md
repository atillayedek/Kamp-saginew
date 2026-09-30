# İlerleme

Oturumlar arası hafıza. Her oturum başında `CLAUDE.md`, `docs/DECISIONS.md` ve bu dosya okunur.
Otomatik devam: saatlik Routine (`KampüsAğı fazlarına otomatik devam`) bu oturumu uyandırır.

## Faz planı

| Faz | Kapsam | Durum |
|---|---|---|
| 1 | Audit + production gap analizi | Bitti (`docs/PHASE1_AUDIT.md`) |
| 2 | Proje iskeleti, CI, Supabase şeması, Supabase Auth, profil tamamlama, üniversite verisi | Bitti — CI yeşil (DB testleri, unit test, lintRelease, debug APK, release AAB), `1fb635b` |
| 3 | Öğrenci belgesi: PDF yükleme → Storage → Edge Function doğrulaması → admin inceleme → APPROVED/REJECTED | Bitti — CI yeşil `77d5289` (DB, Edge Function, Android). Canlı Supabase'e deploy: dış yapılandırma |
| 4 | Ana uygulama iskeleti (alt gezinme) + Topluluklar: gönderi, yorum, beğeni (genel / üniversitem) | Bitti — CI yeşil `dfdaa4e` (4d111da'daki test derleme hatası df07226 ile, RelativeTimeTest beklentisi dfdaa4e ile düzeltildi) |
| 5 | İhtiyaç oluşturma: OpenAI yapılandırılmış çıktı (backend) + embedding + pgvector | Bitti — CI yeşil `dfdaa4e`. Canlı OpenAI: dış yapılandırma (`docs/DEPLOYMENT.md`) |
| 6 | Eşleşme: pgvector benzerlik sorgusu, gerçek skor, eşleşme ekranı | Bitti — CI yeşil `dfdaa4e` |
| 7 | Sohbet: Realtime, gönderildi/okundu | Bitti — CI yeşil `86297d5` (`69b1b42`'deki KampusAgiApp derleme hatası düzeltildi). Yazıyor/çevrimiçi yok (D20) |
| 8 | Bildirimler: uygulama içi + FCM push | Bitti — CI yeşil `86297d5`. FCM sonradan kaldırıldı (D30): bildirimler Supabase Realtime ile |
| 9 | Premium: Google Play Billing + sunucu doğrulaması + entitlement | Bitti — CI yeşil `ddeb8e7` (`5acb808`'deki lint hatası ContextCastToActivity `a41a6d2` ile düzeltildi). Play Console ürünleri + plan içeriği: dış yapılandırma / ürün kararı |
| 10 | Ayarlar, hesap silme, gizlilik, raporlama/engelleme | Bitti — CI yeşil `ddeb8e7`. Gizlilik politikası URL'si ve web silme sayfası: dış yapılandırma |
| 11 | Release sertleştirme: imza, AAB, R8, erişilebilirlik, son tarama | Kod bitti — CI yeşil `ddeb8e7` (R8 + kaynak küçültme, yedekleme kapalı, cleartext kapalı, CI'da keystore secret'ıyla imza + `jarsigner` doğrulaması, versionCode = CI çalıştırma no). Keystore secret'ları yok → AAB **imzasız**: dış yapılandırma |
| 12 | Gözlemlenebilirlik: Supabase çökme raporları (D31), canlı backend CI kontrolü (D32) | Bitti — CI yeşil `646e4e6` (DB, Edge Function, Android, canlı kontrol 10/10) |
| 13 | Web: landing page + `/admin` paneli, Resend toplu e-posta, gelir kaydı, pazarlama izni (D33–D35) | Bitti — CI yeşil `a658e31` (DB 13 test dosyası, Edge Function, web, Android, canlı kontrol). Canlı: migration + 2 fonksiyon + Vercel `kampusagi-nine.vercel.app`. İlk admin atandı (2026-09-27). E-posta secret'ları girildi; sahte abonelik bağlantısı canlıda reddediliyor (CI run 36299964951). Gerçek Resend gönderimi doğrulandı: panelden test e-postası yöneticinin gelen kutusuna ulaştı (2026-09-27) |
| 14 | Görsel yenileme (D37), gönderi kategorileri (D38), profil fotoğrafı/biyografi/otomatik avatar (D39), Eşleşmeler sekmesi (D40) | Bitti — CI yeşil `2f0290b` (DB 14 test dosyası, Edge Function, web, Android birim testleri + lint + APK/AAB, canlı kontrol). Cihazda görsel doğrulama: kullanıcı tarafından yapılacak |
| 15 | Akış: gönderiye fotoğraf (D42), anket, etkinlik, pazar yeri ilanı (D43), kaydedilenler, arama, kişi profili (D44) | Bitti — CI yeşil `eed1174` (DB 15 test dosyası, Edge Function, web, Android birim testleri + lint + APK/AAB, canlı kontrol) |
| 16 | Premium kanallar (D46), çalışma grupları (D47), ders notu arşivi (D48), rozetler (D49) | Bitti — CI yeşil `51306ab` (DB 16 test dosyası, Edge Function 40 test, web, Android birim testleri + lint + APK/AAB, canlı kontrol) |
| 17 | Admin: uygulama içi duyuru (D50), aktiflik istatistikleri (D51), Premium hediye ve promosyon kodu (D52); Premium fiyatı Play'de 100 TL (D45) | Bitti — CI yeşil `c55372e` (DB 17 test dosyası, Edge Function, web, Android birim testleri + lint + APK/AAB, canlı kontrol). Cihazda deneme: kullanıcı tarafından yapılacak |
| 18 | Yapay zekâsız kurallı eşleştirme (D54): form + önerilen etiketler, `create_requirement`, puanlı `find_matches`, eşanlamlılar; OpenAI kaldırıldı | CI bekleniyor (yerel: DB 17 test dosyası PASS, Deno 29 test PASS, web PASS) |

## Son doğrulamalar

- 2026-09-25 — `scripts/test-db.sh` yerelde ve CI'da PASS (001, 002).
- 2026-09-25 — CI `1fb635b`: Android unit test + lintRelease + assembleDebug + bundleRelease PASS.
- 2026-09-26 — Yerel: SQL 001–003 PASS (+2 mutasyon yakalandı), Deno check + 4 Edge Function testi PASS.
- 2026-09-26 — CI `77d5289`: DB, Edge Function (Deno), Android (unit test, lintRelease, APK, AAB) PASS.
- 2026-09-26 — Yerel: SQL 001–004 PASS; 004 cascade testi önce kırmızı (0/1/0) sonra yeşil doğrulandı.
- 2026-09-26 — Yerel: SQL 001–005 PASS (pgvector 0.6), Deno check + 15 Edge Function testi PASS.
- 2026-09-26 — Yerel: SQL 006 (eşleşme) PASS; kampüs filtresi mutasyonu yakalandı.
- 2026-09-26 — Yerel: SQL 007 (sohbet) PASS; üyelik kontrolü mutasyonu yakalandı. CI `dfdaa4e` (faz 4–6 + test düzeltmeleri) bekleniyor.
- 2026-09-26 — CI `dfdaa4e`: DB, Edge Function, Android (unit test, lintRelease, APK, AAB) PASS.
- 2026-09-26 — Yerel: SQL 001–008 PASS, Deno check + 21 test PASS.
- 2026-09-26 — Yerel: SQL 001–009 PASS, Deno check + 24 test PASS.

## Canlı Supabase (2026-09-26)

- Proje `kbyiaqitiukuthjdkwwd` (KampusAgi): 11 migration + 6 Edge Function deploy edildi.
- Canlıda doğrulandı (SQL, rol simülasyonu): 206 üniversite / 81 il, 17 tablo (hepsi RLS), 54 fonksiyon (yerel test DB ile aynı),
  realtime yayını (messages, conversation_members, notifications), özel `student-documents` bucket, pgvector 0.8.2;
  anon üniversiteleri göremez ve RPC çağıramaz; onaysız hesap `approved_student_required`, admin olmayan `admin_required` alır;
  tabloya doğrudan yazma RLS ile reddedilir.
- Danışman bulguları: `handle_new_user` anon/authenticated'a açıktı ve `reports.resolved_by` indekssizdi → düzeltildi, SQL test 011 eklendi.
- HTTP üzerinden salt-okunur kontrol: CI `live-smoke` job'ı, `646e4e6` → 10/10 PASS (istemci anahtarı, e-posta onayı zorunlu,
  anon üniversite/RPC erişimi yok, tetikleyici fonksiyon açık değil, 5 Edge Function oturum ister).
- Giriş yapmış kullanıcıyla uçtan uca akış (kayıt → belge → onay → ihtiyaç → eşleşme → sohbet): **NOT RUN** — gerçek cihaz/hesap gerekir.
- Auth yönlendirme URL'leri, OpenAI/Play secret'ları, release keystore, `PRIVACY_POLICY_URL`, webhook: **dış yapılandırma** (docs/DEPLOYMENT.md).

