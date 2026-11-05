package itmo.user.application.port.in;

import itmo.user.application.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Set;

public interface UserService {
    Mono<UserView> create(CreateUserCommand request);
    Mono<UserView> findById(Long id);
    Flux<UserView> findAll(UserPage page);
    Mono<Long> count();
    Mono<UserView> update(Long id, UpdateUserCommand request);
    Mono<Void> delete(Long id);
    Mono<Void> changePassword(Long id, String password);
    Flux<UserReference> resolve(Set<Long> ids);
}
