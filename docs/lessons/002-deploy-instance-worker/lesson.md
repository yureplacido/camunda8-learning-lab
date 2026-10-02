# LESSON-002 — Deploy, Instância e Primeiro Job Worker

Status: completed

Escopo de versão: **Camunda 8.9.x** ([ADR-0002](../../adr/0002-camunda-8-version-pin.md)), verificado em cluster `8.9.22`.

Evidência: [evidence.md](evidence.md)
Conhecimento base: [Lesson 000](../000-camunda-bpmn-concepts/lesson.md) · [Lesson 001](../001-camunda7-8-mental-model/lesson.md)

## Objetivo
Conectar vocabulário (000) com runtime (001), executando na prática: implantar BPMN, criar uma Process Instance e ter um Job Worker consumindo um Job de Service Task. O foco é **evidência observada**.

## 1. BPMN mínimo
Processo `lesson-002-first-worker` com Start → Service Task `say-hello` (type=say-hello, retries=3) → End.

Arquivo: [processes/002-first-worker/first-worker.bpmn](../../../processes/002-first-worker/first-worker.bpmn)

**Fato (8.9):** `zeebe:taskDefinition type="say-hello"` define o job type que o Worker ativa.

## 2. Deploy via REST v2
**Observado:** Deploy realizado via `POST /v2/deployments` (multipart/form-data), retornando `deploymentKey` e `processDefinitionKey`. Ver comandos e respostas em [evidence.md](evidence.md).

## 3. Criar Process Instance
**Observado:** `POST /v2/process-instances` com `processDefinitionKey` e variáveis `{"name":"Mundo"}`. Instância criada em estado `ACTIVE`.

## 4. Primeiro Job Worker (Java)
Implementado Worker Java (zeebe-client-java 8.9.0) conectando via gRPC em `localhost:26500` com `usePlaintext()`. Registra worker para tipo `say-hello`, recebe Job, loga variáveis e completa Job com `jobClient.newCompleteCommand(...).send().join()`.

**Observado (logs):** `LESSON-002 SIMPLE WORKER: Conectado ao Gateway`, `Worker registrado para tipo 'say-hello'`, Job recebido/completado com `name: Mundo` e `Mensagem: Hello, Mundo!`.

Código (exemplo prático): `SimpleWorker` em execução local (ver evidence.md com trechos).

## 5. Verificação
Busca por `processInstanceKey` via `POST /v2/process-instances/search` retornou `state: "COMPLETED"` com `endDate` preenchido. Sem incidentes.

## 6. Conclusão
Cadeia verificada na prática: **Service Task → Job (criado no log) → Worker (cliente externo, gRPC) → Complete → Instância avança para COMPLETED**. Evidências reais coletadas conforme regras AGENTS.md (Fato/Observado/Interpretação).

## Diagram Review
- [x] Sintaxe/níveis coerentes com escopo prático
- [x] Sem componentes fictícios
- [x] Alegações versão 8.9 verificadas
