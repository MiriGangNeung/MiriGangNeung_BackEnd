package com.mirigangneung.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@EnableAsync
public class WebConfig implements WebMvcConfigurer {
    @Value("${app.cors-origins}") private String origins;
    @Override public void addCorsMappings(CorsRegistry registry) { registry.addMapping("/api/**").allowedOrigins(origins.split(",")).allowedMethods("GET","POST","DELETE","OPTIONS"); }
}
