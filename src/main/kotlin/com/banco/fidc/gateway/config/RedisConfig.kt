package com.banco.fidc.gateway.config

import io.lettuce.core.ClientOptions
import io.lettuce.core.SocketOptions
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.RedisClusterConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import java.time.Duration

@Configuration
class RedisConfig(
    private val redisProperties: RedisProperties
) {

    @Bean
    @Primary
    fun reactiveRedisConnectionFactory(): ReactiveRedisConnectionFactory {
        val redisConfig = RedisClusterConfiguration().apply {
            clusterNode(redisProperties.host, redisProperties.port)
        }

        redisConfig.apply {
            if (!redisProperties.password.isNullOrBlank()) {
                setPassword(redisProperties.password)
            }
            maxRedirects = 3
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