package itmo.integration.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.util.List;

/** Registered only in this Feign client's context, not in the WebFlux server. */
public class UserClientConfiguration {
    @Bean
    HttpMessageConverters userClientMessageConverters(ObjectMapper objectMapper) {
        return new HttpMessageConverters(false, List.of(new MappingJackson2HttpMessageConverter(objectMapper)));
    }
}
