--liquibase formatted sql
--changeset tracker-service:005-outbox-retention
CREATE INDEX ix_outbox_published ON outbox_events(published_at, id) WHERE published_at IS NOT NULL;
