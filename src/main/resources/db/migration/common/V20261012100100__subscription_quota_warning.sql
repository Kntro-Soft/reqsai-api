ALTER TABLE public.subscriptions
    ADD COLUMN quota_warning_percent SMALLINT NOT NULL DEFAULT 0;
