# 🚀 SPRING CLOUD GATEWAY - PROJECT SETUP

---
id: gateway-project-setup
version: 1.0.0
type: template
category: setup
requires: [gateway-architecture]
provides: [project-structure, dependencies, initial-config]
---

## 🎯 SETUP INICIAL DO PROJETO

Este template guia a criação inicial de um projeto Spring Cloud Gateway seguindo padrões estabelecidos de performance e simplicidade.

## 📦 DEPENDÊNCIAS PADRONIZADAS

### build.gradle.kts Template:

```kotlin
plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.3.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "{organization}.{domain}"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.6")
    }
}

dependencies {
    // ========== CORE GATEWAY ==========
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    
    // ========== CACHE/SESSION ==========
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    
    // ========== AUTHENTICATION ==========
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")
    
    // ========== RESILIENCE ==========
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.1.0")
    implementation("io.github.resilience4j:resilience4j-reactor:2.1.0")
    
    // ========== OBSERVABILITY ==========
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    
    // ========== KOTLIN ==========
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    
    // ========== TESTING ==========
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

## 📁 ESTRUTURA INICIAL DE ARQUIVOS

### 1. Application Principal:
```kotlin
// src/main/kotlin/{organization}/{domain}/gateway/Application.kt
package {organization}.{domain}.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class {ProjectName}GatewayApplication

fun main(args: Array<String>) {
    runApplication<{ProjectName}GatewayApplication>(*args)
}
```

### 2. Configuração Base (application.yml):
```yaml
spring:
  application:
    name: {project-name}-gateway

  cloud:
    gateway:
      routes:
        - id: health-check
          uri: http://localhost:8080
          predicates:
            - Path=/actuator/health
      # Rotas específicas serão configuradas aqui
      # Padrão: ${microservices.base-url}/api/service-path

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,metrics
  endpoint:
    health:
      show-details: when-authorized

resilience4j:
  circuitbreaker:
    configs:
      default:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 10

# Logging configurado via logback-spring.xml
```

### 3. Configuração de Logs (logback-spring.xml):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    
    <!-- PROFILE LOCAL: Logs coloridos -->
    <springProfile name="local">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %highlight(%-5level) [%thread] [%X{correlationId:-}] %cyan(%logger{36}) - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
    
    <!-- PROFILES PROD: JSON estruturado -->
    <springProfile name="dev,uat,prd">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
                <providers>
                    <timestamp><timeZone>UTC</timeZone></timestamp>
                    <version/>
                    <logLevel/>
                    <message/>
                    <mdc/>
                    <arguments/>
                    <stackTrace/>
                    <pattern>
                        <pattern>
                            {
                                "application": "{project-name}-gateway",
                                "thread": "%thread",
                                "logger": "%logger{36}",
                                "correlationId": "%X{correlationId:-}"
                            }
                        </pattern>
                    </pattern>
                </providers>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
    
    <!-- Loggers específicos -->
    <logger name="org.springframework.cloud.gateway" level="INFO"/>
    <logger name="{organization}.{domain}.gateway" level="INFO"/>
    <logger name="io.lettuce.core" level="WARN"/>
    <logger name="reactor.netty" level="WARN"/>
    
    <!-- Reduzir verbosidade em produção -->
    <springProfile name="prd">
        <logger name="org.springframework" level="WARN"/>
        <logger name="org.apache" level="WARN"/>
        <logger name="com.zaxxer.hikari" level="WARN"/>
    </springProfile>
</configuration>
```

## 🔧 CONFIGURAÇÕES POR AMBIENTE

### Template application-{env}.yml:

#### Local Development:
```yaml
# application-local.yml
spring:
  redis:
    host: localhost
    port: 6379
    password: ""
    timeout: 3000ms
    lettuce:
      pool:
        max-active: 10
        max-idle: 5
        min-idle: 2
  
  cloud:
    compatibility-verifier:
      enabled: false  # Para desenvolvimento

microservices:
  base-url: http://localhost

management:
  endpoint:
    health:
      show-details: always

logging:
  level:
    org.springframework.cloud.gateway: DEBUG
    {organization}.{domain}.gateway: DEBUG
```

#### Production Template:
```yaml
# application-{env}.yml (dev/uat/prd)
spring:
  redis:
    host: ${REDIS_HOST:{project-name}-redis-{env}.cache.amazonaws.com}
    port: 6379
    password: ${REDIS_PASSWORD}
    timeout: ${REDIS_TIMEOUT:2500ms}
    lettuce:
      pool:
        max-active: ${REDIS_POOL_MAX_ACTIVE:30}
        max-idle: ${REDIS_POOL_MAX_IDLE:15}
        min-idle: ${REDIS_POOL_MIN_IDLE:5}

microservices:
  base-url: ${MICROSERVICES_BASE_URL:https://microservices-{env}.{organization}.com}

management:
  endpoint:
    health:
      show-details: never

logging:
  level:
    org.springframework.cloud.gateway: WARN
    {organization}.{domain}.gateway: INFO
    root: WARN
```

## 🧪 CONFIGURAÇÃO DE TESTES

### application-test.yml:
```yaml
spring:
  application:
    name: {project-name}-gateway

  # Desabilitar Redis para testes
  data:
    redis:
      enabled: false

server:
  port: 0  # Random port

management:
  endpoints:
    web:
      exposure:
        include: health

logging:
  level:
    {organization}.{domain}.gateway: DEBUG
```

### Teste Base:
```kotlin
// src/test/kotlin/{organization}/{domain}/gateway/ApplicationTests.kt
package {organization}.{domain}.gateway

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class {ProjectName}GatewayApplicationTests {

    @Test
    fun contextLoads() {
        // Context startup test
    }
}
```

## 📋 CHECKLIST DE SETUP

### ✅ Dependências:
- [ ] Spring Cloud Gateway + WebFlux
- [ ] Redis Reactive para cache/sessões
- [ ] JWT para autenticação
- [ ] Resilience4j para circuit breakers
- [ ] Actuator + Prometheus para métricas
- [ ] Logback encoder para JSON logs

### ✅ Estrutura:
- [ ] Estrutura de pastas criada
- [ ] Application principal configurada
- [ ] Configurações base (application.yml)
- [ ] Logs estruturados (logback-spring.xml)
- [ ] Configurações por ambiente
- [ ] Testes básicos funcionando

### ✅ Build:
- [ ] Projeto compila sem erros
- [ ] Testes passam
- [ ] JAR gerado corretamente

### ✅ Próximos Passos:
- [ ] Implementar configurações específicas (Redis, etc.)
- [ ] Criar models e DTOs
- [ ] Implementar serviços técnicos
- [ ] Desenvolver filtros customizados
- [ ] Configurar Docker para desenvolvimento

---

**PRÓXIMO**: Use `gateway-configuration.md` para configurações específicas por ambiente.