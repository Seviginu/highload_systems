package itmo.integration.user;

import feign.FeignException;
import itmo.common.exception.ConflictException;
import itmo.common.exception.DependencyUnavailableException;
import itmo.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDirectory {
    private final UserClient userClient;

    public void requireUsers(Long... ids) {
        resolve(ids);
    }

    public void requireTeamLead(Long id) {
        if (!"TEAM_LEAD".equals(resolve(id).get(id).role())) {
            throw new ConflictException("User with id '%d' is not a team lead".formatted(id));
        }
    }

    private Map<Long, UserReference> resolve(Long... values) {
        Set<Long> ids = Arrays.stream(values).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserReference> users = new HashMap<>();
        try {
            for (UserReference user : userClient.resolve(new ResolveUsersRequest(ids))) {
                users.put(user.id(), user);
            }
        } catch (FeignException error) {
            throw new DependencyUnavailableException("User service is unavailable", error);
        }
        for (Long id : ids) {
            if (!users.containsKey(id)) {
                throw new ResourceNotFoundException("User", id);
            }
        }
        return users;
    }
}
