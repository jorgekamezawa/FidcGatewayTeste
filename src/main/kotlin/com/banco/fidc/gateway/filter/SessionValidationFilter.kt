package com.banco.fidc.gateway.filter

import com.banco.fidc.gateway.model.GatewayHeaders
import com.banco.fidc.gateway.model.Permission
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
import reactor.util.function.Tuple2
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
            val startTime = Instant.now()
            
            // Adicionar timestamp do request para métricas
            exchange.attributes[GatewayHeaders.REQUEST_START_TIME] = startTime
            
            validateSession(exchange, config)
                .flatMap { sessionContext ->
                    // Configurar MDC para logs
                    MDC.put("correlationId", exchange.request.headers.getFirst(GatewayHeaders.CORRELATION_ID) ?: "")
                    MDC.put("sessionId", sessionContext.sessionId)
                    MDC.put("partner", sessionContext.partner)
                    MDC.put("userEmail", sessionContext.userEmail)
                    
                    logger.info("Sessão validada com sucesso: sessionId={}, partner={}, userEmail={}", 
                               sessionContext.sessionId, sessionContext.partner, sessionContext.userEmail)
                    
                    // Enriquecer request com headers do contexto da sessão
                    val enrichedExchange = enrichRequestWithHeaders(exchange, sessionContext)
                    
                    // Marcar como processado pelo gateway
                    enrichedExchange.attributes[GatewayHeaders.GATEWAY_PROCESSED] = true
                    
                    // Continuar cadeia de filtros
                    chain.filter(enrichedExchange)
                        .doFinally { 
                            // Limpar MDC ao final
                            MDC.clear() 
                        }
                }
                .onErrorResume { error ->
                    handleValidationError(exchange, error)
                }
        }
    }

    private fun validateSession(exchange: ServerWebExchange, config: Config): Mono<SessionContext> {
        val request = exchange.request
        
        // 1. Extrair Authorization header
        val authHeader = request.headers.getFirst(GatewayHeaders.AUTHORIZATION)
        if (authHeader.isNullOrBlank()) {
            return Mono.error(SessionValidationException("Authorization header não fornecido"))
        }
        
        // 2. Extrair Partner header
        val partnerHeader = request.headers.getFirst(GatewayHeaders.PARTNER_HEADER)
        if (partnerHeader.isNullOrBlank()) {
            return Mono.error(SessionValidationException("Partner header não fornecido"))
        }
        
        // 3. Extrair sessionId e partner do JWT
        return Mono.zip(
            jwtService.extractSessionId(authHeader),
            jwtService.extractPartner(authHeader)
        ).flatMap { tuple: Tuple2<String, String> ->
            val sessionId = tuple.t1
            val jwtPartner = tuple.t2
            
            // 4. Validar se partner do header confere com partner do JWT
            if (!jwtPartner.equals(partnerHeader, ignoreCase = true)) {
                return@flatMap Mono.error<com.banco.fidc.gateway.model.SessionContext>(
                    SessionValidationException("Partner do header ($partnerHeader) não confere com partner do JWT ($jwtPartner)")
                )
            }
            
            // 5. Buscar sessão no Redis
            sessionService.getSession(partnerHeader, sessionId)
                .flatMap { sessionContext ->
                    // 6. Validar AccessToken com sessionSecret
                    jwtService.validateToken(authHeader, sessionContext.sessionSecret)
                        .flatMap { isValid ->
                            if (!isValid) {
                                Mono.error(SessionValidationException("AccessToken inválido"))
                            } else {
                                // 7. Validar relacionamento selecionado
                                if (!sessionContext.hasValidRelationship()) {
                                    Mono.error(SessionValidationException("Relacionamento não selecionado"))
                                } else {
                                    // 8. Validar permissões necessárias para a rota
                                    validateRoutePermissions(exchange, sessionContext, config)
                                        .then(Mono.just(sessionContext))
                                }
                            }
                        }
                }
        }
    }

    /**
     * Valida se o usuário possui as permissões necessárias para acessar a rota
     */
    private fun validateRoutePermissions(
        exchange: ServerWebExchange, 
        sessionContext: com.banco.fidc.gateway.model.SessionContext, 
        config: Config
    ): Mono<Void> {
        
        // Se não há permissões requeridas, libera acesso
        if (config.requiredPermissions.isEmpty()) {
            return Mono.empty()
        }
        
        // Validar se todas as permissões requeridas são válidas
        if (!Permission.areValidCodes(config.requiredPermissions)) {
            logger.error("Permissões inválidas configuradas na rota: {}", config.requiredPermissions)
            return Mono.error(SessionValidationException("Configuração de permissões inválida na rota"))
        }
        
        // Verificar se o usuário possui todas as permissões necessárias
        val hasPermissions = sessionContext.hasPermissions(config.requiredPermissions)
        
        if (!hasPermissions) {
            val routeId = getRouteId(exchange)
            logger.warn("Acesso negado por falta de permissões: route={}, required={}, user={}", 
                       routeId, config.requiredPermissions, sessionContext.userPermissions)
            
            return Mono.error(
                InsufficientPermissionsException(
                    "Usuário não possui permissões necessárias: ${config.requiredPermissions}",
                    config.requiredPermissions,
                    sessionContext.userPermissions
                )
            )
        }
        
        return Mono.empty()
    }

    /**
     * Enriquece o request com headers de contexto da sessão
     */
    private fun enrichRequestWithHeaders(
        exchange: ServerWebExchange, 
        sessionContext: com.banco.fidc.gateway.model.SessionContext
    ): ServerWebExchange {
        
        val sessionHeaders = sessionContext.toHeaders()
        
        // Criar novo request com headers adicionais
        val enrichedRequest = exchange.request.mutate()
            .headers { headers ->
                sessionHeaders.forEach { (key, value) ->
                    headers.set(key, value)
                }
                
                // Adicionar correlation ID se não existir
                if (!headers.containsKey(GatewayHeaders.CORRELATION_ID)) {
                    headers.set(GatewayHeaders.CORRELATION_ID, java.util.UUID.randomUUID().toString())
                }
            }
            .build()
        
        return exchange.mutate().request(enrichedRequest).build()
    }

    /**
     * Tratamento de erros de validação
     */
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

    /**
     * Obtém o ID da rota atual
     */
    private fun getRouteId(exchange: ServerWebExchange): String {
        val route = exchange.getAttribute<Route>(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)
        return route?.id ?: "unknown"
    }
}

/**
 * Exceções específicas de validação de sessão
 */
class SessionValidationException(message: String) : RuntimeException(message)

class InsufficientPermissionsException(
    message: String,
    val requiredPermissions: List<String>,
    val userPermissions: List<String>
) : RuntimeException(message)