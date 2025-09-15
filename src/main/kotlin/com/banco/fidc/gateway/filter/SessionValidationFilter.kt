package com.banco.fidc.gateway.filter

import com.banco.fidc.gateway.exception.InsufficientPermissionsException
import com.banco.fidc.gateway.exception.SessionValidationException
import com.banco.fidc.gateway.model.AllowedHeaders
import com.banco.fidc.gateway.model.GatewayHeaders
import com.banco.fidc.gateway.model.SessionContext
import com.banco.fidc.gateway.service.JwtService
import com.banco.fidc.gateway.service.SessionService
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Instant

@Component
class SessionValidationFilter(
    private val sessionService: SessionService,
    private val jwtService: JwtService
) : AbstractGatewayFilterFactory<SessionValidationFilter.Config>(Config::class.java) {

    private val logger = LoggerFactory.getLogger(javaClass)

    data class Config(
        val requiredPermissions: List<String> = emptyList()
    )

    override fun apply(config: Config): GatewayFilter {
        return GatewayFilter { exchange, chain ->
            setupRequestMetrics(exchange)
            
            validateSession(exchange, config)
                .flatMap { sessionContext ->
                    processValidatedSession(exchange, sessionContext, chain)
                }
                .onErrorResume { error ->
                    handleValidationError(exchange, error)
                }
        }
    }

    private fun setupRequestMetrics(exchange: ServerWebExchange) {
        exchange.attributes[GatewayHeaders.REQUEST_START_TIME] = Instant.now()
    }

    private fun validateSession(exchange: ServerWebExchange, config: Config): Mono<SessionContext> {
        val headers = extractRequiredHeaders(exchange.request)
        
        return jwtService.extractSessionId(headers.authToken)
            .flatMap { sessionId ->
                validateTokenAndSession(headers, sessionId)
            }
            .flatMap { sessionContext ->
                validateBusinessRules(exchange, sessionContext, config)
            }
    }

    private fun extractRequiredHeaders(request: org.springframework.http.server.reactive.ServerHttpRequest): RequiredHeaders {
        val authHeader = request.headers.getFirst(GatewayHeaders.AUTHORIZATION)
            ?: throw SessionValidationException("Authorization header não fornecido")
            
        val partnerHeader = request.headers.getFirst(GatewayHeaders.PARTNER_HEADER)
            ?: throw SessionValidationException("Partner header não fornecido")
            
        return RequiredHeaders(authHeader, partnerHeader)
    }

    private fun validateTokenAndSession(headers: RequiredHeaders, sessionId: String): Mono<SessionContext> {
        return sessionService.getSession(headers.partner, sessionId)
            .flatMap { sessionContext ->
                validateAccessToken(headers.authToken, sessionContext.sessionSecret)
                    .then(Mono.just(sessionContext))
            }
    }

    private fun validateAccessToken(authToken: String, sessionSecret: String): Mono<Void> {
        return jwtService.validateToken(authToken, sessionSecret)
            .flatMap { isValid ->
                if (isValid) {
                    Mono.empty()
                } else {
                    Mono.error(SessionValidationException("AccessToken inválido"))
                }
            }
    }

    private fun validateBusinessRules(
        exchange: ServerWebExchange,
        sessionContext: SessionContext,
        config: Config
    ): Mono<SessionContext> {
        return validateRelationshipSelected(sessionContext)
            .then(validateRoutePermissions(exchange, sessionContext, config))
            .then(Mono.just(sessionContext))
    }

    private fun validateRelationshipSelected(sessionContext: SessionContext): Mono<Void> {
        return if (sessionContext.hasValidRelationship()) {
            Mono.empty()
        } else {
            Mono.error(SessionValidationException("Relacionamento não selecionado"))
        }
    }

    private fun validateRoutePermissions(
        exchange: ServerWebExchange, 
        sessionContext: SessionContext,
        config: Config
    ): Mono<Void> {
        if (config.requiredPermissions.isEmpty()) {
            return Mono.empty()
        }
        
        logger.debug("Verificando permissões da rota: required={}", config.requiredPermissions)
        
        return if (sessionContext.hasPermissions(config.requiredPermissions)) {
            Mono.empty()
        } else {
            createPermissionDeniedError(exchange, config, sessionContext)
        }
    }

    private fun createPermissionDeniedError(
        exchange: ServerWebExchange,
        config: Config,
        sessionContext: SessionContext
    ): Mono<Void> {
        val routeId = getRouteId(exchange)
        logger.warn("Acesso negado por falta de permissões: route={}, required={}, user={}", 
                   routeId, config.requiredPermissions, sessionContext.permissions)
        
        return Mono.error(
            InsufficientPermissionsException(
                "Usuário não possui permissões necessárias: ${config.requiredPermissions}",
                config.requiredPermissions,
                sessionContext.permissions
            )
        )
    }

    private fun processValidatedSession(
        exchange: ServerWebExchange,
        sessionContext: SessionContext,
        chain: org.springframework.cloud.gateway.filter.GatewayFilterChain
    ): Mono<Void> {
        setupLoggingContext(exchange, sessionContext)
        logSuccessfulValidation(sessionContext)
        
        val enrichedExchange = enrichRequestWithHeaders(exchange, sessionContext)
        
        return chain.filter(enrichedExchange)
            .doFinally { MDC.clear() }
    }

    private fun setupLoggingContext(exchange: ServerWebExchange, sessionContext: SessionContext) {
        MDC.put("correlationId", exchange.request.headers.getFirst(GatewayHeaders.CORRELATION_ID) ?: "")
        MDC.put("sessionId", sessionContext.sessionId)
        MDC.put("partner", sessionContext.partner)
    }

    private fun logSuccessfulValidation(sessionContext: SessionContext) {
        logger.info("Sessão validada com sucesso: sessionId={}, partner={}",
                   sessionContext.sessionId, sessionContext.partner)
    }

    private fun enrichRequestWithHeaders(
        exchange: ServerWebExchange, 
        sessionContext: SessionContext
    ): ServerWebExchange {
        val sessionHeaders = sessionContext.toHeaders()
        
        val enrichedRequest = exchange.request.mutate()
            .headers { headers ->
                filterHeadersAllowList(headers)
                
                sessionHeaders.forEach { (key, value) ->
                    headers.set(key, value)
                }
            }
            .build()
        
        return exchange.mutate().request(enrichedRequest).build()
    }

    private fun filterHeadersAllowList(headers: org.springframework.http.HttpHeaders) {
        val headersToRemove = headers.keys.filter { headerName ->
            !AllowedHeaders.isAllowed(headerName)
        }
        
        headersToRemove.forEach { headerName ->
            headers.remove(headerName)
        }
        
        if (headersToRemove.isNotEmpty()) {
            logger.debug("Headers removidos: {}", headersToRemove.joinToString(", "))
        }
    }

    private fun handleValidationError(exchange: ServerWebExchange, error: Throwable): Mono<Void> {
        val response = exchange.response
        val request = exchange.request
        val routeId = getRouteId(exchange)
        
        when (error) {
            is SessionValidationException -> {
                logger.warn("Falha na validação de sessão: route={}, path={}, error={}", 
                           routeId, request.path.value(), error.message)
                response.statusCode = HttpStatus.UNAUTHORIZED
            }
            
            is InsufficientPermissionsException -> {
                logger.warn("Acesso negado por permissões: route={}, path={}, required={}, user={}", 
                           routeId, request.path.value(), error.requiredPermissions, error.userPermissions)
                response.statusCode = HttpStatus.FORBIDDEN
            }
            
            else -> {
                logger.error("Erro inesperado na validação de sessão: route={}, path={}, error={}", 
                            routeId, request.path.value(), error.message, error)
                response.statusCode = HttpStatus.INTERNAL_SERVER_ERROR
            }
        }
        
        return response.setComplete()
    }

    private fun getRouteId(exchange: ServerWebExchange): String {
        val route = exchange.getAttribute<Route>(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)
        return route?.id ?: "unknown"
    }

    private data class RequiredHeaders(
        val authToken: String,
        val partner: String
    )
}