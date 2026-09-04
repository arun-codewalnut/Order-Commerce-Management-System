package com.example.badcommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class BadCommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BadCommerceApplication.class, args);
    }

    @Bean
    public RestClient restClient() {
        // No custom timeouts, no connection pool configuration, default simple client
        return RestClient.builder().build();
    }
}

