# Evidence — Lesson 003: Retries, incidentes e recuperação

Tudo aqui saiu de comando executado contra o cluster local 8.9.22, ou de teste executado.
Onde não houve execução, está escrito `N/A` com o motivo. Nenhum comando deste arquivo é
reconstituído de memória.

Ambiente: `camunda/camunda:8.9.22`, um Broker, uma partição, REST `8080`, gRPC `26500`.

---

## D1 — O SDK consegue falhar e não consegue recuperar

**Observado**, lendo as fontes do client fixado em 8.9.22 (mesma versão do cluster):

```bash
$ unzip -q ~/.m2/repository/io/camunda/zeebe-client-java/8.9.22/zeebe-client-java-8.9.22-sources.jar
$ rg 'newFailCommand' io/camunda/zeebe/client/api/worker/JobClient.java
101:  FailJobCommandStep1 newFailCommand(long jobKey);
121:  FailJobCommandStep1 newFailCommand(ActivatedJob job);
```

O comando de falha existe e tem o que a lesson precisa:

```text
FailJobCommandStep2 retries(int remainingRetries);
FailJobCommandStep2 retryBackoff(final Duration backoffTimeout);
FailJobCommandStep2 errorMessage(String errorMsg);
```

**Observado**, o caminho oposto:

```bash
$ rg 'UpdateRetriesJobCommandStep1 ' -g '!*Test*' .
# nenhum retorno — a classe existe, nenhuma interface pública a devolve
```

```bash
$ find . -iname '*Incident*.java' | rg -i 'client/api'
# só DTOs de search: IncidentResult, IncidentSearchQuery, IncidentStateEnum...
# nenhum comando de resolução
```

**Fato (8.9)** — o pacote `io.camunda.client.api.command` contém `UpdateRetriesJobCommandStep1`
e não há `IncidentResolutionCommand`. Combinado com a observação acima, a conclusão é do
repositório: **o Java Client 8.9.22 expõe a falha e não expõe a recuperação.**

Isso é o eixo da lesson. Quem consegue decrementar `retries` não consegue devolvê-lo.

---

## D2 — A ordem dos comandos de recuperação é obrigatória

**Observado.** Instância com `retries=3`, Job falhou direto para `0`, incidente criado.
Resolvendo **sem** resetar antes:

```bash
$ curl -X POST http://localhost:8080/v2/incidents/2251799813774007/resolution \
    -H 'Content-Type: application/json' -d '{}'
```

```json
{"title":"INVALID_STATE","status":409,
 "detail":"Command 'RESOLVE' rejected with code 'INVALID_STATE': Expected to resolve incident
  with key '2251799813774007', but job with key '2251799813774005' has no retries left.
  Please update the job retries and retry resolving the incident"}
```

```bash
$ curl -X POST http://localhost:8080/v2/incidents/2251799813774007 | jq -c '{state,errorType}'
{"state":"ACTIVE","errorType":"JOB_NO_RETRIES"}

$ curl -X POST http://localhost:8080/v2/jobs/activation \
    -H 'Content-Type: application/json' \
    -d '{"type":"probe-ord-1790979491","maxJobsToActivate":1,"timeout":5000}' | jq '.jobs|length'
0
```

O incidente continua `ACTIVE`, o Job fica `FAILED` com `retries=0` e a ativação devolve zero.

**Observado**, na ordem correta, sobre o mesmo incidente:

```bash
$ curl -o /dev/null -w '%{http_code}\n' -X PATCH \
    http://localhost:8080/v2/jobs/2251799813774005 -H 'Content-Type: application/json' \
    -d '{"changeset":{"retries":3}}'
204

$ curl -o /dev/null -w '%{http_code}\n' -X POST \
    http://localhost:8080/v2/incidents/2251799813774007/resolution \
    -H 'Content-Type: application/json' -d '{}'
204

$ curl -X POST http://localhost:8080/v2/jobs/activation -H 'Content-Type: application/json' \
    -d '{"type":"probe-ord-1790979491","maxJobsToActivate":1,"timeout":10000}' \
  | jq -c '{jobs:(.jobs|length),jobKey:(.jobs[0].jobKey),retries:(.jobs[0].retries)}'
{"jobs":1,"jobKey":"2251799813774005","retries":3}
```

**Interpretação:** recuperar são dois comandos com ordem, não um "desfazer". O `409` é a
prova mais forte possível porque quem recusa nomeia o passo que faltava.

---

## D3 — O ciclo completo, executado pelo worker Java

Deploy e criação da instância com a falha simulada ligada:

```bash
$ curl -X POST http://localhost:8080/v2/deployments \
    -F "resources=@processes/003-retries-incidentes-recuperacao/order-fulfillment.bpmn"
# processDefinitionKey=2251799813774730

$ curl -X POST http://localhost:8080/v2/process-instances \
    -H 'Content-Type: application/json' \
    -d '{"processDefinitionKey":"2251799813774730","variables":{"simulateFailure":true}}'
# processInstanceKey=2251799813774731
```

**Observado**, log do `retry-worker`:

```text
19:38:11.734  retries que o motor gravou: 3 | simulateFailure: true
19:38:11.734  WARN decisao: FALHAR -> commanded retries=2
19:38:11.837  retries que o motor gravou: 2 | simulateFailure: true
19:38:11.837  WARN decisao: FALHAR -> commanded retries=1
19:38:11.943  retries que o motor gravou: 1 | simulateFailure: true
19:38:11.943  WARN decisao: FALHAR -> commanded retries=0
```

Três ativações em 209 ms, sem backoff. O contador é o do motor: o worker **lê**
`job.getRetries()` e informa o valor seguinte, não guarda estado entre tentativas.

**Observado**, o que o motor escreveu:

```bash
$ curl -X POST http://localhost:8080/v2/process-instances/2251799813774731/incidents/search \
    -H 'Content-Type: application/json' -d '{}'
```

```json
{"incidentKey":"2251799813774741","errorType":"JOB_NO_RETRIES","state":"ACTIVE",
 "jobKey":"2251799813774737",
 "errorMessage":"charge-card: recusa do adquirente (simulada)"}
```

```bash
$ curl -X POST http://localhost:8080/v2/process-instances/search \
    -H 'Content-Type: application/json' \
    -d '{"filter":{"processInstanceKey":"2251799813774731"}}' | jq -c '.items[]|{state,endDate}'
{"state":"ACTIVE","endDate":null}
```

A `errorMessage` que o worker escreveu no comando de falha é a mesma que o incidente
carrega. A instância não cancelou nem degradou: ficou parada, esperando decisão humana.

---

## D4 — O runbook de recuperação, executado

Correção da causa **primeiro**, depois o contador, depois o incidente:

```bash
# 1. a causa: a variável que o worker lê
$ curl -o /dev/null -w '%{http_code}\n' -X PUT \
    http://localhost:8080/v2/element-instances/2251799813774736/variables \
    -H 'Content-Type: application/json' \
    -d '{"variables":{"simulateFailure":false},"local":true}'
204

# 2. o contador
$ curl -o /dev/null -w '%{http_code}\n' -X PATCH \
    http://localhost:8080/v2/jobs/2251799813774737 \
    -H 'Content-Type: application/json' -d '{"changeset":{"retries":3}}'
204

# 3. o incidente
$ curl -o /dev/null -w '%{http_code}\n' -X POST \
    http://localhost:8080/v2/incidents/2251799813774741/resolution \
    -H 'Content-Type: application/json' -d '{}'
204
```

**Observado**, o worker recebeu a mesma instância com a causa corrigida:

```text
19:38:40.869  retries que o motor gravou: 3 | simulateFailure: false
19:38:40.869  decisao: CONCLUIR
19:38:40.890  === LESSON-003: Job ship-order recebido (cardCharged=true) ===
```

**Observado**, estado final:

```bash
$ curl -X POST http://localhost:8080/v2/process-instances/search \
    -H 'Content-Type: application/json' \
    -d '{"filter":{"processInstanceKey":"2251799813774731"}}' | jq -c '.items[]|{state,endDate}'
{"state":"COMPLETED","endDate":"2026-10-02T22:38:40.895Z"}

$ curl http://localhost:8080/v2/incidents/2251799813774741 | jq -c '{state}'
{"state":"RESOLVED"}
```

### A ordem dos três passos é a lesson

A parte 1 não é decoração. Se o operador resetasse o contador e resolvesse o incidente sem
corrigir a causa, o Job reativaria e o worker leria `simulateFailure=true` de novo e
**esgotaria os retries outra vez**, criando um incidente novo. Resolver incidente não conserta
nada: só declara que alguém consertou.

**N/A** — essa inversão (resetar e resolver sem corrigir a causa) **não foi executada**. É
inferência do comportamento observado do worker, não medição. Fica como questão aberta.

---

## D5 — Escopo de variável: duas `simulateFailure` coexistem

**Observado**, depois da recuperação:

```bash
$ curl -X POST http://localhost:8080/v2/variables/search \
    -H 'Content-Type: application/json' \
    -d '{"filter":{"processInstanceKey":"2251799813774731"}}' | jq -c '.items[]|{name,value}'
{"name":"simulateFailure","value":"true"}
{"name":"simulateFailure","value":"false"}
{"name":"cardCharged","value":"true"}
{"name":"orderShipped","value":"true"}
```

Duas entradas com o mesmo nome: a do escopo do processo (`true`, da criação da instância) e a
do escopo local do service task (`false`, do `PUT`). O worker leu a local.

**Interpretação:** o `local:true` é o que permite corrigir a causa **onde ela quebrou**,
sem mexer no resto do processo. Isso não foi confirmado por experimento com um segundo
service task que lesse a mesma variável, então é leitura da resolução de escopo, não prova.

---

## D6 — Backoff: o Job fica inativável durante a janela

**Observado**, com tipo de Job único por execução, para não contaminar a medição:

```bash
# falha com retryBackOff em milissegundos
$ curl -o /dev/null -w '%{http_code}\n' -X POST \
    http://localhost:8080/v2/jobs/2251799813773883/failure \
    -H 'Content-Type: application/json' \
    -d '{"retries":2,"errorMessage":"backoff probe","retryBackOff":20000}'
204

# dentro da janela
$ curl -X POST http://localhost:8080/v2/jobs/activation -H 'Content-Type: application/json' \
    -d '{"type":"probe-bf-1790979307","maxJobsToActivate":1,"timeout":5000}' | jq '.jobs|length'
0

# depois de 22s
$ curl -X POST http://localhost:8080/v2/jobs/activation -H 'Content-Type: application/json' \
    -d '{"type":"probe-bf-1790979307","maxJobsToActivate":1,"timeout":5000}' \
  | jq -c '{jobs:(.jobs|length),retries:(.jobs[0].retries)}'
{"jobs":1,"retries":2}
```

O campo é `retryBackOff`, com **O maiúsculo**, e é inteiro:

```bash
$ curl -X POST http://localhost:8080/v2/jobs/2251799813773833/failure \
    -H 'Content-Type: application/json' \
    -d '{"retries":2,"errorMessage":"probe","retryBackoff":"PT20S"}'
{"title":"Bad Request","status":400,"detail":"Request property [retryBackoff] cannot be parsed"}
```

`retryBackoff` em ISO-8601 é recusado. A spec do cluster confirma o contrato:

```bash
$ curl -s http://localhost:8080/v3/api-docs/Orchestration%20Cluster%20API \
  | jq -r '.components.schemas.JobFailRequest.properties|to_entries[]|"\(.key): \(.value.type)"'
retries: integer
errorMessage: string
retryBackOff: integer
variables: object
```

### Um probe que foi invalidado

A primeira medição de backoff **não valeu**. A ativação devolveu `1` Job e pareceu provar que
o backoff não existia. O Job devolvido era `2251799813773833`, de uma execução anterior do
mesmo tipo `probe-003`. O `0` de verdade é o da medição isolada acima.

É o mesmo defeito de escopo corrigido na Lesson 002 (`5431834`): medir varrendo estado
acumulado do ambiente em vez do objeto do request.

---

## D7 — `FAILED` e `TIMED_OUT` são coisas diferentes

**Observado**, no acervo de probes:

```bash
$ curl -X POST http://localhost:8080/v2/jobs/search -H 'Content-Type: application/json' \
    -d '{"filter":{"type":"probe-bf-1790979307"}}' | jq -c '.items[]|{state,retries,errorMessage}'
{"jobKey":"2251799813773883","state":"TIMED_OUT","retries":2,"errorMessage":"backoff probe"}
```

`errorMessage` persiste no registro do Job depois do backoff, mas **não** vem no payload de
ativação — lá veio `null`. São registros diferentes, e a lesson precisa dizer qual usar.

**N/A** — se `TIMED_OUT` consome retry. O registro mostra um Job `TIMED_OUT` com `retries=3`,
que foi ativado e nunca respondido, o que sugere que o timeout não decrementa sozinho. **Não
foi medido** de forma controlada, então a lesson afirma apenas que os estados são distintos,
não o que cada um faz com o contador.

---

## D8 — O rótulo de estado no `search` não é o estado do motor

**Observado.** Logo após o `PATCH` de retries, a busca do secondary storage ainda devolvia o
valor antigo, e depois de alguns segundos devolvia o novo:

```text
Imediatamente após PATCH:  state=FAILED  retries=0     (defasado)
alguns segundos depois:    state=RETRIES_UPDATED retries=3
```

E `RETRIES_UPDATED` coexistiu com o motor já tratando o Job como ativável, porque a
`activation` seguinte devolveu o Job. O índice secundário atrasa; a ativação é o motor.

**Interpretação:** usar `jobs/search` para decidir se um Job está pronto para ser retomado
pode dar resposta errada por alguns instantes. `activation` é a fonte do motor.

---

## D9 — Testes

```bash
$ ./infra/local/mise.sh exec -- mvn -f apps/retry-worker/pom.xml test
```

```text
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in BpmnContractTest
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in ChargeCardWorkerDecisionTest
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Prova por mutação

O contrato BPMN↔worker só vale se reprovar quando o BPMN muda. Medido:

| Mutação | Testes que reprovaram |
|---|---|
| `type="charge-card"` → `type="charge-crd"` | 2 (`bpmnTaskTypesMatchWorkers`, `retriesIsThreeOnTheFailingTask`) |
| `retries="3"` → `retries="5"` | 1 (`retriesIsThreeOnTheFailingTask`) |

O BPMN foi restaurado e os 16 testes voltaram a passar.

### `autoComplete = false` não é estilo

O starter completa o Job sozinho quando o handler retorna. Em `JobHandlerInvokingBeans`:

```java
if (autoComplete) {
  final CommandWrapper command = createCommandWrapper(createCompleteCommand(jobClient, job, result), ...);
  command.executeAsyncWithMetrics(MetricsRecorder::increaseCompleted);
}
```

Se o worker devolvesse normalmente depois de mandar um FAIL, o log receberia FAIL e COMPLETE.
Nesta lesson o comando **é** o assunto, então o automático é desligado e o worker decide.
O teste `failingWorkerDoesNotAutoComplete` fixa essa decisão.

---

## Resíduo dos probes

Os tipos `probe-003`, `probe-bf-*` e `probe-ord-*` e suas instâncias continuam no cluster.
Não foram removidos: apagar histórico de um log distribuído é operação de operador, e este
laboratório não quer ensinar a apagar evidência como se fosse faxina. Estão registrados aqui
para não virarem surpresa no `jobs/search` de quem rodar a lição depois.