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
| `processInstanceKey` | — | request de criação |
| `awaitMode` | — | request de `awaitCompletion` |

`bpmnPath` é o único valor que não sobrevive a outra máquina. Ajuste em **Variables** da coleção antes de rodar a pasta `20`.

## Ordem

As pastas são numeradas porque dependem umas das outras. Rodar `30` sem `20` não faz sentido: a `processDefinitionKey` é capturada no deploy.

| Pasta | Requests | Depende de |
| --- | --- | --- |
| `00 — Saúde e topologia` | 6 | — |
| `10 — A armadilha do 200` | 2 | — |
| `20 — Deploy` | 4 | `bpmnPath` válido |
| `30 — Instância` | 6 | `20` |
| `40 — Jobs pela REST` | 2 | `20` |
| `90 — Descoberta da API` | 2 | — |

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

- `bpmnPath` é absoluto e precisa ser ajustado.
- **Rodar a pasta `20` várias vezes não acumula versões.** Reenviar o recurso corrente é idempotente: devolve a mesma `processDefinitionVersion` e a mesma `processDefinitionKey`. Só conteúdo diferente cria versão nova — e voltar a um conteúdo anterior também cria, com key nova. Isso foi medido, não documentado pela Camunda; ver §2 de `docs/lessons/002-deploy-instance-worker/evidence.md`.
- `run-collections.mjs` implementa um subconjunto de `pm.*` e do Chai. Faltando matcher, o erro é explícito.
- Assumi cluster local em `8080`/`9600`, **sem** autenticação e com **uma** partição. Nada aqui demonstra comportamento que dependa de contagem de partições.