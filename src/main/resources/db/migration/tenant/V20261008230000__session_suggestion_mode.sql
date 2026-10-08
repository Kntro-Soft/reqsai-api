-- US46: the analyst decides whether the assistant analyzes the conversation automatically (default)
-- or only when asked ("Analizar ahora").
alter table discovery_sessions add column auto_suggest boolean not null default true;
