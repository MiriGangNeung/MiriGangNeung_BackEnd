package com.mirigangneung.common.redis;

import java.time.Duration;
import java.util.Set;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisCache {
    private final RedisTemplate<String, String> redis;

    public RedisCache(RedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    public String get(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    public void put(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (Exception ignored) {
        }
    }

    public void deleteByPrefix(String prefix) {
        try {
            Set<String> keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception ignored) {
        }
    }
}
