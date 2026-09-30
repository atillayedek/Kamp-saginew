import type { Metadata } from "next";
import { Contact, H2, LegalPage, List } from "@/components/legal/LegalPage";
import { DeleteAccountForm } from "./DeleteAccountForm";

export const metadata: Metadata = {
  title: "Hesap silme — KampüsAğı",
  description: "KampüsAğı hesabını ve tüm verilerini uygulamadan ya da bu sayfadan kalıcı olarak silebilirsin.",
};

export default function DeleteAccountPage() {
  return (
    <LegalPage title="KampüsAğı hesabını silme" updated="30 Eylül 2026">
      <p>KampüsAğı hesabını ve ona bağlı tüm verileri istediğin zaman kalıcı olarak silebilirsin. İki yolu var:</p>

      <H2>Uygulamadan</H2>
      <List
        items={[
          "KampüsAğı uygulamasını aç ve giriş yap.",
          "Profil sekmesinde sağ üstteki Ayarlar'a gir.",
          "Hesabı sil'e dokun, uyarıyı okuyup onayla.",
        ]}
      />

      <H2>Bu sayfadan</H2>
      <p>Uygulamaya erişemiyorsan hesabının e-posta adresi ve şifresiyle aşağıdan silebilirsin. Giriş bilgilerin yalnızca bu işlem için kullanılır, tarayıcında saklanmaz.</p>
      <DeleteAccountForm />

      <H2>Neler silinir?</H2>
      <List
        items={[
          "Hesabın, profilin, profil fotoğrafın ve öğrenci belgen.",
          "Gönderilerin, yorumların, beğenilerin, anket oyların, kaydettiğin gönderiler, fotoğrafların ve ders notların.",
          "Birebir sohbetlerin, grup ve kanal mesajların, sahibi olduğun gruplar ve kanallar.",
          "İhtiyaç ilanların, bildirimlerin, engelleme ve şikayet kayıtların, günlük açılış kayıtların.",
        ]}
      />
      <p>Silme hemen gerçekleşir ve geri alınamaz. Veriler yedeklerden en geç 30 gün içinde kalkar.</p>

      <H2>Saklanan kayıtlar</H2>
      <p>
        Premium satın alma kayıtları vergi ve muhasebe yükümlülükleri nedeniyle, kişiyle bağı koparılarak saklanır.
        Google Play aboneliğini Google keser; hesabını silmek aboneliği iptal etmez. Silmeden önce Google Play → Ödemeler ve
        abonelikler bölümünden aboneliğini iptal et.
      </p>

      <H2>Yardım</H2>
      <Contact />
    </LegalPage>
  );
}
