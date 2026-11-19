# Evidência — LESSON-005

Status: **executado e revisado em 19/11/2026**. E1–E5 são o chão factual levantado antes da implementação;
E6–E13 registram o experimento REST **realmente executado nesta lesson**, com corpos verbatim, mais a
execução da coleção, do validador e a checagem final dos estados. **E14 registra a reabertura da
lesson para incluir um worker Java mínimo**, com duas execuções reais. A seção E5 mantém a
proveniência explícita da observação da Lesson 004 e **não** é apresentada como medição deste branch.

Convenção: **Fato** = documentação oficial com versão e URL verificada por `webfetch`; **Observado**
= saída de comando executado; **Interpretação** = raciocínio do autor. Correções de afirmações que a
execução refutou ficam visíveis na seção **Correções**, no fim deste arquivo — nunca são apagadas.

---

## E1 — Fato (8.9): ativação é um prazo, não uma conclusão

URL verificada por `webfetch` em 19/11/2026:
https://docs.camunda.io/docs/8.9/components/concepts/job-workers/

Trechos verbatim (não traduzidos, conforme AGENTS.md):

```text
Such a job is considered activated until the job is completed, failed, or the job activation times out.
```

```text
Worker: The identifier of the worker used for auditing purposes.

Timeout: The time a job is assigned to the worker. If a job is not completed within this time, it can be reassigned by Zeebe to another worker.

MaxJobsToActivate: The maximum number of jobs which should be activated by this request.

FetchVariables: A list of required variable names. If the list is empty, all variables of the process instance are requested.
```

```text
With long polling, a request is kept open while no jobs are available. The request is completed when at least one job becomes available.

Long polling is set during job activation with the parameter request-timeout.
```

```text
If the job is not completed or failed within the configured job activation timeout, Zeebe reassigns the job to another job worker. This does not affect the number of `remaining retries`.

A timeout may lead to two different workers working on the same job, possibly at the same time. If this occurs, only one worker successfully completes the job. The other `complete job` command is rejected with a `NOT FOUND` error.
```

```text
The fact that jobs may be worked on more than once means that Zeebe is an "at least once" system with respect to job delivery and that worker code must be idempotent.
```

```text
Zeebe queues any job that has no registered stream for its type in an internal backlog (the `ACTIVATABLE` state), and long polling (via the `ActivateJobs` RPC) is the only path that serves this backlog.
```

```text
Because workers request jobs, they have full control over the rate at which they take on new jobs.
```

**Interpretação:** `ACTIVATABLE` é citado como estado interno do backlog, mas isso não autoriza
afirmar que a REST o devolve como `state`. Ver E3.

---

## E2 — Fato (8.9): endpoint de ativação e o `503`

URL verificada por `webfetch` em 19/11/2026:
https://docs.camunda.io/docs/8.9/apis-tools/orchestration-cluster-api-rest/specifications/activate-jobs/

```text
POST /jobs/activation

Strongly Consistent

Iterate through all known partitions and activate jobs up to the requested maximum.

Responses: 200, 400, 401, 500, 503
```

`200` é a lista de Jobs ativados. **`503` é um código documentado**, mas não foi provocado nem
medido neste laboratório — ver a ressalva em E4 e o `N/A` no fim deste arquivo.

---

## E3 — Observado: spec viva do cluster (campos e estados)

Comando executado em 19/11/2026:

```text
$ curl -sS --max-time 20 'http://localhost:8080/v3/api-docs/Orchestration%20Cluster%20API' -o /tmp/opencode/spec-005.json
$ jq -c '{title:.info.title, version:.info.version, paths:(.paths|keys|map(select(test("jobs/activation|jobs/search|jobs/\\{jobKey\\}/fail|jobs/\\{jobKey\\}/complete"))))}' /tmp/opencode/spec-005.json
{"title":"Orchestration Cluster API","version":"v2","paths":["/v2/jobs/activation","/v2/jobs/search","/v2/jobs/{jobKey}/failure"]}
```

A regex não listou `/completion` por coincidência de recorte; listagem direta confirma os dois
caminhos:

```text
$ jq -r '.paths|keys[]|select(startswith("/v2/jobs"))' /tmp/opencode/spec-005.json
/v2/jobs/activation
/v2/jobs/search
/v2/jobs/statistics/by-types
/v2/jobs/statistics/by-workers
/v2/jobs/statistics/errors
/v2/jobs/statistics/global
/v2/jobs/statistics/time-series
/v2/jobs/{jobKey}
/v2/jobs/{jobKey}/completion
/v2/jobs/{jobKey}/error
/v2/jobs/{jobKey}/failure
```

**`JobActivationRequest`** — required: `maxJobsToActivate`, `timeout`, `type`. Descrições verbatim:

```text
worker: "The name of the worker activating the jobs, mostly used for logging purposes."

timeout: "A job returned after this call will not be activated by another call until the timeout (in ms) has been reached."

maxJobsToActivate: "The maximum jobs to activate by this request."

requestTimeout: "The request will be completed when at least one job is activated or after the requestTimeout (in ms). If the requestTimeout = 0, a default timeout is used. If the requestTimeout < 0, long polling is disabled and the request is completed immediately, even when no job is activated."
```

**`JobStateEnum`** — oito valores, nenhum `ACTIVATED`:

```text
CANCELED, COMPLETED, CREATED, ERROR_THROWN, FAILED, MIGRATED, RETRIES_UPDATED, TIMED_OUT
```

**`ActivatedJobResult`** — campos obrigatórios relevantes: `jobKey`, `worker`, `retries`,
`deadline`, `variables`, `processInstanceKey`. Descrição verbatim de `deadline`:

```text
"When the job can be activated again, sent as a UNIX epoch timestamp."
```

**`PATCH /v2/jobs/{jobKey}`** — `Update job`. Corpo `changeset` com `retries` e `timeout`
nullable; descrição verbatim:

```text
"JSON object with changed job attribute values. The job cannot be completed or failed with this endpoint, use the complete job or fail job endpoints instead."
```

**`POST /v2/jobs/{jobKey}/failure`** — corpo `JobFailRequest` com `retries` (default `0`),
`errorMessage`, `retryBackOff` (default `0`) e `variables`.

**Interpretação:** a spec viva é fonte para **formato de request**, não para comportamento
observado. Nenhum desses campos foi exercitado até agora nesta lesson.

---

## E4 — Fato (8.9): backpressure do broker e do streaming

URLs verificadas por `webfetch` em 19/11/2026:

- https://docs.camunda.io/docs/8.9/components/zeebe/technical-concepts/internal-processing/
- https://docs.camunda.io/docs/8.9/apis-tools/orchestration-cluster-api-rest/specifications/create-deployment/

Trechos verbatim da página de processamento interno:

```text
When a broker receives a client request, it is written to the event stream first, and processed later by the stream processor. If the processing is slow or if there are many client requests in the stream, it might take too long for the processor to start processing the command.
```

```text
Backpressure is indicated to the client by throwing a resource exhausted exception. If a client sees this exception, it can retry the requests with an appropriate retry strategy.
```

```text
There is no fixed limit configured in Zeebe for the maximum rate of requests it accepts. Instead, Zeebe uses an adaptive algorithm to dynamically determine the limit of the number of in-flight requests.
```

```text
When backpressure is active, the broker may reject any request except CompleteJob RPC and FailJob RPC. These requests are allowed during backpressure and are always accepted by the broker even if it is receiving requests above its limits.
```

Trecho verbatim da página de deploy (representa a resposta `503` na REST):

```text
The service is currently unavailable. This may happen only on some requests where the system creates backpressure to prevent the server's compute resources from being exhausted, avoiding more severe failures. In this case, the title of the error object contains RESOURCE_EXHAUSTED. Clients are recommended to eventually retry those requests after a backoff period.
```

Trecho verbatim da página de job workers, seção *Backpressure* (mecanismo diferente — push/stream):

```text
The gateway only sends as much data as the client can consume over a specific connection. If it notices its send buffers fill up, it marks a client as not-ready.
```

**Interpretação:** são dois usos da mesma palavra: limite adaptativo de requests no broker
(`RESOURCE_EXHAUSTED`) e controle de fluxo no caminho de streaming. A lesson ensina o controle de
ritmo pelo cliente; não afirma ter medido nenhum dos dois.

---

## E5 — Observado em 17/11/2026, proveniência da Lesson 004 (repetido na 005)

**Não é medição deste branch.** Este branch foi criado de `main` (`8f3c314`) e não contém os
arquivos da 004; a observação vem do commit `afb9384` na branch
`lesson/004-escala-concorrencia-worker`, evidence E18 daquela lesson. Está aqui apenas para
fundamentar o problema. Na implementação da 005 o cenário foi recriado com tipo próprio
(`lesson-005-lifecycle`), com resultado em E7–E11.

```text
$ ... -X POST .../v2/jobs/search -d '{"filter":{"type":"lesson-004-scale"},...}' | jq ...
{"total":89,"por_estado":[{"state":"COMPLETED","total":85},{"state":"TIMED_OUT","total":4}]}

$ ... -X POST .../v2/jobs/search -d '{"filter":{"type":"lesson-004-scale","state":"TIMED_OUT"}}' | jq ...
{"jobKey":"2251799813697730","state":"TIMED_OUT","worker":"w4-a","retries":3,"deadline":"2026-11-17T20:52:29.023Z",...}
{"jobKey":"2251799813697739","state":"TIMED_OUT","worker":"w4-a","retries":3,"deadline":"2026-11-17T20:52:29.023Z",...}
{"jobKey":"2251799813697747","state":"TIMED_OUT","worker":"w4-a","retries":3,"deadline":"2026-11-17T20:52:29.023Z",...}
{"jobKey":"2251799813697758","state":"TIMED_OUT","worker":"w4-a","retries":3,"deadline":"2026-11-17T20:52:29.023Z",...}
```

```text
$ ... -X POST .../v2/jobs/activation -d '{"worker":"w4-recupera","type":"lesson-004-scale","timeout":120000,"maxJobsToActivate":4}'
os mesmos 4 jobKey, worker w4-recupera, retries 3

$ para cada jobKey: ... -X POST .../v2/jobs/$k/completion -d '{}'
2251799813697730 -> 204
2251799813697739 -> 204
2251799813697747 -> 204
2251799813697758 -> 204

$ ... /v2/jobs/search (type lesson-004-scale)
{"total":89,"por_estado":[{"state":"COMPLETED","total":89}]}
$ ... /v2/jobs/search (state CREATED)
0
```

**Interpretação:** `TIMED_OUT` não é perda, não consome retry e devolve o mesmo `jobKey`. A
recuperação confirmou os três pontos; a explicação do *porquê* é o conteúdo desta lesson.

---

## E6 — Observado: pré-checagem, compose real e um dead end de caminho

Comandos executados em 19/11/2026:

```text
$ curl -sS --max-time 15 -X POST http://localhost:8080/v2/jobs/search \
    -H 'Content-Type: application/json' \
    -d '{"filter":{"type":"lesson-005-lifecycle","state":"CREATED"}}' | jq -c '.page.totalItems'
0
```

**Dead end registrado:** o primeiro `docker compose ps` usou `compose.yaml` e falhou:

```text
$ docker compose -f infra/local/camunda-8.9/compose.yaml ps --format table
compose file "/home/placido/desarrollo/camunda/camunda8-learning-lab/infra/local/camunda-8.9/compose.yaml" is invalid: open /home/placido/desarrollo/camunda/camunda8-learning-lab/infra/local/camunda-8.9/compose.yaml: no such file or directory
```

O arquivo vendorizado é `docker-compose.yaml`. Comando correto e saída real:

```text
$ docker compose -f infra/local/camunda-8.9/docker-compose.yaml ps --format table
NAME            IMAGE                              COMMAND                  SERVICE         CREATED      STATUS                    PORTS
connectors      camunda/connectors-bundle:8.9.14   "/start.sh"              connectors      2 days ago   Up 35 minutes (healthy)   0.0.0.0:8086->8080/tcp, [::]:8086->8080/tcp
orchestration   camunda/camunda:8.9.22             "/usr/local/camunda/…"   orchestration   2 days ago   Up 35 minutes (healthy)   0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp, 0.0.0.0:9600->9600/tcp, [::]:9600->9600/tcp, 0.0.0.0:26500->26500/tcp, [::]:26500->26500/tcp, 26501-26502/tcp
```

**Interpretação:** dois containers, mas um único processo Java de orquestração. `connectors` é outro
componente, não um worker deste experimento. Nenhum container de worker Java existe ou foi criado.

## E7 — Observado: deploy, instância e ativação curta

BPMN versionado em
`processes/005-ciclo-vida-job-backpressure/lesson-005-lifecycle.bpmn`, com
`<zeebe:taskDefinition type="lesson-005-lifecycle" retries="3"/>`.

Deploy — corpo verbatim:

```json
{"deploymentKey":"2251799813720335","tenantId":"<default>","deployments":[{"processDefinition":{"processDefinitionId":"lesson-005-ciclo-vida-job","processDefinitionVersion":1,"resourceName":"lesson-005-lifecycle.bpmn","tenantId":"<default>","processDefinitionKey":"2251799813720336"},"decisionDefinition":null,"decisionRequirements":null,"form":null,"resource":null}]}
```

Criação de uma instância — corpo verbatim:

```json
{"processDefinitionId":"lesson-005-ciclo-vida-job","processDefinitionVersion":1,"tenantId":"<default>","variables":{},"processDefinitionKey":"2251799813720336","processInstanceKey":"2251799813720339","tags":[],"businessId":null}
```

Poll da projeção até o Job aparecer — saída real:

```text
poll 1: CREATED=0
poll 2: CREATED=0
poll 3: CREATED=1
```

Ativação com `timeout: 4000`, `requestTimeout: -1`, `worker: w5-curto`, um Job — corpo verbatim:

```json
{"jobs":[{"type":"lesson-005-lifecycle","processDefinitionId":"lesson-005-ciclo-vida-job","processDefinitionVersion":1,"elementId":"ServiceTask_Lifecycle","customHeaders":{},"worker":"w5-curto","retries":3,"deadline":1795109896146,"variables":{"runId":"005-lifecycle","seq":1},"tenantId":"<default>","jobKey":"2251799813720346","processInstanceKey":"2251799813720339","processDefinitionKey":"2251799813720336","elementInstanceKey":"2251799813720345","kind":"BPMN_ELEMENT","listenerEventType":"UNSPECIFIED","userTask":null,"tags":[],"rootProcessInstanceKey":"2251799813720339"}]}
```

```text
$ date -u -d @$((1795109896146/1000)) '+%Y-%m-%dT%H:%M:%SZ'
2026-11-19T17:38:16Z
$ date -u '+%Y-%m-%dT%H:%M:%SZ'
2026-11-19T17:38:30Z
```

**Observado:** `deadline = 1795109896146` (epoch ms), `retries = 3`, `jobKey = 2251799813720346`.
**Interpretação (corrigida na revisão independente):** `creationTime = 2026-11-19T17:38:05.720Z`
(E8) é o instante de **criação** do Job, não de ativação. Com `timeout = 4000`, o `deadline`
`1795109896146` (17:38:16.146Z) implica ativação às **17:38:12.146Z**, 6 426 ms depois da criação
— intervalo compatível com o poll da projeção. A lesson não capturou o instante da ativação com
relógio local; ele é inferido de `deadline − timeout`. O `date -u` logo em seguida (17:38:30Z) só
confirma que o prazo já havia expirado na leitura do relógio. A versão errada deste parágrafo fica
registrada na seção **Correções**, item 5.

## E8 — Observado: `TIMED_OUT` para o mesmo `jobKey`, com `retries` intacto

```text
$ curl -sS --max-time 15 -X POST http://localhost:8080/v2/jobs/search \
    -H 'Content-Type: application/json' \
    -d '{"filter":{"type":"lesson-005-lifecycle","state":"TIMED_OUT"}}'
{"page":{"totalItems":1,"hasMoreTotalItems":false,"startCursor":"WzIyNTE3OTk4MTM3MjAzNDZd","endCursor":"WzIyNTE3OTk4MTM3MjAzNDZd"},"items":[{"customHeaders":{},"deadline":"2026-11-19T17:38:16.146Z","deniedReason":null,"elementId":"ServiceTask_Lifecycle","elementInstanceKey":"2251799813720345","endTime":null,"errorCode":"","errorMessage":"","hasFailedWithRetriesLeft":false,"isDenied":null,"jobKey":"2251799813720346","kind":"BPMN_ELEMENT","listenerEventType":"UNSPECIFIED","processDefinitionId":"lesson-005-ciclo-vida-job","processDefinitionKey":"2251799813720336","processInstanceKey":"2251799813720339","rootProcessInstanceKey":"2251799813720339","retries":3,"state":"TIMED_OUT","tenantId":"<default>","type":"lesson-005-lifecycle","worker":"w5-curto","creationTime":"2026-11-19T17:38:05.720Z","lastUpdateTime":"2026-11-19T17:38:16.780Z"}]}
```

**Observado:** o `jobKey` devolvido é **exatamente** `2251799813720346`, o mesmo da ativação de E7.
`state: TIMED_OUT`, `retries: 3` (inalterado), `worker: w5-curto` (o último que ativou),
`deadline` agora em ISO `2026-11-19T17:38:16.146Z`.

Comparação das duas representações do mesmo instante, executada em Node:

```text
$ node -e "console.log(new Date('2026-11-19T17:38:16.146Z').getTime(), 1795109896146, new Date('2026-11-19T17:38:16.146Z').getTime()===1795109896146)"
1795109896146 1795109896146 true
```

**Interpretação:** não há divergência de motor entre `deadline: 1795109896146` da ativação e
`deadline: "2026-11-19T17:38:16.146Z"` do search. São epoch ms e ISO do mesmo instante. Compará-los
como texto seria erro de leitura. `lastUpdateTime` 17:38:16.780Z é posterior ao `deadline`: a
projeção registrou a transição depois de o prazo expirar.

## E9 — Observado: reativação devolve a mesma key; completion encerra

Reativação com `worker: w5-recupera`, `timeout: 120000`, `requestTimeout: -1` — corpo verbatim:

```json
{"jobs":[{"type":"lesson-005-lifecycle","processDefinitionId":"lesson-005-ciclo-vida-job","processDefinitionVersion":1,"elementId":"ServiceTask_Lifecycle","customHeaders":{},"worker":"w5-recupera","retries":3,"deadline":1795110042507,"variables":{"runId":"005-lifecycle","seq":1},"tenantId":"<default>","jobKey":"2251799813720346","processInstanceKey":"2251799813720339","processDefinitionKey":"2251799813720336","elementInstanceKey":"2251799813720345","kind":"BPMN_ELEMENT","listenerEventType":"UNSPECIFIED","userTask":null,"tags":[],"rootProcessInstanceKey":"2251799813720339"}]}
```

Completion e verificação final — saída real:

```text
completion HTTP 204
poll 1: {"total":1,"por_estado":[{"state":"ACTIVE","total":1}]}
poll 2: {"total":1,"por_estado":[{"state":"COMPLETED","total":1}]}
0
```

O `0` final é `.page.totalItems` do search com
`{"filter":{"type":"lesson-005-lifecycle","state":"CREATED"}}`.

**Observado:** mesmo `jobKey` (`2251799813720346`), `retries: 3`, `worker` trocado, novo `deadline`
`1795110042507` (maior que `1795109896146`), `completion` com `204`, instância `COMPLETED` e nenhum
Job `CREATED`.

**Interpretação:** a tese da lesson fechou no experimento desta branch — não apenas na proveniência da
004. O `TIMED_OUT` devolveu a entidade ao pool; a reativação pegou a mesma entidade e o completion a
encerrou. O poll por **estado** (`COMPLETED`), não por existência, foi necessário: o `poll 1` mostrou a
instância `ACTIVE` mesmo depois do `204`.

## E10 — Observado: `requestTimeout` é o relógio da request

Com a fila vazia (nenhum Job `CREATED`), duas ativações idênticas exceto por `requestTimeout`:

```text
$ for rt in -1 2000; do ... curl -w 'HTTP %{http_code}, time_total %{time_total}s' ...; done
requestTimeout=-1 -> HTTP 200, time_total 0.004011s
{"total":0}
requestTimeout=2000 -> HTTP 200, time_total 2.192381s
{"total":0}
```

Ambas devolveram `{"jobs":[]}` (corpo verbatim dos dois arquivos salvos). O runner da coleção mediu
novamente, com `Date.now()` em volta de `pm.sendRequest`:

```text
· resposta imediata com requestTimeout -1: 4ms
· request esperou a fila com requestTimeout 2000: 2350ms
```

**Observado:** `-1` respondeu em 0.004011 s (4 ms no runner); `2000` respondeu em 2.192381 s
(2 350 ms no runner). As duas medições são de execuções diferentes, na mesma máquina e na mesma
sessão — os números não são idênticos e não deveriam ser.

**Interpretação:** o tempo medido é o da **HTTP request de ativação**, não o prazo do Job e não vazão.
`timeout` delimita o empréstimo; `requestTimeout` delimita a espera da request quando a fila está
vazia. A documentação oficial (E1) e a spec viva (E3) já diziam isso; agora há medição.

## E11 — Observado: `PATCH /v2/jobs/{jobKey}` encurta o lease

Segunda instância (`2251799813720392`, `runId: 005-patch`), ativação com `timeout: 120000` e
`worker: w5-patch` — corpo verbatim:

```json
{"jobs":[{"type":"lesson-005-lifecycle","processDefinitionId":"lesson-005-ciclo-vida-job","processDefinitionVersion":1,"elementId":"ServiceTask_Lifecycle","customHeaders":{},"worker":"w5-patch","retries":3,"deadline":1795110065449,"variables":{"runId":"005-patch","seq":2},"tenantId":"<default>","jobKey":"2251799813720399","processInstanceKey":"2251799813720392","processDefinitionKey":"2251799813720336","elementInstanceKey":"2251799813720398","kind":"BPMN_ELEMENT","listenerEventType":"UNSPECIFIED","userTask":null,"tags":[],"rootProcessInstanceKey":"2251799813720392"}]}
```

```text
$ curl ... -X PATCH http://localhost:8080/v2/jobs/2251799813720399 \
    -H 'Content-Type: application/json' -d '{"changeset":{"timeout":3000}}'
PATCH HTTP 204
```

Após `sleep 5`, reativação com `worker: w5-patch-2` e `timeout: 60000` — corpo verbatim:

```json
{"jobs":[{"type":"lesson-005-lifecycle","processDefinitionId":"lesson-005-ciclo-vida-job","processDefinitionVersion":1,"elementId":"ServiceTask_Lifecycle","customHeaders":{},"worker":"w5-patch-2","retries":3,"deadline":1795110023230,"variables":{"runId":"005-patch","seq":2},"tenantId":"<default>","jobKey":"2251799813720399","processInstanceKey":"2251799813720392","processDefinitionKey":"2251799813720336","elementInstanceKey":"2251799813720398","kind":"BPMN_ELEMENT","listenerEventType":"UNSPECIFIED","userTask":null,"tags":[],"rootProcessInstanceKey":"2251799813720392"}]}
```

```text
ativa  deadline: 2026-11-19T17:41:05Z
reativa deadline: 2026-11-19T17:40:23Z
hora atual: 2026-11-19T17:39:29Z
completion HTTP 204
0
{"total":2,"por_estado":[{"state":"ACTIVE","total":1},{"state":"COMPLETED","total":1}]}
```

(Após novo poll: `{"total":2,"por_estado":[{"state":"COMPLETED","total":2}]}`.)

**Observado:** o Job voltou 5 s depois do PATCH, embora o `deadline` original (17:41:05Z) ainda estivesse
no futuro. `retries: 3` e o mesmo `jobKey` (`2251799813720399`). **Correção necessária:** o
`deadline` da reativação, `1795110023230` (17:40:23Z), pertence ao **novo** `timeout: 60000` daquela
ativação — não é o `deadline` produzido pelo PATCH. A prova de que o PATCH funcionou é o Job ter
voltado antes do prazo original de 120 s, não uma comparação direta entre os dois `deadline`. Ver a
seção **Correções**.

## E12 — Observado: coleção versionada, validador e runner

Artefatos: pasta `62 — Ciclo de vida do Job (Lesson 005)` em
`infra/local/collections/camunda8-lab.postman_collection.json`, oito variáveis `life*` e o BPMN em
`processes/005-ciclo-vida-job-backpressure/lesson-005-lifecycle.bpmn`.

```text
$ ./infra/local/validate-collections.sh
...
OK: 58 request(s) conferem com a spec viva do cluster.
Lembre: isso valida caminho e método. Não valida se a asserção de cada
request reflete o comportamento real — só a execução da coleção prova isso.
```

```text
$ node infra/local/collections/run-collections.mjs --folder "62 —"
...
================================================================
12 request(s), 31 asserção(ões), 0 request(s) com falha
infra/local/collections/camunda8-lab.postman_collection.json
```

Saída integral do runner salva em `/tmp/opencode/005-runner.txt` no momento da execução. Os 12
requests passaram, incluindo `mesmo jobKey voltou`, `retries intactos depois do timeout`,
`deadline ISO do search e epoch da ativacao sao o mesmo instante`,
`request esperou a fila com requestTimeout 2000: 2350ms` e
`Job voltou antes do deadline original`.

**Observado:** o runner não expõe `pm.response.responseTime`. As asserções de tempo usam `Date.now()`
em volta de `pm.sendRequest` (E10), e o request `07` não chama `pm.response.json()` porque o
`completion` responde `204` sem corpo.

**Reprodutibilidade — segunda execução real:**

```text
$ node infra/local/collections/run-collections.mjs --folder "62 —"
...
· resposta imediata com requestTimeout -1: 4ms
· request esperou a fila com requestTimeout 2000: 2302ms
================================================================
12 request(s), 31 asserção(ões), 0 request(s) com falha
```

Saída integral em `/tmp/opencode/005-runner-2.txt`. Novas instâncias (`2251799813720885` e
`2251799813720908`), nenhuma key repetida da primeira rodada. Verificação posterior:

```text
$ curl ... /v2/jobs/search -d '{"filter":{"type":"lesson-005-lifecycle","state":"CREATED"}}'
0
```

**Interpretação:** a coleção não é decoração da lesson: ela reproduz o experimento e as asserções
são sobre o corpo e sobre a identidade do `jobKey`, nunca sobre `200`. Rodar a pasta uma segunda vez
é seguro — a pré-checagem filtra por `state: CREATED` e o último request exige `CREATED: 0`. Os tempos
de request variam entre execuções (2 350 ms e 2 302 ms), como esperado para medição de tempo.

## E13 — Observado: estados finais dos Jobs da 005 no cluster

Verificação feita depois das duas execuções, para ancorar o diagrama de transições em medição direta:

```text
$ curl -sS --max-time 15 -X POST http://localhost:8080/v2/jobs/search \
    -H 'Content-Type: application/json' \
    -d '{"filter":{"type":"lesson-005-lifecycle","state":"COMPLETED"}}' | jq -c '{total:.page.totalItems, amostra:[.items[]|{jobKey,state,retries}]}'
{"total":4,"amostra":[{"jobKey":"2251799813720346","state":"COMPLETED","retries":3},{"jobKey":"2251799813720399","state":"COMPLETED","retries":3},{"jobKey":"2251799813720594","state":"COMPLETED","retries":3},{"jobKey":"2251799813720619","state":"COMPLETED","retries":3}]}
```

As duas primeiras keys são da execução manual (E7–E11); as duas últimas, da primeira execução do
runner (E12). Depois da segunda execução do runner o mesmo comando devolveu:

```text
{"total":6,"amostra":[{"jobKey":"2251799813720346","state":"COMPLETED","retries":3},{"jobKey":"2251799813720399","state":"COMPLETED","retries":3},{"jobKey":"2251799813720594","state":"COMPLETED","retries":3},{"jobKey":"2251799813720619","state":"COMPLETED","retries":3},{"jobKey":"2251799813720892","state":"COMPLETED","retries":3},{"jobKey":"2251799813720915","state":"COMPLETED","retries":3}]}
```

Duas keys novas, nenhuma repetida, todas com `retries: 3`. O total cresce de 4 para 6 porque cada
execução do experimento usa **dois** Jobs (o do timeout e o do PATCH).

**Interpretação:** `COMPLETED` é um estado de Job observado nesta lesson, não apenas um valor do enum
da spec viva. Isso fecha a ponta `completion → COMPLETED` do diagrama com medição, sem depender da
004.


---

## E14 — Reabertura da lesson: worker Java mínimo (19/11/2026)

**Observado — decisão de escopo.** A versão REST-only foi promovida a `completed` e afirmava,
literalmente, *"não escreve código Java"*, *"Aqui não há JVM nova"* e *"Testes: `N/A` — sem código
Java novo"*. O usuário pediu um projeto Java; a lesson voltou a `ready`. As três afirmações antigas
foram **mantidas visíveis** em `lesson.md` e `spec.md`, com a correção ao lado. Nada de E1–E13 foi
apagado ou reescrito.

### E14.1 — A API do SDK fixado, conferida localmente

Comando executado: `javap` sobre os jars `io.camunda:*` 8.9.22 presentes em `~/.m2/repository`:

```text
public interface io.camunda.client.annotation.JobWorker extends java.lang.annotation.Annotation {
  public abstract java.lang.String type();
  public abstract long timeout();
  public abstract int maxJobsActive();
  public abstract long requestTimeout();
  public abstract boolean[] autoComplete();
  ...
}
```

Trecho de `camunda-spring-boot-starter-8.9.22-sources.jar`,
`io/camunda/client/annotation/JobWorker.java`:

```text
  /**
   * Set the time (in milliseconds) for how long a job is exclusively assigned for this worker.
   * During this time, the job cannot be assigned by other workers to ensure that only one worker
   * works on the job. When the time is over, then the job can be assigned again by this or other
   * worker if it's not completed yet. If no timeout is set, then the default is used from the
   * {@link CamundaClientConfiguration}
   */
  long timeout() default -1L;
```

**Interpretação:** o `timeout` da anotação é o mesmo empréstimo da E1, expresso em milissegundos do
lado Java. Não é `requestTimeout`: esse pertence à configuração do cliente. Por honestidade, o
`application.yml` do módulo **herda** `camunda.client.worker.defaults.request-timeout: 30s` do padrão
de `apps/first-worker` e `apps/retry-worker`; é boilerplate copiado para o worker subir, não um
mecanismo ensinado, medido ou experimentado na 005. `pollInterval`, concorrência de handler e
métricas ficam na 014.

**Limite de fonte — registrado honestamente:** as tentativas de `webfetch` em
`https://docs.camunda.io/docs/8.9/apis-tools/java-client/job-workers/` e em
`https://docs.camunda.io/docs/8.9/apis-tools/java-client/` devolveram, respectivamente, `404` e
`403`. Não há URL oficial verificada para este detalhe. Por isso o rótulo acima é **Observado
(código-fonte local do SDK 8.9.22)**, não **Fato**.

### E14.2 — Testes do módulo e prova por mutação

Comando: `./infra/local/mise.sh exec -- mvn -f apps/lifecycle-worker/pom.xml test`.

```text
[INFO] Running com.camunda8.lab.lifecycleworker.BpmnContractTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.171 s -- in com.camunda8.lab.lifecycleworker.BpmnContractTest
[INFO] Running com.camunda8.lab.lifecycleworker.LifecycleJobWorkerDecisionTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.846 s -- in com.camunda8.lab.lifecycleworker.LifecycleJobWorkerDecisionTest
[INFO] Results:
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Prova por mutação — o `type` no BPMN foi trocado por `lesson-005-lifecycle-java-MUTADO` e o teste
rodou de novo (arquivo restaurado logo em seguida):

```text
[ERROR] Tests run: 8, Failures: 1, Errors: 0, Skipped: 0, ... -- in com.camunda8.lab.lifecycleworker.BpmnContractTest
[ERROR] com.camunda8.lab.lifecycleworker.BpmnContractTest.bpmnTaskTypeMatchesWorker -- ... <<< FAILURE!
expected: "lesson-005-lifecycle-java"
[ERROR] Tests run: 13, Failures: 1, Errors: 0, Skipped: 0
```

A mutação foi **refeita durante a revisão da reabertura** (o revisor não editou arquivos, então não
a reproduziu por conta própria); o resultado foi idêntico — 13 testes, 1 falha em
`bpmnTaskTypeMatchesWorker`, com `expected: "lesson-005-lifecycle-java"`. O BPMN foi restaurado e a
contagem de ocorrências de `-MUTADO` no arquivo voltou a `0`.

**Interpretação:** `BpmnContractTest` lê a anotação `@JobWorker` por reflexão e o `type` do XML por
XPath; ele não compara duas cópias do mesmo texto. A mutação prova que o contrato é verificado.

**Limite dos testes unitários — declarado para não supervalorizá-los.** Nenhum dos 13 testes
asserte que `handleLifecycle` deixa de enviar `complete` na primeira ativação: a decisão pura
(`decidir`) é testada, mas o envio do comando depende da cadeia de builders do `JobClient`. A
ausência de `complete` na primeira ativação é provada pela **execução real** (E14.4 e E14.5: o log
registra `decisao=DEIXAR_EXPIRAR` e a reativação da mesma `jobKey` antes de qualquer `complete`),
não pelo teste unitário. O teste de anotação verifica `autoComplete = false` como contrato estático;
ele não simula o starter.

### E14.3 — Deploy do BPMN Java

BPMN: `processes/005-ciclo-vida-job-backpressure/lesson-005-lifecycle-java.bpmn`, com
`<zeebe:taskDefinition type="lesson-005-lifecycle-java" retries="3"/>`. Tipo diferente do BPMN REST
para não competir com a pasta `62`.

```text
{"deploymentKey":"2251799813722060","tenantId":"<default>","deployments":[{"processDefinition":{"processDefinitionId":"lesson-005-ciclo-vida-job-java","processDefinitionVersion":1,"resourceName":"lesson-005-lifecycle-java.bpmn","tenantId":"<default>","processDefinitionKey":"2251799813722061"},"decisionDefinition":null,"decisionRequirements":null,"form":null,"resource":null}]}
HTTP 200
```

### E14.4 — Execução 1: o mesmo `jobKey` volta depois do `deadline`

Worker subido com
`./infra/local/mise.sh exec -- mvn -q -f apps/lifecycle-worker/pom.xml spring-boot:run`.
Linhas verbatim do log (`/tmp/opencode/lifecycle-worker.log`):

```text
2026-11-19T15:24:43.847-03:00  INFO 1077824 --- [lifecycle-worker] [           main] i.c.client.jobhandling.JobWorkerManager  : Starting job worker: lifecycleJobWorker#handleLifecycle with type lesson-005-lifecycle-java
2026-11-19T15:24:43.853-03:00  INFO 1077824 --- [lifecycle-worker] [           main] c.c.l.l.LifecycleWorkerApplication       : Started LifecycleWorkerApplication in 1.496 seconds (process running for 1.738)
2026-11-19T15:24:44.280-03:00  INFO 1077824 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : === LESSON-005 (Java): ACTIVATION ===
2026-11-19T15:24:44.280-03:00  INFO 1077824 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : jobKey=2251799813722097 processInstanceKey=2251799813722092 worker=lifecycleJobWorker#handleLifecycle
2026-11-19T15:24:44.280-03:00  INFO 1077824 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : deadline=1795112692225 retries=3 chaveJaVista=false
2026-11-19T15:24:44.280-03:00  INFO 1077824 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : decisao=DEIXAR_EXPIRAR motivo=primeira ativacao; retries=3
2026-11-19T15:24:44.280-03:00  WARN 1077824 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : sem complete: o prazo de 8000 ms deve expirar e o motor devolver a MESMA jobKey
2026-11-19T15:24:53.018-03:00  INFO 1077824 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : === LESSON-005 (Java): ACTIVATION ===
2026-11-19T15:24:53.018-03:00  INFO 1077824 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : jobKey=2251799813722097 processInstanceKey=2251799813722092 worker=lifecycleJobWorker#handleLifecycle
2026-11-19T15:24:53.018-03:00  INFO 1077824 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : deadline=1795112701014 retries=3 chaveJaVista=true
2026-11-19T15:24:53.018-03:00  INFO 1077824 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : decisao=COMPLETAR motivo=mesma jobKey voltou; retries preservado=3
2026-11-19T15:24:53.018-03:00  INFO 1077824 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : enviando complete para a jobKey=2251799813722097
2026-11-19T15:24:53.035-03:00  INFO 1077824 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : complete aceito para a jobKey=2251799813722097
```

Criação da instância — corpo verbatim:

```json
{"processDefinitionId":"lesson-005-ciclo-vida-job-java","processDefinitionVersion":1,"tenantId":"<default>","variables":{},"processDefinitionKey":"2251799813722061","processInstanceKey":"2251799813722092","tags":[],"businessId":null}
HTTP 200
```

### E14.5 — Execução 2: reprodutibilidade com outra `jobKey`

```text
{"processDefinitionId":"lesson-005-ciclo-vida-job-java","processDefinitionVersion":1,"tenantId":"<default>","variables":{},"processDefinitionKey":"2251799813722061","processInstanceKey":"2251799813722143","tags":[],"businessId":null}
HTTP 200
2026-11-19T15:25:42.417-03:00  INFO 1089954 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : jobKey=2251799813722148 processInstanceKey=2251799813722143 worker=lifecycleJobWorker#handleLifecycle
2026-11-19T15:25:42.417-03:00  INFO 1089954 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : deadline=1795112750364 retries=3 chaveJaVista=false
2026-11-19T15:25:42.417-03:00  INFO 1089954 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : decisao=DEIXAR_EXPIRAR motivo=primeira ativacao; retries=3
2026-11-19T15:25:50.713-03:00  INFO 1089954 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : === LESSON-005 (Java): ACTIVATION ===
2026-11-19T15:25:50.713-03:00  INFO 1089954 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : jobKey=2251799813722148 processInstanceKey=2251799813722143 worker=lifecycleJobWorker#handleLifecycle
2026-11-19T15:25:50.714-03:00  INFO 1089954 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : deadline=1795112758709 retries=3 chaveJaVista=true
2026-11-19T15:25:50.714-03:00  INFO 1089954 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : decisao=COMPLETAR motivo=mesma jobKey voltou; retries preservado=3
2026-11-19T15:25:50.714-03:00  INFO 1089954 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : enviando complete para a jobKey=2251799813722148
2026-11-19T15:25:50.733-03:00  INFO 1089954 --- [lifecycle-worker] [pool-2-thread-1] c.c.l.l.LifecycleJobWorker               : complete aceito para a jobKey=2251799813722148
```

### E14.6 — Aritmética dos dois prazos

Verificação executada com `node`:

```text
deadline 1 = 2026-11-19T18:24:52.225Z
deadline 2 = 2026-11-19T18:25:01.014Z
ativacao 1 inferida = 2026-11-19T18:24:44.225Z
ativacao 2 inferida = 2026-11-19T18:24:53.014Z
delta entre ativacoes = 8.789 s
reactivacao (log 18:24:53.018Z) - deadline1 = 793 ms
```

**Interpretação:** `deadline − 8000 ms` cai a 55 ms do instante registrado no log da primeira
ativação — a diferença é a resolução do relógio entre o `ActivatedJob` montado pelo gateway e o
`log.info` do worker. O mesmo vale para a segunda ativação. A reativação aconteceu **depois** do
`deadline`, como a E1 afirma que pode acontecer; o `retries = 3` voltou intacto nas duas execuções.

### E14.7 — `state: TIMED_OUT` no worker Java: `N/A` com motivo

Um poller consultou `POST /v2/jobs/search` com
`{"filter":{"type":"lesson-005-lifecycle-java","state":"TIMED_OUT"}}` a cada 250 ms durante as duas
execuções, procurando a **`jobKey` capturada** no corpo (nunca `items[0]`). Não capturou nenhuma
ocorrência. A spec viva confirma que `jobKey`, `state` e `type` são campos válidos do filtro, então o
filtro não estava errado.

**Interpretação:** com o worker conectado, a janela entre o prazo expirar e a reativação foi de
793 ms (E14.6), menor que o intervalo de exportação da projeção. O `TIMED_OUT` **foi medido na E8**,
quando nenhum worker estava ligado e o Job ficou parado no estado. Nas duas execuções Java a prova
do empréstimo é o par `deadline expirado → mesma jobKey reativada`, não um `state` lido da projeção.
Registrar `N/A` aqui é mais correto do que afirmar que a projeção viu o estado.

### E14.8 — Estado final

`POST /v2/process-instances/search` com
`{"filter":{"processDefinitionId":"lesson-005-ciclo-vida-job-java"}}` — corpo verbatim:

```json
{"page":{"totalItems":2,"hasMoreTotalItems":false,"startCursor":"WzIyNTE3OTk4MTM3MjIwOTJd","endCursor":"WzIyNTE3OTk4MTM3MjIxNDNd"},"items":[{"processDefinitionId":"lesson-005-ciclo-vida-job-java","processDefinitionName":"Lesson 005 - Ciclo de vida do Job (worker Java)","processDefinitionVersion":1,"processDefinitionVersionTag":null,"startDate":"2026-11-19T18:24:44.216Z","endDate":"2026-11-19T18:24:53.031Z","state":"COMPLETED","hasIncident":false,"tenantId":"<default>","processInstanceKey":"2251799813722092","processDefinitionKey":"2251799813722061","parentProcessInstanceKey":null,"parentElementInstanceKey":null,"rootProcessInstanceKey":"2251799813722092","tags":[],"businessId":null},{"processDefinitionId":"lesson-005-ciclo-vida-job-java","processDefinitionName":"Lesson 005 - Ciclo de vida do Job (worker Java)","processDefinitionVersion":1,"processDefinitionVersionTag":null,"startDate":"2026-11-19T18:25:42.363Z","endDate":"2026-11-19T18:25:50.728Z","state":"COMPLETED","hasIncident":false,"tenantId":"<default>","processInstanceKey":"2251799813722143","processDefinitionKey":"2251799813722061","parentProcessInstanceKey":null,"parentElementInstanceKey":null,"rootProcessInstanceKey":"2251799813722143","tags":[],"businessId":null}]}
HTTP 200
```

`POST /v2/jobs/search` com
`{"filter":{"type":"lesson-005-lifecycle-java","state":"CREATED"}}`:

```text
{"page":{"totalItems":0,"hasMoreTotalItems":false,"startCursor":null,"endCursor":null},"items":[]}
HTTP 200
```

**Interpretação:** as duas instâncias terminaram e não há resíduo `CREATED`. O worker Java é um
processo local com `spring-boot:run` — nenhum container novo foi criado. O `docker compose ps` da E6
continua sendo a descrição correta da infraestrutura: `orchestration` + `connectors`.

### E14.9 — A reabertura não regrediu o experimento REST

Depois do módulo Java e do BPMN novo no ar, o Runner da pasta `62` foi executado de novo:

```text
================================================================
12 request(s), 31 asserção(ões), 0 request(s) com falha
```

Trecho verbatim da sondagem de long polling nessa execução:

```text
ok   200 09 — sondagem: requestTimeout 2000 (long polling)
       · fila vazia com requestTimeout 2000
       · request esperou a fila com requestTimeout 2000: 2397ms
ok   200 10 — PATCH changeset.timeout: encurta o lease
       · PATCH changeset.timeout aceito com 204
       · Job voltou antes do deadline original
```

**Interpretação:** o tipo Java é separado, mas a reexecução confirma que o deploy e o worker novos
não competem com a pasta `62`. É a terceira execução verde do Runner (E12 registra as duas
anteriores, com 2 350 ms e 2 302 ms); 2 397 ms está na mesma faixa.

## Correções — afirmações que a execução refutou

1. **Rascunho da lesson:** *"A medir na implementação: se o PATCH realmente **estende** o `deadline`
   observado."* **Refutado.** O PATCH com `timeout: 3000` **encurta** o prazo: o Job voltou 5 s depois
   (E11), muito antes do `deadline` original de 120 s. Além disso, o `deadline` devolvido pela
   reativação (`1795110023230`) é o do **novo** lease de 60 s, não o do PATCH. A asserção da coleção
   foi escrita para medir o comportamento correto (`Job voltou antes do deadline original`), e não a
   hipótese errada do rascunho. A hipótese permanece visível aqui.
2. **Topo deste arquivo, antes da execução:** *"O experimento de `TIMED_OUT` ... ainda não foi
   executado nesta lesson"*. **Refutado** por E7–E11 e pelo runner em E12. O texto do topo foi
   atualizado para `executado em 19/11/2026`; a versão antiga fica registrada nesta correção.
3. **Primeiro `docker compose ps`:** usei `infra/local/camunda-8.9/compose.yaml`, que não existe
   (E6). O arquivo vendorizado é `docker-compose.yaml`. Nenhum arquivo de `infra/local/camunda-8.9/`
   foi editado.
4. **Leitura ingênua de tempos:** comparar `deadline: 1795109896146` (epoch ms) com
   `deadline: "2026-11-19T17:38:16.146Z"` (ISO) como se fossem o mesmo formato produziria "divergência"
   inexistente (E8). A verificação real com `new Date(...).getTime()` devolveu `true`.
5. **Interpretação de E7, antes da revisão independente:** *"a ativação aconteceu às
   17:38:05.720Z (ver `creationTime` em E8) e o prazo de 4 000 ms leva ao `deadline`
   17:38:16.146Z"*. **Refutada por aritmética.** Verificação executada na revisão:

   ```text
   deadline - creation    = 10.426 s  (timeout usado = 4s)
   ativacao inferida      = 2026-11-19T17:38:12.146Z (deadline - timeout)
   creation -> ativacao   = 6.426 s (poll/export)
   ```

   `creationTime` é criação, não ativação. O parágrafo de E7 foi reescrito; a versão errada
   permanece aqui. Os dados brutos (`deadline`, `creationTime`, `lastUpdateTime`) não mudaram.
 6. **Rótulos de rascunho na lesson, antes da revisão:** *"A medir na implementação: se o PATCH
   realmente estende o `deadline` observado"* e *"Atenção de formato (a medir na implementação)"*.
   **Refutados** por E11 e E8; a seção de conceito agora fala no passado e aponta a correção.
7. **Escopo sem Java da versão `completed`, antes da reabertura:** *"não escreve código Java"*,
   *"Aqui não há JVM nova"*, *"Testes: `N/A` — sem código Java novo"*, *"sem configuração Java"* no
   roadmap e no README do módulo. **Refutado por decisão do usuário**, não por execução: ele pediu
   um projeto Java na 005. A lesson voltou a `ready`, ganhou `apps/lifecycle-worker` e o BPMN
   `lesson-005-lifecycle-java` (E14). As frases antigas permanecem visíveis em `lesson.md`,
   `spec.md`, `plan.md`, `tasks.md`, roadmap, README do módulo e na seção **N/A** deste arquivo,
   cada uma com a correção ao lado. A revisão da reabertura encontrou esta última ocorrência sem
   correção na primeira passada (B1); foi corrigida e registrada aqui, sem apagar a frase antiga.
8. **E14.7, expectativa inicial do poller:** esperava-se capturar `state: TIMED_OUT` também na
   execução Java. **Não observado** nas duas execuções — janela de 793 ms contra o intervalo de
   exportação da projeção. O filtro foi validado contra a spec viva; o resultado está registrado
   como `N/A` com motivo em E14.7, sem inventar estado.

---

## Revisão independente (19/11/2026)

Veredito da primeira passada: `CHANGES_REQUESTED` com dois blockers **documentais** — B1 (aritmética
de E7) e B2 (rótulos de rascunho na seção de conceito). Ambos corrigidos acima (Correções, itens 5 e
6) e no `lesson.md`. Não houve blocker de implementação, BPMN, coleção ou infraestrutura.

Essa revisão avaliou **somente a versão REST-only**. A reabertura com Java (E14) ainda precisa de
revisão independente própria; o veredito acima não a cobre.

O revisor confirmou de forma independente: `OK: 58 request(s)`, `docker compose ps` com apenas
`connectors` + `orchestration`, `JobStateEnum` com exatamente 8 valores, `52 bloco(s) em 15
arquivo(s), 2 falha(s)` — ambas em `node_modules` gitignorado —, 31 `pm.test` na pasta `62` e os
trechos das docs 8.9. Decisões: manter o título (ele não afirma medição de `503`) e não bloquear a
promoção pelos WARNs do Diagram Review. Re-contracheque dos blockers pendente.

---

## Limitações explícitas

- **`429`/`503` não foi medido.** É Fato (E2/E4) e segue `N/A` com motivo; nenhuma carga foi gerada.
- **`webfetch` é leitura de documentação**, não execução de comportamento do cluster.
- O enum da E3 vem da spec publicada pelo cluster em `localhost:8080`; a porta foi alcançável nesta
  sessão. Não foi necessário usar rede Docker — o runner e a coleção rodaram no host contra
  `http://localhost:8080`.
- **Um único experimento por cenário.** Não há repetição estatística, benchmark nem medição de
  latência de projeção; os tempos em E10 são de request, não de vazão.
- **Cluster de uma partição.** Nada aqui demonstra comportamento dependente de contagem de partições.

## N/A — com motivo, não omitidos

- **`429`/`503` por backpressure:** `N/A` — o laboratório não gera carga; provar o limite
  adaptativo exigiria benchmark fora do escopo. O código está documentado como Fato (E4), não como
  Observado.
- **Vazão (`jobs/s`) e timings de benchmark:** `N/A` — fora do escopo; o único timing previsto é o
  tempo de resposta da request de ativação com fila vazia, que mede `requestTimeout`, não vazão.
- ~~**Testes de unidade Java:** `N/A` — nenhum módulo Java novo nesta lesson.~~ **Correção da
  reabertura:** frase da versão REST-only, mantida visível porque o revisor apontou que era aqui que
  o leitor checaria o `N/A`. Há **13 testes** em `apps/lifecycle-worker` (8 de contrato BPMN ↔
  anotação e 5 de decisão/identidade), executados com 0 falhas e prova por mutação (E14.2). O `N/A`
  que permanece é o de `state: TIMED_OUT` no worker Java (E14.7), não o de testes.
- **`UpdateJobTimeout` gRPC:** `N/A` — tema coberto apenas como Fato; o experimento usa o `PATCH`
  REST quando aplicável, sem cliente gRPC novo.
