package com.sample.hexagonal.sample.application.config.cache

import java.time.Duration

object CacheNames {
    const val DEFAULT = "default"
}

enum class CachePolicy(
    val cacheName: String,
    val maximumSize: Long,
    val ttl: Duration,
) {
    DEFAULT(
        cacheName = CacheNames.DEFAULT,
        maximumSize = 1_000,
        ttl = Duration.ofMinutes(10),
    ),
}
