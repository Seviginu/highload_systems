--liquibase formatted sql

--changeset vs-lab2:008-detach-users
ALTER TABLE tasks DROP CONSTRAINT fk_tasks_author;
ALTER TABLE tasks DROP CONSTRAINT fk_tasks_assignee;
ALTER TABLE project_members DROP CONSTRAINT fk_project_members_user;
-- The legacy users table is retained for an explicit, ID-preserving data transfer.
