--liquibase formatted sql
--changeset file-service:001-files
CREATE TABLE files (
 id UUID PRIMARY KEY,
 owner_id BIGINT NOT NULL CHECK(owner_id>0),
 name VARCHAR(255) NOT NULL,
 content_type VARCHAR(255) NOT NULL,
 size BIGINT NOT NULL CHECK(size>0),
 created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE file_contents (
 id UUID PRIMARY KEY REFERENCES files(id),
 bytes BYTEA NOT NULL CHECK(octet_length(bytes)>0)
);
