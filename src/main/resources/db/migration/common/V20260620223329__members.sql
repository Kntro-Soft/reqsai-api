CREATE TABLE public.members (
    id              UUID         NOT NULL PRIMARY KEY,
    organization_id UUID         NOT NULL,
    user_id         UUID,
    email           VARCHAR(320) NOT NULL,
    display_name    VARCHAR(150) NOT NULL,
    role            VARCHAR(16)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    invited_by      UUID,
    invited_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID
);

CREATE INDEX idx_members_organization_id ON public.members (organization_id);
CREATE UNIQUE INDEX uq_members_org_email_ci ON public.members (organization_id, lower(email));
CREATE UNIQUE INDEX uq_members_org_user_id ON public.members (organization_id, user_id);
CREATE INDEX idx_members_user_id_status ON public.members (user_id, status);
