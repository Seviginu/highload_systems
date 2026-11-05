--liquibase formatted sql
--changeset notification-service:001-notifications
CREATE TABLE processed_events(event_id UUID PRIMARY KEY,processed_at TIMESTAMPTZ NOT NULL);
CREATE TABLE notifications(
    id UUID PRIMARY KEY,event_id UUID NOT NULL REFERENCES processed_events(event_id),recipient_id BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,message VARCHAR(255) NOT NULL,resource_type VARCHAR(255) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,created_at TIMESTAMPTZ NOT NULL,read_at TIMESTAMPTZ,
    CONSTRAINT uq_notification_event_recipient UNIQUE(event_id,recipient_id)
);
CREATE INDEX ix_notifications_recipient_created ON notifications(recipient_id,created_at DESC,id DESC);
