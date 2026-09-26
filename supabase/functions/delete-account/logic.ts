// Pure parts of account deletion, kept apart from Deno.serve so they can be tested.

/** One page of names directly under a storage folder; an empty page ends the listing. */
export type ListPage = (folder: string, offset: number, limit: number) => Promise<string[]>;

export const PAGE_SIZE = 100;

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
