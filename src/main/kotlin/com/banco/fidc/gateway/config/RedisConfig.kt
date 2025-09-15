package com.banco.fidc.gateway.config

import io.lettuce.core.ClientOptions
import io.lettuce.core.SocketOptions
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.RedisClusterConfiguration
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import java.time.Duration

@Configuration
class RedisConfig(
    private val redisProperties: RedisProperties
) {

    // Configuração STANDALONE para profile LOCAL
    @Bean("standaloneRedisConnectionFactory")
    @Primary
    @Profile("local")
    fun standaloneRedisConnectionFactory(): ReactiveRedisConnectionFactory {
        val redisConfig = RedisStandaloneConfiguration().apply {
            hostName = redisProperties.host ?: "localhost"
            port = redisProperties.port
            if (!redisProperties.password.isNullOrBlank()) {
                password = RedisPassword.of(redisProperties.password)
            }
        }

        val clientConfig = buildLettuceClientConfiguration()
        
        return LettuceConnectionFactory(redisConfig, clientConfig)
    }

    // Configuração CLUSTER para outros profiles (dev, uat, prd)
    @Bean("clusterRedisConnectionFactory")
    @Primary
    @Profile("!local")
    fun clusterRedisConnectionFactory(): ReactiveRedisConnectionFactory {
        val redisConfig = RedisClusterConfiguration().apply {
            clusterNode(redisProperties.host ?: "localhost", redisProperties.port)
            if (!redisProperties.password.isNullOrBlank()) {
                setPassword(redisProperties.password)
            }
            maxRedirects = 3
        }

        val clientConfig = buildLettuceClientConfiguration()
        
        return LettuceConnectionFactory(redisConfig, clientConfig)
    }

    // Configuração comum do cliente Lettuce
    private fun buildLettuceClientConfiguration(): LettuceClientConfiguration {
        val socketOptions = SocketOptions.builder()
            .connectTimeout(Duration.ofMillis(3000))
            .keepAlive(true)
            .build()

        val clientOptions = ClientOptions.builder()
            .socketOptions(socketOptions)
            .autoReconnect(true)
            .build()

        return LettuceClientConfiguration.builder()
            .clientOptions(clientOptions)
            .commandTimeout(redisProperties.timeout ?: Duration.ofMillis(3000))
            .build()
    }

    @Bean
    fun reactiveStringRedisTemplate(
        connectionFactory: ReactiveRedisConnectionFactory
    ): ReactiveStringRedisTemplate {
        return ReactiveStringRedisTemplate(connectionFactory)
    }
}