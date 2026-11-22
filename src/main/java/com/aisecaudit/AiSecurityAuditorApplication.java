package com.aisecaudit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AiSecurityAuditorApplication {

    public static void main(String[] args) {
        SpringApplication.run(
            AiSecurityAuditorApplication.class, args);
    }
}
