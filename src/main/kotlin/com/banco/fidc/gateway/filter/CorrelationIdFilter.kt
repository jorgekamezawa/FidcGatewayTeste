package com.banco.fidc.gateway.filter

import org.slf4j.MDC
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.*

@Component
class CorrelationIdFilter : GlobalFilter, Ordered {

    companion object {
        const val CORRELATION_ID_HEADER = "X-Correlation-ID"
        const val CORRELATION_ID_ATTRIBUTE = "correlationId"
    }

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val request = exchange.request
        
        val correlationId = extractOrGenerateCorrelationId(request)
        
        val modifiedRequest = request.mutate()
            .header(CORRELATION_ID_HEADER, correlationId)
            .build()
        
        val modifiedExchange = exchange.mutate()
            .request(modifiedRequest)
            .build()
        
        modifiedExchange.attributes[CORRELATION_ID_ATTRIBUTE] = correlationId
        
        return chain.filter(modifiedExchange)
            .contextWrite { context ->
                context.put(CORRELATION_ID_ATTRIBUTE, correlationId)
            }
            .doOnSubscribe {
                MDC.put(CORRELATION_ID_ATTRIBUTE, correlationId)
            }
            .doFinally {
                MDC.remove(CORRELATION_ID_ATTRIBUTE)
            }
    }

    private fun extractOrGenerateCorrelationId(request: ServerHttpRequest): String {
        return request.headers.getFirst(CORRELATION_ID_HEADER) 
            ?: UUID.randomUUID().toString()
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE
}