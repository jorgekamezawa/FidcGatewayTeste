# Métricas e Observabilidade - FIDC Gateway

## Visão Geral

O sistema de observabilidade do gateway é composto por duas classes principais que trabalham em conjunto para fornecer métricas customizadas e filtradas para monitoramento.

**Componentes**:
- `ObservabilityConfig` - Configuração global do registry de métricas
- `MetricsFilter` - Coleta de métricas customizadas por requisição

## ObservabilityConfig

**Localização**: `src/main/kotlin/com/banco/fidc/gateway/config/ObservabilityConfig.kt:10`

### Finalidade
Customiza o `MeterRegistry` do Micrometer para:
1. **Filtrar métricas desnecessárias** - Remove ruído do sistema
2. **Padronizar identificação** - Adiciona tags comuns

### Configuração Aplicada

#### Filtros de Métricas
```kotlin
.meterFilter(MeterFilter.deny { id -> 
    id.name.startsWith("jvm.gc.pause") || 
    id.name.startsWith("jvm.memory.usage.after.gc") ||
    id.name.startsWith("tomcat.")
})
```

**Métricas bloqueadas**:
- `jvm.gc.pause.*` - Pausas do garbage collector
- `jvm.memory.usage.after.gc.*` - Uso de memória pós-GC
- `tomcat.*` - Métricas específicas do Tomcat

**Razão**: Reduz overhead e poluição visual em dashboards, focando em métricas de negócio.

#### Tags Comuns
```kotlin
.commonTags("application", "fidc-gateway")
```

**Resultado**: Todas as métricas recebem automaticamente a tag `application=fidc-gateway`, facilitando filtragem em Prometheus/Grafana.

### Métricas Mantidas
- Latência de requests (`http.server.requests`)
- Métricas do Spring Cloud Gateway
- Circuit breaker status
- **Métricas customizadas do MetricsFilter**

## MetricsFilter

**Localização**: `src/main/kotlin/com/banco/fidc/gateway/filter/MetricsFilter.kt:13`

### Finalidade
Filtro global que coleta métricas customizadas de todas as requisições com granularidade controlada.

### Configuração
- **Tipo**: `GlobalFilter` com prioridade `LOWEST_PRECEDENCE - 1`
- **Execução**: Última no pipeline, mede o tempo total
- **Normalização**: Paths inteligente com regex para evitar explosão de cardinalidade

### Métricas Coletadas

#### 1. Latência de Requisições
```kotlin
gateway.request.duration
Tags: path, method, status
```

**Exemplo**:
```
gateway.request.duration{path="/api/simulation/*/validate", method="POST", status="200"} = 250ms
```

#### 2. Contador Total de Requisições
```kotlin
gateway.request.total  
Tags: path, method, status
```

**Exemplo**:
```
gateway.request.total{path="/api/simulation/*", method="GET", status="200"} = 1547
```

#### 3. Contador de Erros
```kotlin
gateway.request.errors
Tags: path, method, error
```

**Exemplo**:
```
gateway.request.errors{path="/api/contract/*", method="POST", error="SessionValidationException"} = 12
```

### Normalização de Paths

O `MetricsFilter` implementa normalização inteligente para evitar **explosão de cardinalidade**:

#### Problema Sem Normalização
```
/api/simulation/123 → métrica única
/api/simulation/456 → métrica única  
/api/simulation/789 → métrica única
// Milhares de métricas diferentes!
```

#### Solução: Normalização por Regex

**Versão Ativa** (granularidade útil):
```kotlin
// Exemplos de transformação:
/api/simulation/123/validate    → /api/simulation/*/validate
/api/simulation/456/form/789    → /api/simulation/*/form/*  
/api/contract/123/approve       → /api/contract/*/approve
/api/profile/456/documents      → /api/profile/*/documents
```

**Versão Comentada** (básica):
```kotlin
// Agrupa tudo por microserviço:
/api/simulation/123/validate → /api/simulation
/api/simulation/456/results  → /api/simulation
```

#### Padrões Regex Implementados

**Simulation Service**:
- `/api/simulation/\d+/validate` → `/api/simulation/*/validate`
- `/api/simulation/\d+/form/\d+` → `/api/simulation/*/form/*`
- `/api/simulation/\d+/results` → `/api/simulation/*/results`
- `/api/simulation/\d+` → `/api/simulation/*`

**Contract Service**:
- `/api/contract/\d+/approve` → `/api/contract/*/approve`
- `/api/contract/\d+/documents` → `/api/contract/*/documents`

**Profile Service**:
- `/api/profile/\d+/documents` → `/api/profile/*/documents`
- `/api/profile/\d+/settings` → `/api/profile/*/settings`

### Trade-offs das Abordagens

#### Normalização Básica (Comentada)
**Vantagens**:
- Performance máxima (`startsWith` é rápido)
- Cardinalidade mínima
- Simplicidade de manutenção

**Desvantagens**:
- Perde granularidade de operações
- Dificulta identificação de endpoints problemáticos

#### Normalização Inteligente (Ativa)
**Vantagens**:
- Preserva semântica das operações
- Permite monitoring granular
- Facilita troubleshooting específico

**Desvantagens**:
- Overhead de regex
- Requer manutenção quando APIs mudam
- Cardinalidade maior (mas controlada)

## Integração com Observabilidade

### Fluxo Completo
1. **ObservabilityConfig** prepara o `MeterRegistry`:
   - Filtra métricas desnecessárias
   - Adiciona tag `application=fidc-gateway`

2. **MetricsFilter** usa o registry configurado:
   - Coleta métricas customizadas
   - Normaliza paths conforme estratégia
   - Propaga para Prometheus/Grafana

### Exemplo de Dashboard Grafana

Com as métricas coletadas, você pode criar dashboards com:

**Latência por Operação**:
```promql
rate(gateway_request_duration_sum[5m]) / rate(gateway_request_duration_count[5m])
```

**Throughput por API**:
```promql
rate(gateway_request_total[1m]) * 60
```

**Taxa de Erro por Serviço**:
```promql
rate(gateway_request_errors[5m]) / rate(gateway_request_total[5m]) * 100
```

### Correlação com Correlation ID

As métricas trabalham em conjunto com o `CorrelationIdFilter` para rastreabilidade completa:

- **Métricas**: Visão agregada de performance
- **Logs com Correlation ID**: Debugging específico
- **Traces distribuídos**: Jornada end-to-end

## Configuração de Produção

### Prometheus
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'fidc-gateway'
    static_configs:
      - targets: ['gateway:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
```

### Grafana Queries Úteis

**Top 10 Endpoints Mais Lentos**:
```promql
topk(10, rate(gateway_request_duration_sum{application="fidc-gateway"}[5m]))
```

**Requests por Método HTTP**:
```promql
sum by(method) (rate(gateway_request_total{application="fidc-gateway"}[1m]))
```

**Erros por Path**:
```promql
sum by(path) (rate(gateway_request_errors{application="fidc-gateway"}[5m]))
```

## Alternando Estratégias de Normalização

Para trocar entre normalização básica e inteligente:

1. **Usar normalização básica** (performance):
   - Descomentar método básico
   - Comentar método inteligente

2. **Usar normalização inteligente** (granularidade):
   - Manter configuração atual

**Importante**: Ambos métodos têm o mesmo nome `normalizePath` para facilitar alternância.

---

**Resultado**: Sistema completo de observabilidade com métricas filtradas, granularidade controlada e identificação padronizada para o ecossistema FIDC.