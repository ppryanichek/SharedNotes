CREATE TABLE notes
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title      TEXT,
    body       TEXT,
    owner_id   BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_notes_user
        FOREIGN KEY (owner_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_notes_owner_id_updated_at ON notes (owner_id, updated_at);