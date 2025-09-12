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
                    .tag("method", exchange.request.method?.name() ?: "unknown")
                    .tag("status", statusCode)
                    .register(meterRegistry))
                
                meterRegistry.counter("gateway.request.total",
                    "path", normalizePath(path),
                    "method", exchange.request.method?.name() ?: "unknown",
                    "status", statusCode
                ).increment()
            }
            .doOnError { error ->
                timer.stop(Timer.builder("gateway.request.duration")
                    .tag("path", normalizePath(path))
                    .tag("method", exchange.request.method?.name() ?: "unknown")
                    .tag("status", "error")
                    .register(meterRegistry))
                
                meterRegistry.counter("gateway.request.errors",
                    "path", normalizePath(path),
                    "method", exchange.request.method?.name() ?: "unknown",
                    "error", error.javaClass.simpleName
                ).increment()
            }
    }

    private fun normalizePath(path: String): String {
        return when {
            path.startsWith("/api/simulation") -> "/api/simulation"
            path.startsWith("/api/contract") -> "/api/contract" 
            path.startsWith("/api/profile") -> "/api/profile"
            path.startsWith("/actuator") -> "/actuator"
            else -> "other"
        }
    }

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE - 1
}