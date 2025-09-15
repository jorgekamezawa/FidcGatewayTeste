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
            val jwtParts = token.split(".")
            
            if (jwtParts.size != 3) {
                throw JwtTokenException("Token JWT malformado: deve ter 3 partes separadas por ponto")
            }

            val payloadJson = String(java.util.Base64.getUrlDecoder().decode(jwtParts[1]))

            Jwts.claims()
                .add(parseJsonToClaims(payloadJson))
                .build()
                
        } catch (e: JwtTokenException) {
            throw e
        } catch (e: IllegalArgumentException) {
            logger.warn("Erro ao decodificar Base64 do token: error={}", e.message)
            throw JwtTokenException("Token JWT com encoding inválido: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Erro inesperado ao processar token: error={}", e.message)
            throw JwtTokenException("Erro ao processar token: ${e.message}", e)
        }
    }

    private fun parseJsonToClaims(payloadJson: String): Map<String, Any> {
        return try {
            // Parse manual simples do JSON (ou usar Jackson se disponível)
            val cleanJson = payloadJson.trim()
            if (!cleanJson.startsWith("{") || !cleanJson.endsWith("}")) {
                throw JwtTokenException("Payload JWT não é um JSON válido")
            }
            
            // Para simplicidade, usar regex para extrair sessionId
            // Em produção, seria melhor usar Jackson ObjectMapper
            val sessionIdRegex = """"sessionId"\s*:\s*"([^"]+)"""".toRegex()
            val match = sessionIdRegex.find(cleanJson)
            
            if (match != null) {
                mapOf("sessionId" to match.groupValues[1])
            } else {
                throw JwtTokenException("sessionId não encontrado no token")
            }
            
        } catch (e: JwtTokenException) {
            throw e
        } catch (e: Exception) {
            logger.error("Erro ao extrair sessionId do payload JSON: error={}", e.message)
            throw JwtTokenException("Payload JWT inválido: ${e.message}", e)
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