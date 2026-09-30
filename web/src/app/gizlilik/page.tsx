import type { Metadata } from "next";
import Link from "next/link";
import { Contact, H2, LegalPage, List } from "@/components/legal/LegalPage";

export const metadata: Metadata = {
  title: "Gizlilik politikası ve KVKK aydınlatma metni — KampüsAğı",
  description: "KampüsAğı hangi kişisel verileri neden işler, kimlerle paylaşır, ne kadar saklar ve haklarını nasıl kullanırsın.",
};

export default function PrivacyPage() {
  return (
    <LegalPage title="Gizlilik politikası ve KVKK aydınlatma metni" updated="30 Eylül 2026">
      <p>
        Bu metin, KampüsAğı Android uygulamasını ve kampusagi web sitesini kullanırken kişisel verilerinin nasıl işlendiğini
        6698 sayılı Kişisel Verilerin Korunması Kanunu (KVKK) kapsamında açıklar.
      </p>
      <Contact />

      <H2>1. Hangi verileri işliyoruz?</H2>
      <List
        items={[
          <><strong>Hesap:</strong> e-posta adresin ve şifren (şifre yalnızca kimlik doğrulama sağlayıcısında karma olarak saklanır, bizim tarafımızdan görülemez).</>,
          <><strong>Profil:</strong> ad soyad, kullanıcı adı, üniversite, bölüm, isteğe bağlı biyografi ve profil fotoğrafı.</>,
          <><strong>Öğrenci belgesi:</strong> öğrenci olduğunu doğrulamak için yüklediğin PDF belge. Yalnızca sen ve yetkili yöneticiler görebilir.</>,
          <><strong>Paylaştıkların:</strong> gönderiler, yorumlar, gönderi fotoğrafları, anketler ve oyların, etkinlik ve katılımların, ilan fiyatları, kaydettiğin gönderiler, ders notu PDF&apos;leri, ihtiyaç ilanların.</>,
          <><strong>Mesajlar:</strong> birebir sohbetler, çalışma grubu ve kanal mesajları, beğeniler, okundu bilgisi.</>,
          <><strong>Güvenlik kayıtları:</strong> engellediğin kişiler, gönderdiğin ve hakkında yapılan şikayetler.</>,
          <><strong>Satın alma:</strong> Premium için Google Play satın alma jetonu, sipariş numarası, plan ve fiyat bilgisi. Kart bilgilerin bize hiç ulaşmaz; ödemeyi Google işler.</>,
          <><strong>Teknik veriler:</strong> uygulama çöktüğünde uygulama sürümü, Android sürümü, cihaz modeli ve hata kaydı; uygulamayı hangi gün açtığın (günde tek kayıt, istatistik için).</>,
          <><strong>Tercihler:</strong> pazarlama e-postası izni ve zamanı, görünüm (koyu/açık tema) tercihi (yalnızca cihazında).</>,
        ]}
      />
      <p>Konum, rehber, mikrofon, reklam kimliği veya cihazındaki diğer uygulamalar hakkında veri toplamıyoruz. Uygulamada reklam yoktur.</p>

      <H2>2. Hangi amaçla ve hangi hukuki sebeple?</H2>
      <List
        items={[
          "Hesabını açmak ve hizmeti sunmak (KVKK m.5/2-c, sözleşmenin kurulması ve ifası).",
          "Yalnızca gerçek üniversite öğrencilerinin katılabilmesi için öğrenci belgeni incelemek (m.5/2-c ve m.5/2-f, meşru menfaat: topluluğun güvenliği).",
          "Yapay zekâ ile ihtiyaç ilanını düzenlemek ve benzer ilanlarla eşleştirmek (m.5/2-c).",
          "Kötüye kullanımı önlemek, şikayetleri incelemek, hesapları askıya almak (m.5/2-f ve hukuki yükümlülükler, m.5/2-ç).",
          "Premium satın alımlarını doğrulamak ve muhasebe kayıtlarını tutmak (m.5/2-c, m.5/2-ç).",
          "Hataları düzeltmek ve hizmetin nasıl kullanıldığını toplu olarak görmek (m.5/2-f).",
          "Pazarlama e-postası göndermek — yalnızca uygulamada açık rızanla (m.5/1); rızanı her an geri alabilirsin.",
        ]}
      />

      <H2>3. Kimler görebilir?</H2>
      <List
        items={[
          "Profilin, gönderilerin ve profil fotoğrafın yalnızca onaylanmış öğrencilere görünür; «Üniversitem» gönderileri ve ders notları yalnızca aynı üniversitedeki öğrencilere. E-posta adresin diğer kullanıcılara hiçbir zaman gösterilmez.",
          "Engellediğin ya da seni engelleyen kişi gönderilerini, mesajlarını ve profilini göremez.",
          "Yöneticiler öğrenci belgelerini ve şikayet edilen içeriği inceleyebilir.",
        ]}
      />

      <H2>4. Verileri aktardığımız hizmet sağlayıcılar</H2>
      <p>Hizmeti çalıştırmak için aşağıdaki veri işleyenlerle çalışıyoruz. Bunların bir kısmının sunucuları yurt dışındadır (KVKK m.9):</p>
      <List
        items={[
          <><strong>Supabase</strong> — veritabanı, kimlik doğrulama, dosya depolama ve sunucu fonksiyonları (tüm hesap ve içerik verileri).</>,
          <><strong>OpenAI</strong> — yazdığın ihtiyaç metni analiz ve eşleştirme için gönderilir; adın, e-postan veya belgen gönderilmez.</>,
          <><strong>Google (Google Play)</strong> — Premium ödemeleri ve satın alma doğrulaması.</>,
          <><strong>Resend</strong> — hizmet ve (rızan varsa) pazarlama e-postalarının gönderimi; e-posta adresin ve mesaj içeriği.</>,
          <><strong>Vercel</strong> — web sitesi ve yönetim panelinin barındırılması.</>,
        ]}
      />
      <p>Verilerini satmıyoruz ve reklam amacıyla kimseyle paylaşmıyoruz. Yasal bir zorunluluk olduğunda yetkili kamu kurumlarıyla paylaşabiliriz.</p>

      <H2>5. Ne kadar saklıyoruz?</H2>
      <List
        items={[
          "Hesabın açık olduğu sürece. Hesabını sildiğinde profilin, gönderilerin, mesajların, yüklediğin belgeler ve fotoğraflar ile diğer tüm kişisel verilerin hemen silinir; yedeklerden en geç 30 gün içinde kalkar.",
          "Sildiğin bir gönderi veya yorum diğer kullanıcılara hemen gizlenir; hesabın silindiğinde tamamen kalkar.",
          "Premium satın alma kayıtları vergi ve muhasebe yükümlülükleri için kişiyle bağı koparılarak (anonim) saklanır.",
          "Çökme kayıtları ve günlük açılış kayıtları hesabınla birlikte silinir.",
        ]}
      />

      <H2>6. Güvenlik</H2>
      <p>
        Bağlantılar şifrelidir (HTTPS). Dosyalar herkese açık değildir; yalnızca görmeye yetkili kişilerin oturumuyla indirilebilir.
        Veritabanında her tablo satır düzeyinde güvenlikle korunur; yetki kontrolleri sunucuda yapılır.
      </p>

      <H2>7. Haklarım neler?</H2>
      <p>KVKK m.11 uyarınca verilerinin işlenip işlenmediğini öğrenme, bilgi ve kopyasını isteme, düzeltilmesini veya silinmesini isteme, işlemeye itiraz etme ve zararın giderilmesini talep etme hakların vardır.</p>
      <List
        items={[
          "Profil bilgilerini ve fotoğrafını uygulamadan düzenleyebilirsin.",
          "Pazarlama e-postası iznini Ayarlar ekranından ya da e-postadaki bağlantıdan kapatabilirsin.",
          <>Hesabını uygulamada Ayarlar → Hesabı sil ile ya da <Link className="text-brand underline-offset-2 hover:underline" href="/hesap-silme">web üzerinden</Link> silebilirsin.</>,
          "Diğer talepler için yukarıdaki e-posta adresine yaz; en geç 30 gün içinde yanıtlanır.",
        ]}
      />

      <H2>8. Yaş sınırı</H2>
      <p>KampüsAğı üniversite öğrencileri içindir ve 18 yaşından küçükler kullanamaz. 18 yaşından küçük birine ait olduğunu fark ettiğimiz hesaplar kapatılır.</p>

      <H2>9. Değişiklikler</H2>
      <p>Bu metni güncellediğimizde üstteki tarihi değiştiririz; önemli değişiklikleri uygulama içinden duyururuz.</p>
    </LegalPage>
  );
}
