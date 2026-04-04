package com.example.celcoinmock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CelcoinMockApplication {
    public static void main(String[] args) {
        SpringApplication.run(CelcoinMockApplication.class, args);
    }
}
