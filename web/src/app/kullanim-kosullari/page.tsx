import type { Metadata } from "next";
import Link from "next/link";
import { Contact, H2, LegalPage, List } from "@/components/legal/LegalPage";

export const metadata: Metadata = {
  title: "Kullanım koşulları — KampüsAğı",
  description: "KampüsAğı'nı kullanırken uyman gereken kurallar, içerik ve topluluk kuralları, Premium ve hesap kapatma.",
};

export default function TermsPage() {
  return (
    <LegalPage title="Kullanım koşulları" updated="30 Eylül 2026">
      <p>
        KampüsAğı&apos;na kaydolarak bu koşulları ve <Link className="text-brand underline-offset-2 hover:underline" href="/gizlilik">gizlilik politikasını</Link> kabul
        etmiş olursun. Kabul etmiyorsan hizmeti kullanmamalısın.
      </p>
      <Contact />

      <H2>1. Kimler kullanabilir?</H2>
      <List
        items={[
          "18 yaşını doldurmuş ve Türkiye'deki bir üniversitede öğrenci olan kişiler.",
          "Topluluklara, sohbetlere, gruplara ve kanallara katılmak için öğrenci belgenin yöneticilerce onaylanması gerekir.",
          "Her kişi tek hesap açabilir. Hesap bilgilerin doğru olmalı; başkası adına hesap açılamaz.",
        ]}
      />

      <H2>2. Topluluk kuralları</H2>
      <p>Aşağıdakiler yasaktır ve içeriğin kaldırılmasına, hesabın askıya alınmasına ya da kapatılmasına yol açar:</p>
      <List
        items={[
          "Taciz, zorbalık, tehdit, nefret söylemi, ayrımcılık.",
          "Çıplaklık, cinsel içerik; reşit olmayanları cinselleştiren her türlü içerik (derhal kaldırılır ve yetkili makamlara bildirilir).",
          "Şiddeti, kendine zarar vermeyi, yasa dışı madde veya silah ticaretini özendiren içerik.",
          "Başkalarının kişisel bilgilerini izinsiz paylaşmak, sahte profil, dolandırıcılık, spam.",
          "Telif hakkıyla korunan materyali (ör. yayıncıya ait kitaplar, sınav soruları) izinsiz paylaşmak.",
          "Pazar yerinde yasa dışı ürün veya hizmet ilanı vermek.",
        ]}
      />
      <p>
        Uygunsuz bir gönderiyi, yorumu, mesajı, grubu, kanalı, ders notunu ya da kullanıcıyı uygulama içinden şikayet edebilir, kişileri
        engelleyebilirsin. Şikayetler yöneticiler tarafından incelenir.
      </p>

      <H2>3. Paylaştığın içerik</H2>
      <p>
        Paylaştığın içeriğin sorumluluğu sana aittir ve haklarına sahip olmalısın. İçeriğini KampüsAğı&apos;nda göstermemiz için bize
        gereken sınırlı, ücretsiz ve devredilemez kullanım iznini verirsin; içeriğini silersen veya hesabını kapatırsan bu izin sona erer.
      </p>

      <H2>4. Pazar yeri ve etkinlikler</H2>
      <p>
        Al-sat ilanları ve etkinlikler kullanıcılar arasındadır. KampüsAğı alışverişe veya etkinliğe taraf değildir, ödeme almaz ve
        teslimatı garanti etmez. Buluşmalarda dikkatli ol; şüpheli ilanları şikayet et.
      </p>

      <H2>5. Premium</H2>
      <List
        items={[
          "Premium, Google Play üzerinden yenilenen bir aboneliktir; fiyat Google Play'de gösterilir ve ödeme Google tarafından alınır.",
          "Aboneliği Google Play → Ödemeler ve abonelikler bölümünden istediğin zaman iptal edebilirsin; dönem sonuna kadar Premium sürer.",
          "Premium; kanal açma, kanal fotoğrafı, kanalda anket paylaşma ve daha fazla aktif ihtiyaç ilanı sağlar. Premium sona ererse kanalın silinmez ancak yeni paylaşım yapılamaz.",
          "İade talepleri Google Play iade politikasına tabidir.",
        ]}
      />

      <H2>6. Eşleştirme</H2>
      <p>
        İhtiyaç eşleştirmesi yapay zekâ kullanmaz; aynı kategorideki ilanlar ortak etiket, ortak kelime, zaman ve yer kurallarıyla puanlanır.
        Eşleşme puanı yalnızca ilanların benzerliğini gösterir, kişi hakkında bir değerlendirme değildir.
      </p>

      <H2>7. Hesabın kapatılması</H2>
      <p>
        Hesabını istediğin an uygulamadan veya <Link className="text-brand underline-offset-2 hover:underline" href="/hesap-silme">web üzerinden</Link> silebilirsin.
        Kuralları ihlal eden hesapları askıya alabilir veya kapatabiliriz.
      </p>

      <H2>8. Sorumluluğun sınırı</H2>
      <p>
        Hizmeti olduğu gibi sunarız ve kesintisiz çalışacağını garanti edemeyiz. Kullanıcıların birbirleriyle ilişkilerinden ve
        paylaşımlarından doğan zararlardan, yasaların izin verdiği ölçüde sorumlu değiliz.
      </p>

      <H2>9. Uygulanacak hukuk</H2>
      <p>Bu koşullar Türkiye Cumhuriyeti hukukuna tabidir. Tüketici olarak yasal hakların saklıdır.</p>
    </LegalPage>
  );
}
