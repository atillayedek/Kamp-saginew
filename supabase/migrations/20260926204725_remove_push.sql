-- Push delivery through FCM is not used (D30). Notifications stay in the
-- database and reach the app through Supabase Realtime while it runs; no
-- device tokens are collected.

drop function public.register_device_token(text);
drop function public.unregister_device_token(text);
drop function public.push_payload(uuid);
drop function public.delete_device_tokens(text[]);
drop table public.device_tokens;
