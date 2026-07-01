-- Organization invitations — lives in the PUBLIC schema (like members).
-- Tokenized acceptance lifecycle for a PENDING member. Stores only the SHA-256 hash of the raw
-- token; the raw value is never persisted (carried only in MemberInvitedEvent for the email).
-- Statuses: PENDING / ACCEPTED / EXPIRED / REVOKED / SUPERSEDED.

CREATE TABLE public.invitations (
    id              UUID         NOT NULL PRIMARY KEY,
    organization_id UUID         NOT NULL,
    member_id       UUID         NOT NULL,
    email           VARCHAR(320) NOT NULL,
    role            VARCHAR(16)  NOT NULL,
    token_hash      VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    invited_by      UUID         NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    accepted_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT uq_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_invitations_member FOREIGN KEY (member_id) REFERENCES public.members (id)
);

CREATE INDEX idx_invitations_token_hash ON public.invitations (token_hash);
CREATE INDEX idx_invitations_organization_id ON public.invitations (organization_id);
CREATE INDEX idx_invitations_email_status ON public.invitations (lower(email), status);

-- At most one active (PENDING) invitation per member at a time.
CREATE UNIQUE INDEX uq_invitations_member_pending
    ON public.invitations (member_id)
    WHERE status = 'PENDING';
