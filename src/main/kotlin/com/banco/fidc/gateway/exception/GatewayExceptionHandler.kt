package com.banco.fidc.gateway.exception

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.slf4j.LoggerFactory
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

@Component
@Order(-1)
class GatewayExceptionHandler : ErrorWebExceptionHandler {

    private val logger = LoggerFactory.getLogger(GatewayExceptionHandler::class.java)

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        val response = exchange.response
        val correlationId = exchange.attributes["correlationId"] as? String ?: "unknown"

        return when (ex) {
            is SessionValidationException -> handleSessionValidationException(response, ex, correlationId)
            is InsufficientPermissionsException -> handleInsufficientPermissionsException(response, ex, correlationId)
            is AuthorizationException -> handleAuthorizationException(response, ex, correlationId)
            is CallNotPermittedException -> handleCircuitBreakerException(response, ex, correlationId)
            is ResponseStatusException -> handleResponseStatusException(response, ex, correlationId)
            else -> handleGenericException(response, ex, correlationId)
        }
    }

    private fun handleSessionValidationException(
        response: ServerHttpResponse,
        ex: SessionValidationException,
        correlationId: String
    ): Mono<Void> {
        logger.warn("Session validation failed - correlationId: {} - reason: {}", correlationId, ex.message)
        return writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "INVALID_SESSION", ex.message, correlationId)
    }

    private fun handleInsufficientPermissionsException(
        response: ServerHttpResponse,
        ex: InsufficientPermissionsException,
        correlationId: String
    ): Mono<Void> {
        logger.warn("Insufficient permissions - correlationId: {} - reason: {}", correlationId, ex.message)
        return writeErrorResponse(response, HttpStatus.FORBIDDEN, "INSUFFICIENT_PERMISSIONS", "Usuário não possui permissões necessárias", correlationId)
    }

    private fun handleAuthorizationException(
        response: ServerHttpResponse,
        ex: AuthorizationException,
        correlationId: String
    ): Mono<Void> {
        logger.warn("Authorization failed - correlationId: {} - reason: {}", correlationId, ex.message)
        return writeErrorResponse(response, HttpStatus.FORBIDDEN, "INSUFFICIENT_PERMISSIONS", ex.message, correlationId)
    }

    private fun handleResponseStatusException(
        response: ServerHttpResponse,
        ex: ResponseStatusException,
        correlationId: String
    ): Mono<Void> {
        logger.error("Gateway error - correlationId: {} - status: {} - reason: {}", 
                    correlationId, ex.statusCode, ex.reason, ex)
        return writeErrorResponse(response, HttpStatus.resolve(ex.statusCode.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR, "GATEWAY_ERROR", ex.reason ?: "Internal error", correlationId)
    }

    private fun handleCircuitBreakerException(
        response: ServerHttpResponse,
        ex: CallNotPermittedException,
        correlationId: String
    ): Mono<Void> {
        val circuitBreakerName = ex.causingCircuitBreakerName
        
        return when (circuitBreakerName) {
            "redis" -> {
                logger.warn("Redis circuit breaker is open - correlationId: {} - circuit: {}", correlationId, circuitBreakerName)
                writeErrorResponse(
                    response, 
                    HttpStatus.UNAUTHORIZED, 
                    "SESSION_SERVICE_UNAVAILABLE", 
                    "Session validation service is temporarily unavailable", 
                    correlationId
                )
            }
            "downstream" -> {
                logger.warn("Downstream circuit breaker is open - correlationId: {} - circuit: {}", correlationId, circuitBreakerName)
                writeErrorResponse(
                    response, 
                    HttpStatus.SERVICE_UNAVAILABLE, 
                    "SERVICE_TEMPORARILY_UNAVAILABLE", 
                    "Service is temporarily unavailable, please try again later", 
                    correlationId
                )
            }
            else -> {
                logger.warn("Unknown circuit breaker is open - correlationId: {} - circuit: {}", correlationId, circuitBreakerName)
                writeErrorResponse(
                    response, 
                    HttpStatus.SERVICE_UNAVAILABLE, 
                    "CIRCUIT_BREAKER_OPEN", 
                    "Service temporarily unavailable", 
                    correlationId
                )
            }
        }
    }

    private fun handleGenericException(
        response: ServerHttpResponse,
        ex: Throwable,
        correlationId: String
    ): Mono<Void> {
        logger.error("Unexpected error - correlationId: {}", correlationId, ex)
        return writeErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error", correlationId)
    }

    private fun writeErrorResponse(
        response: ServerHttpResponse,
        status: HttpStatus,
        errorCode: String,
        message: String?,
        correlationId: String
    ): Mono<Void> {
        response.statusCode = status
        response.headers.contentType = MediaType.APPLICATION_JSON
        response.headers.add("X-Correlation-ID", correlationId)

        val errorResponse = """
            {
                "timestamp": "${java.time.Instant.now()}",
                "status": ${status.value()},
                "error": "${status.reasonPhrase}",
                "code": "$errorCode",
                "message": "${message ?: ""}",
                "correlationId": "$correlationId"
            }
        """.trimIndent()

        val buffer: DataBuffer = response.bufferFactory().wrap(errorResponse.toByteArray(StandardCharsets.UTF_8))
        return response.writeWith(Mono.just(buffer))
    }
}