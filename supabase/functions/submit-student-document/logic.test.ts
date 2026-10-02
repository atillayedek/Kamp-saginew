import { assertEquals } from "jsr:@std/assert@1";
import { checkDocument, isOwnDocumentPath, mapSubmitError, MAX_DOCUMENT_BYTES } from "./logic.ts";

const USER = "0b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c10";
const FILE = "5f0c7a3e-6d2b-4c1f-8a9e-7b3d2c1e0f4a";

Deno.test("accepts only a generated name in the caller's folder", () => {
  assertEquals(isOwnDocumentPath(`${USER}/${FILE}.pdf`, USER), true);
  assertEquals(isOwnDocumentPath(`${FILE}/${FILE}.pdf`, USER), false);
  assertEquals(isOwnDocumentPath(`${USER}/belge.pdf`, USER), false);
  assertEquals(isOwnDocumentPath(`${USER}/${FILE}.exe`, USER), false);
  assertEquals(isOwnDocumentPath(`${USER}/../x/${FILE}.pdf`, USER), false);
  assertEquals(isOwnDocumentPath(undefined, USER), false);
  assertEquals(isOwnDocumentPath(42, USER), false);
});

const encode = (text: string) => new TextEncoder().encode(text);

Deno.test("recognises PDF bytes regardless of declared type", () => {
  assertEquals(checkDocument(encode("%PDF-1.7\n...")), "ok");
  assertEquals(checkDocument(encode("\n  %PDF-1.4")), "ok");
  assertEquals(checkDocument(new Uint8Array([0xef, 0xbb, 0xbf, ...encode("%PDF-1.5")])), "ok");
  assertEquals(checkDocument(encode("<html>%PDF-1.4</html>")), "not_pdf");
  assertEquals(checkDocument(encode("\x89PNG\r\n")), "not_pdf");
  assertEquals(checkDocument(new Uint8Array()), "empty");
});

Deno.test("enforces the size limit", () => {
  const big = new Uint8Array(MAX_DOCUMENT_BYTES + 1);
  big.set(encode("%PDF-1.7"));
  assertEquals(checkDocument(big), "too_large");
  const limit = new Uint8Array(MAX_DOCUMENT_BYTES);
  limit.set(encode("%PDF-1.7"));
  assertEquals(checkDocument(limit), "ok");
});

Deno.test("maps database errors to stable codes", () => {
  assertEquals(mapSubmitError("verification_already_pending"), { code: "verification_not_allowed", status: 409 });
  assertEquals(mapSubmitError("document_not_found"), { code: "document_not_found", status: 404 });
  assertEquals(mapSubmitError("something else"), { code: "server_error", status: 500 });
  assertEquals(mapSubmitError(undefined), { code: "server_error", status: 500 });
});
