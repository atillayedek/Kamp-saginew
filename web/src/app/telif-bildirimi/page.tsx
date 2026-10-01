import type { Metadata } from "next";
import Link from "next/link";
import { Contact, H2, LegalPage } from "@/components/legal/LegalPage";
import { CopyrightForm } from "./CopyrightForm";

export const metadata: Metadata = {
  title: "Telif bildirimi — KampüsAğı",
  description: "Eserinin KampüsAğı'nda izinsiz paylaşıldığını düşünüyorsan hesap açmadan bildirebilirsin.",
};

export default function CopyrightNoticePage() {
  return (
    <LegalPage title="Telif hakkı ihlali bildirimi" updated="2 Ekim 2026">
      <p>
        Hak sahibi olduğun bir eserin (ders kitabı, sınav soruları, ders içeriği vb.) KampüsAğı&apos;nda izinsiz paylaşıldığını
        düşünüyorsan bu formu doldur. Hesap açman gerekmez. Bildirimler moderatörlerce incelenir; ihlal açıksa içerik kaldırılır ve
        paylaşan kullanıcıya gerekçesiyle bildirilir. Ayrıntılar:{" "}
        <Link className="text-brand hover:underline" href="/yasal/telif-politikasi">Telif Hakkı ve İçerik Kaldırma Politikası</Link>.
      </p>
      <CopyrightForm />
      <H2>Diğer yollar</H2>
      <Contact />
      <p className="text-sm text-ink-3">Formda verdiğin ad, e-posta ve IP adresi yalnızca bu bildirimin incelenmesi ve ispatı için işlenir.</p>
    </LegalPage>
  );
}
