-- New enum values must be committed before they are used (next migration).

-- Report categories required by the community rules (5651 md.9 personality rights,
-- personal data disclosure, copyright, sexual content, hate speech).
alter type public.report_reason add value if not exists 'HATE_SPEECH';
alter type public.report_reason add value if not exists 'PERSONAL_DATA_LEAK';
alter type public.report_reason add value if not exists 'PERSONALITY_RIGHTS';
alter type public.report_reason add value if not exists 'SEXUAL_CONTENT';
alter type public.report_reason add value if not exists 'COPYRIGHT';

-- Moderation decisions and KVKK answers reach the person as notifications.
alter type public.notification_kind add value if not exists 'CONTENT_REMOVED';
alter type public.notification_kind add value if not exists 'ACCOUNT_SUSPENDED';
alter type public.notification_kind add value if not exists 'APPEAL_DECIDED';
alter type public.notification_kind add value if not exists 'DSR_ANSWERED';
