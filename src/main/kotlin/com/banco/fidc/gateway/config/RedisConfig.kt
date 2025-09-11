package com.banco.fidc.gateway.config

import io.lettuce.core.ClientOptions
import io.lettuce.core.SocketOptions
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@ConditionalOnProperty(
    prefix = "spring.data.redis",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class RedisConfig(
    private val redisProperties: RedisProperties
) {

    @Bean
    fun reactiveRedisConnectionFactory(): ReactiveRedisConnectionFactory {
        val redisConfig = RedisStandaloneConfiguration().apply {
            hostName = redisProperties.host
            port = redisProperties.port
            if (!redisProperties.password.isNullOrBlank()) {
                setPassword(redisProperties.password)
            }
        }

        val socketOptions = SocketOptions.builder()
            .connectTimeout(Duration.ofMillis(3000))
            .keepAlive(true)
            .build()

        val clientOptions = ClientOptions.builder()
            .socketOptions(socketOptions)
            .autoReconnect(true)
            .build()

        val clientConfig = LettuceClientConfiguration.builder()
            .clientOptions(clientOptions)
            .commandTimeout(redisProperties.timeout ?: Duration.ofMillis(3000))
            .build()

        return LettuceConnectionFactory(redisConfig, clientConfig)
    }

    @Bean
    fun reactiveStringRedisTemplate(
        connectionFactory: ReactiveRedisConnectionFactory
    ): ReactiveStringRedisTemplate {
        return ReactiveStringRedisTemplate(connectionFactory)
    }
}