package com.banco.fidc.gateway.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.config.MeterFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer

@Configuration
class ObservabilityConfig {

    @Bean
    fun meterRegistryCustomizer(): MeterRegistryCustomizer<MeterRegistry> {
        return MeterRegistryCustomizer { registry ->
            registry.config()
                .meterFilter(MeterFilter.deny { id -> 
                    id.name.startsWith("jvm.gc.pause") || 
                    id.name.startsWith("jvm.memory.usage.after.gc") ||
                    id.name.startsWith("tomcat.")
                })
                .commonTags("application", "fidc-gateway")
        }
    }
}