-- Spring Modulith event publication registry
-- Lives in the global `public` schema: event publications are cross-module and tenant-agnostic.
-- Column set matches org.springframework.modulith.events.jpa.JpaEventPublication.
CREATE TABLE IF NOT EXISTS event_publication
(
    id                     UUID                     NOT NULL PRIMARY KEY,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    completion_attempts    INTEGER                  NOT NULL DEFAULT 0,
    status                 VARCHAR(16)              NOT NULL DEFAULT 'PROCESSING'
);

CREATE INDEX IF NOT EXISTS idx_event_publication_completion_date
    ON event_publication (completion_date);

CREATE INDEX IF NOT EXISTS idx_event_publication_by_listener_serialized
    ON event_publication (listener_id, serialized_event);
