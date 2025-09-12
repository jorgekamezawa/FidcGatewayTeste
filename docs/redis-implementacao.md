# Redis no FIDC Gateway - Implementação e Arquitetura

## Visão Geral

O gateway utiliza **Redis reativo** para validação de sessões do usuário, consultando dados compartilhados com o `FidcAuth`. A implementação prioriza **performance**, **simplicidade** e **compatibilidade universal**.

## Decisões Arquiteturais

### 1. **Redis Reativo vs Blocking**

**Escolhido**: Redis Reativo (`ReactiveStringRedisTemplate`)
**Razão**: Gateway usa Spring Cloud Gateway (WebFlux) - requires non-blocking I/O

**Comparação**:
```kotlin
// Redis Reativo (Usado) ✅
redisTemplate.opsForValue().get("key"): Mono<String>  // Non-blocking
```

```kotlin
// Redis Blocking (Rejeitado) ❌
redisTemplate.opsForValue().get("key"): String        // Bloqueia Event Loop
```

### 2. **String/String vs JSON Automático**

**Escolhido**: `ReactiveStringRedisTemplate` (String/String)
**Razão**: 
- **Performance**: Parse manual só quando necessário
- **Flexibilidade**: Controle total sobre deserialização
- **Desacoplamento**: Independente do formato usado pelo `FidcAuth`
- **Simplicidade**: Menos overhead do Spring

**Comparação**:
```kotlin
// String/String (Usado) ✅
redisTemplate.opsForValue().get(key)                    // Retorna Mono<String>
    .flatMap { jsonString -> parseJsonManually(jsonString) }  // Parse condicional
```

```kotlin
// JSON Automático (Rejeitado) ❌  
redisTemplate.opsForValue().get(key)                    // Retorna Mono<SessionContext>
    // Spring faz parse sempre (overhead desnecessário)
```

### 3. **Standalone vs Cluster - Configuração Universal**

**Escolhido**: Sempre `RedisClusterConfiguration`
**Razão**: Funciona transparentemente com standalone e cluster real

**Configuração Final**:
```kotlin
// src/main/kotlin/com/banco/fidc/gateway/config/RedisConfig.kt:32-41
val redisConfig = RedisClusterConfiguration().apply {
    clusterNode(redisProperties.host, redisProperties.port)
}

redisConfig.apply {
    if (!redisProperties.password.isNullOrBlank()) {
        setPassword(redisProperties.password)
    }
    maxRedirects = 3  // Ignorado em standalone
}
```

## Como Funciona por Ambiente

### Redis Docker Local (Standalone)
```yaml
# application-local.yml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

**Comportamento**: Lettuce trata como cluster de 1 nó automaticamente.

### Redis AWS ElastiCache (Cluster)
```yaml
# application-prod.yml
spring:
  data:
    redis:
      host: redis-cluster.abc123.cache.amazonaws.com
      port: 6379
      password: ${REDIS_PASSWORD}
```

**Comportamento**: Lettuce descobre nós do cluster automaticamente.

## Transparência da Configuração

### Por que RedisClusterConfiguration Funciona com Standalone?

1. **Lettuce Driver** conecta no endpoint
2. **Executa** `CLUSTER NODES` para descobrir topologia
3. **Se falhar** → trata como standalone (1 nó)
4. **Se retornar nós** → trata como cluster real
5. **Operações** (`GET`/`SET`) funcionam igual em ambos

### Vantagens da Abordagem Universal

- **Zero configuração** por ambiente
- **Migração transparente** standalone ↔ cluster
- **Menos código** para manter
- **Menos chance de erro** de configuração

## Uso no Gateway

### SessionService - Consulta de Sessões

```kotlin
// src/main/kotlin/com/banco/fidc/gateway/service/SessionService.kt:34-52
fun getSession(partner: String, sessionId: String): Mono<SessionContext> {
    val redisKey = SessionContext.buildRedisKey(partner, sessionId)
    
    return redisTemplate.opsForValue()
        .get(redisKey)                                    // Mono<String>
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(Duration.ofSeconds(3))
        .transformDeferred(CircuitBreakerOperator.of(redisCircuitBreaker))  // Proteção
        .flatMap { jsonValue -> parseSessionJson(jsonValue, redisKey) }      // Parse manual
        .switchIfEmpty(Mono.error(SessionNotFoundException("Sessão não encontrada: $redisKey")))
}
```

### Chaves Redis Compartilhadas

**Formato**: `fidc:session:{partner}:{sessionId}`

**Exemplos**:
- `fidc:session:prevcom:uuid-da-sessao`
- `fidc:session:btgmais:another-session-id`

### Circuit Breaker Integrado

```kotlin
// Proteção contra falhas do Redis
.transformDeferred(CircuitBreakerOperator.of(redisCircuitBreaker))
```

**Configuração específica** para Redis:
- **70% falhas** → circuit abre
- **Fica aberto** por 15 segundos
- **Chamadas > 1s** = lentas

## Performance e Otimizações

### 1. **Non-blocking I/O**
```kotlin
.subscribeOn(Schedulers.boundedElastic())  // Thread pool para I/O
.timeout(Duration.ofSeconds(3))            // Timeout não-bloqueante
```

### 2. **Connection Pooling**
```kotlin
// src/main/kotlin/com/banco/fidc/gateway/config/RedisConfig.kt:43-53
val socketOptions = SocketOptions.builder()
    .connectTimeout(Duration.ofMillis(3000))
    .keepAlive(true)                       // Reutiliza conexões
    .build()

val clientOptions = ClientOptions.builder()
    .socketOptions(socketOptions)
    .autoReconnect(true)                   // Reconecta automaticamente
    .build()
```

### 3. **Parse Otimizado**
```kotlin
// Parse JSON só quando necessário, não automaticamente
private fun parseSessionJson(jsonValue: String, redisKey: String): Mono<SessionContext> {
    return Mono.fromCallable {
        objectMapper.readValue(jsonValue, SessionContext::class.java)
    }.subscribeOn(Schedulers.boundedElastic())
}
```

## Integração com FidcAuth

### Responsabilidades Separadas

**FidcAuth** (Producer):
- **Cria** sessões no Redis após autenticação
- **Define formato** JSON das sessões
- **Gerencia TTL** das sessões

**Gateway** (Consumer):
- **Consulta apenas** sessões existentes
- **Valida** integridade das sessões
- **Não modifica** dados de sessão

### Formato JSON Compartilhado

```json
{
  "sessionId": "uuid-da-sessao",
  "partner": "prevcom", 
  "userDocumentNumber": "12345678901",
  "userEmail": "joao.silva@email.com",
  "userName": "João Silva Santos",
  "fundId": "CRED001",
  "fundName": "Prevcom RS",
  "relationshipId": "REL001",
  "contractNumber": "378192372163682",
  "userPermissions": ["VIEW_PROFILE", "VIEW_STATEMENTS"],
  "sessionSecret": "jwt-secret-key",
  "expiresAt": "2024-01-15T18:30:00Z"
}
```

## Tratamento de Erros

### Circuit Breaker Redis
```json
{
  "status": 401,
  "code": "SESSION_SERVICE_UNAVAILABLE",
  "message": "Session validation service is temporarily unavailable"
}
```

### Sessão Não Encontrada
```json
{
  "status": 401,
  "code": "INVALID_SESSION", 
  "message": "Sessão não encontrada: fidc:session:prevcom:uuid"
}
```

### Parse JSON Inválido
```json
{
  "status": 500,
  "code": "INTERNAL_ERROR",
  "message": "Erro ao fazer parse da sessão"
}
```

## Monitoramento e Observabilidade

### Métricas Importantes
- **Latência** de consultas Redis
- **Taxa de hit/miss** de sessões
- **Circuit breaker** aberto/fechado
- **Erros de parse** JSON

### Logs Estruturados
```kotlin
// Apenas erros são logados (performance)
logger.warn("Erro ao buscar sessão no Redis: key={}, error={}", redisKey, error.message)
logger.debug("Sessão encontrada no Redis: key={}", redisKey)  // Debug apenas
```

## Evolução e Compatibilidade

### Cenários Suportados

1. **Redis standalone** → **Redis cluster**: Zero downtime
2. **Mudança de formato** JSON: Parse manual permite adaptação
3. **Múltiplos partners**: Chaves isoladas por partner
4. **Scaling horizontal**: Cluster transparente

### Anti-Patterns Evitados

- ❌ JSON serialização automática (overhead)
- ❌ Blocking I/O no gateway (performance)
- ❌ Configuração condicional por ambiente (complexidade)
- ❌ Estado no gateway (stateless é melhor)
- ❌ Escrita no Redis (responsabilidade do FidcAuth)

## Arquivos do Projeto

- **Configuração**: `src/main/kotlin/com/banco/fidc/gateway/config/RedisConfig.kt`
- **Serviço**: `src/main/kotlin/com/banco/fidc/gateway/service/SessionService.kt`  
- **Modelo**: `src/main/kotlin/com/banco/fidc/gateway/model/SessionContext.kt`
- **Circuit Breaker**: `src/main/kotlin/com/banco/fidc/gateway/config/CircuitBreakerConfig.kt`

## Resumo das Decisões

| Aspecto | Escolha | Alternativa Rejeitada | Razão |
|---------|---------|----------------------|-------|
| **I/O Model** | Reativo | Blocking | WebFlux requires non-blocking |
| **Serialização** | String manual | JSON automático | Performance + flexibilidade |
| **Configuração** | Sempre cluster | Standalone vs cluster | Transparência universal |
| **Responsabilidade** | Read-only | Read/write | Desacoplamento com FidcAuth |
| **Parse** | Sob demanda | Automático | Controle + performance |

**Gateway Redis: Reativo, simples, universal e performático.**