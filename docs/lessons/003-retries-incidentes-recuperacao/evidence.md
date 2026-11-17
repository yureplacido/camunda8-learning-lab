# Evidence — Lesson 003: Retries, incidentes e recuperação

Tudo aqui saiu de comando executado contra o cluster local 8.9.22, ou de teste executado.
Onde não houve execução, está escrito `N/A` com o motivo. Nenhum comando deste arquivo é
reconstituído de memória.

Ambiente: `camunda/camunda:8.9.22`, um Broker, uma partição, REST `8080`, gRPC `26500`.

---

## D1 — A assimetria é entre `JobClient` e `CamundaClient`

A lesson afirmava que "o SDK não consegue recuperar". A afirmação é falsa como estava escrita:
o `CamundaClient` **consegue**. O que é verdadeiro é mais estreito, e foi medido no artifact
correto — `camunda-client-java`, o que o app importa via `camunda-spring-boot-starter`, e não
`zeebe-client-java`, que é artifact distinto.

**Observado**, comando executado contra as fontes da versão fixada:

```bash
$ curl -sSL -o /tmp/opencode/camunda-client-java-8.9.22-sources.jar \
    https://repo1.maven.org/maven2/io/camunda/camunda-client-java/8.9.22/camunda-client-java-8.9.22-sources.jar
$ unzip -q /tmp/opencode/camunda-client-java-8.9.22-sources.jar -d /tmp/opencode/client-src
$ cd /tmp/opencode/client-src
$ rg -n 'newResolveIncidentCommand\(|newUpdateRetriesCommand\(|newUpdateJobCommand\(' \
    io/camunda/client/CamundaClient.java
518:   *  .newResolveIncidentCommand(incidentKey)
525:  ResolveIncidentCommandStep1 newResolveIncidentCommand(long incidentKey);
549:   *  .newUpdateRetriesCommand(jobKey)
556:   * {@link #newResolveIncidentCommand newResolveIncidentCommand(long incidentKey)} .
561:  UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(long jobKey);
570:   *  .newUpdateRetriesCommand(job)
577:   * {@link #newResolveIncidentCommand newResolveIncidentCommand(long incidentKey)} .
582:  UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(ActivatedJob job);
778:   * {@link #newResolveIncidentCommand newResolveIncidentCommand(long incidentKey)} .
787:  UpdateJobCommandStep1 newUpdateJobCommand(long jobKey);
804:   * {@link #newResolveIncidentCommand newResolveIncidentCommand(long incidentKey)} .
813:  UpdateJobCommandStep1 newUpdateJobCommand(ActivatedJob job);

$ rg -n 'newFailCommand\(|newCompleteCommand\(|newThrowErrorCommand\(|newUpdateRetriesCommand\(|newResolveIncidentCommand\(' \
    io/camunda/client/api/worker/JobClient.java
43:   *  .newCompleteCommand(jobKey)
54:  CompleteJobCommandStep1 newCompleteCommand(long jobKey);
63:   *  .newCompleteCommand(job)
74:  CompleteJobCommandStep1 newCompleteCommand(ActivatedJob job);
83:   *  .newFailCommand(jobKey)
94:  FailJobCommandStep1 newFailCommand(long jobKey);
103:   *  .newFailCommand(job)
114:  FailJobCommandStep1 newFailCommand(ActivatedJob job);
124:   *  .newThrowErrorCommand(jobKey)
135:  ThrowErrorCommandStep1 newThrowErrorCommand(long jobKey);
145:   *  .newThrowErrorCommand(job)
156:  ThrowErrorCommandStep1 newThrowErrorCommand(ActivatedJob job);
```

**Correção visível.** A versão anterior deste evidence abria
`zeebe-client-java-8.9.22-sources.jar`, inferia uma limitação a partir de um `find` e de uma
classe órfã, e concluía que o Java Client não expunha recuperação. Artifact errado e conclusão
errada: o `rg` acima devolve os comandos de recuperação em `CamundaClient.java`. As frases
"Quem consegue falhar não consegue recuperar" e "Não é limitação do REST; é do SDK" foram
removidas da lesson por consequência.

**Fato (8.9.22, verificado)** — `CamundaClient` declara os comandos de recuperação mostrados acima
(`newResolveIncidentCommand`, `newUpdateRetriesCommand`, `newUpdateJobCommand`); `JobClient`
declara apenas os verbos de execução (`newCompleteCommand`, `newFailCommand`,
`newThrowErrorCommand`), ativação e stream. Fontes da tag `8.9.22`:
https://raw.githubusercontent.com/camunda/camunda/8.9.22/clients/java/src/main/java/io/camunda/client/CamundaClient.java
e https://raw.githubusercontent.com/camunda/camunda/8.9.22/clients/java/src/main/java/io/camunda/client/api/worker/JobClient.java .
A doc 8.9 mostra a sequência `newSetVariablesCommand → newUpdateRetriesCommand →
newResolveIncidentCommand` em https://docs.camunda.io/docs/8.9/components/concepts/incidents/ .

**Interpretação:** a fronteira é de autoridade/responsabilidade, não de capacidade. O worker
recebe `JobClient`; quem opera recebe `CamundaClient`. Um agente ao qual se dê um `CamundaClient`
pode recuperar — e a decisão de permitir isso passa a ser de design de permissões.

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

**N/A — corrigido em D11.** Esta seção originalmente dizia que essa inversão (resetar e resolver
sem corrigir a causa) **não tinha sido executada** e era apenas inferência. O probe foi executado
depois e está em **D11**: o mesmo `jobKey` voltou a `FAILED retries=0` e abriu um incidente novo,
com os dois comandos respondendo `204`. A inferência estava correta, mas deixou de ser inferência.
O texto original fica visível aqui para que a correção não apague o histórico.

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

**Fato (8.9, verificado)** — timeout de ativação **não afeta** `remaining retries`. Fonte:
https://docs.camunda.io/docs/8.9/components/concepts/job-workers/ . A pergunta conceitual está
respondida pela documentação da versão fixada.

**N/A** — se `TIMED_OUT` consome retry, **medido de forma controlada nesta lesson**. O acervo
mostra um Job `TIMED_OUT` com `retries=2` depois do backoff, e o registro antigo citava
`retries=3`; nenhum dos dois teve condição inicial e contagem explícitas. A lesson afirma que os
estados são distintos e aponta o Fato acima — não afirma ter feito o experimento.

---

## D8 — O rótulo de estado no `search` não é o estado do motor

**Observado** — duas leituras do `jobs/search` em momentos diferentes, uma logo após o `PATCH` e
outra alguns segundos depois. O quadro abaixo é um **resumo** das duas saídas, não texto verbatim:
o comando completo e o timestamp não foram preservados, então não deve ser lido como transcrição.

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

## D10 — A coleção executada: 13 requests, 36 asserções

A pasta `50` transformou o runbook em requests versionados e asseridos. Com o `retry-worker` no
ar e o `first-worker` da Lesson 002 **fora**:

```bash
$ node infra/local/collections/run-collections.mjs
...
35 request(s), 95 asserção(ões), 0 request(s) com falha
```

As 35 requests são as 22 da Lesson 002 mais as 13 da 003, na mesma execução: a 002 não regrediu.
As 36 asserções da pasta `50` são invariantes; as outras 59 vêm da 002 no modo sem worker.

**Por que 95 e não 99.** A contagem estática da coleção é 99 chamadas `pm.test(`. Dez delas estão
em branches condicionais da 002 (`code === 504` / `awaitMode`). No modo sem worker da 002
executam-se 6 → `59 + 36 = 95`; com o `first-worker` no ar executam-se 4 → `57 + 36 = 93`. O
número registrado acima é, portanto, o modo sem worker da parte 002 com o `retry-worker` ligado —
não "qualquer worker no ar". A lesson não esconde essa dependência do modo.

Sem o `retry-worker` no ar, a mesma coleção dá `0 request(s) com falha` nas pastas `00` a `40` e
reprova exatamente quatro requests da `50` — `Aguardar o incidente`, `O Job que ficou sem retry`,
`A instância foi até o fim` e `Os dois service tasks rodaram`. São os quatro que dependem do
incidente existir, e sem worker ninguém comanda `FAIL`: o Job fica em `CREATED` e `retries`
continua `3`. A diferença entre os dois modos **é** o conceito da lição.

### Limite da reexecução durante a revisão

A saída `35/95` acima é a rodada original, executada quando a lesson foi escrita. Na reexecução
feita durante a revisão independente o host não alcançou o cluster:

```text
$ curl -s -o /dev/null -w '%{http_code}\n' --max-time 3 http://localhost:8080/v2/topology
000
```

```text
$ ip route | grep 172.18
172.18.0.0/16 via 10.212.134.138 dev fctvpnc48d5255 proto static
172.18.0.0/16 dev br-a4bb97e51275 proto kernel scope link src 172.18.0.1
```

Dentro da rede Docker o cluster responde `GET /v2/topology HTTP/1.1 200`, mas a rota de VPN
captura o tráfego do host e não há sudo sem senha para removê-la. Portanto **não** há nova
execução da coleção nesta revisão; o que foi reexecutado com sucesso foram os testes JVM
(D9), os diagramas e os probes REST da D12, feitos de dentro da rede `camunda`.

### Sete formas de eu afirmar algo que a resposta contradisse

Cada item abaixo reprovou na primeira execução e foi corrigido contra a resposta real. Estão
registrados porque o padrão é o mesmo da Lesson 002: **endpoint escrito de memória falha, e o
runner é o que faz isso aparecer em vez de passar silencioso.**

| O que afirmei | O que o cluster respondeu |
|---|---|
| `d.processDefinitionKey` no topo do deploy | Aninhado em `deployments[0].processDefinition.processDefinitionKey`; no topo, `undefined` com `200` |
| `job.key` na busca de Job | O campo é `jobKey` |
| `variables.simulateFailure` ecoado no create | `variables: {}` — sem `awaitCompletion`, o create não devolve as variáveis |
| `variables` no `GET /v2/process-instances/{key}` | A propriedade não existe, e a spec não aceita query param para pedi-la |
| `bpmnElementType` na busca de element instance | A propriedade não existe; o filtro `type` funciona, mas não é repetido na resposta |
| `value` como booleano em `variables/search` | String: `"true"`, `"false"` |
| `variables/search` filtrado só por `name` | Devolveu 4 `simulateFailure` de execuções diferentes do cluster |

O último é o mais importante, e é o mesmo defeito que a Lesson 002 corrigiu em `items[0]`:
**sem escopo, a asserção mede o ambiente acumulado e não o objeto do request.**

### O `local: true` medido por dois lados

O request `A correção ficou local, e o processo continua dizendo 'true'` lê o mesmo nome nos dois
escopos, na mesma execução, logo depois do `PUT`:

```text
escopo do element instance  -> simulateFailure = "false"   (o que o worker leu)
escopo da instância         -> simulateFailure = "true"    (o que a criação gravou)
```

E, no fim do processo, `variables/search` por `processInstanceKey` devolve as quatro:

```json
{"name":"simulateFailure","value":"true","scopeKey":"2251799813775437"}
{"name":"simulateFailure","value":"false","scopeKey":"2251799813775442"}
{"name":"cardCharged","value":"true","scopeKey":"2251799813775437"}
{"name":"orderShipped","value":"true","scopeKey":"2251799813775437"}
```

O `scopeKey` do valor local é o `elementInstanceKey` do `charge-card`. **N/A** segue de pé: isto
mede que os dois valores coexistem por escopo, e **não** mede o que um segundo service task leria.
`ship-order` existe e rodou, mas não lê `simulateFailure`. A afirmação "o worker de `ship-order`
veria `true`" continua sendo leitura de resolução de escopo, não prova.

### `validate-collections.sh`

```text
OK: 35 request(s) conferem com a spec viva do cluster.
```

**Reexecução durante a revisão:** o script encerrou com `EXIT_CODE=2` em
`cluster não respondeu a spec em http://localhost:8080/v3/api-docs/Orchestration%20Cluster%20API`,
pelo mesmo bloqueio de rota descrito acima. A linha `OK: 35/35` é da rodada original.

O validador pagou por um erro meu durante a montagem: eu montei a pasta nova e esqueci de
declarar as cinco variáveis novas, e ele reprovou `orderBpmnPath`, `orderProcessDefinitionKey`,
`elementInstanceKey`, `jobKey` e `incidentKey` como não declaradas. É o comportamento pretendido
para um erro que nenhuma execução da coleção pegaria — uma variável não declarada só aparece
quando o request que a preenche é pulado.

---

## D11 — Resetar e resolver SEM corrigir a causa: a mesma falha, um incidente novo

A lição afirmava, por leitura do comportamento do worker, que pular a correção da causa reesgotaria
os retries. Era inferência. Este é o probe que a transformou em medição.

Sequência executada, com o worker no ar, partindo de uma instância parada em incidente:

```bash
$ PATCH /v2/jobs/2251799813776002            {"changeset":{"retries":3}}
status=204
$ POST  /v2/incidents/2251799813776006/resolution   {}
status=204
```

**Observado** — os dois comandos foram aceitos, porque a **ordem** foi respeitada. O que faltou foi
a causa. Seis segundos depois:

```bash
$ curl -s -X POST http://localhost:8080/v2/jobs/search -H 'Content-Type: application/json' \
    -d '{"filter":{"processInstanceKey":"2251799813775996","type":"charge-card"}}' \
    | jq -c '.items[]|{jobKey,state,retries,errorMessage}'
{"jobKey":"2251799813776002","state":"FAILED","retries":0,
 "errorMessage":"charge-card: recusa do adquirente (simulada)"}
```

O **mesmo** `jobKey` consumiu os três retries de novo e voltou a `FAILED retries=0`.

```bash
$ curl -s $B/v2/incidents/2251799813776006 | jq -c '{incidentKey,state,errorType}'
{"incidentKey":"2251799813776006","state":"RESOLVED","errorType":"JOB_NO_RETRIES"}

$ curl -s -X POST $B/v2/process-instances/2251799813775996/incidents/search \
    -H 'Content-Type: application/json' -d '{}' | jq -c '.items[]|{incidentKey,state,errorType}'
{"incidentKey":"2251799813776006","state":"RESOLVED","errorType":"JOB_NO_RETRIES"}
{"incidentKey":"2251799813776012","state":"ACTIVE","errorType":"JOB_NO_RETRIES"}
```

E a instância, de novo parada:

```bash
$ curl -s $B/v2/process-instances/2251799813775996 | jq -c '{processInstanceKey,state}'
{"processInstanceKey":"2251799813775996","state":"ACTIVE"}
```

`variables/search` confirma que a causa não foi corrigida — existe **uma** `simulateFailure`, no
escopo do processo, valendo `true`. Nenhum `scopeKey` de element instance aparece, porque o
`PUT` local nunca aconteceu neste probe.

### O que o probe estabelece

**Observado:** resolver não corrige nada. Ele marca o incidente antigo como tratado e o Job volta
a percorrer os retries, falha de novo e abre um **incidente novo**.

Duas consequências que só aparecem aqui:

- **A `incidentKey` não é estável entre ciclos de recuperação.** O mesmo problema, no mesmo Job,
  produziu a chave `...006` e depois `...012`. Quem tentar correlacionar "é o mesmo incidente" por
  `incidentKey` vai errar; o que correlaciona é o `jobKey`.
- **Resolver é reconhecer, não consertar.** Um runbook que resolve para "limpar o backlog" produz
  exatamente este resultado: um backlog novo, com o dobro de incidentes, e a causa intacta.

**Interpretação:** isto sugere que a ordem `corrigir -> resetar -> resolver` não é sequência de
bons jeitos, e sim que **cada passo pressupõe o anterior ter funcionado**. O `409 INVALID_STATE` de
D2 é o motor recusando porque ele sabe que o Job ainda não tem chance de passar. O `204` desta
seção é o oposto: o motor aceitou os dois comandos e o problema continuou idêntico. É o modo de
falha mais perigoso do trio, porque **não produz sinal de erro nenhum** — nenhum status diferente
de 2xx, nenhum log de erro, nenhuma exceção. Só a repetição do sintoma denuncia.

**N/A — parcialmente corrigido em D12.** Esta seção declarava não ter medido "se a segunda
resolução exigiria um novo reset". O probe de D12 mediu: com `retries` já resetado, a segunda
resolução devolveu `404 NOT_FOUND`, sem exigir novo reset — o incidente já não existia. Continua
**N/A** quanto `retries` o Job tinha entre as falhas individuais, porque o Job só é projetado no
índice quando para.

---

## D12 — Resolver o mesmo incidente duas vezes: `404 NOT_FOUND`

A resposta de entrevista afirmava que a segunda resolução devolvia `404` sem evidence. O probe
foi executado para transformar afirmação em medição.

Cenário: deploy da BPMN, instância com `simulateFailure=true`, três ativações e três `FAIL`
(retries `2 → 1 → 0`) feitas por REST, incidente localizado por
`POST /v2/process-instances/{key}/incidents/search` (`incidentKey=2251799813686139`,
`jobKey=2251799813686135`). Como o host não alcança a porta `8080` (ver D10), o probe rodou com
`curlimages/curl:latest` **dentro** da rede `camunda`, usando `orchestration:8080`.

```bash
$ cat /tmp/opencode/probe-404-part3.sh   # trecho relevante
B=http://orchestration:8080
JK=2251799813686135
IK=2251799813686139
say() { printf '\n### %s\n' "$1"; }

say 'PATCH retries=3'
curl -sS -o /dev/null -w 'status=%{http_code}\n' -X PATCH "$B/v2/jobs/$JK" \
  -H 'Content-Type: application/json' -d '{"changeset":{"retries":3}}'

say 'first resolution'
curl -sS -i -X POST "$B/v2/incidents/$IK/resolution" -H 'Content-Type: application/json' -d '{}' \
  | grep -E '^HTTP/|^{|"title"|"status"|"detail"|"state"' | head -10

say 'second resolution'
curl -sS -i -X POST "$B/v2/incidents/$IK/resolution" -H 'Content-Type: application/json' -d '{}' \
  | grep -E '^HTTP/|^{|"title"|"status"|"detail"|"state"|"instance"' | head -10

say 'incident after both'
curl -sS "$B/v2/incidents/$IK" | head -c 700
```

**Observado** — saída real:

```text
### PATCH retries=3
status=204

### first resolution
HTTP/1.1 204 

### second resolution
HTTP/1.1 404 
{"type":"about:blank","title":"NOT_FOUND","status":404,"detail":"Command 'RESOLVE' rejected with code 'NOT_FOUND': Expected to resolve incident with key '2251799813686139', but no such incident was found","instance":"/v2/incidents/2251799813686139/resolution"}

### incident after both
{"processDefinitionId":"lesson-003-retries-incidentes","errorType":"JOB_NO_RETRIES","errorMessage":"probe segunda resolucao","elementId":"ServiceTask_ChargeCard","creationTime":"2026-11-17T15:48:34.764Z","state":"ACTIVE","tenantId":"<default>","incidentKey":"2251799813686139","processDefinitionKey":"2251799813686127","processInstanceKey":"2251799813686129","rootProcessInstanceKey":"2251799813686129","elementInstanceKey":"2251799813686134","jobKey":"2251799813686135"}
```

As linhas de status acima terminam com um espaço porque é assim que `curl -i` as emite; o espaço
foi preservado de propósito, como qualquer outra parte da saída verbatim.

**Interpretação:** o `204` da primeira resolução remove o incidente do ponto de vista do motor, e o
segundo `RESOLVE` é recusado com `NOT_FOUND`. O `GET` logo em seguida ainda mostra `state=ACTIVE`
porque consulta o índice secundário — a mesma defasagem de D8. Não é contradição: o comando foi
rejeitado pelo motor, a projeção é que está atrasada. A resposta de entrevista pode citar o `404`
como **Observado**, com esta ressalva.

---

## Resíduo dos probes

Os tipos `probe-003`, `probe-bf-*` e `probe-ord-*` e suas instâncias continuam no cluster
**da rodada original** — ver correção no fim desta seção, porque o cluster foi recriado.
Não foram removidos: apagar histórico de um log distribuído é operação de operador, e este
laboratório não quer ensinar a apagar evidência como se fosse faxina. Estão registrados aqui
para não virarem surpresa no `jobs/search` de quem rodar a lição depois.

A instância `2251799813775996` do probe de D11 também fica, parada em `ACTIVE` com um incidente ativo — é o registro vivo de que pular a correção da causa devolve o processo para o mesmo lugar.

As execuções da pasta `50` também ficam. Cada rodada cria uma instância nova com o mesmo
`processDefinitionId`, e é por isso que `variables/search` filtrado só por `name` devolve
histórico misturado — o próprio resíduo é o que prova por que o filtro precisa de escopo.

**Correção registrada na revisão:** o cluster local foi recriado com volumes novos em
2026-11-17 (`docker compose up -d` criou `camunda-89_camunda-data`, `camunda-89_camunda` e a rede
`camunda`). O resíduo descrito acima existia no cluster da rodada original; as chaves e os tipos
`probe-003`, `probe-bf-*`, `probe-ord-*` **não** estão mais no cluster atual. O probe de D12 rodou
neste cluster novo e deixou o seu: `processInstanceKey=2251799813686129`, `jobKey=
2251799813686135`, `incidentKey=2251799813686139`, `errorMessage="probe segunda resolucao"`.