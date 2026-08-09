package com.mirigangneung.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JacksonConfig {
    @Bean
    ObjectMapper jackson2ObjectMapper() {
        return new ObjectMapper();
    }
}
