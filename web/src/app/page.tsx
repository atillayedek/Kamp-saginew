import {
  BadgeCheck,
  BellRing,
  GraduationCap,
  MessagesSquare,
  ShieldCheck,
  Sparkles,
  Users,
} from "lucide-react";
import { config } from "@/lib/config";

const FEATURES = [
  {
    icon: BadgeCheck,
    title: "Belgeyle doğrulanmış öğrenciler",
    text: "Her hesap öğrenci belgesiyle incelenir. Topluluklara ve sohbetlere yalnızca onaylı öğrenciler katılır.",
  },
  {
    icon: Sparkles,
    title: "Yapay zekâ ile ihtiyaç eşleştirme",
    text: "Ne aradığını kendi cümlelerinle yaz; KampüsAğı ihtiyacını analiz eder ve sana en uygun öğrencileri bulur.",
  },
  {
    icon: Users,
    title: "Kampüs toplulukları",
    text: "Üniversitendeki ve Türkiye genelindeki öğrencilerle gönderi paylaş, yorum yap, fikir alışverişi yap.",
  },
  {
    icon: MessagesSquare,
    title: "Gerçek zamanlı sohbet",
    text: "Eşleştiğin öğrencilerle anında mesajlaş; okundu bilgisi ve bildirimlerle hiçbir şeyi kaçırma.",
  },
  {
    icon: ShieldCheck,
    title: "Güvenlik önce gelir",
    text: "Engelleme, şikayet ve moderasyon araçları; verilerin sunucu tarafında korunur, hesabını istediğin an silebilirsin.",
  },
  {
    icon: BellRing,
    title: "Anlık bildirimler",
    text: "Yeni eşleşme, mesaj ve topluluk etkileşimlerinden anında haberdar ol.",
  },
];

const STEPS = [
  { title: "Kaydol", text: "E-posta adresinle hesabını oluştur ve profilini tamamla." },
  { title: "Doğrulan", text: "Öğrenci belgeni yükle; ekibimiz inceleyip hesabını onaylar." },
  { title: "Bağlan", text: "İhtiyacını paylaş, eşleş, topluluklara katıl ve sohbete başla." },
];

export default function Home() {
  const store = config.playStoreUrl;
  return (
    <div className="min-h-dvh">
      <header className="mx-auto flex max-w-6xl items-center justify-between px-4 py-5 sm:px-6">
        <div className="flex items-center gap-2 font-semibold">
          <span className="grid size-9 place-items-center rounded-xl bg-brand text-white">
            <GraduationCap className="size-5" aria-hidden />
          </span>
          KampüsAğı
        </div>
        {store ? (
          <a
            href={store}
            className="rounded-full bg-ink px-4 py-2 text-sm font-medium text-bg transition hover:opacity-85"
          >
            Uygulamayı indir
          </a>
        ) : null}
      </header>

      <main>
        <section className="mx-auto max-w-6xl px-4 pb-20 pt-12 sm:px-6 sm:pt-20">
          <div className="max-w-3xl">
            <p className="mb-5 inline-flex items-center gap-2 rounded-full border border-line bg-surface px-3 py-1 text-sm text-ink-2">
              <BadgeCheck className="size-4 text-brand" aria-hidden />
              Yalnızca doğrulanmış üniversite öğrencileri
            </p>
            <h1 className="text-4xl font-semibold tracking-tight sm:text-6xl">
              Kampüsündeki doğru insanlarla <span className="text-brand">saniyeler içinde</span> bağlan.
            </h1>
            <p className="mt-6 max-w-2xl text-lg leading-relaxed text-ink-2">
              Ders çalışma arkadaşı, proje ortağı, maç için eksik oyuncu, yol ya da ev arkadaşı — neye ihtiyacın varsa
              KampüsAğı seni doğru öğrenciyle buluşturur. Yapay zekâ ihtiyacını anlar, eşleştirir; gerisini sohbet halleder.
            </p>
            <div className="mt-8 flex flex-wrap items-center gap-3">
              {store ? (
                <a
                  href={store}
                  className="rounded-full bg-brand px-6 py-3 font-medium text-white transition hover:bg-brand-strong"
                >
                  Google Play&apos;den indir
                </a>
              ) : (
                <span className="rounded-full border border-line bg-surface px-6 py-3 font-medium text-ink-2">
                  Çok yakında Google Play&apos;de
                </span>
              )}
              <a href="#nasil-calisir" className="rounded-full px-6 py-3 font-medium text-ink-2 hover:text-ink">
                Nasıl çalışır?
              </a>
            </div>
          </div>
        </section>

        <section className="border-y border-line bg-surface">
          <div className="mx-auto grid max-w-6xl gap-px bg-line sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map(({ icon: Icon, title, text }) => (
              <div key={title} className="bg-surface p-6 sm:p-8">
                <span className="grid size-10 place-items-center rounded-lg bg-brand-soft text-brand">
                  <Icon className="size-5" aria-hidden />
                </span>
                <h2 className="mt-4 font-semibold">{title}</h2>
                <p className="mt-2 text-sm leading-relaxed text-ink-2">{text}</p>
              </div>
            ))}
          </div>
        </section>

        <section id="nasil-calisir" className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
          <h2 className="text-3xl font-semibold tracking-tight">Üç adımda başla</h2>
          <ol className="mt-10 grid gap-6 sm:grid-cols-3">
            {STEPS.map((step, i) => (
              <li key={step.title} className="rounded-2xl border border-line bg-surface p-6">
                <span className="text-sm font-medium text-brand">Adım {i + 1}</span>
                <h3 className="mt-2 text-lg font-semibold">{step.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-ink-2">{step.text}</p>
              </li>
            ))}
          </ol>
        </section>
      </main>

      <footer className="border-t border-line">
        <div className="mx-auto flex max-w-6xl flex-col gap-3 px-4 py-8 text-sm text-ink-3 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <span>© {new Date().getFullYear()} KampüsAğı</span>
          {config.privacyPolicyUrl ? (
            <a href={config.privacyPolicyUrl} className="hover:text-ink">
              Gizlilik politikası
            </a>
          ) : null}
        </div>
      </footer>
    </div>
  );
}
