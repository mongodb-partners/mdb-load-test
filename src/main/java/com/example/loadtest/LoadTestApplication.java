package com.example.loadtest;

import com.example.loadtest.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class LoadTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoadTestApplication.class, args);
    }
}
