package itmo.tracker.application.model;

import java.util.Set;

public record Actor(long id, Set<String> roles) {
    public Actor {
        roles = Set.copyOf(roles);
    }

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }

    public boolean isTeamLead() {
        return roles.contains("TEAM_LEAD");
    }
}
