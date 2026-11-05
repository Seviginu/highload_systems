package itmo.user.configuration;

import itmo.user.adapter.in.web.CreateUserRequest;
import itmo.user.application.model.CreateUserCommand;
import itmo.user.application.service.BootstrapAdminService;
import itmo.user.domain.UserRole;
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
    private final BootstrapAdminService service;
    private final Validator validator;
    private final CreateUserRequest request;

    public AdminBootstrap(BootstrapAdminService service, Validator validator,
                          @Value("${app.bootstrap-admin.name:Supervisor}") String name,
                          @Value("${app.bootstrap-admin.email:}") String email,
                          @Value("${app.bootstrap-admin.password:}") String password) {
        this.service = service;
        this.validator = validator;
        this.request = new CreateUserRequest(name, email, UserRole.ADMIN, password);
    }

    @Override
    public void run(ApplicationArguments args) {
        // Startup must finish before readiness; HTTP/application code never blocks.
        initialize().block(Duration.ofSeconds(30));
    }

    public Mono<Void> initialize() {
        return service.initialize(Mono.fromSupplier(() -> {
            if (!validator.validate(request).isEmpty()) {
                throw new IllegalStateException("Invalid bootstrap admin configuration");
            }
            return new CreateUserCommand(request.name(), request.email(), request.role(), request.password());
        }));
    }
}
