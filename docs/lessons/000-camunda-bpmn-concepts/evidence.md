# Lesson 000 — Evidência (Conceitos de Camunda e BPMN)

Status: coletado

Todas as saídas abaixo são **verbatim**, de uma execução real em **2026-09-29** contra o
Orchestration Cluster local do Camunda **8.9.22**. Os comandos são somente leitura: esta lesson não
implantou nada, não alterou nada e não escreveu BPMN.

O texto da lesson é [lesson.md](lesson.md). O objetivo deste arquivo é que um leitor possa reexecutar
cada afirmação e obter a mesma resposta.

## Ambiente

| Item | Valor |
| --- | --- |
| Imagem do Orchestration | `camunda/camunda:8.9.22` |
| Imagem dos Connectors | `camunda/connectors-bundle:8.9.14` |
| Docker Compose | `v5.3.1` |
| Subido com | `docker compose up -d` em `infra/local/camunda-8.9/` |
| Toolchain | Nada instalado globalmente; mise com escopo no repositório |

### E1 — A stack são dois containers, ambos saudáveis

```console
$ docker compose ps
NAME            IMAGE                              STATUS
connectors      camunda/connectors-bundle:8.9.14   Up About an hour (healthy)
orchestration   camunda/camunda:8.9.22             Up About an hour (healthy)
```

Note o que está **ausente**: nenhum container de banco, nenhum Kafka, nenhum Elasticsearch. O cluster
é autocontido.

**Observado.** `docker compose config --services` declara **três** serviços — `camunda-data-init`,
`orchestration`, `connectors`. O terceiro é um init que ajusta permissões de volume e **sai com
código 0**, e por isso não aparece em `docker compose ps`. Ver
[evidence da Lesson 001 §1.3](../001-camunda7-8-mental-model/evidence.md).

## Provar que os componentes existem

### E2 — Broker e Gateway são reportados separadamente

```console
$ curl -s http://localhost:8080/v2/topology
{"brokers":[{"nodeId":0,"host":"172.21.0.2","port":26501,"partitions":[
  {"partitionId":1,"role":"leader","health":"healthy"}],"version":"8.9.22"}],
 "clusterId":"a3311626-9779-454f-ab80-5d9a3d36077f","clusterSize":1,
 "partitionsCount":1,"replicationFactor":1,"gatewayVersion":"8.9.22",
 "lastCompletedChangeId":"-1"}
```

**Interpretação.** O documento reporta *dois* campos de versão e *dois* papéis. `version` pertence
ao broker, na porta interna `26501`; `gatewayVersion` pertence ao gateway que atendeu esta requisição
HTTP. O motor roda atrás de uma porta de entrada. Esse é o trabalho do Gateway.

> **Nota de escopo.** A Lesson 000 usa esta evidência apenas para sustentar que existe *um motor por
> trás de uma API*. A explicação completa de Gateway, Broker e partições é da Lesson 001.

### E3 — As superfícies voltadas ao usuário respondem

```console
$ for p in /operate /tasklist /admin; do curl -s -o pg.html -w "$p %{http_code}\n" "http://localhost:8080$p"; done
/operate 200
/tasklist 200
/admin 200
```

**Observado.** Três UIs, mesma porta, mesmo container. Detalhe em
[evidence da Lesson 001 §6](../001-camunda7-8-mental-model/evidence.md).

### E4 — Existe exatamente um exporter, e é o de RDBMS

```console
$ curl -s http://localhost:9600/actuator/exporters
[{"exporterId":"rdbms","status":"ENABLED"}]
```

## O log, tornado visível

Esta é a evidência mais importante da lesson, porque transforma "log de eventos" de uma frase em um
número que se pode observar.

### E5 — Uma partição é um log com posição, snapshot e pipeline

O endpoint responde JSON **de uma linha**. A saída abaixo é a **crua**, sem reformatação:

```console
$ curl -s http://localhost:9600/actuator/partitions
{"1":{"role":"LEADER","processedPosition":14553,"snapshotId":"14466-1-14533-14534-0-47cbea74","processedPositionInSnapshot":14533,"streamProcessorPhase":"PROCESSING","exporterPhase":"EXPORTING","exportedPosition":14554,"clock":{"instant":"2026-09-29T23:00:06.305Z","modificationType":"None","modification":{}},"health":{"id":"Partition-1","name":"Partition-1","status":"HEALTHY","componentsState":"HEALTHY","children":[{"id":"SnapshotDirector-1","name":"SnapshotDirector-1","status":"HEALTHY","children":[]},{"id":"ZeebePartitionHealth-1","name":"ZeebePartitionHealth-1","status":"HEALTHY","children":[]},{"id":"StreamProcessor-1","name":"StreamProcessor-1","status":"HEALTHY","children":[]},{"id":"Exporter-1","name":"Exporter-1","status":"HEALTHY","children":[]},{"id":"MigrationSnapshotDirector","name":"MigrationSnapshotDirector","status":"HEALTHY","children":[]},{"id":"RaftPartition-1","name":"RaftPartition-1","status":"HEALTHY","children":[]}]}}}
```

> **Correção registrada.** A primeira versão deste bloco mostrava o JSON indentado, sem o campo
> `clock`, e com os filhos de `health` alinhados à mão. Isso **não era** saída verbatim: era
> pretty-printing feito à mão, que omitia campos e alterava o espaçamento. Agora a captura é a saída
> crua. Para lê-la com quebras, use `| python3 -m json.tool` — e o resultado é um **derivado**, não a
> evidência.

O que cada campo demonstra:

| Campo | O que prova |
| --- | --- |
| `role: LEADER` | Uma partição tem um líder. Um escritor por vez. |
| `processedPosition: 14553` | O log é endereçado por um deslocamento inteiro. O registro 14553 foi processado. |
| `snapshotId` | O log é periodicamente dobrado em um snapshot, então o replay não começa do zero. |
| `streamProcessorPhase: PROCESSING` | Os registros passam pelo motor. |
| `exporterPhase: EXPORTING` | Um **segundo pipeline, independente**, envia registros ao armazenamento secundário. |
| `exportedPosition: 14554` | O banco tem posição própria. Está *atrás ou ao lado* do log, nunca autoritativo. |
| `RaftPartition-1`, `StreamProcessor-1`, `Exporter-1` | Uma partição não é uma linha de tabela; é um subsistema em execução com componentes próprios. |

### E6 — O log está realmente avançando

Três amostras, doze segundos de intervalo:

```console
sample 1 @ 18:05:08: processedPosition=6419 exportedPosition=6418
sample 2 @ 18:05:20: processedPosition=6435 exportedPosition=6436
sample 3 @ 18:05:32: processedPosition=6443 exportedPosition=6444
```

> **Observado.** O log continua crescendo. Uma captura feita horas depois, já com este arquivo
> reescrito, mostrou `processedPosition: 14553`, `exportedPosition: 14554` e um snapshot novo
> (`14466-1-14533-14534-0-47cbea74`) no lugar de `6326-1-6393-6394-0-417adda0`. Nenhuma das duas
> posições diminuíram, e o snapshot foi **reescrito** — o log cresce e o snapshot avança com ele.

**Interpretação.** A posição só anda para frente, e os dois contadores ficam a um ou dois registros
de distância. Essa diferença **é** o atraso do exporter, medido. Quando o `exportedPosition` para
enquanto o `processedPosition` sobe, o banco está atrás do log e o Operate está mostrando dado velho.

### E7 — Como uma partição é escolhida: a configuração de roteamento

```console
$ curl -s http://localhost:9600/actuator/cluster
{"version":1,"brokers":[{"id":0,"state":"ACTIVE","partitions":[
  {"id":1,"state":"ACTIVE","priority":1,
   "config":{"exporting":{"exporters":[{"id":"rdbms","state":"ENABLED"}]}}}]}],
 "routing":{"version":1,
   "requestHandling":{"strategy":"AllPartitions","partitionCount":1},
   "messageCorrelation":{"strategy":"HashMod","partitionCount":1}},
 "clusterId":"a3311626-9779-454f-ab80-5d9a3d36077f"}
```

**Interpretação.** `messageCorrelation.strategy: HashMod` responde "como uma mensagem sabe a qual
partição pertence". A *correlation key* tem seu hash calculado módulo a contagem de partições, e isso
decide o destino. Por isso a contagem de partições não pode ser alterada livremente depois que há
dados: mudar a contagem re-hasheia toda chave.

**Limitação declarada.** Com `partitionCount: 1`, `HashMod` e `AllPartitions` degeneram no mesmo
destino, então este cluster **não demonstra** a diferença de comportamento. O que ele comprova é que
as duas estratégias existem e são independentes. Ver
[evidence da Lesson 001 §5](../001-camunda7-8-mental-model/evidence.md).

## Os dois armazenamentos

### E8 — Primary storage: o log, em disco

```console
$ docker compose exec -T orchestration sh -lc 'find /usr/local/camunda/data -maxdepth 3'
/usr/local/camunda/data
/usr/local/camunda/data/raft-partition
/usr/local/camunda/data/raft-partition/partitions
/usr/local/camunda/data/raft-partition/partitions/1
/usr/local/camunda/data/.topology.meta
```

Com os snapshots abaixo:

```console
$ docker compose exec -T orchestration sh -lc \
    'ls -la /usr/local/camunda/data/raft-partition/partitions/1/'
total 131108
drwxr-xr-x 5 camunda root      4096 Sep 29 19:48 .
drwxr-xr-x 3 camunda root      4096 Sep 29 19:48 ..
drwxr-xr-x 2 camunda root      4096 Sep 29 19:48 bootstrap-snapshots
-rw-r--r-- 1 camunda root 134217728 Sep 29 21:46 raft-partition-partition-1-1.log
-rw-r--r-- 1 camunda root         54 Sep 29 19:48 raft-partition-partition-1.conf
-rw-r--r-- 1 camunda root          1 Sep 29 19:48 .raft-partition-partition-1.lock
-rw-r--r-- 1 camunda root        256 Sep 29 21:46 raft-partition-partition-1.meta
drwxr-xr-x 2 camunda root      4096 Sep 29 21:44 runtime
drwxr-xr-x 3 camunda root      4096 Sep 29 21:44 snapshots
```

**Correção registrada.** A primeira versão deste arquivo afirmava que o diretório de snapshot continha
`MANIFEST-000005`, `000008.sst` e `zeebe.metadata`. A listagem acima mostra a árvore real, e
**nenhum dos três existe** nessa forma: os snapshots ficam em
`snapshots/<snapshotId>/`, e o diretório da partição contém `*.log`, `*.meta`, `*.conf`, `runtime/` e
`snapshots/`. A afirmação anterior vinha de memória de documentação, não de observação.

**Regra que daí decorre:** nenhuma afirmação sobre nome de arquivo, caminho interno ou conteúdo de
volume entra no laboratório sem um `ls`/`find` na evidência.

### E9 — Secondary storage: o banco, em disco

```console
$ docker compose exec -T orchestration sh -lc 'ls -lh /usr/local/camunda/camunda-data'
total 432K
-rw-r--r-- 1 camunda camunda 364K Sep 29 21:04 h2db.mv.db
-rw-r--r-- 1 camunda camunda  66K Sep 29 19:48 h2db.trace.db
```

Um único arquivo H2. A configuração correspondente, de
`infra/local/camunda-8.9/configuration/application-h2.yaml`:

```yaml
camunda:
  data:
    secondary-storage:
      type: rdbms
      rdbms:
        url: jdbc:h2:file:./camunda-data/h2db
        flushInterval: PT0.5S
        queueSize: 1000
```

### E10 — E o banco é uma dependência de primeira classe, verificada

```console
$ curl -s http://localhost:9600/actuator/health
{"status":"UP","components":{"brokerReady":{"status":"UP"},...,
 "rdbmsStatus":{"details":{"database":"H2","validationQuery":"isValid()"},"status":"UP"},...}}
```

**Observado.** `rdbmsStatus` é um **componente de health**, com `"database": "H2"` e uma
`validationQuery`. A plataforma consulta o H2 ativamente. Isso não o torna autoritativo — significa
que estar presente na plataforma implica ser verificável.

### E11 — E o motor de fato tem uma segunda pipeline, independente

```console
$ docker compose logs orchestration | grep -iE "exporterdescriptor|rdbms.?exporter"
io.camunda.zeebe.broker.system - Provide ExporterDescriptor for RDBMS Exporter
io.camunda.exporter.rdbms.RdbmsExporter - [RDBMS Exporter P1] RdbmsExporter created with
  Configuration: flushInterval=PT0.5S, queueSize=1000
io.camunda.exporter.rdbms.RdbmsExporter - [RDBMS Exporter P1] Opening exporter with
  broker position -1
```

**Fato (8.9).** A doc do RDBMS Exporter: *"The RDBMS Exporter consumes records from the log stream,
transforming relevant records and writing them to secondary storage database tables. Operate and
Tasklist query this secondary storage data through the Orchestration Cluster APIs."* O fluxo vem do
log, e Operate e Tasklist **consultam** — não possuem.

### E12 — E não há banco externo

```console
$ docker compose exec -T orchestration sh -lc \
    'netstat -tn | grep -E ":(5432|3306|1521|27017|1433) " || echo NONE'
NONE
```

**Cuidado com a conclusão.** Este comando prova que **não há conexão externa**. Ele **não** prova
que não há banco — e há. Usar só esta verificação produziria uma conclusão correta pelo motivo
errado. A verificação de que o H2 existe e tem papel é E9 e E10.

## Definition versus instance, observado

### E13 — O cluster está saudável, o log cresce, e há zero instâncias

```console
$ curl -s -X POST -H 'Content-Type: application/json' -u demo:demo \
    -d '{"filter":{}}' http://localhost:8080/v2/process-instances/search
{"page":{"totalItems":0,"hasMoreTotalItems":false,"startCursor":null,
  "endCursor":null},"items":[]}
```

**Interpretação.** Esta é a demonstração mais limpa da distinção que a lesson precisa estabelecer. O
motor está rodando, a partição é `LEADER`, o log passou de 6400 registros, e o número de instâncias
de processo é **zero**. O tráfego no log é bookkeeping interno do cluster, não execução de processo.
**Um motor em execução e um processo de negócio ausente são fatos independentes.** Nada foi implantado,
porque esta lesson não implanta nada.

## Dois becos sem saída que vale registrar

Ambos custaram tempo, e ambos são o tipo de coisa que um leitor vai encontrar. Registrá-los é mais
útil do que fingir que não aconteceram.

1. **`/v1/*` não existe mais.** `GET /v1/processes` e `/v1/process-instances` retornam `404`. A API
   foi substituída; use `/v2/...`, e note que o endpoint de instâncias em v2 é um `POST` de busca,
   não um `GET` de listagem.
2. **Um `200` pode ser mentira.** `GET /operate/v2/processes` devolve `200`, mas o corpo é
   `<!doctype html>`: a SPA do Operate, não uma API. Qualquer verificação que afirme apenas sobre o
   status code teria registrado "endpoint de processos funcionando" e concluído que processos
   existem. **Afirme sobre o corpo, não apenas sobre o status.**

## Reprodução

```bash
cd infra/local/camunda-8.9
docker compose up -d

# Componentes
curl -s http://localhost:8080/v2/topology
curl -s http://localhost:9600/actuator/exporters
curl -s http://localhost:9600/actuator/cluster

# O log
curl -s http://localhost:9600/actuator/partitions

# Os dois armazenamentos
docker compose exec -T orchestration ls -la /usr/local/camunda/data/raft-partition/partitions/1/
docker compose exec -T orchestration ls -lh /usr/local/camunda/camunda-data

# O H2 é uma dependência verificada
curl -s http://localhost:9600/actuator/health | grep -o '"database":"[^"]*"'

# Zero instâncias
curl -s -X POST -H 'Content-Type: application/json' -u demo:demo \
  -d '{"filter":{}}' http://localhost:8080/v2/process-instances/search
```

## Limpeza

```bash
cd infra/local/camunda-8.9
docker compose down -v
```

## Ver além desta lesson

Os detalhes de componentes, partições e armazenamento estão desenvolvidos na
[evidence da Lesson 001](../001-camunda7-8-mental-model/evidence.md), e o resumo estrutural, em
[mapa de componentes](../../camunda/camunda-8-local-components.md).
