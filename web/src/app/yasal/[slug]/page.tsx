import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { LegalDocumentPage, parseVersion } from "@/components/legal/LegalDocumentPage";
import { LEGAL_SLUGS } from "@/lib/legal";

export const revalidate = 300;

type Params = { params: Promise<{ slug: string }>; searchParams: Promise<Record<string, string | string[] | undefined>> };

export async function generateMetadata({ params }: Params): Promise<Metadata> {
  const { slug } = await params;
  return { title: `${slug.replace(/-/g, " ")} — KampüsAğı` };
}

export default async function LegalTextPage({ params, searchParams }: Params) {
  const { slug } = await params;
  const docType = LEGAL_SLUGS[slug];
  if (!docType) notFound();
  return <LegalDocumentPage docType={docType} version={parseVersion((await searchParams).surum)} fallbackTitle="Hukuki metin" />;
}
