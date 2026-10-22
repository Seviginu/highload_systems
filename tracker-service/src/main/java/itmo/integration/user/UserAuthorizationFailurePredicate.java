package itmo.integration.user;

import feign.FeignException;

import java.util.function.Predicate;

/** Authentication failures are client errors, not downstream availability failures. */
public class UserAuthorizationFailurePredicate implements Predicate<Throwable> {
    @Override
    public boolean test(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof FeignException feign && (feign.status() == 401 || feign.status() == 403)) {
                return true;
            }
        }
        return false;
    }
}
