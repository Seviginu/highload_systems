package itmo.tracker.application.port.out;

import itmo.tracker.application.model.ProjectMembership;

public interface ProjectMemberships {
    void append(ProjectMembership event);
}
