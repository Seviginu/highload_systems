package itmo.user.application.port.out;

import reactor.core.publisher.Mono;
public interface Passwords {
    Mono<String> encode(String password);
    Mono<Boolean> matches(String password, String hash);
}
