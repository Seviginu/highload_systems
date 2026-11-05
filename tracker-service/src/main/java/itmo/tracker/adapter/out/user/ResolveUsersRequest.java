package itmo.tracker.adapter.out.user;

import java.util.Set;

public record ResolveUsersRequest(Set<Long> ids) {}
