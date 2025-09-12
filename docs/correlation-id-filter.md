# CorrelationIdFilter - Rastreamento de Requisições

## Visão Geral

O `CorrelationIdFilter` é um filtro global do Spring Cloud Gateway responsável por garantir que cada requisição tenha um ID único de rastreamento (correlation ID) propagado em todos os contextos da aplicação.

**Localização**: `src/main/kotlin/com/banco/fidc/gateway/filter/CorrelationIdFilter.kt:14`

## Como Funciona

### Execução Automática
- **Tipo**: `GlobalFilter` com `@Component` - executa automaticamente em todas as requisições
- **Prioridade**: `Ordered.HIGHEST_PRECEDENCE` - roda antes de todos os outros filtros
- **Header**: `X-Correlation-ID`

### Fluxo de Processamento

1. **Extração/Geração**: Verifica se já existe o header `X-Correlation-ID`, senão gera um UUID
2. **Propagação**: Injeta o correlation ID em múltiplos contextos
3. **Limpeza**: Remove do MDC após processamento

## Propagação em 4 Contextos

### 1. Header HTTP - Para Microserviços Downstream

```kotlin
.header(CORRELATION_ID_HEADER, correlationId)  // linha 27
```

**Finalidade**: Microserviços que o gateway chama recebem o header automaticamente

**Uso prático**:
- Microserviços podem logar com o mesmo ID
- Repassar para outras APIs chamadas
- Debugar requests específicos end-to-end
- Correlacionar logs distribuídos

### 2. Exchange Attributes - Para Outros Filtros

```kotlin
modifiedExchange.attributes[CORRELATION_ID_ATTRIBUTE] = correlationId  // linha 34
```

**Finalidade**: Compartilhar o ID entre filtros do gateway

**Uso prático**:
```kotlin
// Em outros filtros como SessionValidationFilter
val correlationId = exchange.attributes[CORRELATION_ID_ATTRIBUTE] as String
log.info("Validating session for correlation ID: $correlationId")
```

### 3. Reactive Context - Para Código Reativo

```kotlin
.contextWrite { context ->
    context.put(CORRELATION_ID_ATTRIBUTE, correlationId)  // linha 38
}
```

**Finalidade**: Disponibilizar em operações assíncronas Mono/Flux

**Uso prático**:
```kotlin
return sessionService.validateSession()
    .doOnNext { session ->
        // Acessa correlation ID do context reativo
        val correlationId = Mono.deferContextual { ctx -> 
            Mono.just(ctx.get(CORRELATION_ID_ATTRIBUTE))
        }
    }
```

### 4. MDC (Mapped Diagnostic Context) - Para Logs

```kotlin
MDC.put(CORRELATION_ID_ATTRIBUTE, correlationId)    // linha 41
MDC.remove(CORRELATION_ID_ATTRIBUTE)               // linha 44
```

**Finalidade**: Logs automáticos do Spring aparecem com o correlation ID

**Uso prático**:
- Todos os logs da thread atual incluem automaticamente o ID
- Facilita troubleshooting e debugging
- Permite correlacionar logs de diferentes componentes

## Configuração de Logs

Para visualizar o correlation ID nos logs, configure o pattern no `logback-spring.xml`:

```xml
<pattern>%d{HH:mm:ss.SSS} [%X{correlationId}] %-5level %logger{36} - %msg%n</pattern>
```

## Benefícios

### Rastreabilidade End-to-End
- Acompanhar uma requisição específica desde o gateway até todos os microserviços
- Correlacionar erros distribuídos
- Debugging eficiente em ambiente distribuído

### Observabilidade
- Métricas por correlation ID
- APM tools podem agrupar traces relacionados
- Troubleshooting simplificado

### Integração Transparente
- Não requer modificação nos microserviços existentes
- Funciona automaticamente com qualquer stack que leia headers HTTP
- Compatible com padrões de observabilidade da indústria

## Ordem de Execução

```
1. CorrelationIdFilter (HIGHEST_PRECEDENCE)
   ├── Gera/extrai correlation ID
   ├── Propaga nos 4 contextos
   └── Continue para próximo filtro
2. SessionValidationFilter
   ├── Acessa correlation ID via exchange.attributes
   └── Logs automáticos incluem o ID
3. Outros filtros...
4. Microserviço downstream recebe header X-Correlation-ID
```

## Headers Relacionados

| Header | Direção | Finalidade |
|--------|---------|-----------|
| `X-Correlation-ID` | Client → Gateway → Services | ID único da requisição |
| Outros headers de contexto | Gateway → Services | Dados da sessão (userDocumentNumber, etc.) |

---

**Resultado**: Rastreamento completo e transparente de todas as requisições no ecossistema FIDC, facilitando debugging, observabilidade e troubleshooting distribuído.