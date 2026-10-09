package itmo;

import org.springframework.boot.SpringApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableFeignClients
public class VsLab1Application {

    public static void main(String[] args) {
        SpringApplication.run(VsLab1Application.class, args);
    }
}
