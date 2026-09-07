package com.example.commerce.shared.web;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        // Simplistic in-memory concurrent map cache manager without eviction policies, TTL, or tenant isolation
        return new ConcurrentMapCacheManager("orders", "products");
    }
}
