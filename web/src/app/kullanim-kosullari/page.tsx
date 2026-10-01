import type { Metadata } from "next";
import { LegalDocumentPage, parseVersion } from "@/components/legal/LegalDocumentPage";

// Public address used by Google Play and the app; the text itself is the published version in the database.
export const revalidate = 300;

export const metadata: Metadata = { title: "Kullanım Koşulları — KampüsAğı" };

export default async function Page({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  return <LegalDocumentPage docType="kullanim_kosullari" version={parseVersion((await searchParams).surum)} fallbackTitle="Kullanım Koşulları" />;
}
