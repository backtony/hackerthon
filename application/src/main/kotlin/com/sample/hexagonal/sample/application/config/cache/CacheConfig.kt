package com.sample.hexagonal.sample.application.config.cache

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableCaching
class CacheConfig {
    @Bean
    fun cacheManager(): CacheManager = CaffeineCacheManager().apply {
        setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(CachePolicy.DEFAULT.maximumSize)
                .expireAfterWrite(CachePolicy.DEFAULT.ttl),
        )
        CachePolicy.entries.forEach { policy ->
            registerCustomCache(
                policy.cacheName,
                Caffeine.newBuilder()
                    .maximumSize(policy.maximumSize)
                    .expireAfterWrite(policy.ttl)
                    .build<Any, Any>(),
            )
        }
    }
}
