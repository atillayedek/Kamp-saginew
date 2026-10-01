// Legal texts come from the database (legal_documents), never from the code: every
// published version is kept so consents can be proved against the exact text.

import { backendState, config } from "./config";

export interface LegalText {
  doc_type: string;
  kind: string;
  version: number;
  title: string;
  content: string;
  content_sha256: string;
  published_at: string;
  is_active: boolean;
}

export interface LegalVersion {
  version: number;
  title: string;
  content_sha256: string;
  published_at: string;
  is_active: boolean;
}

/** URL slug -> doc_type. The slugs are public addresses (Play Console, app links). */
export const LEGAL_SLUGS: Record<string, string> = {
  "aydinlatma-metni": "aydinlatma_metni",
  "gizlilik-politikasi": "gizlilik_politikasi",
  "kullanim-kosullari": "kullanim_kosullari",
  "topluluk-kurallari": "topluluk_kurallari",
  "acik-riza-eposta": "acik_riza_pazarlama_eposta",
  "acik-riza-bildirim": "acik_riza_pazarlama_bildirim",
  "cerez-sdk-politikasi": "cerez_sdk_politikasi",
  "telif-politikasi": "telif_politikasi",
  "premium-on-bilgilendirme": "premium_on_bilgilendirme",
  "mesafeli-sozlesme": "mesafeli_sozlesme",
  "saklama-imha-politikasi": "saklama_imha_politikasi",
  "basvuru-formu": "basvuru_formu",
  "cocuk-guvenligi": "cocuk_guvenligi",
};

export function slugFor(docType: string): string | null {
  return Object.entries(LEGAL_SLUGS).find(([, type]) => type === docType)?.[0] ?? null;
}

async function call<T>(fn: string, args: Record<string, unknown>): Promise<T> {
  const response = await fetch(`${config.supabaseUrl}/rest/v1/rpc/${fn}`, {
    method: "POST",
    headers: {
      apikey: config.supabaseAnonKey,
      Authorization: `Bearer ${config.supabaseAnonKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(args),
    next: { revalidate: 300 },
  });
  if (!response.ok) throw new Error(`${fn} failed with HTTP ${response.status}`);
  return await response.json() as T;
}

export type LegalResult =
  | { state: "ok"; text: LegalText; versions: LegalVersion[] }
  | { state: "not_configured" }
  | { state: "not_found" }
  | { state: "error" };

export async function loadLegalText(docType: string, version: number | null): Promise<LegalResult> {
  if (backendState() !== "ready") return { state: "not_configured" };
  try {
    const [texts, versions] = await Promise.all([
      call<LegalText[]>("get_legal_document", { p_doc_type: docType, p_version: version }),
      call<LegalVersion[]>("list_legal_document_versions", { p_doc_type: docType }),
    ]);
    const text = texts[0];
    return text ? { state: "ok", text, versions } : { state: "not_found" };
  } catch (error) {
    console.error(error);
    return { state: "error" };
  }
}

export async function loadLegalIndex(): Promise<{ doc_type: string; title: string; version: number; published_at: string }[] | null> {
  if (backendState() !== "ready") return null;
  try {
    return await call("list_legal_documents", {});
  } catch (error) {
    console.error(error);
    return null;
  }
}

/** Public compliance settings (durations shown on the site), or {} when they cannot be read. */
export async function loadPublicSettings(): Promise<Record<string, unknown>> {
  if (backendState() !== "ready") return {};
  try {
    return await call<Record<string, unknown>>("public_compliance_config", {});
  } catch (error) {
    console.error(error);
    return {};
  }
}
