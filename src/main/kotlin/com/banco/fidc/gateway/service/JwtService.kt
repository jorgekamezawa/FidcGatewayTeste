package com.banco.fidc.gateway.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Service
class JwtService {
    
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Extrai o sessionId do AccessToken JWT
     * O token contém claims como sessionId, partner, etc.
     */
    fun extractSessionId(accessToken: String): Mono<String> {
        return Mono.fromCallable {
            try {
                // Remove prefixo "Bearer " se presente
                val token = accessToken.removePrefix("Bearer ").trim()
                
                // Parse do JWT sem validação de assinatura (será validada depois com sessionSecret)
                val jwt = Jwts.parser()
                    .build()
                    .parseClaimsJwt(token)
                
                val claims = jwt.body
                val sessionId = claims.get("sessionId", String::class.java)
                    ?: throw JwtTokenException("SessionId não encontrado no token")
                
                logger.debug("SessionId extraído do token: sessionId={}", sessionId)
                sessionId
                
            } catch (e: JwtException) {
                logger.warn("Erro ao extrair sessionId do token: error={}", e.message)
                throw JwtTokenException("Token JWT inválido: ${e.message}", e)
            } catch (e: Exception) {
                logger.error("Erro inesperado ao processar token: error={}", e.message)
                throw JwtTokenException("Erro ao processar token: ${e.message}", e)
            }
        }.subscribeOn(Schedulers.boundedElastic())
    }

    /**
     * Extrai o partner do AccessToken JWT
     */
    fun extractPartner(accessToken: String): Mono<String> {
        return Mono.fromCallable {
            try {
                val token = accessToken.removePrefix("Bearer ").trim()
                
                val jwt = Jwts.parser()
                    .build()
                    .parseClaimsJwt(token)
                
                val claims = jwt.body
                val partner = claims.get("partner", String::class.java)
                    ?: throw JwtTokenException("Partner não encontrado no token")
                
                logger.debug("Partner extraído do token: partner={}", partner)
                partner
                
            } catch (e: JwtException) {
                logger.warn("Erro ao extrair partner do token: error={}", e.message)
                throw JwtTokenException("Token JWT inválido: ${e.message}", e)
            }
        }.subscribeOn(Schedulers.boundedElastic())
    }

    /**
     * Valida AccessToken usando o sessionSecret específico da sessão
     * Esta é a validação final de segurança do token
     */
    fun validateToken(accessToken: String, sessionSecret: String): Mono<Boolean> {
        return Mono.fromCallable {
            try {
                val token = accessToken.removePrefix("Bearer ").trim()
                
                // Validação com sessionSecret específico
                Jwts.parser()
                    .verifyWith(javax.crypto.spec.SecretKeySpec(sessionSecret.toByteArray(), "HmacSHA256"))
                    .build()
                    .parseSignedClaims(token)
                
                logger.debug("Token validado com sucesso")
                true
                
            } catch (e: JwtException) {
                logger.warn("Token inválido: error={}", e.message)
                false
            } catch (e: Exception) {
                logger.error("Erro inesperado na validação do token: error={}", e.message)
                false
            }
        }.subscribeOn(Schedulers.boundedElastic())
    }

    /**
     * Extrai todas as claims do token JWT
     * Útil para debugging e logs detalhados
     */
    fun extractClaims(accessToken: String): Mono<Claims> {
        return Mono.fromCallable {
            try {
                val token = accessToken.removePrefix("Bearer ").trim()
                
                Jwts.parser()
                    .build()
                    .parseClaimsJwt(token)
                    .payload
                    
            } catch (e: JwtException) {
                throw JwtTokenException("Erro ao extrair claims do token: ${e.message}", e)
            }
        }.subscribeOn(Schedulers.boundedElastic())
    }

    /**
     * Verifica se o token está expirado
     */
    fun isTokenExpired(accessToken: String): Mono<Boolean> {
        return extractClaims(accessToken)
            .map { claims ->
                val expiration = claims.expiration
                expiration?.before(java.util.Date()) ?: false
            }
            .onErrorReturn(true) // Se não conseguir extrair claims, considera expirado
    }
}

/**
 * Exceção específica para erros de JWT
 */
class JwtTokenException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)