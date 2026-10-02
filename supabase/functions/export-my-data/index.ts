// POST /functions/v1/export-my-data
//
// KVKK md.11 access right / data portability: builds everything the platform holds about
// the caller (export_user_data), stores a JSON file and a readable HTML page in the
// private data-exports bucket and returns short-lived download links (compliance
// setting data_export_link_minutes). Files are destroyed by retention-run after
// data_export_retention_days. At most three exports per 24 hours.

import { authenticate } from "../_shared/auth.ts";
import { errorResponse, json, preflight, withCors } from "../_shared/http.ts";
import { renderExportHtml } from "./logic.ts";

const BUCKET = "data-exports";

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return preflight();
  return withCors(await handle(request));
});

async function handle(request: Request): Promise<Response> {
  if (request.method !== "POST") return errorResponse("method_not_allowed", 405);

  const caller = await authenticate(request);
  if (!caller) return errorResponse("not_authenticated", 401);
  const { admin, userId } = caller;

  const { data: allowed, error: limitError } = await admin.rpc("can_export_data", { p_user_id: userId });
  if (limitError) {
    console.error("can_export_data failed", limitError.message);
    return errorResponse("server_error", 500);
  }
  if (!allowed) return errorResponse("rate_limited", 429);

  const { data: config, error: configError } = await admin.rpc("public_compliance_config");
  const minutes = Number((config as Record<string, unknown> | null)?.data_export_link_minutes);
  if (configError || !Number.isFinite(minutes) || minutes <= 0) {
    console.error("public_compliance_config failed", configError?.message ?? "missing data_export_link_minutes");
    return errorResponse("server_error", 500);
  }

  const { data, error } = await admin.rpc("export_user_data", { p_user_id: userId });
  if (error || !data) {
    console.error("export_user_data failed", error?.message);
    return errorResponse("server_error", 500);
  }

  const id = crypto.randomUUID();
  const jsonPath = `${userId}/${id}.json`;
  const htmlPath = `${userId}/${id}.html`;
  const encoder = new TextEncoder();
  const uploads = await Promise.all([
    admin.storage.from(BUCKET).upload(jsonPath, encoder.encode(JSON.stringify(data, null, 2)), { contentType: "application/json" }),
    admin.storage.from(BUCKET).upload(htmlPath, encoder.encode(renderExportHtml(data as Record<string, unknown>)), { contentType: "text/html" }),
  ]);
  const uploadError = uploads.find((u) => u.error)?.error;
  if (uploadError) {
    console.error("Storing the export failed", uploadError.message);
    await admin.storage.from(BUCKET).remove([jsonPath, htmlPath]);
    return errorResponse("server_error", 500);
  }

  const { error: recordError } = await admin.rpc("record_data_export", {
    p_user_id: userId,
    p_json_path: jsonPath,
    p_html_path: htmlPath,
  });
  if (recordError) {
    await admin.storage.from(BUCKET).remove([jsonPath, htmlPath]);
    if (recordError.message.includes("rate_limited")) return errorResponse("rate_limited", 429);
    console.error("record_data_export failed", recordError.message);
    return errorResponse("server_error", 500);
  }

  const seconds = Math.round(minutes * 60);
  const day = new Date().toISOString().slice(0, 10);
  const [jsonLink, htmlLink] = await Promise.all([
    admin.storage.from(BUCKET).createSignedUrl(jsonPath, seconds, { download: `kampusagi-verilerim-${day}.json` }),
    admin.storage.from(BUCKET).createSignedUrl(htmlPath, seconds, { download: `kampusagi-verilerim-${day}.html` }),
  ]);
  if (jsonLink.error || htmlLink.error || !jsonLink.data || !htmlLink.data) {
    console.error("Signing the export links failed", jsonLink.error?.message ?? htmlLink.error?.message);
    return errorResponse("server_error", 500);
  }
  return json({
    json_url: jsonLink.data.signedUrl,
    html_url: htmlLink.data.signedUrl,
    expires_at: new Date(Date.now() + seconds * 1000).toISOString(),
  });
}
