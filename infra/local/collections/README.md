# Coleções Postman — Camunda 8 Learning Lab

Uma coleção Postman v2.1, organizada por **objetivo** (não por lição), com asserções que verificam o **corpo** das respostas contra o cluster local.

- `camunda8-lab.postman_collection.json` — a coleção.
- `run-collections.mjs` — runner sem dependências que a executa e avalia os scripts de teste.
- `../validate-collections.sh` — confere cada path e método contra a OpenAPI viva do cluster.

## Por que existe um runner no repositório

Script de teste que nunca rodou é hipótese, não verificação. Sem um runner versionado, a única forma de provar a coleção é abrir o Postman na mão — e aí ninguém registra a evidência. O runner executa a mesma coleção, com o mesmo subconjunto de `pm.*`, e sai com código diferente de zero na primeira falha.

Ele **não** é um substituto do Newman: implementa só o que a coleção usa. Se um request precisar de um matcher que não existe, o erro é explícito.

## Uso

```bash
# 1. cluster no ar
docker compose -f infra/local/camunda-8.9/docker-compose.yaml up -d

# 2. conferir caminho e método contra a spec viva
./infra/local/validate-collections.sh

# 3. executar a coleção e avaliar as asserções
node infra/local/collections/run-collections.mjs
node infra/local/collections/run-collections.mjs --folder "20 — Deploy"
```

No Postman: **Import** → escolher o `.json`. Não precisa de collection runner, proxy ou environment separado — as variáveis estão declaradas na própria coleção.

## Variáveis

| Variável | Padrão | Quem preenche |
| --- | --- | --- |
| `baseUrl` | `http://localhost:8080` | manual |
| `monitoringUrl` | `http://localhost:9600` | manual |
| `bpmnPath` | caminho absoluto do `first-worker.bpmn` | **ajustar para a sua máquina** |
| `expectedProcessDefinitionId` | `lesson-002-first-worker` | manual |
| `jobType` | `say-hello` | manual |
| `instanceName` | `Mundo` | manual |
| `requestTimeoutMs` | `5000` | manual |
| `processDefinitionKey` | — | request de deploy |
| `processDefinitionId` | — | request de deploy |
| `processDefinitionVersion` | — | request de deploy |
| `processInstanceKey` | — | request de criação (a pasta `50` sobrescreve) |
| `awaitMode` | — | request de `awaitCompletion` |
| `orderBpmnPath` | caminho absoluto do `order-fulfillment.bpmn` | **ajustar para a sua máquina** |
| `orderProcessDefinitionKey` | — | request de deploy da pasta `50` |
| `scaleBpmnPath` | caminho absoluto do `lesson-004-scale.bpmn` | **ajustar para a sua máquina** |
| `scaleProcessDefinitionKey` | — | request de deploy da pasta `60` (compartilhado com a `61`) |
| `lifeBpmnPath` | caminho absoluto do `lesson-005-lifecycle.bpmn` | **ajustar para a sua máquina** |
| `lifeProcessDefinitionKey` | — | request `01` da pasta `62` |
| `lifeInstanceKey` | — | request `02` da pasta `62` |
| `lifeJobKey` | — | request `04` da pasta `62` |
| `lifeDeadlineCurto` | — | request `04` da pasta `62` (`deadline` em epoch ms) |
| `lifePatchJobKey` | — | request `10` da pasta `62` |
| `lifePatchDeadlineOriginal` | — | request `10` da pasta `62` (prazo de 120 s antes do PATCH) |
| `lifeInstanceKeys` | `[]` | requests `00`, `02` e `10` da pasta `62` |
| `manualInstanceKeys` | `[]` | paste manual `61`: request `00` reinicia, request `02` acrescenta |
| `manualKeysA` | `[]` | pasta `61`: request `04` |
| `manualKeysB` | `[]` | pasta `61`: request `05` |
| `manualKeysC` | `[]` | pasta `61`: request `06` |
| `elementInstanceKey` | — | busca de element instance |
| `jobKey` | — | busca do Job |
| `incidentKey` | — | busca do incidente |

`bpmnPath`, `orderBpmnPath`, `scaleBpmnPath` e `lifeBpmnPath` são os únicos valores que não sobrevivem a outra máquina. Ajuste em **Variables** da coleção antes de rodar as pastas `20`, `50`, `60` e `62`.

A pasta `62` continua usando a variável `jobKey` (já existente) no path do request de `completion`: o validador cruza o nome do parâmetro da URL com a spec viva, e o path é `/v2/jobs/{jobKey}/completion`. Um `{{lifeJobKey}}` ali reprovaria no validador, embora o valor seja o mesmo.

## Ordem

As pastas são numeradas porque dependem umas das outras. Rodar `30` sem `20` não faz sentido: a `processDefinitionKey` é capturada no deploy.

| Pasta | Requests | Depende de |
| --- | --- | --- |
| `00 — Saúde e topologia` | 6 | — |
| `10 — A armadilha do 200` | 2 | — |
| `20 — Deploy` | 4 | `bpmnPath` válido |
| `30 — Instância` | 6 | `20` |
| `40 — Jobs pela REST` | 2 | `20` |
| `50 — Retries, incidentes e recuperação` | 13 | `orderBpmnPath` válido **e o `retry-worker` no ar** |
| `60 — Escala e concorrência do worker` | 2 | `scaleBpmnPath` válido |
| `61 — Prova manual da Lesson 004` | 9 | `scaleBpmnPath` válido; execução **manual**, não no Runner |
| `62 — Ciclo de vida do Job (Lesson 005)` | 12 | `lifeBpmnPath` válido; sequencial no Runner |
| `90 — Descoberta da API` | 2 | — |

## A pasta `50` exige worker — e essa é a diferença

As pastas `00` a `40` rodam nos dois modos. A `50` **só funciona com o `retry-worker` no ar**, e a razão é o conceito da lição, não uma limitação da coleção:

Sem worker, ninguém comanda `FAIL`. O Job nasce em `CREATED`, `retries` continua `3`, o motor nunca materializa incidente e a instância nunca para. Os quatro requests que dependem do incidente reprovam — `Aguardar o incidente`, `O Job que ficou sem retry`, `A instância foi até o fim` e `Os dois service tasks rodaram` — e os que dependem de uma chave que eles nunca capturam saem como `PULADO`.

```bash
# a pasta 50, com o worker no ar
./infra/local/mise.sh exec -- mvn -q -f apps/retry-worker/pom.xml spring-boot:run &
# espere "Started RetryWorkerApplication" antes de rodar
node infra/local/collections/run-collections.mjs --folder "50 —"
```

Rodar `30` e depois `50` na mesma execução é seguro: a `50` sobrescreve `processInstanceKey` com a instância dela, e nada depois dela depende da instância da Lesson 002.

## A pasta `60` — três clientes lógicos, um broker

A pasta `60` não precisa de worker Java: os "workers" são três valores de `worker` em chamadas
`POST /v2/jobs/activation`. `w4-a` e `w4-b` disputam os mesmos 8 Jobs; `w4-drain` recebe os 2
restantes. O request de deploy captura `scaleProcessDefinitionKey`; o segundo request roda o
experimento inteiro via `pm.sendRequest` — 10 instâncias, ativações `4`/`4`/`2`, asserções de
disjunção de `jobKey`, 10 completions e poll da instância `COMPLETED`.

Duas armadilhas ficaram registradas nessa pasta:

- **`/v2/jobs/search` sem filtro de estado devolve Jobs `COMPLETED` de rodadas anteriores.** A
  pré-checagem usa `{"filter":{"type":"lesson-004-scale","state":"CREATED"}}`. Sem o `state`, a
  segunda execução da coleção reprova por causa dos próprios Jobs concluídos da primeira.
- **O estado devolvido pelo search atrasa em relação ao comando.** Logo após ativar e completar,
  o search ainda mostrava `CREATED`; após alguns segundos, `COMPLETED`. A prova da fila é o lote
  `w4-drain` ter recebido os 2 Jobs restantes, não o estado lido da projeção secundária.

## A pasta `61` — prova manual, um request por vez

A pasta `60` executa tudo dentro de um script; a `61` abre o experimento para inspeção. **Não rode no Collection Runner**: o request `02` precisa ser enviado dez vezes, mudando `seq` de 1 a 10 no corpo. A ordem manual é:

1. `00` — pré-checagem (`state: CREATED`); reinicia as quatro variáveis de rastreio.
2. `01` — deploy; captura `scaleProcessDefinitionKey`.
3. `02` — criar instância, **10 envios** com `seq` 1 a 10; cada envio acrescenta a chave em `manualInstanceKeys`.
4. `03` — ver a fila: 10 Jobs `CREATED` antes de qualquer ativação. O script faz poll porque a projeção atrasa ~3 s (E9/E16); a primeira versão, sem poll, reprovou com `esperava 10, veio 2` (E17).
5. `04` / `05` / `06` — ativações `w4-a`, `w4-b`, `w4-drain`, com lotes `4`/`4`/`2`; cada uma guarda seus `jobKey`.
6. `07` — completions no script, para o *lease* de 120 s não expirar enquanto você clica.
7. `08` — asserções: `4 + 4 + 2`, nenhuma chave repetida, interseção vazia entre `w4-a` e `w4-b`, e as 10 instâncias capturadas `COMPLETED`.

A prova central está no request `08`, não no `200`: a disjunção de `jobKey` mostra que o mesmo Job nunca foi entregue a dois clientes. `w4-drain` ter recebido os 2 restantes mostra a fila no Broker.

Execução de verificação: o fluxo foi simulado numa cópia temporária com o request `02` repetido dez vezes — `18 request(s), 22 asserção(ões), 0 request(s) com falha`. O validador reportou `OK: 46 request(s) conferem com a spec viva do cluster.` (E17).

## A pasta `62` — empréstimo, `TIMED_OUT` e os dois relógios

~~A pasta `62` reproduz a Lesson 005 sem worker Java.~~ **Correção da reabertura:** a pasta `62` continua sem worker e com o tipo `lesson-005-lifecycle`; o worker Java reaberto usa o tipo separado `lesson-005-lifecycle-java` e não é exigido pelo Runner. Ela é sequencial e roda inteira no Runner:

1. `00` — pré-checagem com `state: CREATED`; reinicia `lifeInstanceKeys`.
2. `01` — deploy do BPMN `lesson-005-lifecycle`; captura `lifeProcessDefinitionKey`.
3. `02` — cria uma instância; captura `lifeInstanceKey`.
4. `03` — faz poll da projeção até 1 Job `CREATED` aparecer.
5. `04` — ativa com `timeout: 4000` e `requestTimeout: -1`; guarda `jobKey`, `worker`, `retries` e o
   `deadline` em epoch ms.
6. `05` — faz poll até `TIMED_OUT` para **aquele** `jobKey` e converte o `deadline` ISO do search para
   epoch, provando que são o mesmo instante.
7. `06` — reativa com `w5-recupera`: mesmo `jobKey`, `retries: 3`, novo `deadline`.
8. `07` — `completion` com `204`, poll da instância `COMPLETED` e `CREATED: 0`.
9. `08` / `09` — com a fila vazia, medem `requestTimeout: -1` contra `2000`. O runner não expõe
   `pm.response.responseTime`, então o tempo vem de `Date.now()` em volta de `pm.sendRequest`. O que
   se mede é a **request**, não vazão.
10. `10` — nova instância, ativação com `timeout: 120000`, `PATCH {"changeset":{"timeout":3000}}`,
    espera de 5 s e reativação: o Job volta antes do prazo original. O `deadline` devolvido na
    reativação pertence ao **novo** `timeout: 60000` — compará-lo diretamente com o do PATCH seria
    erro; a correção está registrada na evidence da Lesson 005.
11. `11` — varre o resíduo: `CREATED: 0` e as duas instâncias rastreadas `COMPLETED`.

A prova central não é o `200`: é a identidade do `jobKey` entre `04`, `05`, `06` e `10`, com
`retries: 3` em todos. Nenhum `503`/`RESOURCE_EXHAUSTED` é provocado — sem carga, isso é `N/A` com
motivo. Execução real: `12 request(s), 31 asserção(ões), 0 request(s) com falha`; validador:
`OK: 58 request(s) conferem com a spec viva do cluster.` (E12 da Lesson 005). A prova Java
correspondente está na E14 da lesson, fora da coleção.

## Os dois modos da pasta `30`

O request de `awaitCompletion` muda de comportamento conforme existe worker, e a pasta inteira se adapta sozinha — sem flag para marcar.

| | sem worker | com worker |
| --- | --- | --- |
| `awaitCompletion: true` | `504 DEADLINE_EXCEEDED`, **sem** `processInstanceKey` | `200`, só depois de o processo terminar |
| estado da instância | `ACTIVE`, `endDate` nulo | `COMPLETED`, `endDate` preenchido |
| rastro de elementos | `ServiceTask` `ACTIVE`, sem `EndEvent` | tudo `COMPLETED`, com `EndEvent` |

O request publica `awaitMode` e os seguintes leem esse valor. Para ver os dois lados:

```bash
# ramo 504: com o worker parado
node infra/local/collections/run-collections.mjs

# ramo 200: com o worker no ar — espere o "Started FirstWorkerApplication"
# antes de rodar a coleção, senão o awaitCompletion ainda pega o worker frio
./infra/local/mise.sh exec -- mvn -q -f apps/first-worker/pom.xml spring-boot:run &
node infra/local/collections/run-collections.mjs
```

As contagens de asserção diferem entre os dois ramos (59 sem worker, 57 com worker) porque cada regime afirma coisas diferentes. O total exato varia com o estado acumulado do cluster — o arquivo tem 63 `pm.test` e quatro são condicionais ao regime —, então trate o número como medição, não como constante: o invariante é o delta de 2 e o `0 request(s) com falha`. Contagem idêntica nos dois modos não é erro por si só, mas merece checagem: pode significar que o ramo por `awaitMode` não está afirmando nada diferente. Comparar o total com a contagem de `pm.test` no JSON é o que pega esse tipo de ramo morto.

## O que a coleção ensina, e por que cada request existe

- **`200` não prova que uma API respondeu.** `GET /operate/v1/process-instances/search` devolve `200` com `text/html`: quem respondeu foi a SPA do Operate. O request afirma no corpo que **não** há JSON para interpretar.
- **`404` de verdade vem em `application/problem+json`** (RFC 9457), com `type`, `title`, `status`, `detail`, `instance`.
- **`GET /v2/status` devolve `204`**, sem corpo. Sucesso não é `200`.
- **`POST /v2/deployments` não aceita `deployment-name`.** O contrato v2 é `resources` e `tenantId`; o campo é da API v1 por componente.
- **`POST /v2/jobs` é `404` porque esse path não existe — não porque a REST v2 não ative Job.** O caminho real é `POST /v2/jobs/activation`, e ele responde `400 INVALID_ARGUMENT` dizendo o que falta (`type`, `timeout`, `maxJobsToActivate`). O ciclo inteiro do Job é v2: `activation`, `{jobKey}/completion`, `{jobKey}/failure`, `PATCH {jobKey}` para resetar `retries`, e `search`.
- **Correção registrada:** a versão anterior desta coleção afirmava que ativar e completar Job eram fronteira gRPC, e o `validate-collections.sh` tinha o path errado numa lista de exceções que o bendizia. Um `404` prova que um path não existe, não que uma capacidade não existe. Ver `docs/lessons/002-deploy-instance-worker/evidence.md`.
- **O campo do Job é `jobKey`,** não `key`. Os `customHeaders` do BPMN viajam com o Job, e `retries` vem do `retries="3"` do service task.

### O que a pasta `50` encontrou, e não estava na documentação

Todos os itens abaixo saíram de request que **reprovou primeiro** na redação e foi corrigido contra a resposta real. Estão no mesmo formato dos anteriores porque têm o mesmo status: medição, não memória.

- **`POST /v2/deployments` não devolve `processDefinitionKey` no topo.** Ele vem em `deployments[0].processDefinition.processDefinitionKey`, junto de `processDefinitionId` e `processDefinitionVersion`. Um `200` com `undefined` no campo esperado é o `200` vazio da pasta `10`, atingida pelo outro lado.
- **O campo da key do Job é `jobKey`,** não `key`. E não há um nome único para "a key" na REST v2: `processInstanceKey`, `elementInstanceKey`, `incidentKey`, `jobKey`, `deploymentKey`. Escrever `job.key` produz `undefined` com o Job perfeito em mãos.
- **`POST /v2/process-instances` sem `awaitCompletion` devolve `variables: {}`.** O que foi mandado no corpo não volta. Com `awaitCompletion: true` as variáveis **são** ecoadas — a pasta `30` depende disso. A diferença só aparece comparando os dois.
- **`GET /v2/process-instances/{key}` não devolve variáveis, e não aceita query param para pedir.** A spec lista um único parâmetro, o próprio `key`. Para ler variáveis é `POST /v2/variables/search`.
- **`element-instances/search` não devolve `bpmnElementType`.** O filtro `type: SERVICE_TASK` seleciona o registro, mas a resposta não repete o filtro.
- **`variables/search` devolve o valor como string.** Uma variável booleana vem `"true"`, não `true`. Comparar com o booleano reprova mesmo quando a escrita foi a correta.
- **`local: true` cria um escopo próprio, e os dois valores coexistem.** Depois de `PUT .../element-instances/{elementInstanceKey}/variables` com `local: true`, o mesmo nome tem `false` no `scopeKey` do element instance e **continua `true`** no `scopeKey` da instância. O worker lê a local e conclui; uma leitura no nível de processo diria que a falha continua. É a diferença entre "corrigi a causa" e "a causa parou de existir".
- **Filtrar `variables/search` só por `name` vaza execução.** Devolveu quatro `simulateFailure` de execuções diferentes do mesmo cluster, com valores diferentes. É o mesmo defeito de escopo que a Lesson 002 cometeu em `items[0]`: sem escopo, a asserção mede o ambiente, não o seu objeto.

### O atraso do secondary storage

Quatro requests da pasta `30` começam em `404` ou vazias e só convergem depois. Não é bug, e o script não esconde: ele faz poll e a descrição explica.

A escrita está no **log primário** do Broker. A leitura vem do **H2 secundário**, alimentado pelo `rdbms` exporter. Na config deste container:

```yaml
rdbms:
  url: jdbc:h2:file:./camunda-data/h2db
  flushInterval: PT0.5S
```

Medido: a instância criada volta `404` no `GET` imediato e `200` em menos de 0,2 s.

Um detalhe que só aparece quando se polla pelo **estado** e não pela existência: a instância é projetada como `ACTIVE` **antes** de o worker completar o Job. Pollar por "existe" sai na hora e devolve um estado obsoleto; é preciso pollar por `state === COMPLETED`.

## Limites conhecidos

- `bpmnPath` e `orderBpmnPath` são absolutos e precisam ser ajustados.
- **Rodar a pasta `20` várias vezes não acumula versões.** Reenviar o recurso corrente é idempotente: devolve a mesma `processDefinitionVersion` e a mesma `processDefinitionKey`. Só conteúdo diferente cria versão nova — e voltar a um conteúdo anterior também cria, com key nova. Isso foi medido, não documentado pela Camunda; ver §2 de `docs/lessons/002-deploy-instance-worker/evidence.md`.
- `run-collections.mjs` implementa um subconjunto de `pm.*` e do Chai. Faltando matcher, o erro é explícito.
- Assumi cluster local em `8080`/`9600`, **sem** autenticação e com **uma** partição. Nada aqui demonstra comportamento que dependa de contagem de partições.