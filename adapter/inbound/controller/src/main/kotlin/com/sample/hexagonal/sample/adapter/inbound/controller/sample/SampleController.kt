package com.sample.hexagonal.sample.adapter.inbound.controller.sample

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sample")
class SampleController {
    @GetMapping
    @CircuitBreaker(name = "sample", fallbackMethod = "fallback")
    fun sample(): String = "ok"

    private fun fallback(exception: Throwable): String = "fallback"
}
