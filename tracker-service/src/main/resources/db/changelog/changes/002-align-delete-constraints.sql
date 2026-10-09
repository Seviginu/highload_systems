--liquibase formatted sql

--changeset vs-lab1:007-restrict-project-and-label-deletion
ALTER TABLE tasks DROP CONSTRAINT fk_tasks_project;
ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_project
        FOREIGN KEY (project_id) REFERENCES projects (id);

ALTER TABLE task_labels DROP CONSTRAINT fk_task_labels_label;
ALTER TABLE task_labels
    ADD CONSTRAINT fk_task_labels_label
        FOREIGN KEY (label_id) REFERENCES labels (id);
