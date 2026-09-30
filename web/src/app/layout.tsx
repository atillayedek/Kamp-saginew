import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

const inter = Inter({ subsets: ["latin", "latin-ext"], variable: "--font-inter" });

export const metadata: Metadata = {
  title: "KampüsAğı — Üniversite öğrencileri için doğrulanmış kampüs ağı",
  description:
    "KampüsAğı, belgesiyle doğrulanmış üniversite öğrencilerini topluluklar, ihtiyaç eşleştirme ve güvenli sohbetle bir araya getirir.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="tr" className={inter.variable}>
      <body className="min-h-dvh">{children}</body>
    </html>
  );
}
