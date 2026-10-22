package itmo.infrastructure.security;

import itmo.user.dto.CreateUserRequest;
import itmo.user.entity.UserRole;
import itmo.user.repository.UserRepository;
import itmo.user.service.PasswordService;
import itmo.user.mapper.UserMapper;
import itmo.common.exception.ConflictException;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordService passwords;
    private final UserMapper mapper;
    private final Validator validator;
    private final CreateUserRequest request;

    public AdminBootstrap(UserRepository users, PasswordService passwords, UserMapper mapper, Validator validator,
                          @Value("${app.bootstrap-admin.name:Supervisor}") String name,
                          @Value("${app.bootstrap-admin.email:}") String email,
                          @Value("${app.bootstrap-admin.password:}") String password) {
        this.users = users;
        this.passwords = passwords;
        this.mapper = mapper;
        this.validator = validator;
        this.request = new CreateUserRequest(name, email, UserRole.ADMIN, password);
    }

    @Override
    public void run(ApplicationArguments args) {
        initialize().block(Duration.ofSeconds(30));
    }

    public Mono<Void> initialize() {
        return users.existsByRoleAndDeletedFalseAndPasswordHashIsNotNull(UserRole.ADMIN)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.<Void>empty();
                    }
                    if (!validator.validate(request).isEmpty()) {
                        return Mono.error(new IllegalStateException("Invalid bootstrap admin configuration"));
                    }
                    String email = mapper.normalizeEmail(request.email());
                    return users.existsByEmailIgnoreCase(email).flatMap(taken -> {
                        if (taken) {
                            return Mono.error(new ConflictException("Bootstrap admin email is already in use"));
                        }
                        return passwords.encode(request.password()).flatMap(hash -> {
                            var user = mapper.toEntity(request);
                            user.changePasswordHash(hash);
                            return users.save(user);
                        }).then();
                    });
                });
    }
}
