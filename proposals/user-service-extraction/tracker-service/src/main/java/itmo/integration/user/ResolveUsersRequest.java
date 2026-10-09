package itmo.integration.user;

import java.util.Set;

public record ResolveUsersRequest(Set<Long> ids) {
}
