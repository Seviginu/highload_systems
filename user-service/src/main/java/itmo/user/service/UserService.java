package itmo.user.service;

import itmo.user.dto.CreateUserRequest;
import itmo.user.dto.UpdateUserRequest;
import itmo.user.dto.UserResponse;
import itmo.user.dto.UserReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

public interface UserService {
    Mono<UserResponse> create(CreateUserRequest request);
    Mono<UserResponse> findById(Long id);
    Mono<Page<UserResponse>> findAll(Pageable pageable);
    Mono<UserResponse> update(Long id, UpdateUserRequest request);
    Mono<Void> delete(Long id);
    Flux<UserReference> resolve(Set<Long> ids);
}
