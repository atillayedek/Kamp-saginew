import { assert, assertEquals, assertStringIncludes } from "jsr:@std/assert@1";
import { escapeHtml, renderExportHtml } from "./logic.ts";

Deno.test("escapes user content", () => {
  assertEquals(escapeHtml(`<script>alert("x")</script>&'`), "&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;&amp;&#39;");
});

Deno.test("renders every section readably and never executes content", () => {
  const html = renderExportHtml({
    generated_at: "2026-10-02T10:00:00Z",
    profile: { full_name: "Ayşe <b>Y</b>", bio: null },
    posts: [{ body: "<img src=x onerror=alert(1)>", created_at: "2026-10-01" }],
    comments: [],
    activity_days: ["2026-10-01", "2026-10-02"],
  });
  assertStringIncludes(html, "<h2>Profil</h2>");
  assertStringIncludes(html, "Ayşe &lt;b&gt;Y&lt;/b&gt;");
  assertStringIncludes(html, "&lt;img src=x onerror=alert(1)&gt;");
  assertStringIncludes(html, "Kayıt yok");
  assertStringIncludes(html, "2026-10-01, 2026-10-02");
  assert(!html.includes("<script"));
  assert(!html.includes("<img"));
});
