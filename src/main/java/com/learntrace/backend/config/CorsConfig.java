package com.learntrace.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(
                    "https://*.warriors-f3d9.vercel.app",
                    "https://learn-trace-frontend.vercel.app"
                )
                .allowedMethods(
                    "GET",
                    "POST",
                    "PUT",
                    "PATCH",
                    "DELETE",
                    "OPTIONS"
                )
                .allowedHeaders("*")
                .maxAge(3600);
    }
}