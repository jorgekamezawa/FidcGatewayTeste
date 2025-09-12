package com.banco.fidc.gateway.service

import com.banco.fidc.gateway.exception.SessionNotFoundException
import com.banco.fidc.gateway.exception.SessionParseException
import com.banco.fidc.gateway.model.SessionContext
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration

@Service
class SessionService(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val redisCircuitBreaker: CircuitBreaker
) {
    
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getSession(partner: String, sessionId: String): Mono<SessionContext> {
        val redisKey = SessionContext.buildRedisKey(partner, sessionId)
        
        logger.debug("Buscando sessão no Redis: key={}", redisKey)
        
        return redisTemplate.opsForValue()
            .get(redisKey)
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(Duration.ofSeconds(3))
            .transformDeferred(CircuitBreakerOperator.of(redisCircuitBreaker))
            .doOnNext { logger.debug("Sessão encontrada no Redis: key={}", redisKey) }
            .doOnError { error -> logger.warn("Erro ao buscar sessão no Redis: key={}, error={}", redisKey, error.message) }
            .flatMap { jsonValue ->
                parseSessionJson(jsonValue, redisKey)
            }
            .switchIfEmpty(
                Mono.error(SessionNotFoundException("Sessão não encontrada: $redisKey"))
            )
    }

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
}

