-- Client documents (US22): an analyst uploads a PDF or Word file to a project. Its extracted text
-- lives in project_document_contents (loaded only on demand); the file metadata and the reviewed
-- context summary the AI reads live on the project_documents row.
CREATE TABLE project_document_contents (
    id         UUID        NOT NULL PRIMARY KEY,
    body       TEXT        NOT NULL,
    truncated  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID
);

ALTER TABLE project_documents
    ADD COLUMN file_name       VARCHAR(255),
    ADD COLUMN media_type      VARCHAR(100),
    ADD COLUMN size_bytes      BIGINT,
    ADD COLUMN extracted_chars INTEGER,
    ADD COLUMN summary         TEXT,
    ADD COLUMN content_id      UUID REFERENCES project_document_contents(id);

CREATE UNIQUE INDEX uq_project_documents_content_id ON project_documents (content_id);
CREATE INDEX idx_project_documents_project_status ON project_documents (project_id, status);
