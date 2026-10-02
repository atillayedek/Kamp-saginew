import type { Metadata } from "next";
import Link from "next/link";
import { LegalPage } from "@/components/legal/LegalPage";
import { formatDateTime } from "@/lib/format";
import { loadLegalIndex, slugFor } from "@/lib/legal";

export const revalidate = 300;

export const metadata: Metadata = {
  title: "Hukuki metinler — KampüsAğı",
  description: "KampüsAğı aydınlatma metni, gizlilik politikası, kullanım koşulları, topluluk kuralları ve diğer hukuki metinler.",
};

export default async function LegalIndexPage() {
  const texts = await loadLegalIndex();
  return (
    <LegalPage title="Hukuki metinler" updated="Her metnin kendi sürüm tarihi vardır">
      {texts === null ? (
        <p className="rounded-lg border border-warn/40 bg-warn-soft px-4 py-3 text-sm text-warn">Metinler şu an yüklenemedi.</p>
      ) : (
        <ul className="space-y-2">
          {texts.map((t) => {
            const slug = slugFor(t.doc_type);
            return slug ? (
              <li key={t.doc_type}>
                <Link className="font-medium text-brand hover:underline" href={`/yasal/${slug}`}>{t.title}</Link>
                <span className="text-sm text-ink-3"> · Sürüm {t.version} · {formatDateTime(t.published_at)}</span>
              </li>
            ) : null;
          })}
        </ul>
      )}
      <p className="pt-4 text-sm">
        Telif hakkı ihlali bildirimi için <Link className="text-brand hover:underline" href="/telif-bildirimi">telif bildirim formu</Link>.
      </p>
    </LegalPage>
  );
}
