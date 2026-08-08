package com.mirigangneung.common.config;

import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> t = new RedisTemplate<>(); t.setConnectionFactory(factory);
        t.setKeySerializer(new StringRedisSerializer()); t.setValueSerializer(new StringRedisSerializer()); t.afterPropertiesSet(); return t;
    }
}
