--liquibase formatted sql

--changeset user-service:002-add-password-hash
-- NULL preserves existing users without assigning shared credentials.
ALTER TABLE users ADD COLUMN password_hash VARCHAR(60);
ALTER TABLE users ADD CONSTRAINT ck_users_password_hash
    CHECK (password_hash IS NULL OR password_hash ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$');
