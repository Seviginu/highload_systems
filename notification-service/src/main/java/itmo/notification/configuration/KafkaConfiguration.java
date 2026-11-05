package itmo.notification.configuration;

import org.springframework.context.annotation.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class KafkaConfiguration {
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        // Keep failed records pending until stage 5 adds bounded retries and a verified DLT
        // recoverer.
        var handler =
                new DefaultErrorHandler(new FixedBackOff(1000, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.setClassifications(Map.of(Exception.class, true), true);
        return handler;
    }
}
