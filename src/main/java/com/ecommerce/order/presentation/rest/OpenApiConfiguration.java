package com.ecommerce.order.presentation.rest;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Document-level OpenAPI metadata. The server list is fixed here so that the generated document
 * does not depend on the host that asked for it, which keeps the committed snapshot
 * {@code docs/openapi/rest-endpoints.json} reproducible.
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI orderServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .version("v1")
                        .description("REST contract of the Order Service, the first slice extracted from the "
                                + "e-commerce monolith (ADR-0007). Errors are RFC 7807 problem details."))
                .servers(List.of(new Server().url("http://localhost:8080").description("Local development")));
    }
}
