-- New things students can report: channel/group messages, groups themselves and
-- course notes. Kept in its own migration because a new enum value cannot be
-- used in the transaction that adds it.

alter type public.report_target add value if not exists 'GROUP_MESSAGE';
alter type public.report_target add value if not exists 'GROUP';
alter type public.report_target add value if not exists 'NOTE';
