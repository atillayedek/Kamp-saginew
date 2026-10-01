// Files a person uploaded, found by the folder named after their user id. Used when an
// account is finally deleted (retention-run) so nothing is left in storage.

import type { SupabaseClient } from "npm:@supabase/supabase-js@2";

/** One page of names directly under a storage folder; an empty page ends the listing. */
export type ListPage = (folder: string, offset: number, limit: number) => Promise<string[]>;

export const PAGE_SIZE = 100;

/** Buckets where a person uploads into a folder named after their user id. */
export const USER_BUCKETS = ["student-documents", "avatars", "post-media", "group-media", "course-notes", "data-exports"] as const;

/** The body must say {"confirm": "DELETE"}; an accidental call deletes nothing. */
export function isConfirmed(body: Record<string, unknown> | null): boolean {
  return body?.confirm === "DELETE";
}

/** Every object path in the user's own folder of a bucket. */
export async function collectUserFiles(userId: string, listPage: ListPage): Promise<string[]> {
  const paths: string[] = [];
  for (let offset = 0; ; offset += PAGE_SIZE) {
    const names = await listPage(userId, offset, PAGE_SIZE);
    for (const name of names) paths.push(`${userId}/${name}`);
    if (names.length < PAGE_SIZE) return paths;
  }
}

/** Removes every file of the person from every user bucket; returns how many were removed. */
export async function removeUserFiles(admin: SupabaseClient, userId: string): Promise<number> {
  let removed = 0;
  for (const bucket of USER_BUCKETS) {
    const paths = await collectUserFiles(userId, async (folder, offset, limit) => {
      const { data, error } = await admin.storage.from(bucket).list(folder, { offset, limit });
      if (error) throw new Error(`listing ${bucket}: ${error.message}`);
      return (data ?? []).map((item) => item.name);
    });
    if (paths.length > 0) {
      const { error } = await admin.storage.from(bucket).remove(paths);
      if (error) throw new Error(`removing from ${bucket}: ${error.message}`);
      removed += paths.length;
    }
  }
  return removed;
}
