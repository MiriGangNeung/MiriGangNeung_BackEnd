package com.mirigangneung.infrastructure.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiGenerationProperties.class)
public class AiGenerationConfig {
}
