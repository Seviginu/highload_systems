package itmo.tracker.configuration;

import org.springframework.context.annotation.*;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class EventConfiguration {
    @Bean
    Clock eventClock() {
        return Clock.systemUTC();
    }
}
