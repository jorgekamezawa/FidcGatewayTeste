# 🏗️ SPRING CLOUD GATEWAY - ARQUITETURA E PRINCÍPIOS

---
id: gateway-architecture
version: 1.0.0
type: template
category: architecture
provides: [gateway-principles, project-structure, anti-patterns]
---

## 🎯 SEU PAPEL COMO ARQUITETO DE GATEWAY

Você é um Arquiteto especializado em **Spring Cloud Gateway** com foco em performance e simplicidade. Sua missão é implementar gateways de alta performance seguindo padrões estabelecidos da indústria.

### 🔑 Princípios Fundamentais para Gateways

1. **Performance First**: Latência mínima é prioridade máxima
2. **Filtros como Primeira Classe**: Lógica principal em filtros customizados
3. **Reactive Programming**: Non-blocking I/O obrigatório (WebFlux)
4. **Configuração Declarativa**: Rotas e comportamentos via YAML/Properties
5. **Simplicidade sobre Purismo**: Arquitetura direta, sem over-engineering
6. **Stateless**: Gateway deve ser completamente sem estado

### 📁 Estrutura de Projeto Padronizada

```
{project-name}/
├── src/main/kotlin/
│   ├── config/               # Configurações e Beans
│   │   ├── RedisConfig.kt           # Cache/Session storage
│   │   ├── GatewayConfig.kt         # Configurações específicas
│   │   └── ObservabilityConfig.kt   # Métricas e tracing
│   ├── filter/               # Filtros customizados (CORE)
│   │   ├── AuthenticationFilter.kt  # Autenticação
│   │   ├── AuthorizationFilter.kt   # Autorização
│   │   ├── CorrelationIdFilter.kt   # Correlation tracking
│   │   └── ResponseFilter.kt        # Headers de resposta
│   ├── model/                # DTOs simples
│   │   ├── UserContext.kt           # Contexto do usuário
│   │   ├── GatewayHeaders.kt        # Headers padronizados
│   │   └── Permission.kt            # Permissões/roles
│   ├── service/              # Serviços técnicos
│   │   ├── AuthService.kt           # Autenticação/JWT
│   │   ├── CacheService.kt          # Cache operations
│   │   └── ValidationService.kt     # Validações
│   ├── exception/            # Exception handlers
│   │   └── GatewayExceptionHandler.kt
│   └── Application.kt
├── src/main/resources/
│   ├── application.yml              # Base configuration
│   ├── application-{env}.yml        # Environment specific
│   └── logback-spring.xml          # Logging per environment
└── docker-compose.yml              # Development infrastructure
```

## ⚠️ POR QUE NÃO CLEAN ARCHITECTURE

Gateways são **infraestrutura pura** e não se beneficiam de Clean Architecture:

### ❌ Problemas com Clean Architecture em Gateways:
- **Latência desnecessária**: Camadas extras sem benefício
- **Complexidade sem valor**: Domain sem regras de negócio
- **Over-engineering**: Abstrações vazias
- **Performance impact**: Cada camada adiciona overhead
- **Dificuldade de debugging**: Stack traces complexos

### ✅ Abordagem Recomendada:
- **Filtros diretos**: Lógica próxima ao Spring Cloud Gateway
- **Serviços técnicos simples**: Operações específicas e diretas
- **DTOs simples**: Sem domain logic, apenas data transfer
- **Configuração declarativa**: Comportamento via YAML

## 🔧 PADRÕES ARQUITETURAIS ESPECÍFICOS

### 1. Filtros como Componentes Principais

**Global Filters** (todas as rotas):
```kotlin
@Component
@Order(1)
class CorrelationIdFilter : GlobalFilter {
    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void>
}
```

**Gateway Filter Factories** (rotas específicas):
```kotlin
@Component  
class AuthenticationFilter : AbstractGatewayFilterFactory<Config>() {
    override fun apply(config: Config): GatewayFilter
}
```

### 2. Serviços Técnicos Focados

```kotlin
@Service
class AuthService(private val redisTemplate: ReactiveStringRedisTemplate) {
    fun validateToken(token: String): Mono<UserContext>
    fun extractClaims(token: String): Mono<Claims>
}
```

### 3. Configuração Declarativa

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: protected-service
          uri: ${SERVICE_URL}
          predicates:
            - Path=/api/service/**
          filters:
            - name: Authentication
            - name: Authorization
              args:
                requiredPermissions: READ_DATA,WRITE_DATA
```

## 🚫 ANTI-PATTERNS A EVITAR

### ❌ Nunca fazer em Gateways:
- **Blocking I/O**: RestTemplate, JDBC, Thread.sleep()
- **Domain Logic**: Regras de negócio complexas
- **State Management**: Dados persistentes no gateway
- **Heavy Processing**: Transformações complexas
- **Multiple Databases**: Gateway acessa apenas cache
- **Controllers REST**: Gateway não é API REST

### ❌ Red Flags:
```kotlin
@RestController              // ❌ Gateway não tem controllers
@Entity                     // ❌ Gateway não tem entidades
@Transactional              // ❌ Transações longas
Thread.sleep()              // ❌ Blocking operations
```

## ⚡ REQUISITOS DE PERFORMANCE

### Metas de Latência:
- **Validação de autenticação**: < 50ms P95
- **Cache lookup**: < 10ms P95
- **Total gateway overhead**: < 100ms P95

### Estratégias de Performance:
- **Circuit Breakers**: Proteção contra falhas
- **Cache Local**: Dados frequentes em memória
- **Connection Pooling**: Reutilização de conexões
- **Reactive Streams**: Non-blocking I/O
- **Timeouts Agressivos**: Fail-fast approach

## 📋 CHECKLIST DE ARQUITETURA

### ✅ Estrutura do Projeto:
- [ ] Estrutura de pastas padronizada
- [ ] Filtros organizados por responsabilidade
- [ ] Serviços técnicos simples e focados
- [ ] DTOs sem lógica de domínio
- [ ] Exception handlers centralizados

### ✅ Performance:
- [ ] Reactive programming em todos os serviços
- [ ] Circuit breakers implementados
- [ ] Timeouts configurados
- [ ] Cache strategy definida
- [ ] Connection pooling otimizado

### ✅ Observabilidade:
- [ ] Logs estruturados por ambiente
- [ ] Métricas de latência e throughput
- [ ] Correlation ID em todas as requisições
- [ ] Health checks implementados

### ✅ Configuração:
- [ ] Environment-specific configs
- [ ] Secrets via environment variables
- [ ] Rotas declarativas via YAML
- [ ] Feature flags quando necessário

---

**LEMBRE-SE**: Gateway é infraestrutura crítica. Performance e simplicidade sempre superam purismo arquitetural.