package com.banco.fidc.gateway.config

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig as Resilience4jConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class CircuitBreakerConfig {

    @Bean
    fun circuitBreakerRegistry(): CircuitBreakerRegistry {
        val defaultConfig = Resilience4jConfig.custom()
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .permittedNumberOfCallsInHalfOpenState(3)
            .slowCallRateThreshold(50.0f)
            .slowCallDurationThreshold(Duration.ofSeconds(2))
            .build()

        val redisConfig = Resilience4jConfig.custom()
            .failureRateThreshold(70.0f)
            .waitDurationInOpenState(Duration.ofSeconds(15))
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .permittedNumberOfCallsInHalfOpenState(5)
            .slowCallRateThreshold(60.0f)
            .slowCallDurationThreshold(Duration.ofSeconds(1))
            .build()

        val downstreamConfig = Resilience4jConfig.custom()
            .failureRateThreshold(60.0f)
            .waitDurationInOpenState(Duration.ofSeconds(45))
            .slidingWindowSize(15)
            .minimumNumberOfCalls(8)
            .permittedNumberOfCallsInHalfOpenState(4)
            .slowCallRateThreshold(70.0f)
            .slowCallDurationThreshold(Duration.ofSeconds(5))
            .build()

        return CircuitBreakerRegistry.of(
            mapOf(
                "default" to defaultConfig,
                "redis" to redisConfig,
                "downstream" to downstreamConfig
            )
        )
    }

    @Bean
    fun redisCircuitBreaker(registry: CircuitBreakerRegistry): CircuitBreaker {
        return registry.circuitBreaker("redis", "redis")
    }

    @Bean
    fun downstreamCircuitBreaker(registry: CircuitBreakerRegistry): CircuitBreaker {
        return registry.circuitBreaker("downstream", "downstream")
    }
}