# 🐳 Docker Setup para Desenvolvimento Local

## 🚀 Como usar

### 1. Subir a infraestrutura
```bash
# Subir todos os serviços
docker-compose up -d

# Verificar status
docker-compose ps
```

### 2. Verificar serviços
- **Redis**: `localhost:6379`
- **Redis Insight**: `localhost:5540` (interface visual para Redis)
- **Mock Simulation**: `localhost:8081/__admin/health`
- **Prometheus**: `localhost:9090`
- **Grafana**: `localhost:3000` (admin/admin123)

### ✅ Status dos serviços:
```bash
# Verificar todos os containers
docker-compose ps

# Testar conectividade
curl http://localhost:9090  # Prometheus
curl http://localhost:5540  # Redis Insight
curl http://localhost:8081/__admin/health  # Mock Simulation
```

### 3. Rodar o Gateway
```bash
# Com profile local
./gradlew bootRun --args='--spring.profiles.active=local'

# Ou via IDE com environment variable
SPRING_PROFILES_ACTIVE=local
```

### 4. Testar
```bash
# Health check do gateway
curl http://localhost:8080/actuator/health

# Testar rota simulação (sem autenticação)
curl http://localhost:8080/api/simulation
```

## 📊 Monitoramento

### Prometheus
- URL: `http://localhost:9090`
- Métricas do gateway: `http_server_requests_seconds`
- Métricas Redis: `lettuce_*`

### Grafana
- URL: `http://localhost:3000`
- Login: `admin` / `admin123`
- Dashboards serão configurados automaticamente

## 🧪 Mock Services

O mock service (WireMock) simula o microserviço downstream:
- **Simulation**: Simulações de crédito

### Adicionar novos mocks
Criar arquivos JSON em `mock/simulation/mappings/`:
```json
{
  "request": {
    "method": "GET",
    "urlPath": "/api/example"
  },
  "response": {
    "status": 200,
    "body": {"message": "Mock response"}
  }
}
```

## 🔄 Comandos úteis

```bash
# Parar tudo
docker-compose down

# Rebuild imagens
docker-compose up -d --build

# Ver logs
docker-compose logs -f redis
docker-compose logs -f mock-simulation

# Limpar volumes
docker-compose down -v
```

## 🔧 Configuração Redis

### Via Redis Insight (Recomendado)
1. Acesse `http://localhost:5540`
2. O Redis já estará configurado automaticamente
3. Use a interface gráfica para criar/visualizar sessões

### Via CLI (Alternativo)
```bash
# Conectar ao Redis
docker exec -it fidc-redis redis-cli

# Criar sessão de teste
SET "fidc:session:prevcom:test-session-123" '{"sessionId":"test-session-123","partner":"prevcom","userDocumentNumber":"12345678901","userEmail":"test@prevcom.com","userName":"Test User","fundId":"CRED001","fundName":"Test Fund","relationshipId":"REL001","contractNumber":"123456","userPermissions":["CREATE_SIMULATION","VIEW_RESULTS"],"sessionSecret":"test-secret"}'

# Verificar sessão
GET "fidc:session:prevcom:test-session-123"
```

### Redis Insight Features
- **Browser**: Visualizar todas as chaves
- **Workbench**: Executar comandos Redis
- **Analysis**: Análise de memória e performance
- **Profiler**: Monitor de comandos em tempo real