package itmo.files.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class S3Configuration {
    @Bean(destroyMethod = "close")
    S3Client fileS3Client(
            @Value("${app.files.s3.endpoint}") URI endpoint,
            @Value("${app.files.s3.region}") String region,
            @Value("${app.files.s3.bucket}") String bucket,
            @Value("${app.files.s3.access-key}") String accessKey,
            @Value("${app.files.s3.secret-key}") String secretKey) {
        var client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(region))
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(10)))
                .build();
        try {
            try {
                client.headBucket(r -> r.bucket(bucket));
            } catch (S3Exception e) {
                if (e.statusCode() != 404) throw e;
                try {
                    client.createBucket(r -> r.bucket(bucket));
                } catch (S3Exception conflict) {
                    if (!"BucketAlreadyOwnedByYou".equals(conflict.awsErrorDetails().errorCode())) {
                        throw conflict;
                    }
                }
            }
            return client;
        } catch (RuntimeException e) {
            client.close();
            throw e;
        }
    }
}
