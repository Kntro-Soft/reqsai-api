ALTER TABLE public.share_links
    ADD COLUMN session_id UUID,
    ADD COLUMN access VARCHAR(16) NOT NULL DEFAULT 'REVIEW';

ALTER TABLE public.share_links
    ADD CONSTRAINT ck_share_links_access CHECK (access IN ('VIEW', 'REVIEW'));

CREATE INDEX idx_share_links_session ON public.share_links (organization_id, session_id) WHERE session_id IS NOT NULL;
