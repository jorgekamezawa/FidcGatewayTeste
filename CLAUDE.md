# FIDC Gateway - Spring Cloud Gateway

Gateway interno para validação de sessão e roteamento de APIs do portal FIDC pós-autenticação.

## Arquitetura e Contexto

Este projeto implementa um **Spring Cloud Gateway** com arquitetura focada em performance, seguindo padrões específicos para gateways ao invés de Clean Architecture tradicional.

### Responsabilidades Principais
- Validação de AccessToken via sessão Redis compartilhada
- Injeção automática de headers de contexto do usuário
- Roteamento inteligente para microserviços downstream
- Controle granular de permissões por endpoint

### Documentação de Referência

**Contexto do Projeto**: `docs/contexto-fidc-gateway.md`
- Especificação completa do gateway
- Entidades, fluxos e integrações
- Decisões arquiteturais e stack técnica

**Prompt de Desenvolvimento**: `.claude/prompts/gateway-setup.md`
- Padrões específicos para Spring Cloud Gateway
- Arquitetura recomendada (filtros como primeira classe)
- Templates de implementação e anti-patterns

## Estrutura do Projeto

```
fidc-gateway/
├── src/main/kotlin/
│   ├── config/               # Configurações Redis, Gateway, Métricas
│   ├── filter/               # Filtros customizados (core do gateway)
│   ├── model/                # DTOs simples (SessionContext)
│   ├── service/              # Serviços técnicos (SessionService)
│   ├── exception/            # Exception handlers
│   └── Application.kt
├── docs/                     # Documentação do projeto
│   └── contexto-fidc-gateway.md
├── .claude/
│   └── prompts/
│       └── gateway-setup.md  # Prompt de desenvolvimento
└── CLAUDE.md                 # Este arquivo
```

## Stack Técnica

- **Core**: Kotlin 1.9.25 + Spring Boot 3.4.x + Java 21
- **Gateway**: Spring Cloud Gateway (reactive)
- **Cache**: Redis (consulta de sessões)
- **Observabilidade**: Micrometer + Logback estruturado

## Filtros Principais

### SessionValidationFilter
Filtro core que valida AccessToken, busca sessão no Redis e injeta headers:

```kotlin
// Headers injetados automaticamente:
userDocumentNumber: 12345678901
userEmail: joao.silva@email.com  
userName: João Silva Santos
fundId: CRED001
fundName: Prevcom RS
partner: prevcom
relationshipId: REL001
contractNumber: 378192372163682
sessionId: uuid-da-sessao
userPermissions: VIEW_PROFILE,VIEW_STATEMENTS
```

## Configuração de Rotas

Exemplo de rota com controle de permissões:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: simulation-service
          uri: ${SIMULATION_SERVICE_URL}
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

## Integrações

### Redis (Consulta de Sessões)
- **Instância**: Compartilhada com FidcAuth
- **Chaves**: `fidc:session:{partner}:{sessionId}`
- **Operação**: Apenas leitura
- **Timeout**: 2-3 segundos

### Microserviços Downstream
APIs de negócio que recebem requests enriquecidas:
- fidc-simulation (simulações)
- fidc-contract (contratações)
- fidc-profile (perfil do usuário)
- Outros conforme necessidade

## Padrões de Desenvolvimento

### Performance
- **Reactive Programming**: WebFlux obrigatório
- **Non-blocking I/O**: Mono/Flux para todas operações
- **Cache Strategy**: Local + Redis para alta performance
- **Circuit Breakers**: Proteção contra falhas

### Observabilidade
- **Logs**: Apenas rejeições (performance)
- **Métricas**: Latência por microserviço, taxa de rejeição
- **Correlation ID**: Propagação automática

### Anti-Patterns (Evitar)
- Controllers REST no gateway
- Blocking I/O (RestTemplate, JDBC)
- Domain logic complexa
- Estado persistente no gateway

## Desenvolvimento com Claude Code

### Comandos Úteis

**Implementar filtro core**:
```bash
claude-code "Implementar SessionValidationFilter seguindo o template do prompt gateway-setup.md"
```

**Configurar Redis reativo**:
```bash
claude-code "Configurar RedisConfig para consulta de sessões com pool de conexões"
```

**Adicionar nova rota**:
```bash
claude-code "Adicionar rota para API de documentos com permissão VIEW_DOCUMENTS"
```

### Contexto para Claude Code

Ao trabalhar neste projeto, sempre referencie:
1. **docs/contexto-fidc-gateway.md** para contexto completo
2. **.claude/prompts/gateway-setup.md** para padrões técnicos
3. **Foco em filtros e performance** ao invés de Clean Architecture
4. **Reactive programming** obrigatório
5. **Simplicidade** sobre complexidade arquitetural

## Validações de Sessão

O fluxo de validação implementado:
1. Extrair sessionId do AccessToken
2. Buscar sessão no Redis: `fidc:session:{partner}:{sessionId}`
3. Validar partner do header vs sessão
4. Verificar assinatura JWT com sessionSecret
5. Validar relacionamento selecionado obrigatório
6. Verificar permissões necessárias vs metadata da rota
7. Injetar headers de contexto
8. Rotear para microserviço downstream

## Ambientes

- **Dev**: localhost com Docker Compose
- **UAT/Prod**: URLs específicas via environment variables

---

**Gateway focado em performance, simplicidade e filtros eficientes para o ecossistema FIDC.**