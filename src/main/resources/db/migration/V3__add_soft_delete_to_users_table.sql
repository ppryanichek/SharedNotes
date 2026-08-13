ALTER TABLE users
    ADD COLUMN deleted_at TIMESTAMPTZ,
    DROP CONSTRAINT users_email_key,
    DROP CONSTRAINT users_username_key;

CREATE UNIQUE INDEX ux_users_username_active ON users (username) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_users_email_active ON users (email) WHERE deleted_at IS NULL;
