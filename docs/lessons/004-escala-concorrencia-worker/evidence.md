# Evidence — Lesson 004

Cluster observado: `camunda/camunda:8.9.22` (`orchestration`, `STATUS healthy`) e
`camunda/connectors-bundle:8.9.14` (`connectors`, `STATUS healthy`), por
`docker compose -f infra/local/camunda-8.9/docker-compose.yaml ps`:

```text
NAME            IMAGE                              COMMAND                  SERVICE         CREATED       STATUS                       PORTS
connectors      camunda/connectors-bundle:8.9.14   "/start.sh"              connectors      2 hours ago   Up 2 hours (healthy)         0.0.0.0:8086->8080/tcp, [::]:8086->8080/tcp
orchestration   camunda/camunda:8.9.22             "/usr/local/camunda/…"   orchestration   2 hours ago   Up About an hour (healthy)   0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp, 0.0.0.0:9600->9600/tcp, [::]:9600->9600/tcp, 0.0.0.0:26500->26500/tcp, [::]:26500->26500/tcp, 26501-26502/tcp
```

Host → container na porta `8080` está bloqueado nesta máquina:

```text
$ curl -sS --max-time 5 -o /tmp/opencode/health004.txt -w 'http_code=%{http_code}\n' http://localhost:8080/actuator/health/status
curl: (28) Operation timed out after 5001 milliseconds with 0 bytes received
http_code=000
```

Toda medição abaixo foi feita com `docker run --rm --network camunda curlimages/curl:latest`, dentro
da rede `camunda`, contra `http://orchestration:8080`.

## E1 — health check: afirmação corrigida

**Afirmação original do PLAN-004 (errada, mantida visível):** "Gateway precisa estar *healthy*
antes do deploy: esperar `/actuator/health/status`."

**Observado (comando executado):**

```text
$ docker run --rm --network camunda curlimages/curl:latest -sS --max-time 10 -w '\nhttp_code=%{http_code}\n' http://orchestration:8080/actuator/health/status
{"type":"about:blank","title":"Not Found","status":404,"detail":"No static resource actuator/health/status.","instance":"/actuator/health/status"}
http_code=404

$ docker run --rm --network camunda curlimages/curl:latest -sS --max-time 10 -w '\nhttp_code=%{http_code}\n' http://orchestration:8080/actuator/health
{"type":"about:blank","title":"Not Found","status":404,"detail":"No static resource actuator/health.","instance":"/actuator/health"}
http_code=404
```

**Correção:** na 8.9.22 self-managed, nem `/actuator/health/status` nem `/actuator/health`
existem nesse caminho. A prontidão observada veio de `docker compose ps` → `STATUS healthy`.
SPEC, PLAN e TASKS corrigidos. **Interpretação:** um `404` de path é insuficiente para afirmar
prontidão; aqui a fonte é o estado real do container.

## E2 — o path de ativação existe e valida corpo

```text
$ docker run --rm --network camunda curlimages/curl:latest -sS --max-time 10 -X POST -H 'Content-Type: application/json' -d '{}' -w '\nhttp_code=%{http_code}\n' http://orchestration:8080/v2/jobs/activation
{"type":"about:blank","title":"INVALID_ARGUMENT","status":400,"detail":"No type provided. No timeout provided. No maxJobsToActivate provided.","instance":"/v2/jobs/activation"}
http_code=400
```

**Observado:** o Gateway repassou a validação e nomeou os três campos obrigatórios. Isso prova que
o path existe; um `404` provaria apenas que o path não existe (erro da 002, registrado na lesson).

## E3 — pré-checagem: zero Jobs residuais

```text
$ docker run --rm --network camunda curlimages/curl:latest -sS --max-time 10 -X POST -H 'Content-Type: application/json' -d '{"filter":{"type":"lesson-004-scale"}}' -w '\nhttp_code=%{http_code}\n' http://orchestration:8080/v2/jobs/search
{"page":{"totalItems":0,"hasMoreTotalItems":false,"startCursor":null,"endCursor":null},"items":[]}
http_code=200
```

**Observado:** `totalItems: 0` antes de qualquer deploy. A medição começou sem limpeza.

## E4 — formato preenchido de `activation` aceito

```text
$ docker run --rm --network camunda curlimages/curl:latest -sS --max-time 15 -X POST -H 'Content-Type: application/json' -d '{"worker":"w4-probe","type":"lesson-004-scale","timeout":30000,"maxJobsToActivate":4}' -w '\nhttp_code=%{http_code}\n' http://orchestration:8080/v2/jobs/activation
{"jobs":[]}
http_code=200
```

**Observado:** `worker`, `type`, `timeout` e `maxJobsToActivate` são aceitos.

## E5 — deploy do BPMN 004

```text
$ docker run --rm --network camunda -v /home/placido/desarrollo/camunda/camunda8-learning-lab/processes/004-escala-concorrencia-worker:/bpmn:ro curlimages/curl:latest -sS --max-time 20 -X POST -F 'resources=@/bpmn/lesson-004-scale.bpmn;type=text/xml' -w '\nhttp_code=%{http_code}\n' http://orchestration:8080/v2/deployments
{"deploymentKey":"2251799813689100","tenantId":"<default>","deployments":[{"processDefinition":{"processDefinitionId":"lesson-004-escala-concorrencia","processDefinitionVersion":1,"resourceName":"lesson-004-scale.bpmn","tenantId":"<default>","processDefinitionKey":"2251799813689101"},"decisionDefinition":null,"decisionRequirements":null,"form":null,"resource":null}]}
http_code=200
```

**Observado:** a key vem em `deployments[0].processDefinition.processDefinitionKey`, não no topo
(armadilha já registrada na 002).

## E6 — 10 instâncias com `runId` único

`RUN_ID=004-run-1794935516`, corpo
`{"processDefinitionKey":"2251799813689101","variables":{"runId":"004-run-1794935516-<i>","seq":<i>}}`.
Dez respostas `200`, uma chave por instância:

```text
2251799813689107
2251799813689115
2251799813689123
2251799813689131
2251799813689139
2251799813689147
2251799813689155
2251799813689163
2251799813689171
2251799813689179
```

**Observado:** a resposta de criação traz `"variables":{}` — as variáveis enviadas não são
devolvidas no corpo da criação, mas aparecem no payload de ativação (E7).

## E7 — as três ativações: prova central

Script: `POST /v2/jobs/activation` com `{"worker":"...","type":"lesson-004-scale","timeout":120000,"maxJobsToActivate":N}`.

**Pre-search (antes de qualquer ativação):** `totalItems: 10`, todos `state: CREATED`, `worker: ""`,
`deadline: null`.

**W4-A (`maxJobsToActivate: 4`) — `200`:**

```json
{"jobs":[{"type":"lesson-004-scale","processDefinitionId":"lesson-004-escala-concorrencia","processDefinitionVersion":1,"elementId":"ServiceTask_Scale","customHeaders":{},"worker":"w4-a","retries":3,"deadline":1794935654382,"variables":{"runId":"004-run-1794935516-1","seq":1},"tenantId":"<default>","jobKey":"2251799813689114","processInstanceKey":"2251799813689107","processDefinitionKey":"2251799813689101","elementInstanceKey":"2251799813689113","kind":"BPMN_ELEMENT","listenerEventType":"UNSPECIFIED","userTask":null,"tags":[],"rootProcessInstanceKey":"2251799813689107"},{"type":"lesson-004-scale","processDefinitionId":"lesson-004-escala-concorrencia","processDefinitionVersion":1,"elementId":"ServiceTask_Scale","customHeaders":{},"worker":"w4-a","retries":3,"deadline":1794935654382,"variables":{"runId":"004-run-1794935516-2","seq":2},"tenantId":"<default>","jobKey":"2251799813689122","processInstanceKey":"2251799813689115","processDefinitionKey":"2251799813689101","elementInstanceKey":"2251799813689121","kind":"BPMN_ELEMENT","listenerEventType":"UNSPECIFIED","userTask":null,"tags":[],"rootProcessInstanceKey":"2251799813689115"},{"type":"lesson-004-scale","processDefinitionId":"lesson-004-escala-concorrencia","processDefinitionVersion":1,"elementId":"ServiceTask_Scale","customHeaders":{},"worker":"w4-a","retries":3,"deadline":1794935654382,"variables":{"runId":"004-run-1794935516-3","seq":3},"tenantId":"<default>","jobKey":"2251799813689130","processInstanceKey":"2251799813689123","processDefinitionKey":"2251799813689101","elementInstanceKey":"2251799813689129","kind":"BPMN_ELEMENT","listenerEventType":"UNSPECIFIED","userTask":null,"tags":[],"rootProcessInstanceKey":"2251799813689123"},{"type":"lesson-004-scale","processDefinitionId":"lesson-004-escala-concorrencia","processDefinitionVersion":1,"elementId":"ServiceTask_Scale","customHeaders":{},"worker":"w4-a","retries":3,"deadline":1794935654382,"variables":{"runId":"004-run-1794935516-4","seq":4},"tenantId":"<default>","jobKey":"2251799813689138","processInstanceKey":"2251799813689131","processDefinitionKey":"2251799813689101","elementInstanceKey":"2251799813689137","kind":"BPMN_ELEMENT","listenerEventType":"UNSPECIFIED","userTask":null,"tags":[],"rootProcessInstanceKey":"2251799813689131"}]}
```

**W4-B (`maxJobsToActivate: 4`) — `200`:**

```json
{"jobs":[{"type":"lesson-004-scale","processDefinitionId":"lesson-004-escala-concorrencia","processDefinitionVersion":1,"elementId":"ServiceTask_Scale","customHeaders":{},"worker":"w4-b","retries":3,"deadline":1794935654402,"variables":{"runId":"004-run-1794935516-5","seq":5},"tenantId":"<default>","jobKey":"2251799813689146","processInstanceKey":"2251799813689139","processDefinitionKey":"2251799813689101","elementInstanceKey":"2251799813689145","kind":"BPMN_ELEMENT","listenerEventType":"UNSPECIFIED","userTask":null,"tags":[],"rootProcessInstanceKey":"2251799813689139"},{"type":"lesson-004-scale","processDefinitionId":"lesson-004-escala-concorrencia","processDefinitionVersion":1,"elementId":"ServiceTask_Scale","customHeaders":{},"worker":"w4-b","retries":3,"deadline":1794935654402,"variables":{"runId":"004-run-1794935516-6","seq":6},"tenantId":"<default>","jobKey":"2251799813689154","processInstanceKey":"2251799813689147","processDefinitionKey":"2251799813689101","elementInstanceKey":"2251799813689153","kind":"BPMN_ELEMENT","listenerEventType":"UNSPECIFIED","userTask":null,"tags":[],"rootProcessInstanceKey":"2251799813689147"},{"type":"lesson-004-scale","processDefinitionId":"lesson-004-escala-concorrencia","processDefinitionVersion":1,"elementId":"ServiceTask_Scale","customHeaders":{},"worker":"w4-b","retries":3,"deadline":1794935654402,"variables":{"runId":"004-run-1794935516-7","seq":7},"tenantId":"<default>","jobKey":"2251799813689162","processInstanceKey":"2251799813689155","processDefinitionKey":"2251799813689101","elementInstanceKey":"2251799813689161","kind":"BPMN_ELEMENT","listenerEventType":"UNSPECIFIED","userTask":null,"tags":[],"rootProcessInstanceKey":"2251799813689155"},{"type":"lesson-004-scale","processDefinitionId":"lesson-004-escala-concorrencia","processDefinitionVersion":1,"elementId":"ServiceTask_Scale","customHeaders":{},"worker":"w4-b","retries":3,"deadline":1794935654402,"variables":{"runId":"004-run-1794935516-8","seq":8},"tenantId":"<default>","jobKey":"2251799813689170","processInstanceKey":"2251799813689163","processDefinitionKey":"2251799813689101","elementInstanceKey":"2251799813689169","kind":"BPMN_ELEMENT","listenerEventType":"UNSPECIFIED","userTask":null,"tags":[],"rootProcessInstanceKey":"2251799813689163"}]}
```

**W4-DRAIN (`maxJobsToActivate: 2`) — `200`:**

```json
{"jobs":[{"type":"lesson-004-scale","processDefinitionId":"lesson-004-escala-concorrencia","processDefinitionVersion":1,"elementId":"ServiceTask_Scale","customHeaders":{},"worker":"w4-drain","retries":3,"deadline":1794935654414,"variables":{"runId":"004-run-1794935516-9","seq":9},"tenantId":"<default>","jobKey":"2251799813689178","processInstanceKey":"2251799813689171","processDefinitionKey":"2251799813689101","elementInstanceKey":"2251799813689177","kind":"BPMN_ELEMENT","listenerEventType":"UNSPECIFIED","userTask":null,"tags":[],"rootProcessInstanceKey":"2251799813689171"},{"type":"lesson-004-scale","processDefinitionId":"lesson-004-escala-concorrencia","processDefinitionVersion":1,"elementId":"ServiceTask_Scale","customHeaders":{},"worker":"w4-drain","retries":3,"deadline":1794935654414,"variables":{"runId":"004-run-1794935516-10","seq":10},"tenantId":"<default>","jobKey":"2251799813689186","processInstanceKey":"2251799813689179","processDefinitionKey":"2251799813689101","elementInstanceKey":"2251799813689185","kind":"BPMN_ELEMENT","listenerEventType":"UNSPECIFIED","userTask":null,"tags":[],"rootProcessInstanceKey":"2251799813689179"}]}
```

**Observado — a asserção central:**

| Cliente | Quantidade | `jobKey` | `worker` no payload |
|---|---|---|---|
| `w4-a` | 4 | 9114, 9122, 9130, 9138 | `w4-a` |
| `w4-b` | 4 | 9146, 9154, 9162, 9170 | `w4-b` |
| `w4-drain` | 2 | 9178, 9186 | `w4-drain` |

Os conjuntos são **disjuntos**; a união tem 10 chaves; `w4-b` não recebeu nenhuma chave de
`w4-a`; os 2 Jobs restantes só apareceram na terceira ativação. O campo `worker` **está presente**
no payload do Job ativado — a pergunta aberta de E4 está respondida.

## E8 — completion e estado final

```text
$ docker run --rm --network camunda -v /tmp/opencode/lesson004-complete.sh:/complete.sh:ro curlimages/curl:latest sh /complete.sh
2251799813689114 HTTP 204
2251799813689122 HTTP 204
2251799813689130 HTTP 204
2251799813689138 HTTP 204
2251799813689146 HTTP 204
2251799813689154 HTTP 204
2251799813689162 HTTP 204
2251799813689170 HTTP 204
2251799813689178 HTTP 204
2251799813689186 HTTP 204
=== FINAL SEARCH ===
     10 "state":"CREATED"
```

Após 3 segundos:

```text
$ docker run --rm --network camunda curlimages/curl:latest -sS --max-time 15 -X POST -H 'Content-Type: application/json' -d '{"filter":{"processDefinitionKey":"2251799813689101"}}' http://orchestration:8080/v2/process-instances/search -w '\nHTTP %{http_code}\n' | grep -o '"state":"[A-Z_]*"' | sort | uniq -c
     10 "state":"COMPLETED"
```

```text
$ docker run --rm --network camunda curlimages/curl:latest -sS --max-time 15 -X POST -H 'Content-Type: application/json' -d '{"filter":{"type":"lesson-004-scale"}}' http://orchestration:8080/v2/jobs/search -w '\nHTTP %{http_code}\n' | grep -o '"state":"[A-Z_]*"' | sort | uniq -c
     10 "state":"COMPLETED"
```

Nenhum `TIMED_OUT`: os 10 Jobs foram completados dentro do *lease* de 120 s.

## E9 — latência de índice: armadilha registrada

**Observado:** imediatamente depois das três ativações, o `POST /v2/jobs/search` devolveu os 10
Jobs como `state: CREATED`, `worker: ""` e `deadline: null` — igual ao estado anterior às
ativações. Imediatamente depois dos 10 `204` de completion, o mesmo search ainda devolveu
`10 "state":"CREATED"`. Após ~3 s, os dois searches passaram a devolver `10 "state":"COMPLETED"`.

**Interpretação:** `/v2/jobs/search` lê uma projeção secundária, não a fonte de verdade. Usá-lo
para provar "o Job ainda está na fila" imediatamente após a ativação produziria uma conclusão
falsa. A prova da fila nesta lesson é o `w4-drain` ter recebido os 2 Jobs restantes, não o estado
devolvido pelo search. O mesmo efeito foi registrado na 003 (evidence D8).

## E10 — pré-checagem: filtrar por estado é obrigatório

**Observado na primeira execução do runner:**

```text
PULADO Deploy do BPMN 004 (multipart): ENOENT: no such file or directory, open '/home/placido/desarrollo/camunda/camunda8-learning-lab/processes/004-escala-concorrencia-worker/lesson-004-scale.bpmn'
FALHA 200 Ativação exclusiva, limite por request e fila
       → POST http://orchestration:8080/v2/jobs/search
       · pre-checagem: zero Jobs residuais -> esperava 0, veio 10
       · script de teste -> Headers constructor: Argument 1 could not be converted to one of: sequence<sequence<ByteString>>, record<ByteString, ByteString>.
```

Três causas distintas, medida e corrigida cada uma:

1. **Deploy pulado:** o runner foi executado com o repo montado em `/repo`, mas `scaleBpmnPath` é
   absoluto. Correção: montar o repo no próprio caminho absoluto dentro do container.
2. **`Headers constructor`:** o script passava `header: 'Content-Type: application/json'` (string).
   O resto da coleção usa `header: { 'Content-Type': 'application/json' }` (objeto), que é o que o
   `fetch` do runner aceita. Correção aplicada na pasta 60.
3. **Pré-checagem falsa:** após a execução manual, `/v2/jobs/search` com
   `filter: {type: "lesson-004-scale"}` devolveu `totalItems: 10` — os dez Jobs **COMPLETED** da
   rodada anterior. O filtro sem estado não significa "ativos". Sondagem:

```text
$ docker run --rm --network camunda curlimages/curl:latest -sS --max-time 15 -X POST -H 'Content-Type: application/json' -d '{"filter":{"type":"lesson-004-scale","state":"CREATED"}}' -w '\nHTTP %{http_code}\n' http://orchestration:8080/v2/jobs/search
{"page":{"totalItems":0,"hasMoreTotalItems":false,"startCursor":null,"endCursor":null},"items":[]}
HTTP 200
```

**Correção:** a coleção passou a pré-checar `state: CREATED` (evidence E10). **Interpretação:**
"zero Jobs residuais" só tem significado com estado explícito; sem isso, uma reexecução da coleção
seria reprovada por Jobs já concluídos.

## E11 — coleção executada com sucesso

Executada com o runner versionado, dentro da rede `camunda`, com uma cópia temporária da coleção
cujo `baseUrl` foi trocado para `http://orchestration:8080` (o host não alcança o container — ver
limitações). O repositório **não** foi alterado por essa troca.

```text
60 — Escala e concorrência do worker
------------------------------------
  ok   200 Deploy do BPMN 004 (multipart)
        · 200 com deploymentKey
        · o recurso entrou como process definition da Lesson 004
        · capturo a chave, que muda a cada deploy
  ok   200 Ativação exclusiva, limite por request e fila
        · pre-checagem: zero Jobs residuais
        · cada ativacao respeita maxJobsToActivate
        · w4-a e w4-b nao compartilham jobKey
        · o campo worker identifica quem recebeu cada Job
        · a uniao cobre exatamente as 10 instancias capturadas
        · as 10 instancias capturadas terminam COMPLETED

================================================================
2 request(s), 9 asserção(ões), 0 request(s) com falha
```

**Observado:** o runner provou o mesmo contrato da execução manual (E7), agora de forma
reproduzível.

**Correção de nome de teste (WARN da revisão, mantida visível):** a saída acima registra o nome
original `capturo a chave, que muda a cada deploy`. E11 mostrou que o reenvio idêntico preserva a
mesma chave e versão, então o nome afirmava uma regra falsa. Na coleção versionada o teste passou a
se chamar `capturo a chave da process definition`; a saída verbatim de E11 permanece como foi
executada. O runner foi **reexecutado** depois da mudança, com o mesmo resultado:

```text
  ok   200 Deploy do BPMN 004 (multipart)
        · 200 com deploymentKey
        · o recurso entrou como process definition da Lesson 004
        · capturo a chave da process definition
  ok   200 Ativação exclusiva, limite por request e fila
        · pre-checagem: zero Jobs residuais
        · cada ativacao respeita maxJobsToActivate
        · w4-a e w4-b nao compartilham jobKey
        · o campo worker identifica quem recebeu cada Job
        · a uniao cobre exatamente as 10 instancias capturadas
        · as 10 instancias capturadas terminam COMPLETED

================================================================
2 request(s), 9 asserção(ões), 0 request(s) com falha
```

O validador também foi reexecutado contra a spec viva: `OK: 37 request(s) conferem com a spec viva
do cluster.`

**Correção de afirmação errada (encontrada ao medir para E15, mantida visível):** o texto original
dizia "O `deploy` gerou uma **nova versão** da definição — coerente com o registro da 002 de que
reenviar conteúdo diferente cria versão nova". A medição direta mostrou que o runner reenviou o
**mesmo conteúdo** do deploy manual de E5, portanto o comportamento é o outro lado do registro da
002: reenviar o recurso corrente é idempotente e devolve a **mesma** versão e a **mesma** key.

**Correção da revisão (segunda rodada, mantida visível):** a primeira tentativa de medir isso
terminava em `jq -c '.items[0]'`, que descarta o restante do array e **não** prova unicidade — o
mesmo padrão `items[0]` que o Requirement 3 da SPEC proíbe e que a 002 registrou como armadilha.
O comando foi refeito preservando a contagem:

```text
$ docker run --rm --network camunda curlimages/curl:latest -sS --max-time 15 -X POST -H 'Content-Type: application/json' -d '{"filter":{"processDefinitionId":"lesson-004-escala-concorrencia"}}' http://orchestration:8080/v2/process-definitions/search | jq -c '{total:.page.totalItems, itens:[.items[] | {version, processDefinitionKey, resourceName}]}'
{"total":1,"itens":[{"version":1,"processDefinitionKey":"2251799813689101","resourceName":"lesson-004-scale.bpmn"}]}
```

**Observado:** `total: 1` e o array com um único item — uma única versão, `2251799813689101`. A
afirmação de unicidade agora é sustentada pela contagem, não pelo primeiro elemento.

## E12 — validação da coleção contra a spec viva

```text
OK: 37 request(s) conferem com a spec viva do cluster.
```

Executado com `BASE_URL` apontando para uma cópia local da spec obtida do próprio cluster
(`Orchestration Cluster API v2`), porque o host não alcança a porta `8080`. O validador confirmou
path, método e variáveis declaradas — inclusive `scaleBpmnPath` e `scaleProcessDefinitionKey`.

**Correção (E17, mantida visível):** naquela medição o host realmente não alcançava a `8080`. Ao
reverificar nesta sessão, `curl http://localhost:8080/v2/topology` devolveu `200` com corpo JSON
real do cluster. O validador voltou a rodar direto contra a spec viva e reportou
`OK: 46 request(s) conferem com a spec viva do cluster.` Os 9 requests a mais são a pasta
`61 — Prova manual da Lesson 004`, acrescentada para permitir a prova manual no Postman.

## E13 — validação Mermaid: o número `11/0` é da cópia isolada, não do repositório

**Afirmação original do Diagram Review (corrigida, mantida visível):** "Sintaxe Mermaid validada por
`docs/validate-mermaid.sh` — 11 blocos, 0 falhas."

**Observado no repositório real**, com `mmdc` local no `PATH`:

```text
$ PATH="$(pwd)/.opencode/node_modules/.bin:$PATH" ./docs/validate-mermaid.sh
...
Mermaid: 51 bloco(s) em 14 arquivo(s), 2 falha(s).

Diagrama que não renderiza não pode ficar no repositório:
  - .opencode/node_modules/@mermaid-js/mermaid-cli/README.md (bloco 1): Generating single mermaid chart
  - .opencode/node_modules/@mermaid-js/mermaid-cli/README.md (bloco 2): Generating single mermaid chart
```

`exit=1`. As duas falhas estão em READMEs de dependência instalada em `.opencode/node_modules`,
**não** em arquivo versionado. Investigação do bloco 1, isolado do README:

```text
$ mmdc -i /tmp/opencode/mmdfail1.mmd -o /tmp/opencode/mmdfail1.png -p /tmp/opencode/puppeteer004.json
Generating single mermaid chart

Error: Parse error on line 2:
graph   [....]
--------^
Expecting 'SEMI', 'NEWLINE', 'SPACE', 'EOF', 'subgraph', 'end', ... got 'SQS'
```

**Correção:** são erros de sintaxe em trechos ilustrativos da documentação do `mermaid-cli`, não
uma falha da ferramenta e não um problema do diagrama da Lesson 004. O número `11 blocos / 0
falhas` vale **somente para a execução em cópia isolada** de `docs/` e `specs/`, que foi a forma
como o bloco da 004 foi validado:

```text
$ PATH="/tmp/opencode/bin:$PATH" /tmp/opencode/mmdcheck/docs/validate-mermaid.sh
...
Mermaid: 11 bloco(s) em 8 arquivo(s), 0 falha(s).
Todos os diagramas renderizam. Lembre: isso valida sintaxe, não correção arquitetural.
```

O bloco da Lesson 004 também foi renderizado isoladamente com `mmdc` (` Generating single mermaid
chart` e PNG de 22.769 bytes gerado). **Interpretação:** a redação "11 blocos, 0 falhas" estava
tecnicamente correta para a cópia isolada, mas omissa sobre o resultado no repositório real — daí a
correção no Diagram Review. Ajustar `docs/validate-mermaid.sh` para ignorar `node_modules` seria
refatoro estrutural, fora do escopo desta lesson; o `WARN` fica registrado aqui.

## E14 — registro da revisão independente

A primeira revisão independente voltou **`CHANGES_REQUESTED`** com 5 blockers, todos de disciplina
de evidência e redação — nenhum sobre o mecanismo provado em E7/E11:

| # | Bloqueador | Correção aplicada |
|---|---|---|
| 1 | `lesson.md` dizia "Dois clientes Java continuam falando..." | trocado por clientes lógicos e "nenhum cliente Java foi iniciado" |
| 2 | `lesson.md` atribuía medição de timeout × retries à 003 | trocado por "registrado como Fato 8.9, medição controlada `N/A` na 003" |
| 3 | `lesson.md` afirmava "O motor não empurra" em contradição com *job streaming* na própria doc 8.9 | escopado ao polling REST do experimento, com *job streaming* citado como fora de escopo |
| 4 | Diagram Review citava `11/0` como se fosse o resultado no repositório | reescrito como cópia isolada; E13 registra o `51/2` real e a causa das falhas |
| 5 | "Exemplo mínimo" dizia "ainda não executado" e mantinha o filtro refutado | marcado como executado; filtro `state: CREATED` colocado ao lado da versão refutada |

Achados não-bloqueantes também tratados: vazão por unidade de tempo depende do intervalo de
*polling* (014); tese de exclusividade ganhou rótulo `Fato (8.9)` e URL no próprio `lesson.md`;
checkbox de testes separado de `N/A` Java; "dois clientes em disputa + um de drenagem" explicitado;
`scaleBpmnPath` incluído na ressalva de caminhos do README da coleção.

A **segunda revisão independente** voltou **`CHANGES_REQUESTED`** com 1 blocker e 8 WARNs. O
blocker foi irônico e coerente com a tese do laboratório: minha correção de E11 ("o deploy do runner
não criou versão nova") apoiava a unicidade da versão em `jq -c '.items[0]'`, que descarta o resto
do array — exatamente o padrão que o Requirement 3 da SPEC proíbe e que a 002 registrou como
armadilha. Refiz a busca preservando a contagem: `total: 1`, um único item (E11).

WARNs fechados: veredito do Diagram Review trocado de `PASS (com WARN)` para `WARN`, porque a regra
do agente exige "sem WARN aberto" para `PASS`; E1–E15 nos artefatos corrigido para E1–E16; ressalva
do `11/0` acrescentada em `tasks.md` e `spec.md`; "três clientes" alinhado em `spec.md`; `runId:
null` explicado em E16; nome do teste na coleção trocado para `capturo a chave da process definition`,
com runner reexecutado (2 requests, 9 asserções, 0 falhas) e validador verde (37 requests).

A **terceira revisão independente** voltou **`APPROVED`**, sem blockers e sem WARNs abertos
relevantes. O revisor verificou nos arquivos: (a) a unicidade da versão em E11 vem de `total: 1`, não
de `items[0]`, e a forma antiga ficou apenas como correção visível; (b) os oito WARNs foram fechados;
(c) nenhuma correção nova introduziu afirmação não medida — o `item0` de E16 aparece ao lado do
`total` e não sustenta contagem; (d) E7/E11 continuam intactos; (e) a lesson permanecia em `review`
com o gate ainda declarado. O único WARN restante é o de ferramenta em E13 (READMEs de
`.opencode/node_modules` no validador Mermaid), explicitamente fora do escopo e não relevante para o
gate. Sugestão opcional do revisor — trocar `item0:(.items[0] | ...)` em E16 pela forma com array —
**não aplicada**: a saída é verbatim de comando executado e o `total` já estava presente ao lado.

**Promoção:** lesson promovida a `completed` após esta aprovação, sem commit, push ou PR.

## E15 — `state: CREATED` seleciona Jobs vivos, não apenas exclui os concluídos

E10 provou que o filtro com estado **exclui** os 10 Jobs `COMPLETED`. Faltava provar que ele
**seleciona** um Job ativável quando há fila viva. Medido agora, com limpeza na sequência:

```text
# baseline
$ ... -d '{"filter":{"type":"lesson-004-scale","state":"CREATED"}}' .../v2/jobs/search
{"page":{"totalItems":0,...},"items":[]}

# criar 1 instância
$ ... -d '{"processDefinitionKey":"2251799813689101","variables":{"runId":"004-probe-created-filter"}}' .../v2/process-instances
{"processInstanceKey":"2251799813690091",...}
HTTP 200

# medir com filtro de estado
$ ... -d '{"filter":{"type":"lesson-004-scale","state":"CREATED"}}' .../v2/jobs/search | jq -c '{total:.page.totalItems, items:[.items[] | {jobKey, worker, state, runId:.variables.runId}]}'
{"total":1,"items":[{"jobKey":"2251799813690097","worker":"","state":"CREATED","runId":null}]}

# limpar e confirmar que não sobrou resíduo
$ ... -X POST .../v2/jobs/2251799813690097/completion
HTTP 204
$ ... -d '{"filter":{"type":"lesson-004-scale","state":"CREATED"}}' .../v2/jobs/search | jq -c '.page.totalItems'
0
```

**Observado:** `state: CREATED` devolveu exatamente o Job vivo (`worker: ""`, ainda não ativado) e
não devolveu os 10 `COMPLETED`. O estado residual foi limpo no mesmo comando. **Interpretação:** a
pré-checagem da coleção tem base empírica nos dois sentidos — exclusive e inclusive — e não deixa
resíduo para a próxima rodada.

**Limitação restante (não-bloqueante):** a projeção secundária atrasa ~3 s (E9). Se a coleção for
reexecutar em menos desse intervalo, um Job recém-completado ainda pode aparecer como `CREATED` e
reprovar a pré-checagem. É falso-negativo, não falso-positivo; o comportamento é esperado e foi
decidido não introduzir retry na pré-checagem nesta lesson, para não transformar a 004 em lição de
polling — tema da 005.

**WARN da revisão (segunda rodada) — `runId: null` explicado em E16:** o jq de E15 extraiu
`.variables.runId` do item devolvido pelo search e recebeu `null`. Isso **não** é perda de variável:
o `/v2/jobs/search` não projeta o campo `variables` no item, enquanto o payload de ativação de E7
sim. Medido em E16.

## E16 — `variables` ausente no search de Job, e latência de índice também na criação

Para não atribuir causa sem medir, criei 1 instância nova e inspecionei o item devolvido pelo search:

```text
$ ... -d '{"processDefinitionKey":"2251799813689101","variables":{"runId":"004-probe-variables"}}' .../v2/process-instances | jq -c '{processInstanceKey}'
{"processInstanceKey":"2251799813690248"}

# imediatamente depois
$ ... -d '{"filter":{"type":"lesson-004-scale","state":"CREATED"}}' .../v2/jobs/search | jq -c '{total:.page.totalItems, item0:(.items[0] | {jobKey, state, hasVariables:has("variables"), variables:.variables})}'
{"total":0,"item0":{"jobKey":null,"state":null,"hasVariables":false,"variables":null}}

# após 3 segundos
{"total":1,"item0":{"jobKey":"2251799813690254","state":"CREATED","hasVariables":false,"variables":null}}

# limpeza
$ ... -X POST .../v2/jobs/2251799813690254/completion
HTTP 204
$ ... -d '{"filter":{"type":"lesson-004-scale","state":"CREATED"}}' .../v2/jobs/search | jq -c '.page.totalItems'
0
```

**Observado:** (1) o item do `/v2/jobs/search` **não tem** o campo `variables` (`hasVariables: false`),
ao contrário do payload de ativação de E7, que traz `runId` e `seq` — daí o `runId: null` de E15;
(2) o Job recém-criado levou ~3 s para aparecer no search, confirmando que a latência de índice de
E9 também afeta a **criação**, não apenas a conclusão. Nenhum resíduo ficou: `totalItems: 0` ao final.

**Interpretação:** `search` é uma projeção secundária com campos próprios; usá-lo para afirmar que
a variável foi perdida seria erro de interpretação. A variável enviada na criação foi confirmada no
payload de ativação de E7.

## E17 — pasta `61`: prova manual no Postman e correção do poll da fila

**Interpretação (motivação):** a pasta `60` executa o experimento inteiro dentro de um script, o que
é ótimo para reproduzir, mas não para aprender. Foi acrescentada a pasta
`61 — Prova manual da Lesson 004`, com 9 requests que expõem cada etapa: pré-checagem, deploy,
criação de instância, verificação da fila, três ativações, completions e asserção final. Quatro
variáveis de coleção (`manualInstanceKeys`, `manualKeysA/B/C`) rastreiam as chaves entre requests.

**Observado — primeira execução (falha real, mantida visível):** o fluxo foi simulado numa cópia
temporária da coleção com o request `02` repetido dez vezes (`seq` 1 a 10):

```text
  FALHA 200 03 — ver a fila antes de qualquer ativação
        → POST http://orchestration:8080/v2/jobs/search
        · fila visivel antes de qualquer ativacao -> esperava 10, veio 2
...
18 request(s), 22 asserção(ões), 1 request(s) com falha
```

As ativações seguintes ainda receberam `4 + 4 + 2`, provando que os 10 Jobs existiam no motor: o
que faltou foi a **projeção**, não a fila. É o mesmo comportamento de E9 e E16 — a descrição do
request dizia "espere ~3 s", mas o script afirmava sobre a primeira resposta, sem poll. Corrigido
para repetir a busca até `totalItems: 10` (16 tentativas, 500 ms cada), com a versão errada mantida
visível na descrição do request.

**Observado — reexecução após a correção:**

```text
  ok   200 03 — ver a fila antes de qualquer ativação
        · fila visivel antes de qualquer ativacao
  ok   200 04 — ativação w4-a (4)
        · w4-a recebeu 4 Jobs
  ok   200 05 — ativação w4-b (4)
        · w4-b recebeu 4 Jobs
  ok   200 06 — ativação w4-drain (2)
        · w4-drain recebeu os 2 Jobs restantes
  ok   200 07 — completar os 10 Jobs (script)
        · 10 completions com 204
  ok   200 08 — prova final: disjunção + COMPLETED
        · lotes 4 / 4 / 2
        · nenhum jobKey repetido
        · w4-a e w4-b nao compartilham jobKey
        · as 10 instancias capturadas terminam COMPLETED

================================================================
18 request(s), 22 asserção(ões), 0 request(s) com falha
```

**Observado — não regressão e limpeza:** a pasta `60` continuou em `2 request(s), 9 asserção(ões),
0 request(s) com falha`; o validador reportou `OK: 46 request(s) conferem com a spec viva do
cluster.`; a busca final com `{"filter":{"type":"lesson-004-scale","state":"CREATED"}}` devolveu
`totalItems: 0`.

**Limitação da verificação:** a pasta `61` foi executada numa cópia temporária com o request `02`
duplicado dez vezes, porque no Postman o passo é manual por definição — o arquivo versionado tem o
request `02` uma única vez, e o usuário deve enviá-lo dez vezes. O runner não deve ser usado nessa
pasta: com um único envio, os requests `03` a `08` reprovariam por falta de 10 instâncias.

## E18 — as "4 perdidas": lease expirado, não Job perdido

**Observado — diagnóstico:** uma inspeção do estado acumulado do cluster mostrou 89 Jobs, dos quais
85 `COMPLETED` e 4 `TIMED_OUT`, e 89 instâncias, das quais 85 `COMPLETED` e 4 `ACTIVE`:

```text
$ ... -X POST .../v2/jobs/search -d '{"filter":{"type":"lesson-004-scale"},...}' | jq ...
{"total":89,"por_estado":[{"state":"COMPLETED","total":85},{"state":"TIMED_OUT","total":4}]}

$ ... -X POST .../v2/process-instances/search -d '{"filter":{"processDefinitionKey":"..."},...}' | jq ...
{"total":89,"por_estado":[{"state":"ACTIVE","total":4},{"state":"COMPLETED","total":85}]}

$ ... -X POST .../v2/jobs/search -d '{"filter":{"type":"lesson-004-scale","state":"TIMED_OUT"}}' | jq ...
{"jobKey":"2251799813697730","state":"TIMED_OUT","worker":"w4-a","retries":3,"deadline":"2026-11-17T20:52:29.023Z","processInstanceKey":"2251799813697723","variables":null}
{"jobKey":"2251799813697739","state":"TIMED_OUT","worker":"w4-a","retries":3,"deadline":"2026-11-17T20:52:29.023Z","processInstanceKey":"2251799813697732","variables":null}
{"jobKey":"2251799813697747","state":"TIMED_OUT","worker":"w4-a","retries":3,"deadline":"2026-11-17T20:52:29.023Z","processInstanceKey":"2251799813697740","variables":null}
{"jobKey":"2251799813697758","state":"TIMED_OUT","worker":"w4-a","retries":3,"deadline":"2026-11-17T20:52:29.023Z","processInstanceKey":"2251799813697751","variables":null}
```

Os 4 `jobKey` são exatamente um lote de ativação `w4-a` com `timeout: 120000`. A instância ficou
`ACTIVE` porque o `completion` não foi enviado antes do `deadline`. `retries: 3` permaneceu
intacto — o timeout não consumiu retry.

**Interpretação:** "perdida" é o nome errado. O Job nunca saiu do log; ele voltou a ficar elegível
depois que o lease expirou. O que existia era um **cliente que parou no meio do protocolo**: ativou
e não completou. Sem um worker em loop, isso é o estado esperado — não um bug do motor.

**Observado — recuperação:** nova ativação com outro rótulo devolveu os **mesmos** 4 `jobKey`:

```text
$ ... -X POST .../v2/jobs/activation -d '{"worker":"w4-recupera","type":"lesson-004-scale","timeout":120000,"maxJobsToActivate":4}'
{"total":4,"jobs":[{"jobKey":"2251799813697730","state":null,"worker":"w4-recupera","retries":3},{"jobKey":"2251799813697739","state":null,"worker":"w4-recupera","retries":3},{"jobKey":"2251799813697747","state":null,"worker":"w4-recupera","retries":3},{"jobKey":"2251799813697758","state":null,"worker":"w4-recupera","retries":3}]}

$ para cada jobKey: ... -X POST .../v2/jobs/$k/completion -d '{}'
2251799813697730 -> 204
2251799813697739 -> 204
2251799813697747 -> 204
2251799813697758 -> 204

$ ... /v2/jobs/search (type lesson-004-scale)
{"total":89,"por_estado":[{"state":"COMPLETED","total":89}]}
$ ... /v2/jobs/search (state CREATED)
0
$ ... /v2/process-instances/search (processDefinitionKey ...)
{"total":89,"por_estado":[{"state":"COMPLETED","total":89}]}
```

**Limitação da medição:** o payload de ativação não trouxe campo `state` (`null` no `jq`). A prova
da recuperação é a **identidade dos `jobKey`** mais os `204` e a mudança dos estados agregados — não
um campo `state` lido item a item. `w4-recupera` é um rótulo novo no mesmo cluster, não um quinto
processo.

## Limitações explícitas

- **1 Broker, 1 partição:** nada aqui prova roteamento dependente da contagem de partições.
- **Três "workers" são três clientes lógicos**, identificados por três valores de `worker`:
  `w4-a` e `w4-b` em disputa direta, mais `w4-drain` para drenar os 2 restantes. O
  `docker compose ps` mostra um `orchestration` e um `connectors`; nenhum container de worker.
- **Concorrência é lógica, não temporal:** as três chamadas de ativação foram sequenciais no
  script. A asserção é disjunção de chaves, não paralelismo medido.
- ~~**Host → container bloqueado pela VPN:** medido via rede Docker; não foi possível usar o runner
  Postman do host nesta máquina.~~ **Correção (E17):** naquela medição o host não alcançava a `8080`.
  Nesta sessão `curl http://localhost:8080/v2/topology` devolveu `200` com corpo real; a limitação
  não vale mais. O runner segue rodando dentro da rede Docker por escolha de reprodutibilidade, não
  por bloqueio de rede.

## N/A — com motivo, não omitidos

- **Log de JVM:** nenhuma JVM nova neste experimento; a prova é o corpo da resposta do Gateway.
- **Vazão (`jobs/s`) e timing:** o laboratório não faz benchmark; medir vazão exigiria medição
  controlada fora do escopo desta lesson.
- **Backpressure (`429`/`503`):** tema da Lesson 005; não há carga neste experimento para
  provocar sinal de sobrecarga.
- **Testes de unidade Java:** nenhum código Java novo nesta lesson; a verificação é a asserção
  sobre o corpo da resposta (E7).

## Regras seguidas

1. **Observado** só com saída real de comando executado.
2. **Fato** só com versão e URL verificada.
3. **Interpretação** nunca substitui medição.
4. Correção de afirmação errada fica visível ao lado da afirmação (E1).
5. Resíduo de probe registrado, não escondido (`w4-probe` em E4 não ativou Job).
