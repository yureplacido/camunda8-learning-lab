# TASKS-003 — Retries, incidentes e recuperação

Estado da lição: **COMPLETED**. Implementado, com evidência executada, lesson escrita, requests de
coleção prontos e revisão independente aprovada. A primeira rodada voltou `CHANGES_REQUESTED` com
quatro blockers (B1–B4); todos foram corrigidos e a re-revisão voltou `APPROVED`, sem blockers.

## Fase 1 — Chão factual (concluída antes do SPEC)

- [x] Confirmar `newFailCommand` no `JobClient` 8.9.22
- [x] Confirmar que o `JobClient` não expõe reset nem resolução
- [x] **Correção da revisão:** confirmar que o `CamundaClient` expõe `newUpdateRetriesCommand`,
      `newResolveIncidentCommand`, `newSetVariablesCommand` e `newUpdateJobCommand` — evidence D1
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
      (reexecução na revisão bloqueada pela rota VPN do host — evidence D10)

## Fase 4 — Evidence e lesson

- [x] Ciclo real executado: 3 falhas, incidente, runbook de 3 comandos, instância `COMPLETED` — evidence D3/D4
- [x] `evidence.md` com D1–D12, `N/A` declarados com motivo e o probe de backoff invalidado na primeira tentativa
- [x] `lesson.md` na ordem causal, promovido a `completed` após a re-revisão
- [x] Diagrama reescrito por ambiguidade de fluxo; Diagram Review preenchido; `validate-mermaid.sh` 11/11
      (reexecutado na revisão com `mise exec node@24.15.0`)
- [x] Entrevistas respondidas, cada uma com o teste que sustentaria resposta melhor
- [x] Registro da revisão independente com o histórico `CHANGES_REQUESTED` → `APPROVED` e 3 limitações não-bloqueantes

## Fase 5 — Revisão

- [x] Primeira revisão independente executada — `CHANGES_REQUESTED` (B1–B4)
- [x] B1 — tese `JobClient` vs `CamundaClient` corrigida na lesson, no SPEC e na evidence D1
- [x] B2 — D1 refeito contra `camunda-client-java-8.9.22-sources.jar`, com comando executado
- [x] B3 — `FAIL` e incidente reescritos como efeito único do comando de falha; o `409` limitado à ordem dos comandos do operador
- [x] B4 — segunda resolução medida: `204` → `404 NOT_FOUND` (evidence D12)
- [x] Re-revisão independente — `APPROVED`, sem blockers (N1–N5 também fechados)
- [x] Inversão do runbook medida: mesmo `jobKey` volta a `FAILED retries=0`, incidente NOVO, os dois comandos respondem `204` (evidence D11)
- [x] Escopo medido nos dois lados (D10); falta o segundo `@JobWorker` lendo a mesma variável

## Fora de escopo, para não crescer durante a execução

- Backoff exponencial no BPMN
- Idempotência do efeito colateral
- Alertamento e SLA de incidente
- Versionamento, multi-tenancy, autenticação, escala, múltiplas partições