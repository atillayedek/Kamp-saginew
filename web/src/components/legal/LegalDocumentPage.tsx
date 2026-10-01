import Link from "next/link";
import { LegalPage } from "@/components/legal/LegalPage";
import { Markdown } from "@/components/legal/Markdown";
import { formatDateTime } from "@/lib/format";
import { loadLegalText, slugFor } from "@/lib/legal";

/** One legal text as published in the database; ?surum=N shows an older version. */
export async function LegalDocumentPage({ docType, version, fallbackTitle }: {
  docType: string;
  version: number | null;
  fallbackTitle: string;
}) {
  const result = await loadLegalText(docType, version);
  if (result.state !== "ok") {
    return (
      <LegalPage title={fallbackTitle} updated="—">
        <p className="rounded-lg border border-warn/40 bg-warn-soft px-4 py-3 text-sm text-warn">
          {result.state === "not_configured"
            ? "Bu site veritabanı bağlantısı olmadan derlendi; metin gösterilemiyor (kurulum gerekli)."
            : result.state === "not_found"
              ? "Bu metnin istenen sürümü bulunamadı."
              : "Metin şu an yüklenemedi. Biraz sonra tekrar dene."}
        </p>
      </LegalPage>
    );
  }
  const { text, versions } = result;
  const slug = slugFor(docType);
  return (
    <LegalPage title={text.title} updated={`${formatDateTime(text.published_at)} · Sürüm ${text.version}`}>
      {!text.is_active ? (
        <p className="rounded-lg border border-line bg-surface-2 px-4 py-3 text-sm">
          Bu, metnin eski bir sürümüdür. {slug ? <Link className="text-brand hover:underline" href={`/yasal/${slug}`}>Güncel sürüm</Link> : null}
        </p>
      ) : null}
      <Markdown source={text.content} />
      <details className="mt-10 rounded-lg border border-line px-4 py-3 text-sm">
        <summary className="cursor-pointer font-medium text-ink">Sürüm geçmişi ve metin özeti</summary>
        <p className="mt-2 break-all text-xs text-ink-3">Bu sürümün SHA-256 özeti: {text.content_sha256}</p>
        <ul className="mt-2 space-y-1">
          {versions.map((v) => (
            <li key={v.version}>
              {slug ? <Link className="text-brand hover:underline" href={`/yasal/${slug}?surum=${v.version}`}>Sürüm {v.version}</Link> : `Sürüm ${v.version}`}
              {" "}· {formatDateTime(v.published_at)}{v.is_active ? " · yürürlükte" : ""}
            </li>
          ))}
        </ul>
      </details>
    </LegalPage>
  );
}

export function parseVersion(value: string | string[] | undefined): number | null {
  const raw = Array.isArray(value) ? value[0] : value;
  if (!raw || !/^\d{1,6}$/.test(raw)) return null;
  return Number(raw);
}
