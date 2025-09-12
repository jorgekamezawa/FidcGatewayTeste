# Circuit Breaker - Como Funciona no FIDC Gateway

## O que é Circuit Breaker?

Circuit Breaker é um **padrão de proteção** que evita que falhas em dependências externas (Redis, APIs downstream) causem cascata de erros e timeouts no gateway.

Funciona como um **disjuntor elétrico** - quando detecta muitas falhas, "abre" e bloqueia novas tentativas, protegendo o sistema.

## Estados do Circuit Breaker

```
CLOSED ──falhas──> OPEN ──tempo──> HALF_OPEN
  ↑                                    │
  └──────────── sucesso ←──────────────┘
```

### 1. CLOSED (Fechado - Normal)
- **Permite todas as chamadas**
- Monitora falhas e latência
- Estado normal de operação

### 2. OPEN (Aberto - Proteção Ativa)
- **Bloqueia todas as chamadas**
- Retorna erro imediatamente
- Evita sobrecarregar serviço com problemas

### 3. HALF_OPEN (Meio Aberto - Teste)
- **Permite poucas chamadas de teste**
- Verifica se o serviço voltou ao normal
- Se sucesso → volta para CLOSED
- Se falha → volta para OPEN

## Configurações no Projeto

### Redis Circuit Breaker
```yaml
failureRateThreshold: 70%        # 70% de falhas → abre
waitDurationInOpenState: 15s     # Fica aberto por 15 segundos
slidingWindowSize: 20           # Analisa últimas 20 chamadas
minimumNumberOfCalls: 10        # Mínimo 10 chamadas para análise
slowCallDurationThreshold: 1s   # Chamadas > 1s = lentas
```

**Usado em**: Consultas de sessão no Redis (`SessionService.kt`)

### Downstream Circuit Breaker
```yaml
failureRateThreshold: 60%        # 60% de falhas → abre  
waitDurationInOpenState: 45s     # Fica aberto por 45 segundos
slidingWindowSize: 15           # Analisa últimas 15 chamadas
minimumNumberOfCalls: 8         # Mínimo 8 chamadas para análise
slowCallDurationThreshold: 5s   # Chamadas > 5s = lentas
```

**Usado em**: Chamadas para APIs downstream (fidc-simulation, fidc-contract, etc.)

## Como Funciona o CircuitBreakerRegistry

O `CircuitBreakerRegistry` é um **factory/cache** que gerencia os circuit breakers:

```kotlin
// 1. Registry criado com configs nomeadas
CircuitBreakerRegistry.of(mapOf(
    "default" to defaultConfig,     // Config padrão/fallback
    "redis" to redisConfig,         // Config específica do Redis
    "downstream" to downstreamConfig // Config específica das APIs
))

// 2. "Pedir" um circuit breaker
registry.circuitBreaker("redis")    // Usa config "redis"
registry.circuitBreaker("unknown")  // Usa config "default" (fallback)
```

### Padrões de Uso

**@Bean Explícito** - Para circuit breakers específicos que serão injetados:
```kotlin
@Bean
fun redisCircuitBreaker(registry: CircuitBreakerRegistry): CircuitBreaker {
    return registry.circuitBreaker("redis", "redis")
}
```

**Registry Direto** - Para circuit breakers dinâmicos:
```kotlin
@Autowired private val registry: CircuitBreakerRegistry
val cb = registry.circuitBreaker("service-abc") // Usa config "default"
```

## Exemplo Prático: SessionService

```kotlin
class SessionService(
    private val redisCircuitBreaker: CircuitBreaker // Injetado via @Bean
) {
    fun getSession(partner: String, sessionId: String): Mono<SessionContext> {
        return redisTemplate.opsForValue().get(redisKey)
            .transformDeferred(CircuitBreakerOperator.of(redisCircuitBreaker))
            // ↑ Aplica circuit breaker na operação Redis
    }
}
```

**Cenário de Falha**:
1. Redis com problemas → 70% das chamadas falham
2. Circuit breaker "abre" → próximas chamadas rejeitadas imediatamente
3. Evita timeouts longos → gateway responde rápido com erro
4. Após 15s → permite algumas chamadas de teste
5. Se Redis voltou → circuit "fecha" e volta ao normal

## Tratamento de Erros por Circuit Breaker

### Antes da Melhoria
Todos os circuit breakers retornavam erro genérico:
```json
{
  "status": 500,
  "code": "INTERNAL_ERROR",
  "message": "Internal server error"
}
```

### Após Melhoria (`GatewayExceptionHandler`)
**Redis Circuit Breaker aberto**:
```json
{
  "status": 401,
  "code": "SESSION_SERVICE_UNAVAILABLE", 
  "message": "Session validation service is temporarily unavailable"
}
```

**Downstream Circuit Breaker aberto**:
```json
{
  "status": 503,
  "code": "SERVICE_TEMPORARILY_UNAVAILABLE",
  "message": "Service is temporarily unavailable, please try again later"  
}
```

## Como o GatewayExceptionHandler Distingue a Origem

```kotlin
when (ex) {
    is CallNotPermittedException -> {
        when (ex.causingCircuitBreakerName) {
            "redis" -> return401WithSessionError()
            "downstream" -> return503WithServiceError()
            else -> returnGenericCircuitBreakerError()
        }
    }
}
```

## Benefícios no Gateway

### 1. **Proteção contra Cascata de Falhas**
- Redis com problema → não trava todo o gateway
- API downstream lenta → não afeta outras APIs

### 2. **Resposta Rápida ao Usuário**
- Sem circuit breaker: timeout de 30s
- Com circuit breaker: erro em milissegundos

### 3. **Recovery Automático**
- Detecta quando dependência volta ao normal
- Retoma operação automaticamente

### 4. **Observabilidade**
- Logs específicos por circuit breaker
- Métricas de falhas e recuperação

## Arquivos do Projeto

- **Configuração**: `src/main/kotlin/com/banco/fidc/gateway/config/CircuitBreakerConfig.kt`
- **Uso no Redis**: `src/main/kotlin/com/banco/fidc/gateway/service/SessionService.kt:43`  
- **Tratamento de Erros**: `src/main/kotlin/com/banco/fidc/gateway/exception/GatewayExceptionHandler.kt:30`

## Resumo

Circuit Breaker **protege o gateway** de dependências instáveis, **evita timeouts** em cascata e mantém o sistema **responsivo** mesmo quando Redis ou APIs downstream têm problemas.

É uma camada essencial de **resiliência** em arquiteturas de microserviços e gateways.