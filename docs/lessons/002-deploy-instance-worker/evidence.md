# Evidence — Lesson 002

Observações reais (não simuladas).

## Ambiente
- Cluster 8.9.22 (lightweight), Orchestration + Connectors up/healthy
- Topology: 1 broker, partition 1 leader/healthy
- APIs REST em http://localhost:8080 (v2), gRPC 26500

## 1. Deploy BPMN
Comando:
```bash
curl -s -X POST http://localhost:8080/v2/deployments \
  -H "Content-Type: multipart/form-data" \
  -F "deployment-name=lesson-002-first-worker" \
  -F "resources=@processes/002-first-worker/first-worker.bpmn"
```
Resposta (observada):
```json
{"deploymentKey":"2251799813748843","tenantId":"<default>","deployments":[{"processDefinition":{"processDefinitionId":"lesson-002-first-worker","processDefinitionVersion":1,"resourceName":"first-worker.bpmn","tenantId":"<default>","processDefinitionKey":"2251799813748844"},"decisionDefinition":null,"decisionRequirements":null,"form":null,"resource":null}]}
```
**Fato (8.9):** Deploy via REST v2 multipart. **Observado:** DeploymentKey 2251799813748843, processDefinitionKey 2251799813748844.

## 2. Criar Process Instance
```bash
curl -s -X POST http://localhost:8080/v2/process-instances \
  -H "Content-Type: application/json" \
  -d '{"processDefinitionKey":"2251799813748844","variables":{"name":"Mundo"}}'
```
Resposta (observada):
```json
{"processDefinitionId":"lesson-002-first-worker","processDefinitionVersion":1,"tenantId":"<default>","variables":{},"processDefinitionKey":"2251799813748844","processInstanceKey":"2251799813751291","tags":[],"businessId":null}
```

## 3. Job Worker (Java, ZeebeClient, plain gRPC)
Worker simples (Java 21, zeebe-client-java 8.9.0) conectado em localhost:26500 com `usePlaintext()`. Registrado para `say-hello`. Logs observados (trecho):
```text
[main] INFO com.camunda8.lab.simpleworker.SimpleWorker - === LESSON-002 SIMPLE WORKER: Conectado ao Gateway ===
[main] INFO com.camunda8.lab.simpleworker.SimpleWorker - === Worker registrado para tipo 'say-hello' ===
[pool-2-thread-1] INFO com.camunda8.lab.simpleworker.SimpleWorker - === LESSON-002: Job recebido ===
[pool-2-thread-1] INFO com.camunda8.lab.simpleworker.SimpleWorker - JobKey: 2251799813751297
[pool-2-thread-1] INFO com.camunda8.lab.simpleworker.SimpleWorker - ProcessInstanceKey: 2251799813751291
[pool-2-thread-1] INFO com.camunda8.lab.simpleworker.SimpleWorker - Type: say-hello
[pool-2-thread-1] INFO com.camunda8.lab.simpleworker.SimpleWorker - name: Mundo
[pool-2-thread-1] INFO com.camunda8.lab.simpleworker.SimpleWorker - Mensagem: Hello, Mundo!
[pool-2-thread-1] INFO com.camunda8.lab.simpleworker.SimpleWorker - === Completando Job ===
```

## 4. Verificação de conclusão
```bash
curl -s -X POST http://localhost:8080/v2/process-instances/search \
  -H "Content-Type: application/json" \
  -d '{"filter":{"processInstanceKey":"2251799813751291"}}'
```
Resposta (observada): `state: "COMPLETED"`, `endDate` presente.

**Observado:** Instância completou após worker completar o Job. Sem incidentes.

## 5. Partições/Engine (snapshot)
`processedPosition` avançou consistentemente; exporter em `EXPORTING` e broker saudável.

## Interpretação
A cadeia `Service Task → Job → Worker → Completion` funcionou na prática com fronteira gRPC entre Gateway e Worker (cliente externo). Worker completou Job explicitamente; engine avançou instância.

## Limitações
- Worker usado via `ZeebeClient` (não Spring Boot starter, por compatibilidade de classpath na execução prática). Ambos seguem API Camunda 8.9.
