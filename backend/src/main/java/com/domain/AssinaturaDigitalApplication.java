package com.domain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AssinaturaDigitalApplication {
    public static void main(String[] args) {
        SpringApplication.run(AssinaturaDigitalApplication.class, args);
    }
}
