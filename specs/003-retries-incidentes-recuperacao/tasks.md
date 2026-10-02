# TASKS-003 — Retries, incidentes e recuperação

Estado da lição: **REVIEW**. Implementado, com evidência executada e lesson escrita. Faltam os
requests de coleção da Fase 3 e a revisão independente da Fase 5.

## Fase 1 — Chão factual (concluída antes do SPEC)

- [x] Confirmar `newFailCommand` no Java Client 8.9.22
- [x] Confirmar ausência de reset de retries no SDK
- [x] Confirmar ausência de comando de resolução de incidente no SDK
- [x] Medir o ciclo completo por `curl`
- [x] Medir o backoff por ausência de Job ativável

## Fase 2 — Responder o que o probe deixou aberto

- [x] A ordem reset→resolve é obrigatória? Invertida devolve `409 INVALID_STATE` — evidence D2
- [x] `errorMessage` persiste no registro do Job, mas não vem no payload de ativação — evidence D7
- [x] Latência entre o índice e o motor: `search` devolveu `FAILED retries=0` logo após um `PATCH` já gravado — evidence D8
- [x] Resíduo de probe (`probe-003`, `probe-bf-*`) registrado em `evidence.md`, não apagado

## Fase 3 — Implementação

- [x] `processes/003-retries-incidentes-recuperacao/` com o BPMN e `retries="3"`
- [x] Teste de contrato BPMN↔worker para o novo processo
- [x] `apps/retry-worker/` com o worker que falha sob comando — módulo independente da Lesson 002
- [x] Teste da decisão de falhar, incluindo a guarda de `retries > 0`
- [x] Prova por mutação: `charge-crd` derruba 2 testes, `retries="5"` derruba 1 — evidence D9
- [x] Requests de coleção para o ciclo completo — 13 requests, 36 asserções, 0 falhas (evidence D10)
- [x] `validate-collections.sh` verde com os paths novos — 35/35; o validador pagou por 5 variáveis que eu esqueci de declarar

## Fase 4 — Evidence e lesson

- [x] Ciclo real executado: 3 falhas, incidente, runbook de 3 comandos, instância `COMPLETED` — evidence D3/D4
- [x] `evidence.md` com 8 seções, 3 `N/A` declaradas com motivo, e o probe de backoff invalidado na primeira tentativa
- [x] `lesson.md` na ordem causal, status `review`
- [x] Diagrama reescrito por ambiguidade de fluxo; Diagram Review preenchido; `validate-mermaid.sh` 11/11
- [x] Entrevistas respondidas, cada uma com o teste que sustentaria resposta melhor
- [x] Seção "O que ainda não é verdade sobre esta lesson", com 4 questões abertas

## Fase 5 — Revisão

- [ ] Revisão independente da lesson e da evidence
- [x] Inversão do runbook medida: mesmo `jobKey` volta a `FAILED retries=0`, incidente NOVO, os dois comandos respondem `204` (evidence D11)
- [x] Escopo medido nos dois lados (D10); falta o segundo `@JobWorker` lendo a mesma variável

## Fora de escopo, para não crescer durante a execução

- Backoff exponencial no BPMN
- Idempotência do efeito colateral
- Alertamento e SLA de incidente
- Versionamento, multi-tenancy, autenticação, escala, múltiplas partições