-- Universities in the Turkish Republic of Northern Cyprus (KKTC), added at the product owner's
-- request (2026-10-03). The city carries "(KKTC)" so the picker shows the country and a search for
-- "kktc" finds them. Domains are filled only where the official address is known; the rest stay
-- null until confirmed. Re-running is safe: rows are matched by name, nothing is deleted.

insert into public.universities (name, city, website_domain) values
    ('ADA KENT ÜNİVERSİTESİ', 'Gazimağusa (KKTC)', null),
    ('AKDENİZ KARPAZ ÜNİVERSİTESİ', 'Lefkoşa (KKTC)', null),
    ('ARKIN YARATICI SANATLAR VE TASARIM ÜNİVERSİTESİ', 'Girne (KKTC)', null),
    ('BAHÇEŞEHİR KIBRIS ÜNİVERSİTESİ', 'Lefkoşa (KKTC)', 'baucyprus.edu.tr'),
    ('DOĞU AKDENİZ ÜNİVERSİTESİ', 'Gazimağusa (KKTC)', 'emu.edu.tr'),
    ('FİNAL ULUSLARARASI ÜNİVERSİTESİ', 'Girne (KKTC)', 'final.edu.tr'),
    ('GİRNE AMERİKAN ÜNİVERSİTESİ', 'Girne (KKTC)', 'gau.edu.tr'),
    ('GİRNE ÜNİVERSİTESİ', 'Girne (KKTC)', 'kyrenia.edu.tr'),
    ('KIBRIS AMERİKAN ÜNİVERSİTESİ', 'Lefkoşa (KKTC)', 'cau.edu.tr'),
    ('KIBRIS BATI ÜNİVERSİTESİ', 'Gazimağusa (KKTC)', null),
    ('KIBRIS İLİM ÜNİVERSİTESİ', 'Girne (KKTC)', null),
    ('KIBRIS SAĞLIK VE TOPLUM BİLİMLERİ ÜNİVERSİTESİ', 'Güzelyurt (KKTC)', null),
    ('KIBRIS SOSYAL BİLİMLER ÜNİVERSİTESİ', 'Lefkoşa (KKTC)', null),
    ('LEFKE AVRUPA ÜNİVERSİTESİ', 'Lefke (KKTC)', 'eul.edu.tr'),
    ('ORTA DOĞU TEKNİK ÜNİVERSİTESİ KUZEY KIBRIS KAMPUSU', 'Güzelyurt (KKTC)', 'ncc.metu.edu.tr'),
    ('RAUF DENKTAŞ ÜNİVERSİTESİ', 'Lefkoşa (KKTC)', null),
    ('ULUSLARARASI KIBRIS ÜNİVERSİTESİ', 'Lefkoşa (KKTC)', 'ciu.edu.tr'),
    ('YAKIN DOĞU ÜNİVERSİTESİ', 'Lefkoşa (KKTC)', 'neu.edu.tr'),
    ('İSTANBUL TEKNİK ÜNİVERSİTESİ KKTC EĞİTİM-ARAŞTIRMA YERLEŞKESİ', 'Lefkoşa (KKTC)', null)
on conflict (name) do update
    set city = excluded.city,
        website_domain = coalesce(excluded.website_domain, public.universities.website_domain),
        is_active = true;
