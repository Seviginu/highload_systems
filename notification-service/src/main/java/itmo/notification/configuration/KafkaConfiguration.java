package itmo.notification.configuration;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class KafkaConfiguration {
    @Bean
    DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> template,
            @Value("${app.notifications.retry-delay:1000}") long delay,
            @Value("${app.notifications.max-retries:2}") long retries) {
        if (delay < 0 || retries < 0 || retries > 10)
            throw new IllegalArgumentException("Invalid notification retry configuration");
        var recoverer =
                new DeadLetterPublishingRecoverer(
                        template,
                        (record, error) ->
                                new TopicPartition(record.topic() + ".dlt", record.partition()));
        recoverer.setVerifyPartition(false);
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(15));
        recoverer.excludeHeader(
                DeadLetterPublishingRecoverer.HeaderNames.HeadersToAdd.EX_MSG,
                DeadLetterPublishingRecoverer.HeaderNames.HeadersToAdd.EX_STACKTRACE);
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(delay, retries));
        handler.setClassifications(Map.of(Exception.class, true), true);
        return handler;
    }
}
