-- Code-aware copilot: the client's source repositories connected to a project, and the map of modules
-- ReqsAI builds from them. Only summaries and symbol names are stored, never raw source code.
create table code_repositories
(
    id                      uuid          primary key,
    project_id              uuid          not null,
    provider                varchar(16)   not null, -- CodeHostProvider enum: GITHUB
    owner                   varchar(100)  not null,
    name                    varchar(100)  not null,
    branch                  varchar(255)  not null,
    html_url                varchar(500)  not null,
    private_repo            boolean       not null default false,
    access_token_ciphertext bytea,                  -- AES-256-GCM, only for private repositories
    status                  varchar(16)   not null, -- CodeRepositoryStatus enum: PENDING | INDEXING | READY | FAILED
    error                   varchar(500),
    commit_sha              varchar(64),
    indexed_at              timestamptz,
    progress_at             timestamptz,
    file_count              integer       not null default 0,
    module_count            integer       not null default 0,
    modules_done            integer       not null default 0,
    summarized              boolean       not null default false,
    profile                 jsonb         not null default '{}'::jsonb,

    created_at              timestamptz   not null,
    updated_at              timestamptz   not null,
    created_by              uuid,
    updated_by              uuid
);

create unique index uq_code_repositories_project_repo
    on code_repositories (project_id, lower(owner), lower(name));

create table code_modules
(
    id             uuid          primary key,
    repository_id  uuid          not null references code_repositories (id) on delete cascade,
    project_id     uuid          not null,
    path           varchar(500)  not null,
    name           varchar(200)  not null,
    summary        text          not null,
    capabilities   jsonb         not null default '[]'::jsonb,
    business_rules jsonb         not null default '[]'::jsonb,
    endpoints      jsonb         not null default '[]'::jsonb,
    entities       jsonb         not null default '[]'::jsonb,
    file_count     integer       not null,
    content_hash   varchar(64)   not null,
    summarized     boolean       not null default false,
    embedding      vector(768),

    created_at     timestamptz   not null,
    updated_at     timestamptz   not null,
    created_by     uuid,
    updated_by     uuid
);

create unique index uq_code_modules_repository_path on code_modules (repository_id, path);
create index idx_code_modules_project on code_modules (project_id);
create index idx_code_modules_embedding on code_modules using hnsw (embedding vector_cosine_ops)
    where embedding is not null;
