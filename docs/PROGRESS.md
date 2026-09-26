# İlerleme

Oturumlar arası hafıza. Her oturum başında `CLAUDE.md`, `docs/DECISIONS.md` ve bu dosya okunur.
Otomatik devam: saatlik Routine (`KampüsAğı fazlarına otomatik devam`) bu oturumu uyandırır.

## Faz planı

| Faz | Kapsam | Durum |
|---|---|---|
| 1 | Audit + production gap analizi | Bitti (`docs/PHASE1_AUDIT.md`) |
| 2 | Proje iskeleti, CI, Supabase şeması, Supabase Auth, profil tamamlama, üniversite verisi | Bitti — CI yeşil (DB testleri, unit test, lintRelease, debug APK, release AAB), `1fb635b` |
| 3 | Öğrenci belgesi: PDF yükleme → Storage → Edge Function doğrulaması → admin inceleme → APPROVED/REJECTED | Bitti — CI yeşil `77d5289` (DB, Edge Function, Android). Canlı Supabase'e deploy: dış yapılandırma |
| 4 | Ana uygulama iskeleti (alt gezinme) + Topluluklar: gönderi, yorum, beğeni (genel / üniversitem) | Kod bitti. CI `4d111da`: test derlemesi FAILED (test yardımcısı gölgelendi) → `df07226` ile düzeltildi, CI bekleniyor |
| 5 | İhtiyaç oluşturma: OpenAI yapılandırılmış çıktı (backend) + embedding + pgvector | Kod bitti; CI bekleniyor. Canlı OpenAI: dış yapılandırma (`docs/DEPLOYMENT.md`) |
| 6 | Eşleşme: pgvector benzerlik sorgusu, gerçek skor, eşleşme ekranı | Kod bitti; CI bekleniyor. Sohbete geçiş Faz 7'de |
| 7 | Sohbet: Realtime, gönderildi/okundu | Kod bitti; CI bekleniyor. Yazıyor/çevrimiçi yok (D20) |
| 8 | Bildirimler: uygulama içi + push | Bekliyor (push sağlayıcı kararı gerekli: Bölüm 0.12 FCM) |
| 9 | Premium: Google Play Billing + sunucu doğrulaması + entitlement | Bekliyor |
| 10 | Ayarlar, hesap silme, gizlilik, raporlama/engelleme | Bekliyor |
| 11 | Release sertleştirme: imza, AAB, R8, erişilebilirlik, son tarama | Bekliyor |

## Son doğrulamalar

- 2026-09-25 — `scripts/test-db.sh` yerelde ve CI'da PASS (001, 002).
- 2026-09-25 — CI `1fb635b`: Android unit test + lintRelease + assembleDebug + bundleRelease PASS.
- 2026-09-26 — Yerel: SQL 001–003 PASS (+2 mutasyon yakalandı), Deno check + 4 Edge Function testi PASS.
- 2026-09-26 — CI `77d5289`: DB, Edge Function (Deno), Android (unit test, lintRelease, APK, AAB) PASS.
- 2026-09-26 — Yerel: SQL 001–004 PASS; 004 cascade testi önce kırmızı (0/1/0) sonra yeşil doğrulandı.
- 2026-09-26 — Yerel: SQL 001–005 PASS (pgvector 0.6), Deno check + 15 Edge Function testi PASS.
- 2026-09-26 — Yerel: SQL 006 (eşleşme) PASS; kampüs filtresi mutasyonu yakalandı.
- 2026-09-26 — Yerel: SQL 007 (sohbet) PASS; üyelik kontrolü mutasyonu yakalandı. CI `dfdaa4e` (faz 4–6 + test düzeltmeleri) bekleniyor.
