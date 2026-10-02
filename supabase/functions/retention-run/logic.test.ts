import { assertEquals, assertStringIncludes } from "jsr:@std/assert@1";
import { inactiveNoticeText, type RetentionDue, type RetentionOps, runRetention } from "./logic.ts";

function recorder(overrides: Partial<RetentionOps> = {}) {
  const calls: string[] = [];
  const ops: RetentionOps = {
    removeFiles: (bucket, paths) => {
      calls.push(`remove ${bucket} ${paths.join("+")}`);
      return Promise.resolve();
    },
    recordDocumentPurged: (id) => {
      calls.push(`purged ${id}`);
      return Promise.resolve();
    },
    recordExportDeleted: (id) => {
      calls.push(`export ${id}`);
      return Promise.resolve();
    },
    sendInactiveNotice: (email, days) => {
      calls.push(`mail ${email} ${days}`);
      return Promise.resolve(true);
    },
    recordInactiveNotice: (id) => {
      calls.push(`noticed ${id}`);
      return Promise.resolve();
    },
    deleteAccount: (id, reason) => {
      calls.push(`delete ${id} ${reason}`);
      return Promise.resolve();
    },
    ...overrides,
  };
  return { calls, ops };
}

const DUE: RetentionDue = {
  documents: [{ id: "v1", user_id: "u1", path: "u1/a.pdf" }, { id: "v2", user_id: "u2", path: "u2/b.pdf" }],
  exports: [{ id: "e1", user_id: "u1", paths: ["u1/x.json", "u1/x.html"] }],
  accounts: [{ user_id: "u3", reason: "user_request" }],
  inactive_to_warn: [{ user_id: "u4", email: "u4@example.edu.tr" }],
  inactive_notice_days: 30,
};

Deno.test("destroys first, records after", async () => {
  const { calls, ops } = recorder();
  const summary = await runRetention(DUE, ops);
  assertEquals(calls, [
    "remove student-documents u1/a.pdf",
    "purged v1",
    "remove student-documents u2/b.pdf",
    "purged v2",
    "remove data-exports u1/x.json+u1/x.html",
    "export e1",
    "delete u3 user_request",
    "mail u4@example.edu.tr 30",
    "noticed u4",
  ]);
  assertEquals(summary, {
    documents_destroyed: 2,
    exports_destroyed: 1,
    accounts_deleted: 1,
    inactive_warned: 1,
    inactive_not_warned_email_off: 0,
    errors: [],
  });
});

Deno.test("a failed removal is not recorded as destroyed and does not stop the run", async () => {
  const { calls, ops } = recorder({
    removeFiles: (bucket, paths) => {
      if (paths[0] === "u1/a.pdf") return Promise.reject(new Error("storage down"));
      calls.push(`remove ${bucket} ${paths.join("+")}`);
      return Promise.resolve();
    },
  });
  const summary = await runRetention(DUE, ops);
  assertEquals(calls.includes("purged v1"), false);
  assertEquals(calls.includes("purged v2"), true);
  assertEquals(summary.documents_destroyed, 1);
  assertEquals(summary.errors, ["document v1: storage down"]);
});

Deno.test("without e-mail nobody is marked as warned", async () => {
  const { calls, ops } = recorder({ sendInactiveNotice: () => Promise.resolve(false) });
  const summary = await runRetention({ ...DUE, documents: [], exports: [], accounts: [] }, ops);
  assertEquals(calls, []);
  assertEquals(summary.inactive_not_warned_email_off, 1);
});

Deno.test("notice text names the deadline", () => {
  const text = inactiveNoticeText(30, "https://kampusagi.example/");
  assertStringIncludes(text.text, "30 gün içinde");
  assertStringIncludes(text.text, "https://kampusagi.example/gizlilik");
  assertStringIncludes(text.html, "<p>Merhaba,</p>");
});
