import type { Metadata } from "next";
import { Suspense } from "react";
import { Unsubscribe } from "./Unsubscribe";

export const metadata: Metadata = {
  title: "E-posta aboneliği — KampüsAğı",
  robots: { index: false, follow: false },
};

export default function UnsubscribePage() {
  return (
    <div className="grid min-h-dvh place-items-center px-4">
      <Suspense>
        <Unsubscribe />
      </Suspense>
    </div>
  );
}
