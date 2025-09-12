package com.banco.fidc.gateway.filter

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class MetricsFilter(
    private val meterRegistry: MeterRegistry
) : GlobalFilter, Ordered {

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val timer = Timer.start(meterRegistry)
        val path = exchange.request.path.pathWithinApplication().value()
        
        return chain.filter(exchange)
            .doOnSuccess {
                val statusCode = exchange.response.statusCode?.value()?.toString() ?: "unknown"
                timer.stop(Timer.builder("gateway.request.duration")
                    .tag("path", normalizePath(path))
                    .tag("method", exchange.request.method.name())
                    .tag("status", statusCode)
                    .register(meterRegistry))
                
                meterRegistry.counter("gateway.request.total",
                    "path", normalizePath(path),
                    "method", exchange.request.method.name(),
                    "status", statusCode
                ).increment()
            }
            .doOnError { error ->
                timer.stop(Timer.builder("gateway.request.duration")
                    .tag("path", normalizePath(path))
                    .tag("method", exchange.request.method.name())
                    .tag("status", "error")
                    .register(meterRegistry))
                
                meterRegistry.counter("gateway.request.errors",
                    "path", normalizePath(path),
                    "method", exchange.request.method.name(),
                    "error", error.javaClass.simpleName
                ).increment()
            }
    }

    //
    // Normalização básica por prefixo - agrupa todas rotas de um microserviço em uma métrica.
    // Trade-off: Performance alta, mas perde granularidade de operações específicas.
    // Evita explosão de cardinalidade mas remove informações sobre diferentes endpoints.
    //
//    private fun normalizePath(path: String): String {
//        return when {
//            path.startsWith("/api/simulation") -> "/api/simulation"
//            path.startsWith("/api/contract") -> "/api/contract"
//            path.startsWith("/api/profile") -> "/api/profile"
//            path.startsWith("/actuator") -> "/actuator"
//            else -> "other"
//        }
//    }

    //
    // Normalização inteligente com regex - preserva semântica de operações substituindo apenas IDs.
    // Trade-off: Mais processamento, mas mantém granularidade útil para monitoring.
    // Exemplo: /api/simulation/123/validate -> /api/simulation/*/validate
    //
    private fun normalizePath(path: String): String {
        return when {
            // Simulation patterns
            path.matches(Regex("/api/simulation/\\d+/validate/?.*")) -> "/api/simulation/*/validate"
            path.matches(Regex("/api/simulation/\\d+/form/\\d+/?.*")) -> "/api/simulation/*/form/*"
            path.matches(Regex("/api/simulation/\\d+/results/?.*")) -> "/api/simulation/*/results"
            path.matches(Regex("/api/simulation/\\d+/?$")) -> "/api/simulation/*"
            path.startsWith("/api/simulation") -> "/api/simulation/other"
            
            // Contract patterns  
            path.matches(Regex("/api/contract/\\d+/approve/?.*")) -> "/api/contract/*/approve"
            path.matches(Regex("/api/contract/\\d+/documents/?.*")) -> "/api/contract/*/documents"
            path.matches(Regex("/api/contract/\\d+/?$")) -> "/api/contract/*"
            path.startsWith("/api/contract") -> "/api/contract/other"
            
            // Profile patterns
            path.matches(Regex("/api/profile/\\d+/documents/?.*")) -> "/api/profile/*/documents"
            path.matches(Regex("/api/profile/\\d+/settings/?.*")) -> "/api/profile/*/settings"
            path.matches(Regex("/api/profile/\\d+/?$")) -> "/api/profile/*"
            path.startsWith("/api/profile") -> "/api/profile/other"
            
            path.startsWith("/actuator") -> "/actuator"
            else -> "other"
        }
    }

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE - 1
}