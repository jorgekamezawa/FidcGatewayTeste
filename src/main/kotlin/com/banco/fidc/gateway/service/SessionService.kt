package com.banco.fidc.gateway.service

import com.banco.fidc.gateway.model.SessionContext
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration

@Service
@ConditionalOnProperty(
    prefix = "spring.data.redis",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class SessionService(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Busca sessão no Redis de forma reativa
     * Aplica circuit breaker para proteção contra falhas do Redis
     */
    @CircuitBreaker(name = "redis-session", fallbackMethod = "getSessionFallback")
    fun getSession(partner: String, sessionId: String): Mono<SessionContext> {
        val redisKey = SessionContext.buildRedisKey(partner, sessionId)
        
        logger.debug("Buscando sessão no Redis: key={}", redisKey)
        
        return redisTemplate.opsForValue()
            .get(redisKey)
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(Duration.ofSeconds(3))
            .doOnNext { logger.debug("Sessão encontrada no Redis: key={}", redisKey) }
            .doOnError { error -> logger.warn("Erro ao buscar sessão no Redis: key={}, error={}", redisKey, error.message) }
            .flatMap { jsonValue ->
                parseSessionJson(jsonValue, redisKey)
            }
            .switchIfEmpty(
                Mono.error(SessionNotFoundException("Sessão não encontrada: $redisKey"))
            )
    }

    /**
     * Valida se a sessão existe no Redis
     */
    @CircuitBreaker(name = "redis-session", fallbackMethod = "existsSessionFallback")
    fun existsSession(partner: String, sessionId: String): Mono<Boolean> {
        val redisKey = SessionContext.buildRedisKey(partner, sessionId)
        
        return redisTemplate.hasKey(redisKey)
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(Duration.ofSeconds(2))
            .doOnNext { exists -> 
                logger.debug("Verificação de existência da sessão: key={}, exists={}", redisKey, exists) 
            }
            .doOnError { error -> 
                logger.warn("Erro ao verificar existência da sessão: key={}, error={}", redisKey, error.message) 
            }
    }

    /**
     * Parse do JSON da sessão do Redis
     */
    private fun parseSessionJson(jsonValue: String, redisKey: String): Mono<SessionContext> {
        return Mono.fromCallable {
            try {
                objectMapper.readValue(jsonValue, SessionContext::class.java)
            } catch (e: Exception) {
                logger.error("Erro ao fazer parse da sessão JSON: key={}, json={}, error={}", redisKey, jsonValue, e.message)
                throw SessionParseException("Erro ao fazer parse da sessão: ${e.message}", e)
            }
        }.subscribeOn(Schedulers.boundedElastic())
    }

    /**
     * Fallback para quando o Redis estiver indisponível
     */
    private fun getSessionFallback(partner: String, sessionId: String, ex: Exception): Mono<SessionContext> {
        logger.error("Circuit breaker ativo para busca de sessão: partner={}, sessionId={}, error={}", 
                     partner, sessionId, ex.message)
        return Mono.error(RedisUnavailableException("Redis indisponível para busca de sessão", ex))
    }

    /**
     * Fallback para verificação de existência quando Redis indisponível
     */
    private fun existsSessionFallback(partner: String, sessionId: String, ex: Exception): Mono<Boolean> {
        logger.error("Circuit breaker ativo para verificação de sessão: partner={}, sessionId={}, error={}", 
                     partner, sessionId, ex.message)
        return Mono.error(RedisUnavailableException("Redis indisponível para verificação de sessão", ex))
    }

    /**
     * Invalida sessão no Redis (logout)
     * Usado quando necessário forçar logout do usuário
     */
    @CircuitBreaker(name = "redis-session")
    fun invalidateSession(partner: String, sessionId: String): Mono<Boolean> {
        val redisKey = SessionContext.buildRedisKey(partner, sessionId)
        
        logger.info("Invalidando sessão: key={}", redisKey)
        
        return redisTemplate.delete(redisKey)
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(Duration.ofSeconds(5))
            .map { deletedCount -> deletedCount > 0 }
            .doOnNext { deleted -> 
                logger.info("Sessão invalidada: key={}, deleted={}", redisKey, deleted) 
            }
            .doOnError { error -> 
                logger.error("Erro ao invalidar sessão: key={}, error={}", redisKey, error.message) 
            }
    }
}

/**
 * Exceções específicas do serviço de sessão
 */
class SessionNotFoundException(message: String) : RuntimeException(message)
class SessionParseException(message: String, cause: Throwable) : RuntimeException(message, cause)
class RedisUnavailableException(message: String, cause: Throwable) : RuntimeException(message, cause)