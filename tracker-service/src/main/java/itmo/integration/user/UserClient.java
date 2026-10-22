package itmo.integration.user;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.List;

@FeignClient(name = "user-service", configuration = UserClientConfiguration.class)
public interface UserClient {
    @PostMapping("/internal/users/resolve")
    List<UserReference> resolve(@RequestHeader("Authorization") String authorization, @RequestBody ResolveUsersRequest request);
}
