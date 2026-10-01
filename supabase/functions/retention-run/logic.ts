// The daily destruction run (KVKK periodic destruction). Pure orchestration with the
// side effects injected, so the order and the bookkeeping are tested without a network.

export interface RetentionDue {
  documents: { id: string; user_id: string; path: string }[];
  exports: { id: string; user_id: string; paths: string[] }[];
  accounts: { user_id: string; reason: "user_request" | "inactive_account" }[];
  inactive_to_warn: { user_id: string; email: string }[];
  inactive_notice_days: number;
}

export interface RetentionOps {
  removeFiles(bucket: string, paths: string[]): Promise<void>;
  recordDocumentPurged(id: string): Promise<void>;
  recordExportDeleted(id: string): Promise<void>;
  /** False when e-mail is not configured; throws when sending failed. */
  sendInactiveNotice(email: string, days: number): Promise<boolean>;
  recordInactiveNotice(userId: string): Promise<void>;
  deleteAccount(userId: string, reason: string): Promise<void>;
}

export interface RetentionSummary {
  documents_destroyed: number;
  exports_destroyed: number;
  accounts_deleted: number;
  inactive_warned: number;
  inactive_not_warned_email_off: number;
  errors: string[];
}

/** Every item is independent: one failure is recorded and the rest still run. A thing is
 * recorded as destroyed only after the destruction itself succeeded. */
export async function runRetention(due: RetentionDue, ops: RetentionOps): Promise<RetentionSummary> {
  const summary: RetentionSummary = {
    documents_destroyed: 0,
    exports_destroyed: 0,
    accounts_deleted: 0,
    inactive_warned: 0,
    inactive_not_warned_email_off: 0,
    errors: [],
  };
  const fail = (what: string, error: unknown) =>
    summary.errors.push(`${what}: ${error instanceof Error ? error.message : String(error)}`);

  for (const doc of due.documents ?? []) {
    try {
      await ops.removeFiles("student-documents", [doc.path]);
      await ops.recordDocumentPurged(doc.id);
      summary.documents_destroyed++;
    } catch (error) {
      fail(`document ${doc.id}`, error);
    }
  }

  for (const exp of due.exports ?? []) {
    try {
      await ops.removeFiles("data-exports", exp.paths);
      await ops.recordExportDeleted(exp.id);
      summary.exports_destroyed++;
    } catch (error) {
      fail(`export ${exp.id}`, error);
    }
  }

  for (const account of due.accounts ?? []) {
    try {
      await ops.deleteAccount(account.user_id, account.reason);
      summary.accounts_deleted++;
    } catch (error) {
      fail(`account ${account.user_id}`, error);
    }
  }

  for (const person of due.inactive_to_warn ?? []) {
    try {
      if (await ops.sendInactiveNotice(person.email, due.inactive_notice_days)) {
        await ops.recordInactiveNotice(person.user_id);
        summary.inactive_warned++;
      } else {
        summary.inactive_not_warned_email_off++;
      }
    } catch (error) {
      fail(`inactive notice ${person.user_id}`, error);
    }
  }
  return summary;
}

export function inactiveNoticeText(days: number, siteUrl: string | null): { subject: string; text: string; html: string } {
  const subject = "KampüsAğı hesabın silinecek";
  const lines = [
    "Merhaba,",
    "",
    "KampüsAğı hesabına uzun süredir giriş yapılmadı. Kişisel Veri Saklama ve İmha Politikamız gereği, " +
    `${days} gün içinde uygulamaya giriş yapmazsan hesabın ve içeriklerin kalıcı olarak silinecek.`,
    "",
    "Hesabını korumak için uygulamayı açıp giriş yapman yeterli. Silinmesini istiyorsan bir şey yapmana gerek yok.",
    ...(siteUrl ? ["", `Ayrıntılar: ${siteUrl.replace(/\/+$/, "")}/gizlilik`] : []),
    "",
    "KampüsAğı",
  ];
  const text = lines.join("\n");
  const escape = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  const html = lines.map((l) => (l === "" ? "<br>" : `<p>${escape(l)}</p>`)).join("");
  return { subject, text, html };
}
