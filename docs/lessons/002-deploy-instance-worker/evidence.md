# Evidence — Lesson 002

Tudo aqui saiu de execução real neste repositório. Saída de comando está literal; nada foi normalizada nem completada de memória.

## Ambiente

| Item | Valor | Origem |
| --- | --- | --- |
| Cluster | `camunda/camunda:8.9.22`, container `orchestration` | `docker ps` |
| Connectors | `camunda/connectors-bundle:8.9.14` | `docker ps` |
| Topologia | 1 broker, 1 partição, `replicationFactor: 1` | `GET /v2/topology` |
| REST Gateway | `http://localhost:8080` | `GET /v2/topology` |
| gRPC Gateway | `localhost:26500` | config do worker |
| Broker `commandApi` | `localhost:26501`, protocolo SBE | `GET /v2/topology` |
| Broker `monitoringApi` | `http://localhost:9600` | `GET /v2/topology` |
| Secondary storage | H2, `rdbmsStatus.componentsState: UP` | `GET :9600/actuator/health` |
| Exporter | `rdbms` `ENABLED`, partição 1 `EXPORTING` | `GET :9600/actuator/exporters` |

---

## Correção do registro anterior

A versão anterior desta evidência descrevia um worker que **não existe** neste repositório. A correção fica visível, não apagada:

| Afirmação anterior | Realidade |
| --- | --- |
| `com.camunda8.lab.simpleworker.SimpleWorker` | `com.camunda8.lab.firstworker.SayHelloWorker` |
| `ZeebeClient` com `usePlaintext()` | `camunda-spring-boot-starter` com `@JobWorker` |
| `newCompleteCommand()` explícito | completion automático ao retornar do método sem exceção |
| `=== Completando Job ===` | `=== Job completado ===` |
| `=== LESSON-002 SIMPLE WORKER: Conectado ao Gateway ===` | não existe; o starter não loga isso |
| `-F "deployment-name=..."` no deploy | campo inexistente no contrato v2 (§1) |
| `processDefinitionVersion: 1` | o arquivo mudou depois; hoje o cluster tem versões 2, 3 e 4 (§2) |

A lição inteira estava documentando um caminho que ninguém executou. Os logs abaixo são do código que está commitado.

---

## 1. Deploy

O contrato v2 aceita `resources` e `tenantId`. `deployment-name` pertence à API v1 por componente e **não** existe aqui:

```bash
curl -s -X POST http://localhost:8080/v2/deployments \
  -F "resources=@processes/002-first-worker/first-worker.bpmn"
```

```json
{"deploymentKey":"2251799813760687","tenantId":"<default>","deployments":[{"processDefinition":{"processDefinitionId":"lesson-002-first-worker","processDefinitionVersion":4,"resourceName":"first-worker.bpmn","tenantId":"<default>","processDefinitionKey":"2251799813758764"},"decisionDefinition":null,"decisionRequirements":null,"form":null,"resource":null}]}
```

**Observado:** `deploymentKey=2251799813760687`, `processDefinitionKey=2251799813758764`, versão 4.

## 2. Experimentos de versionamento (A/B/C)

A primeira versão desta evidência e da coleção afirmava que «cada deploy produz uma key nova». O experimento refutou a forma simples disso, com o mesmo arquivo:

```bash
# A — reenviar o arquivo do deploy mais recente
curl -s -X POST http://localhost:8080/v2/deployments -F "resources=@processes/002-first-worker/first-worker.bpmn"
# {"v":2,"key":"2251799813758479","resource":"first-worker.bpmn"}

# B — conteúdo diferente
sed 's/Lesson 002 - First Worker/Lesson 002 - First Worker v2/' \
  processes/002-first-worker/first-worker.bpmn > /tmp/opencode/varied.bpmn
curl -s -X POST http://localhost:8080/v2/deployments -F "resources=@/tmp/opencode/varied.bpmn"
# {"v":3,"key":"2251799813758762","name":null,"resource":"varied.bpmn"}

# C — voltar ao conteúdo original
curl -s -X POST http://localhost:8080/v2/deployments -F "resources=@processes/002-first-worker/first-worker.bpmn"
# {"v":4,"key":"2251799813758764"}
```

**Observado:** reenviar o recurso corrente é idempotente (A), conteúdo novo gera versão nova (B), e voltar a um conteúdo anterior gera **versão nova** em vez de reaproveitar a antiga (C). Numa key nunca é reutilizada.

Não encontrei documentação oficial que fixe essa regra, então ela fica como **Observado**, não Fato. Ela também explica a divergência de versões entre a evidência antiga (1) e o cluster de hoje: o BPMN foi alterado depois que a evidência foi escrita, e ninguém reexecutou.

## 3. Job Worker

```bash
./infra/local/mise.sh exec -- mvn -q -f apps/first-worker/pom.xml spring-boot:run
```

Registro e startup:

```text
sayHelloWorker#handleSayHello with type say-hello
Started FirstWorkerApplication in 1.166 seconds (process running for 1.38)
```

## 4. Criar instância e completar o Job

```bash
curl -s -X POST http://localhost:8080/v2/process-instances \
  -H 'Content-Type: application/json' \
  -d '{"processDefinitionKey":"2251799813758764","variables":{"name":"Mundo"},"awaitCompletion":true,"requestTimeout":15000}'
```

```json
{"processDefinitionId":"lesson-002-first-worker","processDefinitionVersion":4,"tenantId":"<default>","variables":{"name":"Mundo"},"processDefinitionKey":"2251799813758764","processInstanceKey":"2251799813760732","tags":[],"businessId":null}
```

Log do worker, integral:

```text
=== LESSON-002: Job recebido ===
JobKey: 2251799813760738
ProcessInstanceKey: 2251799813760732
Type: say-hello
Variável 'name': Mundo
Mensagem: Hello, Mundo!
=== Job completado ===
```

Estado final:

```bash
curl -s http://localhost:8080/v2/process-instances/2251799813760732
```

```json
{"processInstanceKey":"2251799813760732","state":"COMPLETED","endDate":"2026-10-02T16:10:37.633Z","processDefinitionVersion":4}
```

**Observado:** cadeia `service task → Job → worker → completion` completa, `state: COMPLETED`, sem incidente.

---

## Dead ends

Registrados porque o caminho que funciona é o único que se vê depois que ele funciona.

### D1 — Spring Boot 3.3.9 não sobe com o starter 8.9

```text
Caused by: java.lang.ClassNotFoundException: org.springframework.boot.actuate.endpoint.web.AdditionalPathsMapper
```

O `dependencyManagement` do Spring Boot 3.3.9 forçava `spring-boot-actuator:3.3.9`, e `AdditionalPathsMapper` só existe a partir do 3.4. Spring Boot 3.3.9 nunca foi combinação suportada.

**Fato (8.9)** — [8.9 release announcements](https://docs.camunda.io/docs/reference/announcements-release-notes/890/890-announcements/): *"Camunda Spring Boot Starter default now requires Spring Boot 4.0.x. Starting with 8.9.0-alpha3, the default Camunda Spring Boot Starter (`camunda-spring-boot-starter`) is bundled with Spring Boot 4.0.x... If you're not yet ready to upgrade, switch to `camunda-spring-boot-3-starter`, which is bundled with Spring Boot 3.5.x."*

Resolvido subindo para `spring-boot-starter-parent` 4.0.8, mantendo o starter default.

### D2 — `NoSuchMethodError` no httpclient5

Com o Spring Boot corrigido, o contexto passou a subir e o cliente falhou ao construir:

```text
'org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder.disableContentCompression()'
```

O `camunda-client-java:8.9.22` declara `httpclient5:5.6.4` e `httpcore5:5.4.4`; o `dependencyManagement` do Spring Boot 4.0.8 rebaixa para 5.5.2 / 5.3.6. O método só existe a partir do 5.6.

**Observado:** `mvn dependency:tree` antes e depois do pin explícito no `pom.xml`:

```text
+- org.apache.httpcomponents.client5:httpclient5:jar:5.5.2:compile   # rebaixado pelo Spring Boot
\- org.apache.httpcomponents.core5:httpcore5:jar:5.3.6:compile

+- org.apache.httpcomponents.client5:httpclient5:jar:5.6.4:compile   # após o pin
\- org.apache.httpcomponents.core5:httpcore5:jar:5.4.4:compile
```

A lição que fica: dois `dependencyManagement` disputam a mesma versão, e o erro aparece só em runtime, nunca em compilação.

### D3 — propriedade legada aceita, mas avisada

```text
WARN s.p.CamundaClientPropertiesPostProcessor : Legacy property 'camunda.client.zeebe.rest-address' found,
setting to 'camunda.client.rest-address'. Please update your setup to use the latest property
```

O starter aceita `camunda.client.zeebe.rest-address` e o traduz. Funciona, mas fica um aviso de depreciação no log. `application.yml` foi corrigido para `camunda.client.rest-address`.

### D4 — o runner dava verde sem rodar os testes

O primeiro runner executava o script de teste assim:

```js
const body_ = new AsyncFunction('pm', s.script.exec.join('\n'));
body_(pm);            // <- sem await
```

A coleção usa `await` no topo do script para pollar o secondary storage. Sem `await`, `body_(pm)` devolvia a promise e seguia; o runner avançava para o próximo request e executava `Promise.all(pending)` com a lista **ainda vazia**, porque o `pm.test` só seria chamado depois do primeiro `await`.

**Observado:** as saídas pareciam normais — `0 request(s) com falha` — e as linhas das requisições de poll apareciam sem nenhuma asserção embaixo:

```text
  ok  200 Recuperar a instância (a key do 504 se perdeu)
  ok  200 Ler a instância pela key
  ok  200 Por onde o processo avançou (element instances)
  ok  200 A variável chegou ao processo
```

Foi o total de asserções que denunciou: 47 quando os scripts de poll deveriam ter contribuído. A correção é `await body_(pm)` antes do `Promise.all`, mais um `try/catch` que reprova o request se o script estourar. Depois da correção os mesmos requests imprimem as asserções e o total passa a 59 sem worker e 57 com worker — número que depende do estado do cluster, como explica a seção da coleção Postman.

A lição que fica: **um runner que não aguarda o próprio script é um gerador de verde falso**, e ele passa mais vezes do que falha. Comparar a contagem de asserções com o número de `pm.test` no arquivo é o que pega isso; conferir só o código de saída, não.

---

## O atraso do secondary storage

Quatro requests da coleção Postman começam em `404` ou vazios e só convergem. Medido:

```text
t+0s   -> GET /v2/process-instances/2251799813759941  404
t+0.2s -> GET /v2/process-instances/2251799813759941  200
```

Config do exporter no container:

```yaml
rdbms:
  url: jdbc:h2:file:./camunda-data/h2db
  flushInterval: PT0.5S
```

**Observado:** a escrita está no log primário do Broker; a leitura vem do H2 secundário, alimentado pelo `rdbms` exporter, e por isso chega atrasada em até `flushInterval`.

Um detalhe que só aparece quando se polla pelo **estado** e não pela existência: a instância é projetada como `ACTIVE` **antes** de o worker completar o Job. Pollar por "existe" sai na primeira tentativa e devolve estado obsoleto. Foi exatamente o que aconteceu na primeira versão da coleção.

## `awaitCompletion` tem dois regimes

| | sem worker | com worker |
| --- | --- | --- |
| status | `504` | `200` |
| corpo | `{"type":"about:blank","title":"DEADLINE_EXCEEDED","status":504,"detail":"Expected to handle request, but request timed out between gateway and broker",...}` | `CreateProcessInstanceResponse` puro |
| `processInstanceKey` | **ausente** | presente |
| instância | `ACTIVE`, `endDate` nulo, **não** cancelada | `COMPLETED` |

**Observado:** o `504` é do Gateway, não do motor. A instância continua viva e parada no service task. E mesmo com `awaitCompletion: true` e `200`, a REST v2 **não** devolve `state`, `hasEnded` nem `endDate` — o corpo é o da criação. O `200` é a prova de que terminou; o estado confirmável está no `GET`.

## `INVALID_ARGUMENT` em filtro de busca

Uma busca com `limit` no corpo respondeu:

```json
{"type":"about:blank","title":"Bad Request","status":400,"detail":"Request property [limit] cannot be parsed"}
```

E, com key vazia:

```json
{"type":"about:blank","title":"INVALID_ARGUMENT","status":400,"detail":"The provided processDefinitionKey '' is not a valid key. Expected a numeric value. Did you pass an entity id instead of an entity key?","instance":"/v2/process-instances"}
```

**Fato (8.9)** — [8.9 release announcements](https://docs.camunda.io/docs/reference/announcements-release-notes/890/890-announcements/): *"REST API search endpoints now collect all filter validation errors and return them together in a single 400 Bad Request response... The ProblemDetail title changed from 'Bad Request' to 'INVALID_ARGUMENT'."*

Também na mesma fonte: `versionTag` passou a devolver `null` em vez de string vazia quando ausente — o que bate com o `versionTag: null` observado nas respostas de definition.

## A armadilha do 200

```bash
curl -s -o /dev/null -w "%{http_code} %{content_type}\n" \
  http://localhost:8080/operate/v1/process-instances/search
# 200 text/html
```

```bash
curl -s -o /dev/null -w "%{http_code} %{content_type}\n" \
  -X POST http://localhost:8080/v2/nao-existe
# 404 application/problem+json
```

**Observado:** `200` no caminho da SPA não prova que uma API respondeu. Um `404` em `/v2/nao-existe` prova que o Gateway recusou o path — e nada além disso.

---

## Correção: o ciclo de Job existe na REST v2

Esta seção substitui uma afirmação que estava errada nesta própria evidence. Ela é deixada
visível porque o erro é instrutivo.

**O que estava escrito.** "`POST /v2/jobs` dá 404, logo ativação e completion de Job são fronteira
gRPC/SDK, e essa fronteira é real." Esse `404` era real. A conclusão não era.

**O que a spec viva do cluster responde** (`GET /v3/api-docs/Orchestration Cluster API`):

```text
POST   /v2/jobs/activation
POST   /v2/jobs/{jobKey}/completion
POST   /v2/jobs/{jobKey}/failure
PATCH  /v2/jobs/{jobKey}
POST   /v2/jobs/search
```

**Observado, na spec e ao vivo:**

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/v2/jobs/search \
  -H 'Content-Type: application/json' -d '{}'
# 200
```

```json
{"totalItems":78,"items":[{"jobKey":"2251799813748851","retries":3,"state":"COMPLETED","type":"say-hello","elementId":"ServiceTask_SayHello","hasFailedWithRetriesLeft":false,...}]}
```

```bash
curl -s -X POST http://localhost:8080/v2/jobs/activation \
  -H 'Content-Type: application/json' -d '{}'
```

```json
{"type":"about:blank","title":"INVALID_ARGUMENT","status":400,
 "detail":"No type provided. No timeout provided. No maxJobsToActivate provided.",
 "instance":"/v2/jobs/activation"}
```

E o Broker respondendo sobre um Job, não o roteador do Gateway:

```bash
curl -s -X POST http://localhost:8080/v2/jobs/2251799813748851/failure \
  -H 'Content-Type: application/json' -d '{"retries":0,"errorMessage":"probe"}'
```

```json
{"title":"NOT_FOUND","status":404,
 "detail":"Command 'FAIL' rejected with code 'NOT_FOUND': Expected to fail job with key '2251799813748851', but no such job was found"}
```

**Observado:** a REST v2 tem o ciclo de Job completo — buscar, ativar, falhar, completar, e
`PATCH {jobKey}` para resetar `retries`. `POST /v2/jobs` dava 404 porque esse caminho exato não
existe; o caminho real tem `/activation` no fim.

### Por que o erro passou

O `validate-collections.sh` tinha `POST /v2/jobs` na lista de exceções, com a justificativa
"não existe ativação de Job na REST v2". Ou seja, o validador estava **bendizendo** a afirmação
errada: ele confirmava que o path não estava na spec, e isso era verdade, enquanto a conclusão
ao lado era falsa. Passou 22/22 durante meses sem nunca checar a conclusão.

A lição que fica: **um 404 prova que um path não existe, não que uma capacidade não existe.**
Para afirmar um limite de API é preciso sondar o endpoint real, e afirmar sobre a linguagem de
transporte exige observar a conexão.

### O worker fala REST, não gRPC

A mesma lição afirmava "ativação por gRPC" no diagrama e na conclusão. O ADR-0002 do próprio
repositório já registrava que o Camunda Java Client usa REST por padrão desde a 8.8.

**Observado**, com o worker no ar e as conexões do processo Java:

```bash
$ PID=$(pgrep -f 'First[W]orkerApplication' | head -1); ss -tnp | grep "pid=${PID},"
ESTAB 0 0 [::ffff:127.0.0.1]:42208 [::ffff:127.0.0.1]:8080 \
  users:(("java",pid=726459,fd=184))

$ ss -tnp | grep "pid=${PID}," | grep -c 26500
0
```

Uma conexão estabelecida em `8080` e **nenhuma** em `26500`. Repetido em execuções distintas,
com pids e portas efêmeras diferentes.

**Fato (8.9)** — [Job workers](https://docs.camunda.io/docs/components/concepts/job-workers) descreve o
worker pelo cliente, sem fixar transporte; a escolha é do client, e o 8.8+ usa REST por padrão.

Os rótulos `ativar por gRPC` no diagrama e na conclusão da lesson foram corrigidos. O worker da
Lesson 002 e o da Lesson 003 falam **REST** na porta 8080.

### Correção 2: a coleção acumulava state e reprovava sozinha

Descoberta ao remedir os dois modos acima, depois da correção do ciclo de Job. Não é o mesmo
erro, e estava latente desde a versão original da coleção.

**O que estava escrito.** A request `Recuperar a instância` tinha a asserção
`sem worker: o 504 não cancelou a instância`, implementada sobre **todas** as instâncias
retornadas para aquela `processDefinition`:

```js
items.filter(function (x) { return x.processDefinitionKey === defKey; }).forEach(function (x) {
  pm.expect(x.state).to.not.eql('TERMINATED');
  pm.expect(x.isCanceled).to.not.eql(true);
});
```

**Observado:** a request passou a reprovar, e o erro dizia exatamente o que a asserção negava:

```text
FALHA 200 Recuperar a instância (a key do 504 se perdeu)
      · sem worker: o 504 não cancelou a instância -> esperava "TERMINATED", veio "TERMINATED"
```

E o cluster mostrava **uma única** instância `TERMINATED` entre 57:

```bash
curl -s -X POST http://localhost:8080/v2/process-instances/search \
  -H 'Content-Type: application/json' \
  -d '{"filter":{"processDefinitionKey":"2251799813758764"},"sort":[{"field":"startDate","order":"DESC"}]}' \
  | jq -r '.items[] | .state' | sort | uniq -c
```

```text
     54 COMPLETED
      2 ACTIVE
      1 TERMINATED     <- end=2026-10-02T20:38:43.949Z, de uma sessão anterior
```

O `504` sob teste nunca cancelou nada. A reprovação vinha de um `TERMINATED` antigo, deixado por
uma sessão de experimentation das 20:38, e a request passaria a reprovar **para sempre** a cada
nova execução do lab.

**Correção:** a afirmação passou a ser sobre a instância *daquele* request, que é `items[0]`
porque a busca pede `startDate DESC`. As outras asserções da request já eram por existência
(`length > 0`), que é o tipo certo de afirmação quando o alvo não pode ser isolado.

**A lição que fica:** uma asserção que varre o estado **acumulado** do sistema não está testando o
request que a segue — está testando a história do ambiente. Isso é a mesma família do verde falso
de §D4, com sinal invertido: ali o runner deixava de rodar asserções, aqui a coleção reprovava por
uma instância que nada teve a ver com o `504`. `AGENTS.md` já pedia para afirmar sobre o que prova
o conceito; aqui a versão forte da regra é **afirmar sobre o objeto do request**, não sobre tudo
que o filtro trouxe.

---

## Testes

```bash
./infra/local/mise.sh exec -- mvn -f apps/first-worker/pom.xml test
```

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- in BpmnContractTest
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in SayHelloWorkerTest
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

O `BpmnContractTest` compara o BPMN versionado com a anotação `@JobWorker` do método. Para provar que ele não é decorativo, o BPMN foi mutado para `say-helo`:

```text
[ERROR] BpmnContractTest.bpmnTaskTypeMatchesWorker:85
[INFO] Tests run: 12, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

BPMN restaurado em seguida. **Observado:** o contrato entre BPMN e worker é verificável e falha alto quando alguém renomeia o Job de um lado só.

`SayHelloWorkerTest` captura os eventos de log e exige as mesmas linhas citadas acima, para que evidência e teste não possam divergir em silêncio.

## Coleção Postman

```bash
./infra/local/validate-collections.sh
# OK: 22 request(s) conferem com a spec viva do cluster.
```

Os dois modos, com o runner corrigido (§D4) e com a coleção corrigida (ver
[correção do escopo de `TERMINATED`](#correção-2-a-coleção-acumulava-state-e-reprovava-sozinha)):

```bash
node infra/local/collections/run-collections.mjs   # worker parado
# 22 request(s), 59 asserção(ões), 0 request(s) com falha

node infra/local/collections/run-collections.mjs   # worker no ar
# 22 request(s), 57 asserção(ões), 0 request(s) com falha
```

**Observado:** a contagem **muda entre os modos** porque os requests de `awaitCompletion` e de leitura afirmam coisas diferentes conforme o regime. Sem worker: `504`, ausência de `processInstanceKey`, `ServiceTask` parada em `ACTIVE` e `EndEvent` inexistente — são 2 asserções a mais. Com worker: `200`, trilha completa até o `EndEvent`.

E as quatro requisições que dependem do secondary storage — `Recuperar a instância`, `Ler a instância pela key`, `Por onde o processo avançou` e `A variável chegou ao processo` — passam a imprimir suas asserções nos dois modos. Antes de §D4 elas não imprimiam nada.

### A contagem exata não é um invariante

O arquivo tem 63 `pm.test`; 63 − 4 = 59 é o teto alcançável no modo sem worker, porque
quatro deles são condicionais ao regime. **A versão anterior desta evidence registrava 58 e 56,
e esses números não reproduziram** quando foram remedidos: medi 56 com a coleção antiga e sem
worker, depois 57, depois 59, conforme o estado do cluster ia mudando.

A lição: **o total de asserções é uma medição dependente de estado, não uma constante do
arquivo.** Registrar `58` como se fosse a verdade do arquivo foi registrar um retrato. O que é
verificável e deve ser conferido é o delta de 2 entre os modos, e `0 request(s) com falha` nos
dois.

---

## Verificação humana, independente do runner

Esta seção é um **relato de execução humana**, não saída de comando. Não há transcript para colar, e nada aqui deve ser lido como log verbatim. A data não foi registrada no momento da execução, e não vou inventá-la.

Executado pelo autor do laboratório, fora do `run-collections.mjs`:

- a coleção foi importada no Postman e executada ponta a ponta sem alteração no arquivo;
- o caminho `SayHelloWorker → handleSayHello` foi inspecionado no IntelliJ, com breakpoint no método do worker;
- o processo concluiu como esperado.

**Observado:** a coleção é código válido do **sandbox do Postman**, e não apenas DSL do runner deste repositório. Essa era a limitação que restava: `run-collections.mjs` implementa um subconjunto de `pm.*`/Chai, e o Postman real era a referência contra a qual o runner nunca tinha sido conferido. Os dois agora concordam.

Isso fecha o gate de revisão do `AGENTS.md` ("revisão independente sem achados bloqueantes"): o código e a documentação foram escritos por outra pessoa, e a verificação veio de quem executou a lição.

**Limitação que permanece:** foi verificado o caminho **com worker no ar**. Não foi exercitado worker que lança exceção, então os `retries="3"` do BPMN continuam declarados e não comprovados. Ver §4, `Limitações` e a lição.

---

## Interpretação

Duas conclusões que vão além do que foi medido:

1. **O service task é uma fronteira de execução, não um ponto de espera.** O rastro de elementos mostra `StartEvent_1 COMPLETED` e `ServiceTask_SayHello ACTIVE` sem `EndEvent_1` enquanto ninguém consome o Job. É a fronteira que o Camunda 7 não tinha: no 7, o worker participava da mesma transação que escrevia o estado do processo; aqui a instância já está `ACTIVE` no log antes de qualquer worker existir.

2. **A projeção nunca é a fonte da verdade.** O `404` inicial e o `ACTIVE` obsoleto são o mesmo fato: existe uma janela entre a decisão do motor e o banco relacional. Um código que trata a projeção como estado corrente vai tomar decisão sobre dado velho — e o `flushInterval` é só o primeiro lugar onde isso aparece.

## Limitações

- Cluster local de **uma** partição. Nada aqui demonstra roteamento dependente de contagem de partições, nem resiliência a queda de broker.
- Sem autenticação. O cluster aceita `No 'camunda.client.auth.method' detected, will be set to 'none'`.
- O `504` foi observado com `requestTimeout` de 5 s. Não se afirma nada sobre o comportamento acima desse valor, nem sobre `awaitCompletion` com Job que falha e é retentado.
- Regra de versionamento (§2) é **Observado**, não documentada pela Camunda.
- `flushInterval: PT0.5S` é a config deste container. Outro valor muda a janela, não a existência dela.