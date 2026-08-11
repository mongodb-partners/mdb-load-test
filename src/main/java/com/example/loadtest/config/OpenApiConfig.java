package com.example.loadtest.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata shown on the Swagger UI at /docs. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI loadTestOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("mdb-load-test API")
                .version("0.0.1")
                .description("Products, Orders, Customers CRUD + vector search for MongoDB load testing."));
    }
}
