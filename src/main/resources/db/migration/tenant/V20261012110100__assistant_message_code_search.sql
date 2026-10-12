-- Assistant chat: a reply that came from a deep search of the pure source code keeps the places it cites
-- (file, lines, link to GitHub), like the code references of a suggestion.
alter table assistant_messages
    add column code_refs     jsonb   not null default '[]'::jsonb,
    add column code_searched boolean not null default false;
