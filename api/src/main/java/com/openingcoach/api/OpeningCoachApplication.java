package com.openingcoach.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OpeningCoachApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpeningCoachApplication.class, args);
    }
}
