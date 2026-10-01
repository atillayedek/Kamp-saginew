import { describe, expect, it } from "vitest";
import { buildFaq, duration, faqJsonLd, faqParts, faqPlain } from "./faq";

const settings = {
  min_age: 18,
  document_retention_days: 30,
  deletion_grace_days: 30,
  access_log_retention_days: 730,
  data_export_link_minutes: 60,
  data_export_retention_days: 7,
  dsr_response_days: 30,
  urgent_report_hours: 24,
  report_context_messages: 5,
};

function answer(sections: ReturnType<typeof buildFaq>, id: string): string {
  const item = sections.flatMap((s) => s.items).find((i) => i.id === id);
  if (!item) throw new Error(`no item ${id}`);
  return item.answer;
}

describe("faq", () => {
  it("states durations from the settings", () => {
    const faq = buildFaq(settings, "iletisim@example.com");
    expect(answer(faq, "belge-ne-olur")).toContain("en geç 30 gün içinde");
    expect(answer(faq, "hesap-silme")).toContain("30 gün içinde vazgeçebilirsin");
    expect(answer(faq, "kayitlar")).toContain("2 yıl saklanır");
    expect(answer(faq, "indir")).toContain("60 dakika");
    expect(answer(faq, "mesajlar")).toContain("en fazla 5 mesajı");
  });

  it("never guesses a number when the settings cannot be read", () => {
    const faq = buildFaq({}, "");
    const all = faq.flatMap((s) => s.items).map((i) => i.answer).join(" ");
    expect(all).not.toMatch(/\d+ (gün|yıl|dakika|saat)/);
    expect(answer(faq, "belge-ne-olur")).toContain("saklama ve imha politikasında belirtilen süre içinde");
    expect(answer(faq, "kimler")).toContain("18 yaşını");
    expect(answer(faq, "iletisim")).toContain("uygulamadaki iletişim adresi");
  });

  it("formats durations", () => {
    expect(duration({ a: 730 }, "a")).toBe("2 yıl");
    expect(duration({ a: 45 }, "a")).toBe("45 gün");
    expect(duration({ a: "x" }, "a")).toBeNull();
    expect(duration({ a: 0 }, "a")).toBeNull();
  });

  it("turns only site paths and mailto into links", () => {
    expect(faqParts("Bkz. [form](/telif-bildirimi) ve [x](https://evil.example) ya da [e](mailto:a@b.c).")).toEqual([
      { text: "Bkz. " },
      { text: "form", href: "/telif-bildirimi" },
      { text: " ve " },
      { text: "x" },
      { text: " ya da " },
      { text: "e", href: "mailto:a@b.c" },
      { text: "." },
    ]);
    expect(faqPlain("Bkz. [form](/telif-bildirimi).")).toBe("Bkz. form.");
  });

  it("builds FAQPage structured data with every question", () => {
    const faq = buildFaq(settings, "iletisim@example.com");
    const ld = faqJsonLd(faq);
    expect(ld["@type"]).toBe("FAQPage");
    expect(ld.mainEntity).toHaveLength(faq.flatMap((s) => s.items).length);
    expect(JSON.stringify(ld)).not.toContain("](");
  });
});
