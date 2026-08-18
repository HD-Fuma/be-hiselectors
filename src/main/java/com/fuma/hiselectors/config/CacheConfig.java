package com.fuma.hiselectors.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 인메모리 캐시. 추후 인프라 캐시(Redis 등) 붙일 때 이 빈만 교체하면 서비스 코드는 그대로.
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }
}
