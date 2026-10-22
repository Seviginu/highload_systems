package itmo.user.service;

import itmo.user.validation.PasswordValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Service
public class PasswordService {
    private final PasswordEncoder encoder;
    private final Scheduler scheduler;

    public PasswordService(PasswordEncoder encoder, @Qualifier("passwordScheduler") Scheduler scheduler) {
        this.encoder = encoder;
        this.scheduler = scheduler;
    }

    public Mono<String> encode(String password) {
        return Mono.fromCallable(() -> {
            if (!PasswordValidator.isValidPassword(password)) {
                throw new IllegalArgumentException("Password does not satisfy the password policy");
            }
            return encoder.encode(password);
        }).subscribeOn(scheduler);
    }
}
