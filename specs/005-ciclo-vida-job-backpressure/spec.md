# SPEC-005 — Ciclo de vida do Job e backpressure

## Status

**Completed — reaberta em 19/11/2026 para incluir Java e aprovada no mesmo dia.** ~~**Completed**. Chão factual levantado e
experimento executado em 19/11/2026 (E6–E13 da evidence). BPMN e pasta `62` implementados; runner
com `12 request(s), 31 asserção(ões), 0 request(s) com falha`. Revisão independente aprovada no
contracheque, depois de corrigir B1 (aritmética de E7) e B2 (rótulos de rascunho), com as versões
erradas mantidas em **Correções** na evidence. Veredito final `APPROVED`, com quatro WARNs não
bloqueantes no Diagram Review.~~

O texto riscado é o estado da versão REST-only. O usuário pediu um projeto Java; a lesson voltou a
`ready` e ganhou `apps/lifecycle-worker` + `lesson-005-lifecycle-java.bpmn` (E14), com 13 testes e
duas execuções reais. **O `APPROVED` riscado avaliou somente a versão REST.** A reabertura teve
revisão própria: `CHANGES_REQUESTED` por B1 (correção de escopo ausente no `N/A` de `evidence.md`),
corrigido com a frase antiga visível, e `APPROVED` na sequência. As duas revisões são mantidas como
registro; nenhuma foi apagada.

## Goal

Ensinar o que acontece com um Job **entre** a ativação e o `completion`: o prazo do empréstimo
(`timeout`/`deadline`), a reatribuição (`TIMED_OUT`), a preservação de `retries`, a entrega
*at least once* e a diferença entre o relógio do Job e o relógio da request (`requestTimeout`).
Backpressure entra como **vocabulário preciso**, separando o controle de ritmo do cliente do limite
adaptativo do broker — sem afirmar medição de `503`.

Frase a internalizar:

> `TIMED_OUT` é fim de empréstimo, não falha de negócio. O Job volta com o mesmo `jobKey` e os
> mesmos `retries`; quem decide quando pedir trabalho é o cliente.

## O problema primeiro (ordem causal)

A 004 respondeu "quem recebe o quê". A pergunta seguinte, nascida das 4 `TIMED_OUT` daquela rodada,
é "por que o Job voltou e por que nada foi realmente perdido?". Antes de falar de configuração Java
(012/014) ou de idempotência de efeito colateral (015), é preciso provar o contrato mínimo da
entidade Job: estado, prazo, reatribuição.

### Eixo C7 → 8

**Fato (C7, doc 8.7 verificada na Lesson 003)** — o *job executor* vive no engine e compete pela
transação de banco entre *wait states*.
https://docs.camunda.io/docs/8.7/components/best-practices/development/understanding-transaction-handling-c7/

**Fato (C8, 8.9, verificada em 19/11/2026)** — o Job é uma máquina de estados no log; o cliente
pede com `timeout`, e o motor reatribui quando o prazo acaba:
https://docs.camunda.io/docs/8.9/components/zeebe/technical-concepts/internal-processing/
https://docs.camunda.io/docs/8.9/components/concepts/job-workers/

**Interpretação (decisão de ensino)** — o eixo obrigatório é a **fronteira transacional ACID
compartilhada perdida**. Aqui ela aparece como a janela entre `activation` e `completion`: não há
um `commit` único cobrindo a aplicação e o engine, e é essa janela que o `timeout` delimita. "O
banco virou log" é consequência, já ensinada na 001/003; não reconstruir a seção.

| | Camunda 7 | Camunda 8 |
|---|---|---|
| Estado do trabalho | transação no banco, junto com o engine | log do broker + comandos do cliente |
| Se o executor para | transação não comita; engine recupera | `deadline` expira; outro cliente ativa |
| Ritmo | configuração do job executor no engine | cliente escolhe quando e quanto pedir |

## Scope

1. BPMN novo com service task de tipo `lesson-005-lifecycle` e `retries="3"` explícito, versionado
   com a lesson.
2. Pré-checagem de **zero** Jobs `CREATED` com esse tipo; se houver resíduo, parar e limpar.
3. Uma instância, uma ativação com `timeout` curto (a medir, da ordem de segundos) e
   `requestTimeout: -1`, capturando `jobKey`, `deadline`, `worker` e `retries` do **corpo**.
4. Espera ativa até o `deadline` passar; busca com `state: TIMED_OUT` e asserção sobre o `jobKey`
   capturado — nunca `items[0]`.
5. Reativação com `worker` distinto e asserção de que o **mesmo** `jobKey` voltou, com `retries`
   preservado.
6. `completion` com `204`, busca final com `state: CREATED` igual a `0` e instância `COMPLETED`.
7. Sondagem de `requestTimeout` com fila vazia: `-1` (resposta imediata) contra um valor positivo;
   medir **tempo de request**, não vazão.
8. Sondagem de `PATCH /v2/jobs/{jobKey}` com `changeset.timeout` **apenas se** a sequência acima
   deixar margem; se não alterar o `deadline` observado, registrar a falha em vez de ajustar a
   afirmação.
9. Diagrama de estados do Job em Mermaid + Diagram Review; `docs/validate-mermaid.sh` em cópia
   isolada.
10. Coleção Postman com requests que exponham as etapas, com asserções sobre corpo e sobre o
    `jobKey`, não sobre `200`.
11. **(reabertura)** Módulo Java mínimo e isolado `apps/lifecycle-worker`, com `@JobWorker` de tipo
    próprio, `timeout = 8000` ms e `autoComplete = false`: a primeira ativação não completa; a
    reativação com a mesma `jobKey` completa via `JobClient`.
12. **(reabertura)** BPMN Java versionado com `retries="3"` e teste de contrato BPMN ↔ anotação,
    incluindo prova por mutação.
13. **(reabertura)** Duas execuções reais com log verbatim, medição de `deadline − timeout` e
    asserção de que `retries` volta intacto.

## Out of scope

- ~~Java, Spring Boot, `@JobWorker`~~ **Correção da reabertura:** existe um módulo Java mínimo
  (itens 11–13) que **usa** `@JobWorker`, `timeout` e `autoComplete = false` como mecanismo. Ele não
  ensina configuração de cliente.
- `maxJobsActive`, `pollInterval`, threads, graceful shutdown, métricas
  `camunda.client.worker.job.*` — Lesson 014. Continuam fora do escopo.
- Idempotência de efeito colateral de negócio — Lesson 015. Aqui `at least once` é Fato, não
  experimento de duplicata.
- Escala e disputa entre três clientes — Lesson 004.
- Retries, incidentes e runbook — Lesson 003.
- Particionamento, roteamento e múltiplos brokers — Lesson 025.
- Provar `503`/`RESOURCE_EXHAUSTED` por carga, benchmark de vazão ou limites numéricos de
  backpressure — `N/A` com motivo.
- Edição de `infra/local/camunda-8.9/`, novo container, instalação global, `.mise.toml`.

## Requirements

1. Toda afirmação versionada traz versão e URL verificada por `webfetch` (E1–E4).
2. Nenhum estado é inventado: `ACTIVATED` não entra como valor de `state`, porque não está no enum
   da spec viva (E3).
3. `timeout` e `requestTimeout` são tratados como relógios distintos, com as descrições literais da
   spec viva.
4. A prova de reatribuição é a identidade do `jobKey`, não contagem de instâncias nem `items[0]`.
5. O `deadline` do payload de ativação (epoch ms) e o `deadline` do search (ISO na 004) não são
   comparados como se tivessem o mesmo formato.
6. `retries` é lido antes e depois; a afirmação "timeout não consome retry" só vira Observado se a
   medição confirmar.
7. Resíduo é limpo ao final, com `state: CREATED` igual a `0`.
8. Evidência em PT-BR, rotulada `Fato` / `Observado` / `Interpretação`, com correções visíveis.
9. **(reabertura)** A prova Java é a identidade da `jobKey` entre duas ativações, nunca contagem de
   linhas de log. `state: TIMED_OUT` sem worker é medido na E8; no worker Java é `N/A` com motivo
   (E14.7).
10. **(reabertura)** Configuração do SDK é lida do código-fonte local 8.9.22 e rotulada
   **Observado**, porque a URL oficial do Java Client não respondeu (`404`/`403`, E14.1).

## Chão factual — separação rigorosa

| Classe | Afirmação | Origem |
|---|---|---|
| **Fato (8.9)** | Job ativado até completar, falhar ou expirar; timeout reatribui e não afeta retries | https://docs.camunda.io/docs/8.9/components/concepts/job-workers/ (verificado 19/11) |
| **Fato (8.9)** | Entrega é *at least once*; outro `completion` é rejeitado com `NOT FOUND` | mesma URL |
| **Fato (8.9)** | Long polling mantém a request aberta; parâmetro `request-timeout` | mesma URL |
| **Fato (8.9)** | Job é máquina de estados; certas transições só valem em certo estado | https://docs.camunda.io/docs/8.9/components/zeebe/technical-concepts/internal-processing/ (verificado 19/11) |
| **Fato (8.9)** | Backpressure do broker = `RESOURCE_EXHAUSTED`, limite adaptativo por partição; `CompleteJob`/`FailJob` aceitos | mesma URL |
| **Fato (8.9)** | `503` na REST por backpressure; `RESOURCE_EXHAUSTED` no título | https://docs.camunda.io/docs/8.9/apis-tools/orchestration-cluster-api-rest/specifications/create-deployment/ (verificado 19/11) |
| **Observado (19/11)** | `JobActivationRequest` tem `requestTimeout` com `0`/`<0`/`>0`; `JobStateEnum` tem 8 valores sem `ACTIVATED` | spec viva em `localhost:8080/v3/api-docs/...` (E3) |
| **Observado (17/11, 004)** | 4 `TIMED_OUT`, `retries: 3`, mesma key reativada e completada | commit `afb9384`, E18 da 004; repetido na 005 (E7–E11) |
| **Medido** | `timeout` curto gera `TIMED_OUT` observável neste tipo | E8 (sem worker); E14.4–E14.6 (com worker, mede a reatribuição) |
| **Medido** | `PATCH` com `changeset.timeout` altera o prazo efetivo | E11 — o prazo encurtou e o Job voltou antes do `deadline` original |
| **Medido** | Tempo de resposta com `requestTimeout: -1` vs. positivo | E10 e E12: 4 ms vs. 2 350 / 2 302 / 2 397 ms |

Na versão de rascunho estas três linhas diziam **A medir**. O texto acima registra o que a execução
efetivamente entregou; as formulações antigas permanecem visíveis na seção **Correções** de
`evidence.md` (itens 5 e 6) e no histórico de revisão de `lesson.md`.

## Resultado da execução (19/11/2026)

| Item do escopo | Situação | Evidência |
|---|---|---|
| BPMN `lesson-005-lifecycle` com `retries="3"` | feito | E7 |
| Pré-checagem `CREATED: 0` | feito, antes e depois | E6, E9, E12 |
| Ativação curta com captura de `jobKey`/`deadline`/`retries` | feito | E7 |
| `TIMED_OUT` do mesmo `jobKey`, `retries` preservado | feito | E8 |
| Reativação com mesmo `jobKey` | feito | E9 |
| `completion` `204`, instância `COMPLETED`, `CREATED: 0` | feito | E9, E12, E13 |
| `requestTimeout: -1` vs. positivo medido | feito | E10 |
| `PATCH` com `changeset.timeout` | feito, com correção de interpretação | E11, Correções |
| Diagrama de estados + Diagram Review | feito, veredito `WARN` com 4 WARNs explícitos | lesson, Diagram Review |
| Coleção Postman + validador + runner | feito | E12 |
| `503`/`429` por carga | `N/A` com motivo — sem carga | evidence, N/A |
| Módulo `apps/lifecycle-worker` + BPMN Java | feito na reabertura | E14.1–E14.3 |
| 13 testes Java + prova por mutação | feito | E14.2 |
| Mesma `jobKey` reativada após `deadline`, `retries` preservado | feito em 2 execuções | E14.4–E14.6 |
| `state: TIMED_OUT` no worker Java | `N/A` — janela de 793 ms e projeção | E14.7 |
| 2 instâncias `COMPLETED`, `CREATED: 0` | feito | E14.8 |

**Correção de escopo registrada:** o rascunho dizia que o PATCH poderia "estender o deadline
observado". A execução mostrou o contrário: o PATCH encurta o prazo e o `deadline` da reativação
pertence ao novo lease. A afirmação errada permanece visível na seção **Correções** da evidence.

## Constraints

- Java 21+, Maven e cluster local 8.9.x conforme ADR-0002. ~~**nenhum módulo Java novo**~~
  **Correção da reabertura:** `apps/lifecycle-worker` é novo, autônomo e não altera 002/003.
  Nenhum módulo existente foi modificado.
- Nenhuma instalação global; toolchain via `infra/local/mise.sh`.
- `infra/local/camunda-8.9/` é upstream e não é editado.
- Pré-checagem com `{"filter":{"type":"lesson-005-lifecycle","state":"CREATED"}}`, nunca o search
  sem estado — correção da 004 E10.
- Poll da projeção: a 004 E17 mostrou `esperava 10, veio 2` sem poll. Usar poll, nunca `items[0]`
  como prova de contagem.
- Branch criado de `main` (`8f3c314`), rebaseado sobre `main` (`1f6c1fe`) depois do landing da 004.
  **Correção registrada:** o texto original dizia que a 004 estava "não mergeada" e que a
  implementação dependia dessa precedência. O landing foi feito por decisão explícita do usuário e a
  dependência foi resolvida antes da implementação.

## Verification

- `./infra/local/validate-collections.sh` sem divergência após alterar a coleção.
- Experimento com corpo verbatim das respostas: ativação, `TIMED_OUT`, reativação, `completion`,
  busca final.
- Diagrama Mermaid validado por `docs/validate-mermaid.sh` em cópia isolada; Diagram Review
  preenchido com veredito explícito.
- `evidence.md` só com comando executado e saída real; `N/A` com razão escrita.
- ~~Testes: `N/A` — sem código Java novo; a prova é asserção sobre o corpo da resposta.~~
  **Correção da reabertura:** os corpos continuam sendo a prova REST (E6–E13); o módulo Java tem
  13 testes, incluindo contrato BPMN ↔ anotação e prova por mutação (E14.2).
- Revisão independente focada em três riscos: (1) inventar `ACTIVATED`, (2) confundir os dois
  relógios, (3) chamar `503` de "observado" sem carga.

## Dependencies

- Lesson 003 `completed` e Lesson 004 `completed` em **main** antes da implementação. Na data de
  criação deste branch, 004 estava `completed` na branch própria mas `main` ainda não a continha;
  **corrigido** pelo merge local `1f6c1fe` e pelo rebase desta branch, antes de qualquer execução.
- Fronteira de escopo: 004 = entrega exclusiva, 005 = estado/prazo do Job, 014 = configuração Java,
  015 = idempotência. A reabertura Java usa `@JobWorker` como mecanismo, sem cruzar a fronteira.

## Related lesson

`docs/lessons/005-ciclo-vida-job-backpressure/lesson.md`
