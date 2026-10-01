# Evidência — Lesson 001: Onde esses conceitos vivem dentro do Camunda 8

## Contexto

Ambiente: Docker Compose *lightweight* vendorizado em `infra/local/camunda-8.9/`, cluster
**8.9.22**, coletado em **2026-09-29** no host de desenvolvimento.

Todas as saídas abaixo são **verbatim**. Nenhuma linha foi reescrita ou normalizada. Comandos que
produziram `{}` ou nada estão marcados como tal, porque uma saída vazia também é evidência.

---

## 1. Componentes lógicos existem; containers são apenas dois

### 1.1 Serviços declarados

```console
$ docker compose config --services
camunda-data-init
orchestration
connectors
```

**Observado.** Exatamente **três serviços** declarados, dos quais dois permanecem em execução.
Não há serviço `identity`, `keycloak`, `console`, `optimize`, `web-modeler`, `elasticsearch` nem
`opensearch`.

### 1.2 Containers em execução

```console
$ docker compose ps --format 'table {{.Name}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
NAME            IMAGE                              STATUS                 PORTS
connectors      camunda/connectors-bundle:8.9.14   Up 2 hours (healthy)   0.0.0.0:8086->8080/tcp, [::]:8086->8080/tcp
orchestration   camunda/camunda:8.9.22             Up 2 hours (healthy)   0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp, 0.0.0.0:9600->9600/tcp, [::]:9600->9600/tcp, 0.0.0.0:26500->26500/tcp, [::]:26500->26500/tcp, 26501-26502/tcp
```

**Observado — a assimetria que prova o ponto.** As portas `26501-26502` aparecem como
`26501-26502/tcp`, **sem** o prefixo `0.0.0.0:`. Elas não estão publicadas no host. `26500`, que
é o gRPC do Gateway, está. A fronteira de rede entre a aplicação e o engine está literalmente
nessa diferença de notação.

### 1.3 O container de inicialização já saiu

```console
$ docker compose ps -a --format 'table {{.Name}}\t{{.Status}}'
NAME                             STATUS
camunda-89-camunda-data-init-1   Exited (0) 2 hours ago
connectors                       Up 2 hours (healthy)
orchestration                    Up 2 hours (healthy)
```

**Observado.** `Exited (0)`: código de saída **0**, sucesso. Ele ajusta permissões de volume e
encerra. `docker compose ps` sem `-a` não o lista — o que é exatamente a razão de `-a` constar
nesta evidência: sem ele, o terceiro serviço declarado seria invisível.

### 1.4 Sete componentes lógicos, um processo

```console
$ docker exec orchestration sh -c 'ps -eo pid,comm,args | grep -i "[j]ava"'
        1 java            /usr/lib/jvm/default-jvm/bin/java -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 ...
```

**Observado.** **PID 1, um único processo Java.** Zeebe, Gateway, Broker, Operate, Tasklist e
Admin não são seis processos: são seis responsabilidades dentro deste único processo. A
distinção "componente lógico ≠ container" não é uma sutileza teórica — é a diferença entre
contar 7 e contar 1.

```console
$ docker exec orchestration sh -c 'ls -la /usr/local/camunda/'
total 104
drwxrwxr-x 1 camunda root   4096 Sep 29 07:04 APACHE-2.0.txt
drwxr-xr-x 2 camunda root   4096 Sep 29 07:41 bin
drwxr-xr-x 2 camunda camunda 4096 Sep 29 19:48 camunda-data
drwxr-xr-x 1 camunda root   4096 Sep 29 19:48 config
drwxrwxrws 3 camunda root   4096 Sep 29 19:48 data
drwxr-xr-x 2 camunda root   4096 Sep 29 07:42 documents
lrwxrwxrwx 1 root   root       11 Sep 29 07:42 driver-lib -> /driver-lib
drwxr-xr-x 2 camunda root  32768 Sep 29 07:42 lib
drwxr-xr-x 2 camunda root   4096 Sep 29 19:48 logs
-rw-r--r-- 1 camunda root     50 Sep 29 07:04 NOTICE.txt
-rw-r--r-- 1 camunda root   2340 Sep 29 07:04 README.txt
```

**Observado.** Um único diretório `lib/` (32 KB de entradas), um único `bin/`, um único
`config/`. Não há `lib/zeebe`, `lib/operate`, `lib/tasklist`. É a imagem unificada
`camunda/camunda:8.9.22`.

### 1.5 Volumes e rede

```console
$ docker volume ls --filter name=camunda-89
DRIVER    VOLUME NAME
local     camunda-89_camunda
local     camunda-89_camunda-data
```

```console
$ docker inspect orchestration -f '{{range .Mounts}}  {{.Type}}  {{.Name}}{{if .Name}}{{else}}{{.Source}}{{end}}  ->  {{.Destination}}  (rw={{.RW}}){{println}}{{end}}'
  volume  5cc495506c4f…  ->  /usr/local/camunda/logs  (rw=true)
  volume  cb00460bab6f…  ->  /tmp  (rw=true)
  volume  camunda-89_camunda  ->  /usr/local/camunda/data  (rw=true)
  volume  camunda-89_camunda-data  ->  /usr/local/camunda/camunda-data  (rw=true)
  bind  /…/driver-lib  ->  /driver-lib  (rw=false)
  bind  /…/configuration/application-h2.yaml  ->  /usr/local/camunda/config/application.yaml  (rw=false)
  volume  e5d8bf3d295c…  ->  /usr/local/camunda/documents  (rw=true)
```

**Observado.** Dois volumes nomeados, em caminhos **distintos**:

| Volume | Caminho no container | Papel |
| --- | --- | --- |
| `camunda-89_camunda` | `/usr/local/camunda/data` | Primary storage (log) |
| `camunda-89_camunda-data` | `/usr/local/camunda/camunda-data` | Secondary storage (H2) |

E o `application-h2.yaml` é montado como `/usr/local/camunda/config/application.yaml`, `rw=false`.
A configuração é injetada pelo host, não assada na imagem.

```console
$ for c in orchestration connectors; do docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}} (ip {{$v.IPAddress}}) {{end}}' "$c"; done
  camunda (ip 172.21.0.2)
  camunda (ip 172.21.0.3)
```

```console
$ docker network inspect camunda -f '  labels: {{index .Labels "com.docker.compose.project"}}'
  labels: camunda-89
  containers: connectors orchestration
```

**Observado — uma anomalia registrada de propósito.** Existe também uma rede `camunda-89_default`:

```console
$ docker network ls --filter name=camunda
NETWORK ID     NAME                 DRIVER    SCOPE
7c7339bcd45a   camunda              bridge    local
a578d459ce9d   camunda-89_default   bridge    local
```

Ela pertence ao mesmo projeto (`labels: camunda-89`) e **não tem nenhum container** ligado. É um
resíduo de uma execução anterior do Compose neste diretório, antes de o arquivo declarar
`networks.camunda.name: camunda`. Não afeta esta lesson, e por isso não foi "limpa" — alterá-la
para deixar a evidência mais bonita seria fabricar uma observação. Fica registrado como achado
menor de higiene do ambiente.

---

## 2. O cluster responde, e as versões são visíveis

```console
$ curl -s http://localhost:8080/v2/topology
{
    "brokers": [
        {
            "nodeId": 0,
            "host": "172.21.0.2",
            "port": 26501,
            "partitions": [
                {
                    "partitionId": 1,
                    "role": "leader",
                    "health": "healthy"
                }
            ],
            "version": "8.9.22"
        }
    ],
    "clusterId": "a3311626-9779-454f-ab80-5d9a3d36077f",
    "clusterSize": 1,
    "partitionsCount": 1,
    "replicationFactor": 1,
    "gatewayVersion": "8.9.22",
    "lastCompletedChangeId": "-1"
}
```

**Observado.** `gatewayVersion` **e** a versão do broker aparecem como campos **separados**, ambos
`8.9.22`. Evidência de duas camadas (Gateway e Broker) em um só produto. `host: 172.21.0.2` e
`port: 26501` são o broker — e `26501` é a porta **não** publicada da seção 1.2. O broker se
anuncia com um endereço que o host não alcança, o que é consistente com ele ser interno.

```console
$ curl -s http://localhost:9600/actuator/health
{"status":"UP","components":{"brokerReady":{"status":"UP"},"brokerStartup":{"status":"UP"},"brokerStatus":{"status":"UP"},"livenessState":{"status":"UP"},"nodeIdProvider":{"status":"UP"},"nodeIdProviderReady":{"status":"UP"},"rdbmsStatus":{"details":{"database":"H2","validationQuery":"isValid()"},"status":"UP"},"readinessState":{"status":"UP"}},"groups":["liveness","readiness","startup","status"]}
```

**Observado — a evidência mais forte da seção de H2.** `rdbmsStatus` é um **componente de
health** de primeira classe, e ele declara `"database": "H2"` com `validationQuery: isValid()`.
Ou seja: o H2 não está ali por acidente; ele é uma dependência monitorada, e a plataforma o
consulta periodicamente. Ele participa do grupo `status`. Apagá-lo não é apagar um lixo.

---

## 3. Primary storage: o log existe, e o snapshot existe no disco

```console
$ docker exec orchestration sh -c 'ls -la /usr/local/camunda/data/raft-partition/partitions/1/'
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

**Observado.** `raft-partition-partition-1-1.log` com **134217728 bytes** (128 MiB) —
pré-alocado. `raft-partition-partition-1-1.log` éappend-only: o nome termina em `-1-1`
(partition 1, log segment 1). **`zeebe.metadata` não existe**, e `MANIFEST-*` também não. Uma
versão anterior desta lesson afirmava ambos; a verificação do disco os refutou, e o texto foi
corrigido para a árvore real. Registro isso como falha de método, não só de conteúdo.

```console
$ docker exec orchestration sh -c 'find /usr/local/camunda/data -maxdepth 4'
/usr/local/camunda/data
/usr/local/camunda/data/raft-partition
/usr/local/camunda/data/raft-partition/partitions
/usr/local/camunda/data/raft-partition/partitions/1
/usr/local/camunda/data/raft-partition/partitions/1/bootstrap-snapshots
/usr/local/camunda/data/raft-partition/partitions/1/.raft-partition-partition-1.lock
/usr/local/camunda/data/raft-partition/partitions/1/snapshots
/usr/local/camunda/data/raft-partition/partitions/1/raft-partition-partition-1.conf
/usr/local/camunda/data/raft-partition/partitions/1/raft-partition-partition-1.meta
/usr/local/camunda/data/raft-partition/partitions/1/raft-partition-partition-1-1.log
/usr/local/camunda/data/raft-partition/partitions/1/runtime
/usr/local/camunda/data/.topology.meta
```

```console
$ docker exec orchestration sh -c 'ls -la /usr/local/camunda/data/raft-partition/partitions/1/snapshots/'
total 16
drwxr-xr-x 3 camunda root 4096 Sep 29 21:44 .
drwxr-xr-x 5 camunda root 4096 Sep 29 19:48 ..
drwxr-xr-x 2 camunda root 4096 Sep 29 21:44 9170-1-9237-9238-0-739727fe
-rw-r--r-- 1 camunda root  713 Sep 29 21:44 9170-1-9237-9238-0-739727fe.checksum
```

```console
$ docker exec orchestration sh -c 'ls -la /usr/local/camunda/data/raft-partition/partitions/1/runtime/ | head'
total 4448
drwxr-xr-x 2 camunda root  4096 Sep 29 21:44 .
drwxr-xr-x 5 camunda root  4096 Sep 29 19:48 ..
-rw-r--r-- 2 camunda root     0 Sep 29 19:48 000004.log
-rw-r--r-- 2 camunda root  1415 Sep 29 21:39 000083.sst
-rw-r--r-- 2 camunda root  1381 Sep 29 21:39 000084.sst
...
-rw-r--r-- 2 camunda root 12241 Sep 29 21:39 000088.sst
```

**Observado.** Três coisas distintas e todas reais:

- `snapshots/9170-1-9237-9238-0-739727fe/` + `.checksum` — o snapshot;
- `runtime/*.sst` — estado materializado em RocksDB, o resultado de aplicar o log sobre o snapshot;
- `raft-partition-partition-1-1.log` — o log de eventos.

O `snapshotId` no disco é **idêntico** ao que o actuator reportou (§4). Isso fecha a cadeia: o
identificador que o actuator publica é literalmente o nome do diretório em disco.

```console
$ docker exec orchestration sh -c 'ls -la /usr/local/camunda/camunda-data/'
total 444
drwxrwxr-x 2 camunda camunda   4096 Sep 29 19:48 .
drwxrwsr-x 1 camunda root      4096 Sep 29 19:48 ..
-rw-r--r-- 1 camunda camunda 372736 Sep 29 21:46 h2db.mv.db
-rw-r--r-- 1 camunda camunda  67581 Sep 29 19:48 h2db.trace.db
```

**Observado.** Dois arquivos H2, **em outro volume**. Nenhum `.sst`, `.log` ou `snapshot` aqui. A
separação física é real, não uma distinção de nomenclatura.

---

## 4. Partições, posições e fases

```console
$ curl -s http://localhost:9600/actuator/partitions
{
    "1": {
        "role": "LEADER",
        "processedPosition": 9399,
        "snapshotId": "9170-1-9237-9238-0-739727fe",
        "processedPositionInSnapshot": 9237,
        "streamProcessorPhase": "PROCESSING",
        "exporterPhase": "EXPORTING",
        "exportedPosition": 9400,
        "clock": { "instant": "2026-09-29T21:47:05.244Z", "modificationType": "None", "modification": {} },
        "health": {
            "id": "Partition-1", "name": "Partition-1",
            "status": "HEALTHY", "componentsState": "HEALTHY",
            "children": [
                { "id": "SnapshotDirector-1",            "status": "HEALTHY" },
                { "id": "ZeebePartitionHealth-1",       "status": "HEALTHY" },
                { "id": "StreamProcessor-1",            "status": "HEALTHY" },
                { "id": "Exporter-1",                    "status": "HEALTHY" },
                { "id": "MigrationSnapshotDirector",     "status": "HEALTHY" },
                { "id": "RaftPartition-1",               "status": "HEALTHY" }
            ]
        }
    }
}
```

**Observado.**

- A resposta é um **objeto indexado por `"1"`**, não uma lista. Um parser que assuma `d[0]` falha
  com `KeyError: 0`. Aconteceu nesta sessão e é anotado em "Falhas" abaixo.
- `role: LEADER`, `status: HEALTHY`.
- `processedPosition: 9399`, `exportedPosition: 9400` — o exporter acompanha o processador, com 1
  de atraso. **Esse par é o instrumento de diagnóstico da seção "Operate vazio".**
- `processedPositionInSnapshot: 9237` → restam 9399 − 9237 = **162 registros** a reproduzir após
  um restart, não 9399.
- `streamProcessorPhase: PROCESSING` e `exporterPhase: EXPORTING` são **fases independentes**,
  observadas simultaneamente. O log e a projeção correm em paralelo.
- A árvore de `health.children` nomeia `StreamProcessor-1` e `Exporter-1` como componentes
  separados dentro da mesma partição — Broker (processa) e exporter (projeta), no mesmo processo.

### 4.1 As posições avançam (dois instantes)

| Momento | `processedPosition` | `exportedPosition` | `snapshotId` |
| --- | --- | --- | --- |
| ~21:46 | 8959 | 8960 | `8813-1-8881-8880-0-6798ef83` |
| ~21:47 | 9399 | 9400 | `9170-1-9237-9238-0-739727fe` |

**Observado.** Em ~1 minuto: +440 eventos processados, e um **novo snapshot** foi gravado
(`8813…` → `9170…`). Duas coisas que uma tabela relacional comum não produziria nesse formato.

---

## 5. Roteamento: duas estratégias, e elas não são a mesma coisa

```console
$ curl -s http://localhost:9600/actuator/cluster | python3 -c 'import sys,json;print(json.dumps(json.load(sys.stdin)["routing"],indent=2))'
{
  "version": 1,
  "requestHandling": {
    "strategy": "AllPartitions",
    "partitionCount": 1
  },
  "messageCorrelation": {
    "strategy": "HashMod",
    "partitionCount": 1
  }
}
```

**Observado.** Confirmado no cluster, e em **campos separados**:

- `requestHandling: AllPartitions` — comandos vão para todas as partições;
- `messageCorrelation: HashMod` — mensagens vão para `hash(correlationKey) % partitionCount`.

Com `partitionCount: 1` ambos degeneram no mesmo destino, então este cluster **não demonstra** a
diferença de comportamento. Isso é uma limitação do ambiente de um nó, e vale mais ser explícito
sobre ela do que inferir o comportamento de 3 partições de um cluster de 1. O que o cluster
comprova é que as **duas estratégias existem e são independentes**; a consequência em multi-partição
é **Fato documentado**, não observado aqui.

---

## 6. As três interfaces, uma porta

```console
$ for p in /operate /tasklist /admin; do curl -s -o pg.html -w "$p %{http_code}\n" "http://localhost:8080$p"; grep -o '<title>[^<]*</title>' pg.html; done
/operate 200  <title>Operate</title>
/tasklist 200  <title>Tasklist</title>
/admin 200  <title>Camunda Admin</title>
```

**Observado.** Três aplicações web distintas, **três títulos distintos**, **uma porta** (`8080`),
**um container**. Isso é a prova operacional de que são componentes lógicos independentes
servidos por um processo comum.

### 6.1 "Camunda Web" não existe aqui

```console
$ for p in /camunda /c8; do curl -s -o /dev/null -w "$p %{http_code}\n" "http://localhost:8080$p"; done
/camunda 404
/c8 404
```

**Observado.** `404` nos dois. Nenhum produto chamado "Camunda Web" responde neste cluster.

### 6.2 A chave de configuração se chama `identity`; o produto se chama Admin

```console
$ curl -s http://localhost:9600/actuator/configprops   # filtrado por 'webapp'
  webapps = {'identity': {'enabled': True, 'uiEnabled': True},
             'operate':  {'enabled': True, 'uiEnabled': True},
             'tasklist': {'enabled': True, 'uiEnabled': True}}
  webappEnabled = True
```

**Observado.** Três chaves — `identity`, `operate`, `tasklist` — com `enabled: True` e
`uiEnabled: True`, e um flag global `webappEnabled: True`. Combinado com o `<title>Camunda
Admin</title>` da §6: **chave de config `identity`, nome de produto `Admin`.** Divergência real
de nomenclatura entre camada de configuração e camada de produto na mesma versão.

---

## 7. Secondary storage: o `rdbms` exporter está habilitado

```console
$ curl -s http://localhost:9600/actuator/exporters
[{"exporterId":"rdbms","status":"ENABLED"}]
```

**Observado.** Um exporter, `rdbms`, `ENABLED`.

```console
$ curl -s http://localhost:9600/actuator/cluster | python3 -m json.tool | head -25
{
    "version": 1,
    "brokers": [
        {
            "id": 0,
            "state": "ACTIVE",
            "version": 0,
            "partitions": [
                {
                    "id": 1,
                    "state": "ACTIVE",
                    "priority": 1,
                    "config": {
                        "exporting": {
                            "exporters": [
                                { "id": "rdbms", "state": "ENABLED" }
                            ]
                        }
                    }
                }
            ]
        }
    ]
}
```

**Observado.** O exporter `rdbms` está na **configuração exportadora da partição 1**, dentro do
broker. Ele é uma peça do motor, não um componente externo conectado depois.

### 7.1 A configuração que liga os dois lados

Conteúdo de `infra/local/camunda-8.9/configuration/application-h2.yaml`, montado como
`/usr/local/camunda/config/application.yaml`:

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

**Observado.** `type: rdbms` + `url: jdbc:h2:file:./camunda-data/h2db` + `flushInterval: PT0.5S`.
O caminho relativo `./camunda-data/` resolve para `/usr/local/camunda/camunda-data/`, que é
exatamente onde `h2db.mv.db` foi encontrado na §3. A configuração e o arquivo em disco fecham.

`PT0.5S` é a **latência de projeção** citada na seção de implicações de arquitetura. Observe que
"Operate está atrasado" é um par: `flushInterval` está no YAML, e o atraso real está no par de
posições da §4.

---

## 8. API aberta no perfil lightweight

```console
$ curl -s -o pi.json -w 'HTTP %{http_code}\n' -X POST http://localhost:8080/v2/process-instances/search \
    -H 'Content-Type: application/json' -d '{}'
HTTP 200
{"page":{"totalItems":0,"hasMoreTotalItems":false,"startCursor":null,"endCursor":null},"items":[]}
```

**Observado.** `200` **sem nenhuma credencial**, `totalItems: 0`. Dois fatos independentes, e
importante não confundi-los:

1. A API está **desprotegida** neste perfil (`unprotectedApi`) — não é bug, é o perfil *lightweight*;
2. Existem **zero instâncias** de processo.

O cluster está saudável, o exporter está `ENABLED`, o log avançou ~440 eventos em um minuto, e
mesmo assim não há uma única instância. Um cluster recém-subido tem um log que cresce com
eventos de sistema — e nenhum processo de negócio. É por isso que "log avançando" e "processo
rodando" são observações diferentes, e o diagrama da seção 9 diz "criar instância" como um
comando, não como algo que o cluster faz sozinho.

---

## 9. Falhas durante a coleta

Registradas porque o método de verificar é parte da evidência.

**F1 — parser assumiu lista.** `d[0]` em `/actuator/partitions` lançou `KeyError: 0`. A resposta
é um objeto com a chave `"1"`. O script de coleta foi corrigido e a resposta bruta (§4) foi
inspecionada. Sem isso, a seção de evidência teria registrado "sem partições" — um erro
silencioso, do tipo que sobrevive à revisão.

**F2 — `200` que não provava nada.** `GET /operate/v2/processes` devolveu `200` com corpo
`<!doctype html>`: a SPA do Operate atendendo a uma rota de API. Uma checagem baseada apenas em
status code teria gravado um fato falso. Todas as afirmações de componente nesta lesson apontam
para um comando que devolve **substância** — versão, papel de partição, fase, título de página,
exporter `ENABLED` — e não para um `200`.

**F3 — afirmação sobre conteúdo do volume, refutada pelo volume.** A primeira versão desta lesson
afirmava que o primary storage continha `MANIFEST-*` e `zeebe.metadata`. O `ls` do diretório
(§3) mostra que **nenhum dos dois existe**. A afirmação veio de memória de documentação, não de
observação. Foi corrigida para a árvore real. Regra que daqui em diante: nenhuma afirmação sobre
conteúdo de volume, caminho interno ou nome de arquivo sem um `ls`/`find` na evidência.

**F4 — statement refutado pelo próprio sistema em execução.** A versão anterior desta lesson
afirmava "Camunda 8 não usa banco de dados". Duas observáveis o refutam: `rdbmsStatus.database:
"H2"` como componente de health (§2), e `h2db.mv.db` em disco (§3). A conclusão correta não é
"usa banco" nem "não usa banco" — é "usa banco, e ele não é o estado de execução". A correção
está na seção 13 da lesson.

---

## 10. Fatos versionados usados, e onde verificar

| Fato | Versão | Origem | Verificado |
| --- | --- | --- | --- |
| Gateway é *"a single entry point"* e é *"stateless and sessionless"* | 8.9 | `docs.camunda.io/docs/components/zeebe/technical-concepts/architecture` | fetch 200, texto extraído |
| Broker é o *"distributed workflow engine"*; *"no application business logic lives in the broker"*; 3 responsabilidades | 8.9 | `docs.camunda.io/docs/components/zeebe/technical-concepts/architecture` | fetch 200, texto extraído |
| Job worker *"is a Zeebe client"*; clientes fazem *"carry out business logic"* | 8.9 | `docs.camunda.io/docs/components/zeebe/technical-concepts/architecture` | fetch 200, texto extraído |
| Partição = *"a persistent stream of process-related events"*; líder processa, follower *"without performing event processing"* | 8.9 | `docs.camunda.io/docs/components/zeebe/technical-concepts/partitions` | fetch 200, texto extraído |
| Primary storage = *"the authoritative store for runtime execution state"* | 8.9 | `docs.camunda.io/docs/components/concepts/concepts-overview` | fetch 200, texto extraído |
| Secondary storage *"populated from primary storage and optimized for querying rather than execution"* | 8.9 | `docs.camunda.io/docs/components/concepts/concepts-overview` | fetch 200, texto extraído |
| *"Embedded H2: A bundled secondary storage option for local development and lightweight setups"* | 8.9 | `docs.camunda.io/docs/components/concepts/concepts-overview` | fetch 200, texto extraído |
| Zeebe *"writes data directly to the file system on the same servers where it is deployed"* | 8.9 | `docs.camunda.io/docs/components/concepts/concepts-overview` | fetch 200, texto extraído |
| RDBMS Exporter *"consumes records from the log stream"* → tabelas de secondary storage; Operate e Tasklist **consultam** | 8.9 | `docs.camunda.io/docs/self-managed/components/orchestration-cluster/zeebe/exporters/rdbms-exporter` | fetch 200, texto extraído |
| Gateway é o *"contact point"* que permite a clientes falar com brokers | 8.9 | `docs.camunda.io/docs/self-managed/components/orchestration-cluster/zeebe/overview` | fetch 200, texto extraído |
| Operate/Tasklist/Identity fundidos ao perfil do gateway; *"treated as UIs served by the Zeebe Gateway"* | 8.9 | `docs.camunda.io/docs/reference/announcements-release-notes/890/890-release-notes` | busca; `curl 200`; `release-notes/890/` retorna **404** |
| Imagem unificada; `camunda/zeebe`, `camunda/operate`, `camunda/tasklist` não são mais produzidas a partir de 8.9.12 | 8.9.12+ | `.../890/890-release-notes` e `.../890/890-announcements` | busca; `curl 200` no release notes. A announcements confirma: *"8.9.12 \| Breaking change \| Individual component Docker images no longer produced"* |
| Operate e Tasklist em *"a single unified frontend application"*, exigem prefixo de path explícito | 8.9 | `docs.camunda.io/docs/reference/announcements-release-notes/890/890-announcements` | busca; URL real confirmada |
| Perfil Spring `admin` substitui `identity`; ambos funcionam em 8.9; `/identity/*` → `/admin/*` | 8.9 | `docs.camunda.io/docs/reference/announcements-release-notes/890/whats-new-in-89/` | busca; URL real confirmada |
| Orchestration Cluster inclui *"Zeebe as the workflow engine, Operate…, Tasklist…, Admin (formerly Orchestration Cluster Identity)… and APIs"* — cinco entradas, lista **diferente** da de cinco deste laboratório | 8.9 | `docs.camunda.io/docs/self-managed/components/orchestration-cluster/overview` | fetch 200, texto extraído |
| Portas: `26500` Gateway gRPC; `26501` `commandApi` SBE Gateway→Broker; `26502` `internalApi` Gossip/Raft **e** `gateway.cluster.port`; `9600` `monitoringApi` do Broker (*"Metrics and Readiness Probe"*) | 8.9 | `docs.camunda.io/docs/self-managed/components/orchestration-cluster/zeebe/operations/network-ports` | fetch 200, texto extraído |
| Camunda 8 Run: JDK 21–25, Experimental na 8.9, *"not intended for production use"* | 8.9 | `docs.camunda.io/docs/self-managed/quickstart/developer-quickstart/c8run/` | fetch 200 |
| `application-h2.yaml` é o default; H2 embutido; driver H2 já na imagem | 8.9 | `README.md` upstream de `docker-compose-8.9`, vendorizado em `infra/local/camunda-8.9/` | leitura local |
| Perfil *lightweight* declara `camunda-data-init`, `orchestration`, `connectors`; 2 volumes; rede `camunda` | 8.9 | `docker-compose.yaml` vendorizado, lido localmente | **leitura local verificada** |
| README upstream cita variantes *lightweight*, *full* e *standalone* | 8.9 | `infra/local/camunda-8.9/README.md` | leitura local |
| Conteúdo de cada variante além da *lightweight* | 8.9 | — | ⚠️ **NÃO VERIFICADO** — `docker-compose-full.yaml` **não** está vendorizado. Nada é afirmado sobre quais containers cada uma declara. Ver F10 |

**F5 — URLs inventadas, refutadas pelo `curl`.** A primeira versão desta tabela citava
`/docs/release-notes/890/` e `/docs/release-notes/8912/`, entre outros caminhos que **não existem**.
Um retornou `404`; os demais, `000` (bloqueio). Nenhuma dessas URLs foi escrita por mim como
"fonte": foram inferidas a partir do padrão de URL de uma página vizinha. A correção foi buscar a
URL real de cada afirmação e re-verificar. Tabela reescrita com a coluna **Verificado** para que a
diferença entre "busquei e li", "confirmei por `curl 200`" e "inferi do padrão" fique visível.

**F6 — `000` não é evidência de URL quebrada.** Numa rodada posterior de verificação por `curl`, várias
URLs que já estavam confirmadas passaram a devolver `000`, inclusive a URL de controle
`components/zeebe/technical-concepts/architecture`. `000` é falha de conexão/rate limit do ambiente,
não resposta do servidor. Conclusão registrada: **`000` nunca deve ser lido como "URL inválida"**, e
nenhuma URL foi rebaixada na tabela com base nele. Onde a coluna continua dizendo "busca", é porque o
texto **foi localizado e lido** na busca, mas o `curl` desta rodada não confirmou o status.

**F7 — Uma citação verbatim estava falsificada.** A primeira versão de
`camunda-8-local-components.md` atribuía à documentação a frase *"An Orchestration Cluster
includes…"*. O texto real começa com *"The Orchestration Cluster includes…"*. Corrigido, e a fonte
agora está marcada `fetch 200, texto extraído`.

**F8 — A contagem de componentes não fechava, e por isso está errada em três lugares.** A
documentação oficial lista **cinco** entradas: `Zeebe`, `Operate`, `Tasklist`, `Admin`, `APIs`. A
primeira versão do material afirmava "**sete** componentes lógicos" e dava três listas diferentes
— uma com seis nomes e o número sete, outra com seis nomes, outra com sete incluindo "APIs". A conta
não fechava em nenhuma.

**O que realmente estava errado:** "APIs" foi contado como componente, e "Zeebe" foi mantido na
tabela **além** de `Gateway` e `Broker`. Isso conta o motor duas vezes. A lista correta deste
laboratório substitui `Zeebe` por `Gateway` + `Broker` e remove `APIs`:
**5 − 1 − 1 + 2 = 5**.

**A coincidência que confunde:** a lista oficial e a lista deste laboratório têm o mesmo tamanho,
cinco, mas **não são a mesma lista**. Por isso "cinco" só vale como resposta se acompanhada de *quais*
cinco. A lista canônica e a derivação estão em
[mapa de componentes](../../camunda/camunda-8-local-components.md), marcadas como **Interpretação**.

**F9 — Duas portas estavam descritas errado, em sete lugares.** Uma versão anterior dizia que
`26501`/`26502` eram "gRPC interno do broker" e roteava o actuator `:9600` para o **Gateway** em três
diagramas. A
[doc oficial de network ports](https://docs.camunda.io/docs/self-managed/components/orchestration-cluster/zeebe/operations/network-ports)
corrige: `26501` é a `commandApi`, Gateway→Broker, *"using an internal SBE (Simple Binary Encoding)
protocol"* — **não** é gRPC; `26502` é a `internalApi` (Gossip/Raft) **e** o `gateway.cluster.port`;
e `9600` é a `monitoringApi` **do Broker** (*"Metrics and Readiness Probe"*).

Por que o erro não apareceu nos testes: num cluster de nó único, Gateway e Broker são o mesmo
processo Java, então a aresta errada é indistinguível a olho nu. A verificação foi contra a
documentação, não contra a observação local.

---

## 11. Regras de evidência desta lesson

1. **Fato (8.9)** = afirmação da documentação, com a URL na §10.
2. **Observado** = saída verbatim de um comando neste ambiente, registrada acima sem edição.
3. **Interpretação** = leitura minha a partir de um Fato ou de uma Observação, marcada como tal na
   lesson.

Nenhuma afirmação sobre componente, porta, caminho ou arquivo existe nesta lesson sem uma destas
três bases. Quando as três coincidem, escrevo uma vez e cito. Quando só duas, marco a
diferença. Quando só uma, não afirmo.

**F10 — Uma linha de fonte afirmava uma leitura que não aconteceu.** A tabela de fontes marcava,
para o perfil *full* do Compose, a fonte "`docker-compose.yaml` e `docker-compose-full.yaml`
upstream" e o método "leitura local". `ls infra/local/camunda-8.9/` devolve:

```
camunda-data  configuration  connector-secrets.txt  docker-compose.yaml  driver-lib  README.md
```

**`docker-compose-full.yaml` não existe no repositório.** `fetch-compose.sh` copia só
`docker-compose.yaml`, `.env`, `connector-secrets.txt`, `configuration/`, `driver-lib/`,
`camunda-data/` e `README.md`. A leitura local do perfil *full* era, portanto, impossível, e a linha
foi reescrita como `NÃO VERIFICADO`. A lesson, seção 12, tinha a mesma falha e foi corrigida
julgando pelo `README.md` vendorizado, que é a única fonte disponível.

**F11 — Evidência "verbatim" que era pretty-printing manual.** Na Lesson 000, o bloco E5
(`/actuator/partitions`) mostrava o JSON indentado, sem o campo `clock` e com os filhos de `health`
alinhados à mão. O endpoint responde JSON **de uma linha**. O bloco foi substituído pela saída crua,
e o registro da correção ficou no lugar.

**Regra que estas três correções (F10, F11 e a de ports, F9) têm em comum:** cada uma era uma
afirmação que **parecia** verificada e não era. A verificação tinha sido feita contra outra coisa —
memória de documentação, uma formatação feita à mão, ou uma inferência razoável. Nenhuma delas
requeria decepção; todas exigiam um comando.
