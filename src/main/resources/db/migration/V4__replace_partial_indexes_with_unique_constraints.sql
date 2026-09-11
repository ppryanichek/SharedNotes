DROP INDEX IF EXISTS ux_users_username_active;
DROP INDEX IF EXISTS ux_users_email_active;

ALTER TABLE users
    ADD CONSTRAINT uq_users_email UNIQUE (email),
    ADD CONSTRAINT uq_users_username UNIQUE (username);
