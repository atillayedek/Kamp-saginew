import type { Metadata } from "next";
import { Contact, H2, LegalPage, List } from "@/components/legal/LegalPage";

export const metadata: Metadata = {
  title: "Çocuk güvenliği standartları — KampüsAğı",
  description: "KampüsAğı'nın çocukların cinsel istismarına ve istismar materyaline (CSAE/CSAM) karşı standartları.",
};

export default function ChildSafetyPage() {
  return (
    <LegalPage title="Çocuk güvenliği standartları" updated="30 Eylül 2026">
      <p>
        KampüsAğı, çocukların cinsel istismarına ve istismarı içeren materyallere (CSAE / CSAM) karşı sıfır tolerans uygular.
        Bu sayfa, Google Play&apos;in çocuk güvenliği standartları politikası kapsamında yayımlanmıştır.
      </p>

      <H2>Yasaklar</H2>
      <List
        items={[
          "Reşit olmayanları cinselleştiren, istismar eden veya tehlikeye atan her türlü metin, görsel, bağlantı ve mesaj.",
          "Reşit olmayanlarla cinsel amaçlı iletişim kurma, onları kandırma veya yönlendirme (grooming).",
          "Bu tür materyalin talep edilmesi, paylaşılması veya nereden bulunabileceğinin söylenmesi.",
        ]}
      />

      <H2>Nasıl önlüyoruz?</H2>
      <List
        items={[
          "Hizmet yalnızca 18 yaşını doldurmuş, üniversite öğrenci belgesi yöneticilerce onaylanmış kişilere açıktır.",
          "Her gönderi, yorum, mesaj, grup ve kanal mesajı, ders notu ve kullanıcı uygulama içinden tek dokunuşla şikayet edilebilir; kişiler engellenebilir.",
          "Şikayetler yöneticiler tarafından öncelikle incelenir; ihlal içeren içerik kaldırılır ve hesap kalıcı olarak kapatılır.",
          "CSAM tespit edildiğinde içerik kaldırılır, gerekli kayıtlar saklanır ve yetkili makamlara (Türkiye'de Emniyet Genel Müdürlüğü Siber Suçlarla Mücadele birimi, gerektiğinde NCMEC) bildirilir.",
          "Yürürlükteki çocuk güvenliği mevzuatına uyarız.",
        ]}
      />

      <H2>Bildirmek için</H2>
      <p>Uygulamadaki «Şikayet et» seçeneğini kullan ya da aşağıdaki adrese yaz. Acil bir tehlike varsa önce 112&apos;yi ara.</p>
      <Contact />
      <p>Bu adres çocuk güvenliğiyle ilgili bildirimler ve Google Play ile iletişim için belirlenmiş sorumlu kişiye ulaşır.</p>
    </LegalPage>
  );
}
