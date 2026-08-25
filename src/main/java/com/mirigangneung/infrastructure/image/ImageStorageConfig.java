package com.mirigangneung.infrastructure.image;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ImageCacheProperties.class)
public class ImageStorageConfig {
}
