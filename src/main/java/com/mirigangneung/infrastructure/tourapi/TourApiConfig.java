package com.mirigangneung.infrastructure.tourapi;
import org.springframework.boot.context.properties.EnableConfigurationProperties; import org.springframework.context.annotation.Configuration;
@Configuration @EnableConfigurationProperties(TourApiProperties.class) public class TourApiConfig {}
