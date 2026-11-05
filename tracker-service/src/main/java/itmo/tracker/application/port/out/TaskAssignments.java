package itmo.tracker.application.port.out;

import itmo.tracker.application.model.TaskAssignment;

public interface TaskAssignments {
    void append(TaskAssignment assignment);
}
