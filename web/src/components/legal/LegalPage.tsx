import { GraduationCap } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { config } from "@/lib/config";

export const LEGAL_LINKS = [
  { href: "/yasal/aydinlatma-metni", label: "KVKK aydınlatma metni" },
  { href: "/gizlilik", label: "Gizlilik politikası" },
  { href: "/kullanim-kosullari", label: "Kullanım koşulları" },
  { href: "/yasal/topluluk-kurallari", label: "Topluluk kuralları" },
  { href: "/yasal/cerez-sdk-politikasi", label: "Çerez ve SDK politikası" },
  { href: "/yasal/saklama-imha-politikasi", label: "Saklama ve imha" },
  { href: "/yasal/basvuru-formu", label: "KVKK başvurusu" },
  { href: "/telif-bildirimi", label: "Telif bildirimi" },
  { href: "/cocuk-guvenligi", label: "Çocuk güvenliği" },
  { href: "/hesap-silme", label: "Hesap silme" },
  { href: "/yasal", label: "Tüm metinler" },
] as const;

/** The data controller and contact, or a visible notice that they are not configured yet. */
export function Contact() {
  if (!config.legalName || !config.contactEmail) {
    return (
      <p className="rounded-lg border border-warn/40 bg-warn-soft px-4 py-3 text-sm text-warn">
        Veri sorumlusu ve iletişim adresi henüz yapılandırılmadı (NEXT_PUBLIC_LEGAL_NAME, NEXT_PUBLIC_CONTACT_EMAIL).
      </p>
    );
  }
  return (
    <p>
      Veri sorumlusu: <strong>{config.legalName}</strong> · E-posta:{" "}
      <a className="text-brand underline-offset-2 hover:underline" href={`mailto:${config.contactEmail}`}>
        {config.contactEmail}
      </a>
    </p>
  );
}

export function LegalPage({ title, updated, children }: { title: string; updated: string; children: ReactNode }) {
  return (
    <div className="min-h-dvh">
      <header className="mx-auto flex max-w-3xl items-center justify-between px-4 py-5 sm:px-6">
        <Link href="/" className="flex items-center gap-2 font-semibold">
          <span className="grid size-9 place-items-center rounded-xl bg-brand text-white">
            <GraduationCap className="size-5" aria-hidden />
          </span>
          KampüsAğı
        </Link>
      </header>
      <main className="mx-auto max-w-3xl px-4 pb-16 pt-6 sm:px-6">
        <h1 className="text-3xl font-semibold tracking-tight">{title}</h1>
        <p className="mt-2 text-sm text-ink-3">Son güncelleme: {updated}</p>
        <div className="legal mt-8 space-y-4 text-[15px] leading-relaxed text-ink-2">{children}</div>
      </main>
      <LegalFooter />
    </div>
  );
}

export function LegalFooter() {
  return (
    <footer className="border-t border-line">
      <div className="mx-auto flex max-w-6xl flex-col gap-3 px-4 py-8 text-sm text-ink-3 sm:flex-row sm:items-center sm:justify-between sm:px-6">
        <span>© {new Date().getFullYear()} KampüsAğı</span>
        <nav className="flex flex-wrap gap-x-5 gap-y-2" aria-label="Yasal">
          {LEGAL_LINKS.map((link) => (
            <Link key={link.href} href={link.href} className="hover:text-ink">
              {link.label}
            </Link>
          ))}
        </nav>
      </div>
    </footer>
  );
}

export function H2({ children }: { children: ReactNode }) {
  return <h2 className="pt-4 text-lg font-semibold text-ink">{children}</h2>;
}

export function List({ items }: { items: ReactNode[] }) {
  return (
    <ul className="list-disc space-y-1.5 pl-5">
      {items.map((item, i) => <li key={i}>{item}</li>)}
    </ul>
  );
}
