package itmo.integration.user;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;

@FeignClient(name = "user-service", configuration = UserClientConfiguration.class)
public interface UserClient {
    @PostMapping("/internal/users/resolve")
    List<UserReference> resolve(@RequestBody ResolveUsersRequest request);
}
