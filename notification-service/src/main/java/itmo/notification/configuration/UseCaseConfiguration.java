package itmo.notification.configuration;

import itmo.notification.application.NotificationService;
import itmo.notification.application.port.*;

import org.springframework.context.annotation.*;

import reactor.core.scheduler.*;

import java.time.Clock;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {
    @Bean
    Clock notificationClock() {
        return Clock.systemUTC();
    }

    @Bean
    NotificationService notificationService(
            Notifications notifications, EventInbox inbox, UnitOfWork transactions, Clock clock) {
        return new NotificationService(notifications, inbox, transactions, clock, UUID::randomUUID);
    }

    @Bean(destroyMethod = "dispose")
    Scheduler notificationWorker() {
        return Schedulers.newBoundedElastic(4, 100, "notification-blocking");
    }
}
