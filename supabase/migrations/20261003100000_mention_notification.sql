-- New notification kind for @mentions. Enum values must be committed before they are used,
-- so this runs in its own migration.

alter type public.notification_kind add value if not exists 'MENTIONED';
