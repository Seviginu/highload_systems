package itmo.infrastructure.security;

import itmo.user.dto.CreateUserRequest;
import itmo.user.entity.UserRole;
import itmo.user.repository.UserRepository;
import itmo.user.service.UserService;
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
    private final UserService service;
    private final Validator validator;
    private final CreateUserRequest request;

    public AdminBootstrap(UserRepository users, UserService service, Validator validator,
                          @Value("${app.bootstrap-admin.name:Supervisor}") String name,
                          @Value("${app.bootstrap-admin.email:}") String email,
                          @Value("${app.bootstrap-admin.password:}") String password) {
        this.users = users;
        this.service = service;
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
                    // create() rejects any existing email, including deleted users; never promote or reset it.
                    return service.create(request).then();
                });
    }
}
