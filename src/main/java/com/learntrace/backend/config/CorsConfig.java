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
                  "http://localhost:5173",
                  "http://127.0.0.1:5173",
                  "https://*-warriors-f3d9.vercel.app",
                    "https://learn-trace-frontend.vercel.app"
                )
                .allowedMethods(
                    "GET",
                    "POST",
                    "OPTIONS"
                )
                .allowedHeaders("Authorization", "Content-Type")
                .maxAge(3600);
    }
}
