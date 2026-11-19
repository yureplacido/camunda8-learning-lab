# PLAN-005 — Ciclo de vida do Job e backpressure

**Estado:** fases 0 a 5 executadas em 19/11/2026; **fase 6 (reabertura Java) executada e aprovada
no mesmo dia**.
Resultado: BPMN + pasta `62` + evidência E6–E13; runner `12 request(s), 31 asserção(ões), 0
falha(s)`; revisão aprovada no contracheque após B1 e B2 **para a versão REST**. Fase 6 acrescentou
`apps/lifecycle-worker`, BPMN Java, 13 testes e E14, com duas execuções reais. A lesson voltou a
`ready` para a revisão da reabertura, que devolveu `CHANGES_REQUESTED` (B1), foi corrigida e
aprovada; a lesson voltou a `completed`. O texto abaixo descreve a sequência planejada;
o que divergiu dela está registrado em `spec.md` e na seção **Correções** de `evidence.md`.

## Sequência de execução

A ordem repete a regra que salvou a 003 e a 004: **medir antes de afirmar**. Aqui o risco é duplo —
inventar um estado `ACTIVATED` que a API não expõe, e chamar "backpressure" a um `503` que nunca
foi provocado. O chão factual (E1–E4) já está feito; a implementação só começa depois do gate de
escopo aprovado e da precedência da 004 resolvida.

### Fase 0 — Gate de precedência (antes de qualquer arquivo de código)

1. Confirmar se `main` já contém o commit `afb9384` da Lesson 004.
2. Se não contiver, decidir explicitamente: (a) fazer merge/PR da 004 e rebase desta branch em
   `main`, ou (b) rebase desta branch sobre `lesson/004-...`. Registrar a decisão na evidence.
3. Não mesclar artefatos da 004 "de passagem" nesta branch: a lesson tem de ser um PR focado.

### Fase 1 — Reproduzir o chão factual neste branch

1. Rodar `docker compose ps` e registrar a saída real (um `orchestration`, não N workers).
2. Repetir a leitura da spec viva e confirmar que `requestTimeout`, `JobStateEnum` e
   `PATCH /v2/jobs/{jobKey}` continuam presentes na versão do cluster em execução.
3. Pré-checagem com `{"filter":{"type":"lesson-005-lifecycle","state":"CREATED"}}`; abortar se
   não for `0`.
4. Confirmar health por endpoint que **existe** — `/actuator/health` respondeu `404` na 004 E1;
   usar `docker compose ps` + `/v2/topology` com corpo verificado.

### Fase 2 — Probe do timeout (a tese)

1. Deploy do BPMN novo e captura da `processDefinitionKey`.
2. Criar **uma** instância; capturar a `processInstanceKey`.
3. Ativar com `timeout` curto, `requestTimeout: -1`, `worker: w5-curto`, um Job. Guardar o corpo
   inteiro: `jobKey`, `deadline`, `retries`, `worker`.
4. Não completar. Fazer poll até o search mostrar `state: TIMED_OUT` para **aquele** `jobKey`.
   Registrar o tempo decorrido como observação, não como benchmark.
5. Reativar com `worker: w5-recupera` e conferir o mesmo `jobKey` e `retries`.
6. Completar com `204`; confirmar instância `COMPLETED` e `CREATED: 0`.
7. **Se o timeout não aparecer no prazo esperado**, registrar a falha e investigar — não ajustar o
   BPMN silenciosamente.

### Fase 3 — Probe dos dois relógios

1. Com a fila vazia, medir `curl -w '%{time_total}'` para `requestTimeout: -1` e para um valor
   positivo (ex.: `2000`). A diferença observada é sobre a **request**, não sobre o Job.
2. Opcional, se houver tempo: ativar com `timeout` longo, aplicar `PATCH` com
   `changeset.timeout` menor e medir se o `deadline` da reativação mudou. Se não mudar, registrar
   como dead end.
3. Não provocar `503`: sem carga, `N/A` com motivo.

### Fase 4 — Artefatos

1. Coleção Postman: requests de pré-checagem, deploy, criação, ativação curta, espera/`TIMED_OUT`,
   reativação, completion e verificação final. Asserções no corpo, nunca `items[0]`.
2. Diagrama de estados do Job em Mermaid (`stateDiagram-v2`, se o validador aceitar; senão
   `flowchart` com rótulo textual de notação). Validar em cópia isolada com
   `docs/validate-mermaid.sh`.
3. `lesson.md` sai de `drafting` para `ready`, com a seção final
   **"O que ainda não é verdade sobre esta lesson"** preenchida.
4. `evidence.md` ganha os corpos verbatim e a correção de qualquer afirmação que a execução
   refutar — sem apagar a versão errada.

### Fase 5 — Revisão (executada)

- Revisor independente verificou: nenhum `ACTIVATED` inventado; `timeout` ≠ `requestTimeout`;
  `503` marcado como `N/A`; `jobKey` usado como prova; nenhum container de worker fictício.
- Primeira passada: `CHANGES_REQUESTED` com B1 (aritmética de E7) e B2 (rótulos de rascunho).
  Ambos corrigidos com as versões erradas visíveis; contracheque: `APPROVED`.
- Título mantido (não afirma medição de `503`); os quatro `WARN` do Diagram Review não bloqueiam.
- Promovida a `completed`; roadmap e README do módulo atualizados.
- Sem commit/push/PR da 005 sem confirmação explícita do usuário.

### Fase 6 — Reabertura para incluir Java (executada em 19/11/2026)

1. O usuário pediu um projeto Java na 005. A lesson voltou de `completed` para `ready`; a versão
   REST-only e suas afirmações de escopo ficaram visíveis com a correção ao lado.
2. Confirmar a API do SDK 8.9.22 localmente (`javap` + código-fonte do starter) antes de escrever
   `@JobWorker` com `timeout` em ms (E14.1).
3. Criar módulo autônomo `apps/lifecycle-worker` no padrão de `apps/first-worker`: POM com parent
   Spring Boot 4.0.8, Java 21, `camunda-sdk.version 8.9.22`, pins de httpclient5/httpcore5, pacote
   `com.camunda8.lab.lifecycleworker`, `application.yml` self-managed, `web-application-type: none`.
4. BPMN novo com tipo `lesson-005-lifecycle-java` e `retries="3"`, separado da pasta `62`.
5. Worker com `timeout = 8000`, `autoComplete = false`, decisão pura extraída e log de `jobKey`,
   `deadline` e `retries`. Sem `maxJobsActive`, `pollInterval`, concorrência, graceful shutdown ou
   métricas (014).
6. Testes: 8 de contrato BPMN ↔ anotação e 5 de decisão/identidade = 13; prova por mutação (E14.2).
7. Duas execuções reais: deploy, worker, instância, primeira ativação sem `complete`, reativação da
   mesma `jobKey`, `complete`, instância `COMPLETED`, `CREATED: 0` (E14.3–E14.8).
8. Registrar `N/A` para `state: TIMED_OUT` no worker Java, com motivo e filtro validado (E14.7).
9. Enviar a versão reaberta para nova revisão independente antes de promover a `completed` — feito:
   `CHANGES_REQUESTED` por B1, correção documental visível, `APPROVED` e promoção.

## Fora da sequência, por decisão de escopo

- Configuração Java de worker (`maxJobsActive`, `pollInterval`, concorrência, shutdown, métricas) — 014.
- Idempotência de efeito colateral — 015.
- Backpressure por carga e benchmark — fora do laboratório, `N/A`.
- Particionamento — 025.
- Edição do Compose vendorizado ou instalação global — proibido.
