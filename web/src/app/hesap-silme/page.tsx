import type { Metadata } from "next";
import { Contact, H2, LegalPage, List } from "@/components/legal/LegalPage";
import { DeleteAccountForm } from "./DeleteAccountForm";

export const metadata: Metadata = {
  title: "Hesap silme — KampüsAğı",
  description: "KampüsAğı hesabını ve tüm verilerini uygulamadan ya da bu sayfadan silebilirsin; 30 gün içinde vazgeçebilirsin.",
};

export default function DeleteAccountPage() {
  return (
    <LegalPage title="KampüsAğı hesabını silme" updated="2 Ekim 2026">
      <p>KampüsAğı hesabını ve ona bağlı tüm verileri istediğin zaman kalıcı olarak silebilirsin. İki yolu var:</p>

      <H2>Uygulamadan</H2>
      <List
        items={[
          "KampüsAğı uygulamasını aç ve giriş yap.",
          "Profil sekmesinde sağ üstteki Ayarlar'a, ardından Gizlilik ve KVKK'ya gir.",
          "Hesabımı sil'e dokun, uyarıyı okuyup onayla.",
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
          "Birebir sohbetlerin. Grup ve kanallardaki mesajların diğer üyeler için \"Silinmiş kullanıcı\" adıyla kalır; kurduğun gruplar en eski yöneticiye ya da üyeye devredilir.",
          "İhtiyaç ilanların, bildirimlerin, engelleme ve şikayet kayıtların, günlük açılış kayıtların.",
        ]}
      />
      <p>
        Talebinden sonra <strong>30 gün</strong> boyunca uygulamaya giriş yapıp silmekten vazgeçebilirsin. Süre dolunca silme otomatik
        yapılır ve geri alınamaz. Veriler barındırma sağlayıcısının yedeklerinden, yedek saklama süresi sonunda kendiliğinden kalkar.
      </p>

      <H2>Saklanan kayıtlar</H2>
      <p>
        5651 sayılı Kanun gereği tutulan giriş-çıkış kayıtları (IP adresi, zaman, cihaz) ile verdiğin onayların kayıtları, yasal
        saklama süreleri boyunca profilinden ayrı olarak saklanır ve süre dolunca silinir; bunlar başka hiçbir amaçla kullanılmaz.
        Premium satın alma kayıtları vergi ve muhasebe yükümlülükleri nedeniyle, kişiyle bağı koparılarak saklanır.
        Google Play aboneliğini Google keser; hesabını silmek aboneliği iptal etmez. Silmeden önce Google Play → Ödemeler ve
        abonelikler bölümünden aboneliğini iptal et.
      </p>

      <H2>Yardım</H2>
      <Contact />
    </LegalPage>
  );
}
