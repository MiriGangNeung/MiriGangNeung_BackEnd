package com.mirigangneung.common.redis;
import org.springframework.data.redis.core.RedisTemplate; import org.springframework.stereotype.Component; import java.time.Duration;
@Component public class RedisCache { private final RedisTemplate<String,String> redis; public RedisCache(RedisTemplate<String,String> r){redis=r;} public String get(String k){try{return redis.opsForValue().get(k);}catch(Exception e){return null;}} public void put(String k,String v,Duration ttl){try{redis.opsForValue().set(k,v,ttl);}catch(Exception ignored){}} }
