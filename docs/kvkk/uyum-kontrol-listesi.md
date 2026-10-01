# KVKK ve Yasal Uyum — Kontrol Listesi

Durumlar: **Kod tamam** (yazıldı, testli, CI'da doğrulanır) · **Hukuki işlem gerekli** (kod hazır, avukat/veri
sorumlusu adımı bekliyor) · **Bekliyor** (dış yapılandırma ya da karar bekliyor; kod yok ya da kapalı).
Görev tanımındaki Firebase/iOS/api.kampusagi.com karşılıkları için `veri-envanteri.md` → "Önemli not: gerçek mimari".

## 0. Çalışma kuralları

| Madde | Durum | Nerede |
|---|---|---|
| Kişisel veri envanteri | Kod tamam | `docs/kvkk/veri-envanteri.md` |
| Şirket bilgisi uydurulmadı, yer tutucular + "TASLAK — AVUKAT ONAYI GEREKLİ" | Hukuki işlem gerekli (S0, S0b) | `docs/legal/*.md` |
| Yasal süreler tek yapılandırmadan | Kod tamam | `compliance_settings` tablosu (panel → KVKK → Ayarlar, her değişiklik loglanır); istemciler `public_compliance_config()` okur. `config/compliance.ts` yerine veritabanı: Android, web ve SQL aynı kaynağı okur |
| Mock veri yok | Kod tamam | Testler gerçek migration'lar üzerinde çalışır |

## 1. Hukuki metinler

| Madde | Durum | Nerede |
|---|---|---|
| Aydınlatma Metni (kategori bazında) | Hukuki işlem gerekli | `docs/legal/aydinlatma_metni.md` |
| Açık rıza metinleri — ayrı ve hizmete bağlı değil (e-posta kampanyası, bildirim kampanyası) | Kod tamam / Hukuki işlem gerekli | `acik_riza_pazarlama_*.md`; analitik SDK'sı olmadığı için analitik rızası yok; yurt dışı için açık rıza yok (S3) |
| Kullanım Koşulları, Topluluk Kuralları, Gizlilik, Çerez/SDK, Telif, Premium ön bilgilendirme + mesafeli sözleşme, Saklama ve İmha, Başvuru Formu | Hukuki işlem gerekli | `docs/legal/` (13 metin) |
| Veritabanında sürümlü, `content_sha256`, eski sürüm silinemez | Kod tamam | `legal_documents` (değişmezlik tetikleyicisi), test 018 |
| Uygulamada ve web'de gösterim | Kod tamam | Android `LegalDocumentScreen`; web `/yasal`, `/yasal/[slug]`, `/gizlilik`, `/kullanim-kosullari`, `/cocuk-guvenligi` |
| Yeni sürüm yayımlama | Kod tamam | Panel → KVKK → Hukuki metinler (gerekçe zorunlu, loglanır) |

## 2. Kayıt ve giriş

| Madde | Durum | Nerede |
|---|---|---|
| Doğum tarihiyle 18+ kontrolü, tarih saklanmaz | Kod tamam | Android `AgePolicy`; DB `kvkk_before_signup` (sunucuda tekrar kontrol + silme), test 018 |
| Aydınlatma "okudum" (rıza değil, `informed`) | Kod tamam | Kayıt ekranı + `consent_logs` |
| Koşullar + Topluluk Kuralları zorunlu, işaretsiz | Kod tamam | `SignUpLegalSection`, `SignUpForm` testleri |
| Açık rızalar ayrı, işaretsiz, reddetmek kaydı engellemez | Kod tamam | `UseCaseTest` |
| Log bilgilendirme kutusu (metin yapılandırmadan) | Kod tamam | `register_log_notice`, `login_log_notice` |
| Giriş altında kalıcı not | Kod tamam | `SignInScreen` |
| Metin güncellenince yeniden kabul / okundu | Kod tamam | `pending_legal_documents` → `LegalUpdateScreen` |
| Yüksek yaş beyanının doğrulanmaması | Hukuki işlem gerekli (S15) | — |

## 3. Öğrenci belgesi

| Madde | Durum | Nerede |
|---|---|---|
| Yükleme öncesi bilgilendirme (amaç, kim görür, süre) | Kod tamam | `document_upload_notice` + `VerificationScreen` |
| Karar sonrası en geç N gün içinde otomatik imha | Kod tamam / Bekliyor (pg_cron) | `retention-run`, `retention_due`, `deletion_logs`; S18 |
| Kalıcı olarak yalnızca karar, doğrulayan, SHA-256 | Kod tamam | `student_verifications.document_sha256`, `submit-student-document` |
| OCR/belge içeriği kaydı yok | Kod tamam | — |
| Okuma yalnızca verifier/superadmin, kısa süreli imzalı URL, her açılış loglanır | Kod tamam | `admin-document` (5 dk), storage politikası, test 003/018 |

## 4. Veri minimizasyonu

| Madde | Durum | Nerede |
|---|---|---|
| md.6 alanı yok | Kod tamam | Envanter "Özel nitelikli veriler" |
| Etiketlerde hassas kategori reddi, serbest metinde uyarı | Kod tamam | `sensitive_terms`, `create_requirement` → `sensitive_tag`; Android uyarısı; test 019 |
| GPS yok, yer elle yazılır | Kod tamam | Manifestte konum izni yok |
| Soyad/e-posta varsayılan gizli, ayar | Kod tamam | `profiles.public_name`, `set_show_full_name`; telefon alanı hiç yok |
| Eşleşme açıklaması + itiraz (md.11/1-g) | Kod tamam | `MatchesScreen` "Neden?", `object_to_match`, panel → İtirazlar |

## 5. Loglar ve panel

| Madde | Durum | Nerede |
|---|---|---|
| consent / access / admin_audit / deletion logları append-only, istemci yazamaz/okuyamaz | Kod tamam | Test 018 (UPDATE/DELETE/TRUNCATE reddi, API rolleri erişemez); canlı smoke |
| SHA-256 zinciri + "Doğrula" | Kod tamam | `verify_log_chain`, panel → Gösterge |
| 5651: giriş, çıkış, başarısız giriş, şifre sıfırlama, IP, cihaz | Kod tamam / Bekliyor (pg_cron) | Uygulama olayları + Auth denetim kaydı kopyası (5 dk); port: S6 |
| data_subject_requests (30 gün, 7 gün uyarısı) | Kod tamam | Test 019, panel |
| breach_register + 72 saat sayacı | Kod tamam | Panel → İhlaller |
| content_reports (yeni kategoriler, sonuç, itiraz) | Kod tamam | `reports`, `moderation_appeals` |
| Filtre, detay, CSV/JSON dışa aktarma (dışa aktarma loglanır) | Kod tamam | Panel → Kayıtlar |
| Roller: superadmin / verifier / moderator / compliance | Kod tamam / Bekliyor (atama, S17) | `has_staff_role`, sekmeler role göre |
| Zorunlu MFA, 30 dk hareketsizlikte çıkış | Kod tamam | `MfaGate`, `useIdleSignOut`, aal2 kontrolü veritabanında |
| Yönetici özel mesaj okuyamaz; şikâyette sınırlı bağlam, gerekçeli ve loglu | Kod tamam | `admin_report_context`, test 019 |
| Her yönetici okuması/işlemi gerekçeyle loglanır | Kod tamam | `staff_gate`, `x-audit-reason`, `audit_reason_required` |

## 6. Veri sahibi hakları

| Madde | Durum | Nerede |
|---|---|---|
| Verilerimi indir (JSON + okunabilir, kısa süreli link) | Kod tamam | `export-my-data` (60 dk imzalı URL, 7 günde imha, günde 3) |
| Hesabımı sil — uygulama + giriş gerektirmeyen web, 30 gün geri alma, loglar profilden ayrılır, grup mesajları anonim | Kod tamam | `delete-account`, `/hesap-silme`, `prepare_account_deletion`, test 019 |
| Rızalarım — liste, geçmiş, tek dokunuşla geri çekme | Kod tamam | `PrivacyScreen`, `set_consent` |
| Başvuru formu → başvuru no → panel | Kod tamam | `submit_data_subject_request` |
| Profil düzeltme + doğrulanmış alan için düzeltme talebi | Kod tamam | Profil düzenleme; başvuru türü "Düzeltme" |
| Birebir sohbetlerin silmede karşı taraftan da gitmesi | Hukuki işlem gerekli (S8) | — |

## 7. Moderasyon ve 5651

| Madde | Durum | Nerede |
|---|---|---|
| Şikâyet et + Engelle (gönderi, yorum, mesaj, kanal, not, profil) | Kod tamam | `ModerationDialogs` |
| Kategoriler (taciz, nefret, kişisel veri, cinsel, telif, sahte hesap, spam, kişilik hakkı, diğer) | Kod tamam | `report_reason` enum |
| Kişilik hakkı + kişisel veri ifşası "Acil", 24 saat sayacı | Kod tamam | Panel → Moderasyon, `urgent_report_hours` |
| Girişsiz telif bildirim formu | Kod tamam | `/telif-bildirimi`, oran sınırı |
| Ders notunda zorunlu hak beyanı | Kod tamam | `rights_declared_at`, 8 argümanlı `create_course_note` |
| Kaldırma bildirimi + gerekçe + itiraz; kararlar loglanır | Kod tamam | `notify_moderation_decision`, `submit_moderation_appeal` |
| TC kimlik / IBAN / telefon uyarısı (engellemez) | Kod tamam | `PersonalDataDetector` (+ test) |

## 8. Yurt dışı aktarım

| Madde | Durum | Nerede |
|---|---|---|
| Alıcı listesi ve kontrol listesi | Kod tamam | `docs/kvkk/yurtdisi-aktarim.md` |
| Standart sözleşmeler + 5 iş günü bildirim | Hukuki işlem gerekli | — |
| Supabase bölgesinin tespiti | Bekliyor (S1) | — |

## 9. Analitik, bildirim, ticari ileti

| Madde | Durum | Nerede |
|---|---|---|
| Analitik SDK'sı yok; çökme raporu e-posta/ad içermez | Kod tamam | `NoPersonalDataInLogsTest`, `client_errors` |
| İşlemsel ve kampanya bildirim kanalları ayrı; kampanya yalnızca ayrı rızayla | Kod tamam | `MARKETING_CHANNEL_ID`, `claim_announcement_push(…, marketing)` |
| Her kampanyada kapatma yolu | Kod tamam | Bildirim → Gizlilik ve KVKK; e-postada abonelikten çık bağlantısı |
| İYS kaydı | Hukuki işlem gerekli (S13, S14) | Kayıt yapılana kadar kampanya e-postası gönderilmemeli |
| Bildirim izni gerekçeyle ve ilk kullanımda | Kod tamam | `MainScreen` gerekçe penceresi |

## 10. Güvenlik

| Madde | Durum | Nerede |
|---|---|---|
| Varsayılan kapalı RLS, olumlu/olumsuz testler | Kod tamam | `supabase/tests/001–020` |
| App Check / Play Integrity | Bekliyor (S4) | Supabase'de doğrudan karşılığı yok |
| Rate limit (giriş, şikâyet, başvuru, mesaj, telif, dışa aktarma) | Kod tamam | Auth kendi sınırları + RPC sınırları |
| Sırlar kodda yok; sızıntı taraması | Kod tamam | `config()` + secret'lar; `scripts/scan-secrets.py` CI'da dosyaları ve tüm geçmişi tarar (2026-10-01: 0 bulgu) |
| Loglarda kişisel veri yok (test) | Kod tamam | `NoPersonalDataInLogsTest`, `_shared/logging.test.ts`, `redact()` |
| Yedekler ve süreleri | Bekliyor (S5) | Supabase planına bağlı; imha politikasında yer tutucu |

## 11. Otomatik saklama ve imha

| Madde | Durum | Nerede |
|---|---|---|
| Günlük iş: belge, geri alma süresi dolan hesap, süresi dolan loglar, dışa aktarmalar, çökme raporları | Kod tamam / Bekliyor (pg_cron) | `run_daily_retention` → `retention-run`; test 019 |
| 2 yıl hareketsiz hesaba e-posta, 30 gün sonra silme | Kod tamam | `inactive_account_days`, `inactive_notice_days` |
| Her imha `deletion_logs`'a, periyodik rapor panelde | Kod tamam | Panel → İmha |
| Zamanlamanın kurulması | Bekliyor | pg_cron açılır → panel → İmha → "Zamanlamayı kur" (`admin_ensure_schedules`, test 020) |

## 12. Mağaza

| Madde | Durum | Nerede |
|---|---|---|
| Play veri güvenliği yanıtları | Kod tamam | `docs/store/play-data-safety.md` |
| App Store gizlilik etiketi; ATT gerekmez (takip yok) | Kod tamam (iOS uygulaması yok) | `docs/store/app-store-privacy.md` |
| Herkese açık gizlilik ve hesap silme URL'leri | Kod tamam | `<WEBSITE_URL>/gizlilik`, `/hesap-silme` |
| Premium ön bilgilendirme, cayma istisnası onayı, "Aboneliği yönet" | Kod tamam / Hukuki işlem gerekli (S11) | `PremiumScreen`, `consent_logs` `channel=purchase` |

## Öz denetim (görevin son sorusu)

- **Toplanan her veri envanterde mi?** Evet — tablolar, Storage bucket'ları, Auth ve web taraması envanterde.
- **Her verinin dayanağı, süresi, imha yolu var mı?** Evet; süresiz kalan ikisi (ihlal kaydı, telif bildirimi) S9/S10'da avukata soruldu.
- **Açık rıza hizmete bağlı mı?** Hayır — kampanya rızaları isteğe bağlı, reddeden kayıt olur (test).
- **Loglar istemciden değiştirilebiliyor mu?** Hayır — test 018 ve canlı smoke kontrolü.
- **Yönetici iz bırakmadan veriye erişebiliyor mu?** Hayır — yönetici okumaları `staff_gate` üzerinden loglanır, Storage
  doğrudan okunamaz, mesajlar yalnızca şikâyet bağlamında ve gerekçeyle açılır. İstisna: Supabase Dashboard'a erişen
  proje sahibi veritabanını doğrudan görebilir; bu erişim Supabase'in kendi denetim kaydındadır ve dashboard
  üyeleri sınırlı tutulmalıdır (organizasyonel tedbir).
