import type { Metadata } from "next";
import { ChevronDown } from "lucide-react";
import { Contact, LegalPage } from "@/components/legal/LegalPage";
import { config } from "@/lib/config";
import { buildFaq, faqJsonLd, faqParts } from "@/lib/faq";
import { loadPublicSettings } from "@/lib/legal";

export const revalidate = 300;

export const metadata: Metadata = {
  title: "Sık sorulan sorular — KampüsAğı",
  description:
    "KampüsAğı hakkında merak edilenler: kimler katılabilir, öğrenci belgesi, etiketleme, eşleştirme, gizlilik, hesap silme ve Premium.",
};

export default async function FaqPage() {
  const sections = buildFaq(await loadPublicSettings(), config.contactEmail);
  return (
    <LegalPage title="Sık sorulan sorular" updated="3 Ekim 2026">
      <script
        type="application/ld+json"
        // Built from our own static text; no user input.
        dangerouslySetInnerHTML={{ __html: JSON.stringify(faqJsonLd(sections)).replace(/</g, "\\u003c") }}
      />
      <nav aria-label="Bölümler" className="flex flex-wrap gap-2">
        {sections.map((section, i) => (
          <a
            key={section.title}
            href={`#bolum-${i + 1}`}
            className="rounded-full border border-line bg-surface px-3 py-1 text-sm text-ink-2 hover:text-ink"
          >
            {section.title}
          </a>
        ))}
      </nav>
      {sections.map((section, i) => (
        <section key={section.title} id={`bolum-${i + 1}`} className="scroll-mt-6 pt-4">
          <h2 className="text-lg font-semibold text-ink">{section.title}</h2>
          <div className="mt-3 divide-y divide-line overflow-hidden rounded-xl border border-line bg-surface">
            {section.items.map((item) => (
              <details key={item.id} id={item.id} className="group">
                <summary className="flex cursor-pointer list-none items-center justify-between gap-4 px-4 py-3 font-medium text-ink [&::-webkit-details-marker]:hidden">
                  {item.question}
                  <ChevronDown className="size-4 shrink-0 text-ink-3 transition group-open:rotate-180" aria-hidden />
                </summary>
                <p className="px-4 pb-4 text-ink-2">
                  {faqParts(item.answer).map((part, j) =>
                    "href" in part ? (
                      <a key={j} href={part.href} className="text-brand underline-offset-2 hover:underline">
                        {part.text}
                      </a>
                    ) : (
                      <span key={j}>{part.text}</span>
                    ),
                  )}
                </p>
              </details>
            ))}
          </div>
        </section>
      ))}
      <div className="pt-6">
        <Contact />
      </div>
    </LegalPage>
  );
}
