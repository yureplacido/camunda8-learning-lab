# TASKS-005 — Ciclo de vida do Job e backpressure

Estado da lição: **COMPLETED**. Versão REST concluída em 19/11/2026 (E6–E13, revisão aprovada
após B1/B2); reabertura Java executada e revisada no mesmo dia (E14): `CHANGES_REQUESTED` por B1,
correção visível aplicada e `APPROVED` na sequência. Commit + push da branch pedidos pelo usuário e
feitos depois do gate; nenhum PR pedido.

Legenda: `[x]` = executado neste branch · `[ ]` = pendente · `N/A` = não aplicável com motivo.

## Fase 0 — gate de precedência

- [x] Confirmar se `main` já contém `afb9384` (Lesson 004) — não continha
- [x] Decidir e registrar landing da 004: merge local sem PR, `main` em `1f6c1fe`, push feito
- [x] Rebase desta branch sobre `main` (`1f6c1fe`) e conflitos de roadmap/README resolvidos
- [x] Garantir que este branch não carregue artefatos da 004 "de passagem"

## Fase 1 — chão factual neste branch

- [x] `webfetch` da doc 8.9 `job-workers/` com caps e URL verificada (E1)
- [x] `webfetch` da doc 8.9 `activate-jobs/` com `503` documentado (E2)
- [x] `curl` na spec viva e extração de `JobActivationRequest`, `JobStateEnum`,
      `ActivatedJobResult`, `JobUpdateRequest`, `JobFailRequest` (E3)
- [x] `webfetch` da doc 8.9 `internal-processing/` e `create-deployment/` para backpressure (E4)
- [x] Registrar proveniência das 4 `TIMED_OUT` da 004 sem fingir medição neste branch (E5)
- [x] `docker compose ps` real neste branch (E6) — dead end `compose.yaml` corrigido para
      `docker-compose.yaml`
- [x] Re-leitura da spec viva no cluster em execução (`validate-collections.sh`, E12)
- [x] Pré-checagem `state: CREATED` com o tipo novo retornando `0` (E6)
- [x] Branch e status da lesson no roadmap/README ajustados

## Fase 2 — experimento do timeout

- [x] BPMN `processes/005-ciclo-vida-job-backpressure/lesson-005-lifecycle.bpmn`
- [x] Deploy e captura da `processDefinitionKey` `2251799813720336` (E7)
- [x] Uma instância `2251799813720339` e captura da `processInstanceKey` (E7)
- [x] Ativação com `timeout: 4000` e `requestTimeout: -1`, corpo verbatim salvo (E7)
- [x] Poll até `TIMED_OUT` observável para o `jobKey` `2251799813720346` (E8)
- [x] Reativação com `worker: w5-recupera` e mesmo `jobKey` (E9)
- [x] `retries` lido antes e depois: `3` em E7, E8, E9, E13
- [x] `completion` com `204`, instância `COMPLETED`, `CREATED: 0` (E9)

## Fase 3 — dois relógios

- [x] Tempo de request com `requestTimeout: -1` medido: 0.004011 s / 4 ms (E10)
- [x] Tempo de request com `requestTimeout: 2000` medido: 2.192381 s / 2 350 ms (E10)
- [x] `PATCH /v2/jobs/{jobKey}` com `changeset.timeout` testado (E11); hipótese do rascunho
      ("estende o deadline") refutada e corrigida em **Correções**
- [x] `503`/`429` marcado `N/A` com motivo — sem carga
- [x] Checagem final dos 4 Jobs `COMPLETED` com `retries: 3` (E13)

## Fase 4 — artefatos

- [x] 12 requests na pasta `62` da coleção Postman, com asserções sobre corpo e `jobKey`
- [x] Variáveis `life*` declaradas e documentadas no README da coleção
- [x] `./infra/local/validate-collections.sh` sem divergência: `OK: 58 request(s)`
- [x] Runner da pasta `62`: `12 request(s), 31 asserção(ões), 0 request(s) com falha`
- [x] Diagrama de transições do Job com Diagram Review preenchido (veredito `WARN`, 4 WARNs)
- [x] `docs/validate-mermaid.sh`: bloco da 005 com `ok`; 2 falhas apenas em `node_modules`
      gitignorado, pré-existentes
- [x] `lesson.md` promovida a `ready` com **"O que ainda não é verdade sobre esta lesson"**
- [x] `evidence.md` com E6–E13, corpos verbatim e seção **Correções** visível
- [x] SPEC, PLAN e TASKS reconciliados com o estado real

## Fase 5 — revisão e fechamento

- [x] Revisão independente (riscos: `ACTIVATED` inventado, relógios confundidos, `503` inventado)
- [x] Decisão do revisor: título mantido; 4 WARNs do diagrama não bloqueiam
- [x] Perguntas de entrevista revisadas — P6 (PATCH), P7 (`ACTIVATABLE`) e P8 (`requestTimeout`)
- [x] Veredito final `APPROVED` no contracheque, sem blockers
- [x] Promoção a `completed`; roadmap e README do módulo atualizados
- [x] Sem commit/push/PR da 005 sem confirmação explícita do usuário

## Fase 6 — reabertura com Java

- [x] Decisão do usuário: reabrir a 005 e adicionar projeto Java (escopo antigo mantido visível)
- [x] API do `@JobWorker` confirmada no SDK 8.9.22 local; `timeout` é `long` em ms (E14.1)
- [x] URL oficial do Java Client tentada: `404`/`403`; rótulo **Observado**, não **Fato** (E14.1)
- [x] `apps/lifecycle-worker/` criado como módulo autônomo, sem tocar em 002/003
- [x] BPMN `lesson-005-lifecycle-java.bpmn` com tipo próprio e `retries="3"`
- [x] Worker com `timeout = 8000`, `autoComplete = false`, decisão pura e log de chave/prazo/retries
- [x] 13 testes Java, 0 falhas; mutação do `type` derruba `BpmnContractTest` (E14.2)
- [x] Deploy, 2 instâncias e 2 execuções com a mesma `jobKey` reativada (E14.3–E14.5)
- [x] Aritmética conferida: `deadline − 8000 ms` bate com o log; reativação 793 ms após o prazo
      (E14.6)
- [x] `state: TIMED_OUT` no worker Java marcado `N/A` com motivo: janela curta + projeção (E14.7)
- [x] 2 instâncias `COMPLETED` e `CREATED: 0` no tipo Java (E14.8)
- [x] Revisão independente da reabertura — `CHANGES_REQUESTED` por B1 (linha `N/A` sem correção),
      corrigida com frase antiga visível; `APPROVED` em seguida
- [x] Promoção a `completed` após `APPROVED`
- [x] Commit + push da branch, pedidos pelo usuário, feitos **depois** do gate

## Histórico

- 19/11/2026 — branch criada de `main` (`8f3c314`); lesson, SPEC, PLAN e TASKS abertos em
  `drafting`; E1–E5 registrados somente com o que foi executado.
- 19/11/2026 — landing da 004 por decisão do usuário (merge local sem PR, `main` em `1f6c1fe`,
  push feito); rebase da 005 e conflitos resolvidos.
- 19/11/2026 — BPMN, pasta `62`, runner e evidência E6–E13; lesson promovida a `ready`.
- 19/11/2026 — revisão independente: `CHANGES_REQUESTED` com B1/B2; correções aplicadas com as
  versões erradas visíveis; contracheque `APPROVED`; lesson promovida a `completed`.
- 19/11/2026 — o usuário pediu projeto Java; lesson reaberta para `ready`, módulo
  `apps/lifecycle-worker` + BPMN Java + 13 testes + E14. Nova revisão devolveu
  `CHANGES_REQUESTED` por B1; correção documental aplicada e `APPROVED`; lesson promovida a
  `completed`. Commit + push após o gate.
