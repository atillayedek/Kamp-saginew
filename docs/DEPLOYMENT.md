# Dağıtım (Supabase + Android)

Bu belge, kodun canlı ortamda çalışması için **kullanıcının sağlaması gereken** yapılandırmayı listeler.
Hiçbir değer repoya yazılmaz.

## 1. Supabase projesi

```bash
supabase link --project-ref <proje-ref>
supabase db push                       # supabase/migrations/* (üniversite listesi dahil)
supabase functions deploy submit-student-document
supabase functions deploy analyze-requirement
supabase functions deploy publish-requirement
```

- **Auth → URL Configuration:** Redirect URL'lere `kampusagi://auth-callback` ve `kampusagi://auth-callback?type=recovery` eklenir.
- **Auth → Email:** "Confirm email" açık olmalı.
- **Database → Extensions:** `vector` (migration zaten `extensions` şemasında açar).

## 2. Edge Function secret'ları

| Secret | Kullanım | Not |
|---|---|---|
| `OPENAI_API_KEY` | analyze/publish-requirement | Yalnızca backend. Android'e asla girmez. |
| `OPENAI_CHAT_MODEL` | Yapılandırılmış ihtiyaç analizi | JSON schema (strict) destekleyen bir sohbet modeli. |
| `OPENAI_EMBEDDING_MODEL` | Eşleşme vektörü | 1536 boyut üretebilen bir model (ör. `text-embedding-3-small`). |

`SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY` Supabase tarafından fonksiyonlara otomatik verilir.

```bash
supabase secrets set OPENAI_API_KEY=... OPENAI_CHAT_MODEL=... OPENAI_EMBEDDING_MODEL=...
```

## 3. İlk yönetici

Supabase Dashboard → Authentication → kullanıcı → **app_metadata**: `{"role": "admin"}` (yalnızca service role/Dashboard yazabilir).
Kullanıcı yeniden giriş yaptığında (yeni JWT) uygulamada "Doğrulama başvuruları" görünür.

## 4. Android build

GitHub repo secret'ları (istemciye açık değerler): `SUPABASE_URL`, `SUPABASE_ANON_KEY`.
Yerelde aynı adlarla `local.properties` veya ortam değişkeni kullanılabilir.

Release imzası: `keystore.properties` veya ortam değişkenleri
`KAMPUSAGI_KEYSTORE_FILE`, `KAMPUSAGI_KEYSTORE_PASSWORD`, `KAMPUSAGI_KEY_ALIAS`, `KAMPUSAGI_KEY_PASSWORD`.
Sağlanmazsa release AAB imzasız üretilir ve Play'e yüklenemez.
