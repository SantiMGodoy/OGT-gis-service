package com.ogt.gis.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI gisOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("GIS Service API")
                        .description("API Geoespacial para conversión, importación, exportación y análisis.")
                        .version("1.0"));
    }
}