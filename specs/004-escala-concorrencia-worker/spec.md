# SPEC-004 — Escala e concorrência do worker

## Status

**Completed**. Implementado e executado; três rodadas de revisão independente terminaram em
`APPROVED` sem blockers (E14). Escopo aprovado antes da implementação. Nenhum módulo Java novo foi
escrito. Promovida sem commit, push ou PR.

## Goal

Ensinar que escalar consumo de Jobs em Camunda 8 é **multiplicar clientes**, não multiplicar o
motor: vários workers podem pedir o mesmo `job type`, o motor entrega cada Job a **um único**
deles, o limite de vazão vive no **request de ativação**, e o excedente fica **enfileirado no
Broker** até outro cliente pedir.

A frase que a lesson tem de fazer o leitor internalizar:

> Dois workers do mesmo `job type` **não** recebem o mesmo Job. Quem decide a entrega é o motor;
> quem define o tamanho do lote é o cliente, por ativação. O que sobra espera no log.

## O problema primeiro (ordem causal)

A Lesson 003 terminou com um worker, um tipo de Job e um incidente recuperado. A pergunta natural
de produção é a seguinte: se o volume de instâncias crescer, **o que eu aumento**? A resposta
ingênua é "o banco" ou "o Zeebe". A resposta correta começa em outro lugar: o worker é um
**cliente**, e foi ele quem assumiu o ritmo. Antes de falar de `maxJobsActive` ou de thread pool —
que são tema da 014 — é preciso provar o contrato de entrega: exclusividade, limite por request e
fila.

### Eixo C7 → 8

**Fato (C7, doc 8.7, verificada na Lesson 003)** — o *job executor* vive dentro do engine e disputa
capacidade da transação de banco que cobre a distância entre *wait states*. Fonte:
https://docs.camunda.io/docs/8.7/components/best-practices/development/understanding-transaction-handling-c7/ .

**Fato (C8, doc 8.9, verificada nesta sessão)** — o cliente pede Jobs por polling e o motor
garante a entrega exclusiva:

> "Many workers can request the same job type to scale up processing. In this scenario, Zeebe
> ensures each job is sent to only one of the workers."

> "Zeebe queues all jobs during any job worker outages, and progress resumes as soon as workers
> are available."

Fonte: https://docs.camunda.io/docs/8.9/components/concepts/job-workers/ .

**Interpretação (decisão de ensino)** — a fronteira ACID compartilhada perdida aparece aqui como
**causa remota**, não como manchete: ela explica por que o paralelismo saiu do *job executor* e
foi parar no cliente. "O banco virou log" é consequência e já foi ensinado na 001/003. O eixo novo
desta lesson é **onde o paralelismo é controlado** e **onde o excedente espera**. Uma frase
conectando as duas coisas basta; reconstruir a seção da 003 deslocaria a atenção do conceito novo.

| | Camunda 7 | Camunda 8 |
|---|---|---|
| Onde mora o paralelismo | *job executor* dentro do engine | cliente externo pede Jobs |
| Quem entrega | engine, dentro da transação | motor, com entrega exclusiva a um worker |
| Unidade de vazão | pool / transação | `MaxJobsToActivate` por ativação + `Timeout` como *lease* |
| Excedente | disputa de capacidade do banco | fila no log, à espera de outro pedido |

## Scope

1. BPMN novo, com um service task de tipo próprio da lesson (`lesson-004-scale`), versionado
   junto com o código.
2. **Três clientes lógicos** — três valores distintos de `worker` em chamadas
   `POST /v2/jobs/activation` competindo pelo mesmo `job type`: `w4-a` e `w4-b` em disputa direta,
   `w4-drain` para os 2 restantes. **Zero JVMs novas**: configurar `@JobWorker` é tema da 014.
3. 10 instâncias de processo com `runId` único, para haver fila observável desde a criação.
4. Pré-checagem de zero Jobs do tipo no Broker antes de medir; se houver resíduo, abortar e limpar.
   **Correção executada (evidence E10):** o filtro original `{"filter":{"type":"lesson-004-scale"}}`
   devolveu os 10 Jobs `COMPLETED` da rodada anterior, porque o `search` sem estado inclui Jobs
   concluídos. O filtro usado é `{"filter":{"type":"lesson-004-scale","state":"CREATED"}}`.
   O experimento usa **três** valores de `worker`: `w4-a` e `w4-b` em disputa, mais `w4-drain`
   para os 2 restantes.
5. Ativações com `maxJobsToActivate: 4`, `4` e `2`, capturando `jobKey` e `processInstanceKey` de
   cada resposta.
6. Completar **todos** os Jobs ativados no mesmo script, sem deixar *lease* expirar.
7. Asserção central: conjuntos de `jobKey` **disjuntos**, `4 + 4` ativados, `2` ainda ativáveis,
   e união cobrindo as 10 instâncias capturadas.
8. Requests de coleção que reproduzam o experimento com saída verificável por corpo, não por
   status code.

## Out of scope

Declarado aqui para não crescer durante a execução:

- **Particionamento, múltiplos Brokers, `HashMod`, roteamento dependente de partição** — este
  laboratório tem **1 partição**; nada aqui prova esse comportamento. Fica para a lesson 025.
- **`maxJobsActive`, `pollInterval`, pool de threads, *graceful shutdown*, `Job.YIELD`, métricas
  `camunda.client.worker.job.*`** — configuração e operação de worker Java/Spring Boot, Lesson 014.
- **Estados do Job (`CREATED` → `COMPLETED`/`FAILED`/`TIMED_OUT`) como tese** — Lesson 005. Aqui
  o estado aparece só como instrumento da fila.
- **Retries, incidentes e runbook de operador** — Lesson 003.
- **Idempotência do efeito colateral de negócio** — Lesson 015. A at-least-once é nomeada como
  limitação, não exercitada.
- **Edição de `infra/local/camunda-8.9/`, novo container, instalação global, mudança em
  `.mise.toml`**, modificação de `first-worker` ou `retry-worker` (artefatos de lessons já
  revisadas).
- **Benchmark de vazão, `jobs/s`, timing ou contagem fixa de asserções** — sem medição
  controlada, `N/A` com motivo.

## Requirements

1. O tipo de Job da 004 deve ser distinto dos tipos 002 e 003; a pré-checagem devolve `0`.
2. Cada resposta de `activation` é identificada pelo valor de `worker` enviado; as chaves
   retornadas são lidas do **corpo**, nunca deduzidas do `200`.
3. As asserções usam conjuntos de `jobKey` e os `processInstanceKey` capturados — nunca
   `items[0]`, nunca total de requests.
4. A terceira ativação deve provar que os 2 Jobs restantes continuavam ativáveis; a busca final
   deve mostrar 10 instâncias `COMPLETED`.
5. Todo `jobKey` ativado recebe `completion` no mesmo script; falha de script deixa registro de
   limpeza na evidence.
6. A lesson registra explicitamente que os "workers" são **três clientes lógicos** (dois em disputa
   e um de drenagem), não containers: `docker compose ps` continua mostrando um `orchestration`.
7. Concorrência aqui é **lógica** (dois `worker` names em disputa, mais um de drenagem), não
   simultaneidade temporal de threads; a asserção é disjunção de chaves, não paralelismo medido.
8. Toda evidência em PT-BR, rotulada `Fato` / `Observado` / `Interpretação`.
9. Nenhum caminho ou campo de API é afirmado a partir de doc sem sondagem: o erro da 002 foi
   exatamente esse.

## Chão factual — separação rigorosa

| Classe | Afirmação | Origem |
|---|---|---|
| **Fato (8.9)** | Vários workers podem pedir o mesmo `job type`; cada Job vai a um só worker | doc oficial, URL verificada acima |
| **Fato (8.9)** | Jobs são enfileirados durante indisponibilidade de worker | doc oficial, URL verificada acima |
| **Fato (8.9)** | `MaxJobsToActivate`, `Timeout`, `Worker` e `FetchVariables` são propriedades de ativação | doc oficial, URL verificada acima |
| **Fato (8.9)** | Entrega é *at least once*; worker deve ser idempotente | doc oficial, URL verificada acima |
| **Observado (003)** | `POST /v2/jobs/activation`, `/v2/jobs/search`, `/v2/jobs/{key}/completion` e `/v2/process-instances` existem e respondem | coleção e evidence da 003, reutilizadas |
| **Observado (003)** | `POST /v2/deployments` devolve a key em `deployments[0].processDefinition.processDefinitionKey` | coleção, request de deploy da 003 |
| **A medir (probe)** | Formato exato do corpo de `activation` com `worker` preenchido — o `400 INVALID_ARGUMENT` da coleção só nomeia `type`, `timeout`, `maxJobsToActivate` | não lido ainda; vira tarefa, não afirmação |
| **A medir (probe)** | Se `maxJobsToActivate` é respeitado literalmente na resposta v2 | não medido ainda |
| **A medir (probe)** | Se o Gateway devolve `429`/`503` sob este Volume — backpressure é tema da 005 | `N/A` previsto, com motivo |

## Constraints

- Java 21+, Maven, Spring Boot e cluster local 8.9.x conforme ADR-0002 — mesmo assim, **nenhum
  módulo Java novo** nesta lesson.
- Nenhuma instalação global; toolchain via `infra/local/mise.sh`.
- `infra/local/camunda-8.9/` é material upstream e não é editado.
- Gateway precisa estar *healthy* antes do deploy: confirmar `STATUS healthy` no
  `docker compose ps`. **Correção (evidence E1):** o PLAN dizia `/actuator/health/status`, mas
  esse caminho e `/actuator/health` respondem `404` na 8.9.22.
- Se a rota VPN capturar a rede Docker na porta `8080`, medir com `curlimages/curl` dentro da
  rede `camunda` e registrar a limitação — nunca fingir execução.

## Verification

- `./infra/local/validate-collections.sh` sem divergência.
- Experimento executado com corpo de resposta capturado para as três ativações.
- Bloco Mermaid novo renderizado com `mmdc`; `docs/validate-mermaid.sh` em **cópia isolada** verde
  (11/0). No repositório real o script reporta 51 blocos e 2 falhas em READMEs de
  `.opencode/node_modules`, não versionados — ver **E13**. Diagram Review preenchido com veredito
  `WARN` por causa dessa ressalva de ferramenta.
- `evidence.md` só com comando executado e saída real; `N/A` com razão escrita.
- Testes: `N/A` nesta lesson — não há código Java novo. A prova é a asserção sobre o corpo da
  resposta do Gateway. Registrar o motivo, não omitir.

## Dependencies

- Lesson 003 `completed` (coleção, runner, padrão de evidence, correções de probe).
- Fronteira de escopo acordada com `course-architect`: 004 = mecanismo de escala, 005 = ciclo de
  vida do Job, 014 = configuração e observabilidade Java.

## Related lesson

`docs/lessons/004-escala-concorrencia-worker/lesson.md`
