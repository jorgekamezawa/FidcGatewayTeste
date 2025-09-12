package com.banco.fidc.gateway.service

import com.banco.fidc.gateway.exception.JwtTokenException
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Service
class JwtService {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun extractSessionId(accessToken: String): Mono<String> {
        return Mono.fromCallable {
            val cleanToken = removeTokenPrefix(accessToken)
            val claims = parseUnsecuredToken(cleanToken)
            
            extractClaimValue(claims, "sessionId")
                .also { sessionId ->
                    logger.debug("SessionId extraído do token: sessionId={}", sessionId)
                }
        }.subscribeOn(Schedulers.boundedElastic())
    }

    fun validateToken(accessToken: String, sessionSecret: String): Mono<Boolean> {
        return Mono.fromCallable {
            validateTokenSignature(accessToken, sessionSecret)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    private fun removeTokenPrefix(accessToken: String): String {
        return accessToken.removePrefix("Bearer ").trim()
    }

    private fun parseUnsecuredToken(token: String): Claims {
        return try {
            Jwts.parser()
                .build()
                .parseUnsecuredClaims(token)
                .payload
        } catch (e: JwtException) {
            logger.warn("Erro ao extrair claims do token: error={}", e.message)
            throw JwtTokenException("Token JWT inválido: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Erro inesperado ao processar token: error={}", e.message)
            throw JwtTokenException("Erro ao processar token: ${e.message}", e)
        }
    }

    private fun extractClaimValue(claims: Claims, claimName: String): String {
        return claims.get(claimName, String::class.java)
            ?: throw JwtTokenException("$claimName não encontrado no token")
    }

    private fun validateTokenSignature(accessToken: String, sessionSecret: String): Boolean {
        return try {
            val cleanToken = removeTokenPrefix(accessToken)
            val key = Keys.hmacShaKeyFor(sessionSecret.toByteArray())

            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(cleanToken)

            logger.debug("Token validado com sucesso")
            true

        } catch (e: JwtException) {
            logger.warn("Token inválido: error={}", e.message)
            false
        } catch (e: Exception) {
            logger.error("Erro inesperado na validação do token: error={}", e.message)
            false
        }
    }
}