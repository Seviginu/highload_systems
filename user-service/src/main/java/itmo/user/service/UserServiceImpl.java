package itmo.user.service;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.user.dto.CreateUserRequest;
import itmo.user.dto.UpdateUserRequest;
import itmo.user.dto.UserReference;
import itmo.user.dto.UserResponse;
import itmo.user.entity.User;
import itmo.user.mapper.UserMapper;
import itmo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public Mono<UserResponse> create(CreateUserRequest request) {
        return Mono.defer(() -> {
            String email = userMapper.normalizeEmail(request.email());
            return userRepository.existsByEmailIgnoreCase(email)
                    .flatMap(exists -> exists ? Mono.error(emailConflict(email))
                            : userRepository.save(userMapper.toEntity(request)))
                    .map(userMapper::toResponse)
                    .onErrorMap(DataIntegrityViolationException.class, error -> translateEmailConflict(error, email));
        });
    }

    @Override
    public Mono<UserResponse> findById(Long id) {
        return findEntity(id).map(userMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<Page<UserResponse>> findAll(Pageable pageable) {
        return userRepository.findActivePage(pageable.getPageSize(), pageable.getOffset())
                .map(userMapper::toResponse).collectList()
                .flatMap(users -> userRepository.countByDeletedFalse()
                        .map(total -> new PageImpl<>(users, pageable, total)));
    }

    @Override
    @Transactional
    public Mono<UserResponse> update(Long id, UpdateUserRequest request) {
        return Mono.defer(() -> {
            String email = userMapper.normalizeEmail(request.email());
            return findEntity(id).flatMap(user -> userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)
                    .flatMap(exists -> {
                        if (exists) {
                            return Mono.error(emailConflict(email));
                        }
                        userMapper.updateEntity(user, request);
                        return userRepository.save(user);
                    }))
                    .map(userMapper::toResponse)
                    .onErrorMap(DataIntegrityViolationException.class, error -> translateEmailConflict(error, email))
                    .onErrorMap(OptimisticLockingFailureException.class, error -> versionConflict(id));
        });
    }

    @Override
    @Transactional
    public Mono<Void> delete(Long id) {
        return findEntity(id).flatMap(user -> {
            user.delete();
            return userRepository.save(user);
        }).onErrorMap(OptimisticLockingFailureException.class, error -> versionConflict(id)).then();
    }

    @Override
    public Flux<UserReference> resolve(Set<Long> ids) {
        return userRepository.findAllByIdInAndDeletedFalse(ids)
                .map(user -> new UserReference(user.getId(), user.getRole()));
    }

    private Mono<User> findEntity(Long id) {
        return userRepository.findByIdAndDeletedFalse(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User", id)));
    }

    private RuntimeException translateEmailConflict(DataIntegrityViolationException error, String email) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains("uq_users_email_lower")) {
                return emailConflict(email);
            }
        }
        return error;
    }

    private ConflictException emailConflict(String email) {
        return new ConflictException("User with email '%s' already exists".formatted(email));
    }

    private ConflictException versionConflict(Long id) {
        return new ConflictException("User with id '%d' was modified by another request".formatted(id));
    }
}
