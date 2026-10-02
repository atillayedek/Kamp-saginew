// Frequently asked questions for /sss. Durations are read from the compliance settings (the same
// values the app and the database use) so the page never states a period that is no longer true;
// when a setting cannot be read, the answer points to the policy instead of guessing a number.
// Links are written as [label](/path) and rendered by faqParts().

export type ComplianceSettings = Record<string, unknown>;

export interface FaqItem {
  id: string;
  question: string;
  answer: string;
}

export interface FaqSection {
  title: string;
  items: FaqItem[];
}

export type FaqPart = { text: string } | { text: string; href: string };

function days(settings: ComplianceSettings, key: string): number | null {
  const value = Number(settings[key]);
  return Number.isInteger(value) && value > 0 ? value : null;
}

/** "30 gün", "2 yıl", or null. */
export function duration(settings: ComplianceSettings, key: string): string | null {
  const d = days(settings, key);
  if (d === null) return null;
  return d % 365 === 0 ? `${d / 365} yıl` : `${d} gün`;
}

function within(settings: ComplianceSettings, key: string, fallback: string): string {
  const d = duration(settings, key);
  return d ? `en geç ${d} içinde` : fallback;
}

export function buildFaq(settings: ComplianceSettings, contactEmail: string): FaqSection[] {
  const minAge = days(settings, "min_age") ?? 18;
  const contact = contactEmail ? `[${contactEmail}](mailto:${contactEmail})` : "uygulamadaki iletişim adresi";
  const grace = duration(settings, "deletion_grace_days");
  const accessLogs = duration(settings, "access_log_retention_days");
  const linkMinutes = days(settings, "data_export_link_minutes");
  const exportKept = duration(settings, "data_export_retention_days");
  const dsrDays = duration(settings, "dsr_response_days");
  const urgentHours = days(settings, "urgent_report_hours");
  const contextCount = days(settings, "report_context_messages");

  return [
    {
      title: "Genel",
      items: [
        {
          id: "nedir",
          question: "KampüsAğı nedir?",
          answer:
            "Öğrenci belgesiyle doğrulanmış üniversite öğrencilerinin buluştuğu bir topluluk uygulaması. Akışta gönderi, " +
            "fotoğraf, anket, etkinlik ve ilan paylaşabilir; birebir mesajlaşabilir, çalışma gruplarına katılabilir, ders " +
            "notu paylaşabilir ve ihtiyacına (ders çalışma, ev ya da yol arkadaşı, proje ortağı…) uygun öğrencilerle eşleşebilirsin.",
        },
        {
          id: "kimler",
          question: "Kimler katılabilir?",
          answer:
            `${minAge} yaşını doldurmuş, Türkiye'deki veya KKTC'deki bir üniversitede okuyan öğrenciler. Her hesap öğrenci ` +
            "belgesiyle incelenir; topluluklara ve sohbetlere yalnızca onaylanan öğrenciler katılır.",
        },
        {
          id: "platform",
          question: "Uygulama hangi cihazlarda var?",
          answer: "Şu an yalnızca Android'de, Google Play üzerinden. iOS sürümü henüz yok.",
        },
        {
          id: "ucret",
          question: "Ücretli mi?",
          answer:
            "Hayır, temel kullanım ücretsiz. İstersen aylık Premium aboneliği alabilirsin; güncel fiyatı satın almadan önce " +
            "Google Play'in ödeme ekranında görürsün.",
        },
      ],
    },
    {
      title: "Hesap ve doğrulama",
      items: [
        {
          id: "neden-belge",
          question: "Neden öğrenci belgesi istiyorsunuz?",
          answer:
            "Toplulukta yalnızca gerçek öğrenciler olsun diye. Belgen yalnızca öğrenciliğini doğrulamak için kullanılır ve " +
            "yalnızca yetkili doğrulama personeli tarafından görülür; her görüntüleme kayıt altına alınır.",
        },
        {
          id: "belge-ne-olur",
          question: "Yüklediğim belge ne oluyor?",
          answer:
            `Onay ya da red kararından sonra ${within(settings, "document_retention_days", "saklama ve imha politikasında belirtilen süre içinde")} ` +
            "kalıcı olarak silinir. Yalnızca doğrulandığın bilgisi, üniversiten, bölümün ve belgenin özeti (SHA-256) saklanır; " +
            "belgedeki kimlik numarası, fotoğraf veya adres hiçbir yere kaydedilmez.",
        },
        {
          id: "belge-reddedildi",
          question: "Belgem reddedildi, ne yapmalıyım?",
          answer: "Uygulama ret gerekçesini gösterir. Gerekçeye uygun, okunaklı ve güncel bir belgeyle yeniden yükleyebilirsin.",
        },
        {
          id: "universite-yok",
          question: "Üniversitem listede yok.",
          answer: `Türkiye'deki ve KKTC'deki üniversiteler listede. Seninki eksikse ${contact} adresine yaz, ekleyelim.`,
        },
      ],
    },
    {
      title: "Topluluk ve eşleşme",
      items: [
        {
          id: "etiketleme",
          question: "Gönderilerde kişileri ve konuları nasıl etiketlerim?",
          answer:
            "Bir konuyu #kampus gibi, bir kişiyi @kullaniciadi biçiminde yaz; \"@\" yazınca uygulama kişi önerir. Etiketlenen " +
            "kişiye bildirim gider. Yalnızca gönderiyi görebilen kişiler etiketlenebilir. Bir #etikete dokununca o konudaki " +
            "gönderiler açılır; aramada bu haftanın popüler etiketleri görünür.",
        },
        {
          id: "eslesme",
          question: "Eşleştirme nasıl çalışıyor?",
          answer:
            "Yapay zekâ kullanılmaz; kurallarla hesaplanır. Aynı üniversitedeki aynı kategorideki ilanlar ortak etiketlere, " +
            "ortak kelimelere, tarih yakınlığına ve yere göre puanlanır. Her eşleşmede \"Bu eşleşme neden önerildi?\" " +
            "açıklaması ve itiraz düğmesi bulunur.",
        },
        {
          id: "rahatsiz",
          question: "Biri beni rahatsız ediyor, ne yapabilirim?",
          answer:
            "Kişiyi profilinden engelleyebilir, gönderiyi, yorumu, mesajı veya profili şikâyet edebilirsin. Engellediğin kişi " +
            "seninle iletişim kuramaz ve paylaşımlarını görmezsin. Kişisel verinin ifşası ve kişilik hakkı ihlali " +
            `şikâyetleri öncelikli incelenir${urgentHours ? ` (hedef: ${urgentHours} saat)` : ""}.`,
        },
        {
          id: "icerik-kaldirildi",
          question: "İçeriğim kaldırıldı, itiraz edebilir miyim?",
          answer:
            "Evet. Kaldırma kararı gerekçesiyle sana bildirilir; uygulamada Ayarlar → Gizlilik ve KVKK → Hakkımdaki " +
            "moderasyon kararları bölümünden itiraz edebilirsin.",
        },
        {
          id: "telif",
          question: "Ders notumu biri izinsiz paylaştı / telif hakkımı ihlal eden bir içerik var.",
          answer:
            "Ders notu yükleyen herkes hak sahibi olduğunu veya paylaşma izni bulunduğunu beyan eder. Hak sahipleri, hesap " +
            "açmadan [telif bildirimi formu](/telif-bildirimi) ile bildirimde bulunabilir.",
        },
      ],
    },
    {
      title: "Gizlilik ve verilerin",
      items: [
        {
          id: "soyad",
          question: "Diğer öğrenciler adımı ve e-postamı görüyor mu?",
          answer:
            "Diğer öğrenciler adını varsayılan olarak \"Ayşe Y.\" biçiminde görür; tam adını göstermeyi Ayarlar → Gizlilik ve " +
            "KVKK bölümünden açabilirsin. E-posta adresin hiçbir zaman gösterilmez.",
        },
        {
          id: "mesajlar",
          question: "Yöneticiler mesajlarımı okuyabilir mi?",
          answer:
            "Hayır. Tek istisna şikâyet edilen bir mesajdır: moderatör yalnızca o mesajı ve öncesinden ve sonrasından " +
            (contextCount ? `en fazla ${contextCount} mesajı` : "sınırlı sayıda mesajı") +
            " görebilir; bu erişim gerekçesiyle kayıt altına alınır.",
        },
        {
          id: "indir",
          question: "Verilerimin bir kopyasını alabilir miyim?",
          answer:
            "Evet. Ayarlar → Gizlilik ve KVKK → Verilerimi indir ile profilin, paylaşımların, mesajların, notların, onay " +
            "geçmişin ve giriş kayıtların okunabilir ve JSON dosyası olarak hazırlanır" +
            (linkMinutes ? `; indirme bağlantısı ${linkMinutes} dakika geçerlidir` : "") +
            (exportKept ? ` ve dosya ${exportKept} sonra silinir` : "") + ".",
        },
        {
          id: "hesap-silme",
          question: "Hesabımı nasıl silerim?",
          answer:
            "Uygulamada Ayarlar → Gizlilik ve KVKK bölümünden ya da giriş yaparak [hesap silme sayfasından](/hesap-silme). " +
            (grace ? `Talepten sonra ${grace} içinde vazgeçebilirsin; süre dolunca` : "Geri alma süresi dolunca") +
            " hesabın ve içeriklerin kalıcı olarak silinir, grup mesajların \"Silinmiş kullanıcı\" olarak kalır. Yasal " +
            "olarak saklanması gereken giriş kayıtları profilinden ayrılır ve yalnızca saklama süresi boyunca tutulur.",
        },
        {
          id: "kayitlar",
          question: "Hangi kayıtlar (log) tutuluyor?",
          answer:
            "Hesabının güvenliği ve 5651 sayılı Kanun gereği giriş-çıkış zamanı, IP adresi ve cihaz bilgisi kaydedilir" +
            (accessLogs ? ` ve ${accessLogs} saklanır` : "") +
            ". Bu kayıtları uygulamada kendin de görebilirsin. Ayrıntılar [aydınlatma metninde](/yasal/aydinlatma-metni).",
        },
        {
          id: "reklam",
          question: "Reklam veya takip var mı?",
          answer:
            "Hayır. Uygulamada reklam, reklam kimliği veya analitik/takip aracı yoktur ve verilerin satılmaz. Ayrıntılar " +
            "[gizlilik politikasında](/gizlilik).",
        },
        {
          id: "kvkk-basvuru",
          question: "KVKK kapsamındaki haklarımı nasıl kullanırım?",
          answer:
            "Uygulamada Ayarlar → Gizlilik ve KVKK bölümündeki başvuru formuyla ya da [başvuru formundaki](/yasal/basvuru-formu) " +
            "yollarla. Başvuruna bir numara verilir" + (dsrDays ? ` ve en geç ${dsrDays} içinde yanıtlanır` : "") + ".",
        },
      ],
    },
    {
      title: "Premium ve bildirimler",
      items: [
        {
          id: "premium",
          question: "Premium ne sağlar?",
          answer:
            "Kendi kanalını açma (en fazla 5), kanal fotoğrafı ve sabitlenen duyurular, kanalda anket, profilde Premium rozeti " +
            "ve daha fazla aktif ihtiyaç ilanı. Kanallara katılmak herkes için ücretsizdir.",
        },
        {
          id: "iptal",
          question: "Aboneliğimi nasıl iptal ederim?",
          answer:
            "Google Play → Ödemeler ve abonelikler → Abonelikler bölümünden ya da uygulamadaki Premium ekranında \"Aboneliği " +
            "yönet\" ile. Ödemeler Google Play üzerinden alınır; iptal ettiğinde Premium, ödenmiş dönemin sonuna kadar sürer.",
        },
        {
          id: "kampanya",
          question: "Kampanya bildirimleri ve e-postaları nasıl kapatırım?",
          answer:
            "Kampanya bildirimleri ve e-postaları yalnızca ayrıca onay verdiysen gönderilir. Onayını Ayarlar → Gizlilik ve " +
            "KVKK bölümünden tek dokunuşla geri çekebilirsin; e-postalarda abonelikten çıkma bağlantısı da bulunur. Mesaj " +
            "ve yorum gibi bildirimler ayrı bir kanaldadır.",
        },
      ],
    },
    {
      title: "İletişim",
      items: [
        {
          id: "iletisim",
          question: "Size nasıl ulaşabilirim?",
          answer: `${contact} adresine yazabilirsin.`,
        },
      ],
    },
  ];
}

const LINK = /\[([^\]]+)\]\(([^)\s]+)\)/g;

/** Splits an answer into text and links. Only site paths and mailto: become links. */
export function faqParts(answer: string): FaqPart[] {
  const parts: FaqPart[] = [];
  let last = 0;
  for (const match of answer.matchAll(LINK)) {
    const [whole, label, href] = match;
    const index = match.index ?? 0;
    if (index > last) parts.push({ text: answer.slice(last, index) });
    parts.push(href.startsWith("/") || href.startsWith("mailto:") ? { text: label, href } : { text: label });
    last = index + whole.length;
  }
  if (last < answer.length) parts.push({ text: answer.slice(last) });
  return parts;
}

/** The answer as plain text (for structured data). */
export function faqPlain(answer: string): string {
  return faqParts(answer).map((p) => p.text).join("");
}

/** schema.org FAQPage for search engines. */
export function faqJsonLd(sections: FaqSection[]) {
  return {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: sections.flatMap((s) =>
      s.items.map((item) => ({
        "@type": "Question",
        name: item.question,
        acceptedAnswer: { "@type": "Answer", text: faqPlain(item.answer) },
      })),
    ),
  };
}
