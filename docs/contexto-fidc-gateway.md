# CONTEXTO DO PROJETO: FIDC GATEWAY

## 📋 Propósito e Contexto de Negócio

O **FIDC Gateway** é o ponto de entrada centralizado para todas as APIs de negócio do portal FIDC (Fundo de Investimento em Direitos Creditórios) pós-autenticação. Atua como proxy inteligente que valida sessões ativas de usuários autenticados e enriquece automaticamente as requisições com informações de contexto antes de roteá-las para os microserviços downstream.

**Responsabilidade**: Gateway de sessão que intercepta requisições de usuários logados, valida AccessTokens via sessão Redis, verifica permissões contextuais e injeta headers de identificação em todas as chamadas para APIs de negócio.

**Valor agregado**: Centralização da validação de sessão, eliminação de duplicação de código nos microserviços downstream, controle granular de permissões por endpoint e injeção automática de contexto de usuário, prevenindo manipulação de dados.

**Usuários impactados**: Representantes de empresas parceiras (Prevcom, CAIO) navegando no portal após login para acessar funcionalidades como simulação, contratação, consultas e gestão de operações.

**Contexto no ecossistema**: Situa-se entre o frontend do portal e os microserviços de negócio, após a autenticação feita pelo FidcAuth. NÃO intercepta as APIs de autenticação (FidcPassword, FidcAuth), apenas APIs de funcionalidades internas.

**Limites de responsabilidade**: Gerencia apenas validação de sessão e enriquecimento de headers. NÃO gerencia autenticação inicial, criação de sessões ou regras de negócio específicas.

## 🔧 Identificação Técnica
- **Nome do Serviço**: `fidc-gateway`
- **Group ID**: com.banco.fidc.gateway
- **Porta**: 8080
- **Contexto DDD**: Session Gateway (Infrastructure Layer)

## 🗃️ Entidades de Domínio Identificadas

### Entidade: SessionContext
**Descrição**: Contexto completo da sessão do usuário extraído do Redis para enriquecimento de headers
**Responsabilidade**: Fornecer dados de contexto validados para injeção automática

**Atributos Principais**:
- sessionId: UUID (identificador da sessão)
- partner: String (identificador do parceiro)
- userDocumentNumber: String (CPF do usuário)
- userEmail: String (email do usuário)
- userName: String (nome completo)
- fundId: String (identificador do fundo)
- fundName: String (nome do fundo)
- relationshipId: String (relacionamento selecionado obrigatório)
- contractNumber: String (número do contrato)
- userPermissions: List<String> (permissões ativas)

**Regras de Negócio**:
- Relacionamento selecionado é obrigatório (relationshipSelected != null)
- Sessão deve estar ativa e não expirada
- Partner do header deve coincidir com partner da sessão
- Permissões devem coincidir com permissões requeridas pela rota

**Relacionamentos**:
- Vinculado temporariamente à sessão ativa no Redis
- Correlacionado com dados do FidcAuth (mesmo contexto de sessão)

## 📊 Fluxos de Negócio

### FLUXO 1: Validação e Enriquecimento de Requisição
- **Trigger**: Requisição HTTP do portal para qualquer API mapeada
- **Objetivo**: Validar AccessToken, verificar sessão ativa, validar permissões e enriquecer request com headers
- **Integrações**:
    - Redis: Busca de sessão ativa (`fidc:session:{partner}:{sessionId}`)
    - Downstream APIs: Proxy enriquecido para microserviços
- **Entidades**: SessionContext (read e validate)
- **Documentação Completa**: Fluxo principal do filtro SessionValidationFilter

### FLUXO 2: Roteamento com Controle de Permissão
- **Trigger**: Configuração de rota com permissões específicas no metadata
- **Objetivo**: Verificar se usuário possui permissões necessárias antes de rotear
- **Integrações**:
    - Configuração: Metadata das rotas (requiredPermissions)
    - Headers: Injeção de contexto completo
- **Entidades**: SessionContext (permission validation)
- **Documentação Completa**: Validação granular por endpoint

## 🔌 Catálogo de Integrações

### Redis Session Storage (Consulta)
- **Propósito**: Buscar dados completos da sessão ativa para validação e enriquecimento
- **Instância**: Compartilhada com FidcAuth (mesma instância)
- **Configuração**:
    - Dev: localhost:6379 (Docker Compose)
    - UAT/Prod: [URLs específicas por ambiente]
- **Operações**: Apenas leitura (GET)
- **Chaves consultadas**: `fidc:session:{partner}:{sessionId}`
- **Timeout**: 2-3 segundos com circuit breaker
- **Pool de conexões**: Configurável (10-100 baseado no volume)

### Microserviços Downstream
- **Propósito**: APIs de negócio que recebem requisições enriquecidas
- **Tipos identificados**:
    - APIs de simulação (fidc-simulation)
    - Outros conforme necessidade
- **Configuração**: URLs específicas por ambiente via Spring Cloud Gateway routes
- **Headers injetados**: Contexto completo do usuário (10 headers padrão)
- **Tratamento de falhas**: Retorno 503 se microserviço indisponível

## 💾 Stack Técnica Definida

### Core
- **Linguagem**: Kotlin 1.9.25
- **Framework**: Spring Boot 3.4.x
- **Gateway**: Spring Cloud Gateway
- **JVM**: Java 21

### Comunicação
- **Proxy**: Spring Cloud Gateway routes
- **Load Balancing**: Spring Cloud LoadBalancer (se aplicável)

### Persistência & Cache
- **Cache**: Redis (consulta de sessões ativas)
- **Conexão**: Lettuce Redis Client

### Observabilidade
- **Logs**: Logback com JSON estruturado (apenas rejeições)
- **Métricas**: Micrometer + Prometheus
    - Latência por microserviço downstream
    - Taxa de rejeição de requests
    - Throughput por rota
- **Traces**: OpenTelemetry

### Infraestrutura AWS
- **Deployment**: ECS/EKS (a definir)
- **Região**: us-east-1

## ⚡ Requisitos Não-Funcionais

### Performance
- **Latência P95**: < 100ms para validação de sessão
- **Timeout Redis**: 2-3 segundos
- **Timeout Downstream**: Configurável por rota (padrão 30s)

### Disponibilidade
- **SLA**: 99.9% (crítico para acesso às funcionalidades)
- **Circuit Breaker**: Para Redis e microserviços downstream
- **Fallback**: Retorno 503 em caso de indisponibilidade

### Segurança
- **Validação**: AccessToken via sessionSecret específico da sessão
- **Headers**: Injeção automática previne manipulação
- **Isolamento**: Validação de partner por sessão

### Volumes
- **Throughput**: Configuração adaptável baseada no volume real
- **Pool Redis**: 10-100 conexões (baseado na demanda)
- **Cache Local**: Possível implementação para alta performance

## 🔧 Configuração de Rotas (Exemplo)

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: simulation-service
          uri: ${SIMULATION_SERVICE_URL:lb://fidc-simulation}
          predicates:
            - Path=/api/simulation/**
          filters:
            - name: SessionValidation
          metadata:
            timeout: 30s
            requiredPermissions:
              - CREATE_SIMULATION
              - VIEW_SIMULATION_RESULTS
              
```

## 🔍 Decisões Arquiteturais

### AD1: Filtro Condicional por Rota
- **Contexto**: Necessidade de flexibilidade para rotas futuras sem autenticação
- **Decisão**: SessionValidationFilter aplicado apenas em rotas configuradas
- **Justificativa**: Máxima flexibilidade e controle granular
- **Trade-offs**: Configuração manual vs automação dinâmica

### AD2: Validação via SessionSecret
- **Contexto**: Reutilizar mesma lógica de validação do FidcAuth
- **Decisão**: Validar AccessToken usando sessionSecret específico da sessão
- **Justificativa**: Consistência com ecosystem, segurança por sessão
- **Alternativas consideradas**: Secret global (rejeitada por menor segurança)

### AD3: Injeção Automática de Headers
- **Contexto**: Prevenção de manipulação e simplificação de microserviços
- **Decisão**: Headers fixos injetados automaticamente em todas as rotas autenticadas
- **Justificativa**: Segurança, DRY, simplificação downstream
- **Headers fixos**: 10 headers padrão com dados de contexto

### AD4: Controle de Permissão por Metadata
- **Contexto**: Diferentes endpoints requerem permissões específicas
- **Decisão**: requiredPermissions configurável no metadata da rota
- **Justificativa**: Controle granular, configuração declarativa
- **Trade-offs**: Flexibilidade vs complexidade de configuração

### AD5: Logs Apenas de Rejeições
- **Contexto**: Volume de requests pode ser alto
- **Decisão**: Log apenas de requests rejeitadas, não de sucessos
- **Justificativa**: Performance, foco em troubleshooting
- **Métricas**: Counters para requests válidas

## ⚠️ Riscos e Mitigações

### Alto: Redis Indisponível
- **Impacto**: Alto - bloqueia todas as requisições autenticadas
- **Mitigação**: Circuit breaker, timeouts agressivos, alertas imediatos

### Médio: Microserviço Downstream Fora do Ar
- **Impacto**: Médio - funcionalidade específica indisponível
- **Mitigação**: Retorno 503, circuit breaker, health checks

### Baixo: Volume Alto Inesperado
- **Impacto**: Baixo - latência aumentada
- **Mitigação**: Pool de conexões configurável, cache local implementável

## 📋 Headers Injetados Automaticamente

```
userDocumentNumber: 12345678901
userEmail: joao.silva@email.com
userName: João Silva Santos
fundId: CRED001
fundName: Prevcom RS
partner: prevcom
relationshipId: REL001
contractNumber: 378192372163682
sessionId: uuid-da-sessao
userPermissions: VIEW_PROFILE,VIEW_STATEMENTS,CREATE_SIMULATION
x-correlation-id: uuid-correlation (se recebido na request)
```

## 📄 Fluxos Futuros Identificados
1. **Cache Local de Sessões**: Para otimização em alto volume
2. **Rate Limiting por Usuário**: Complementar ao WAF
3. **Auditoria de Requests**: Log estruturado para compliance
4. **Health Checks Customizados**: Monitoramento específico do gateway

## 📚 Referências
- Spring Cloud Gateway Documentation
- Redis Lettuce Client Configuration
- Padrões de Gateway empresariais
- Documentação FidcAuth (sessão compartilhada)
- Métricas e Observabilidade corporativa

---

🎉 **DOCUMENTAÇÃO COMPLETA!**

Agora temos toda a especificação do microserviço `fidc-gateway` com:
- ✅ Contexto e responsabilidades definidas
- ✅ Fluxo de validação e enriquecimento detalhado
- ✅ Configuração de rotas e permissões
- ✅ Stack técnica e integrações mapeadas
- ✅ Decisões arquiteturais justificadas

**Próximo passo**: Aplicar o prompt **INITIAL-SETUP** para gerar toda a estrutura base do projeto (Gradle, Docker, Spring configs).