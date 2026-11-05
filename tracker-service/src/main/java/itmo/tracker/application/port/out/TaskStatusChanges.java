package itmo.tracker.application.port.out;

import itmo.tracker.application.model.TaskStatusChange;

public interface TaskStatusChanges {
    void append(TaskStatusChange event);
}
