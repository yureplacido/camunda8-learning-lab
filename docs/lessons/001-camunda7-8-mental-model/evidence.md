# Lesson 001 — Evidence (Camunda 7 → 8 Mental Model)

Status: collected

All output below is verbatim from an actual run on **2026-09-29**. Nothing here is
reconstructed or assumed. See [Camunda 7 vs 8](../../camunda/camunda-7-vs-8.md) for the
interpretation built on top of these observations.

## Environment under test

| Item | Value |
| --- | --- |
| Compose file | `infra/local/camunda-8.9/docker-compose.yaml` (vendored official lightweight) |
| Orchestration image | `camunda/camunda:8.9.22` |
| Connectors image | `camunda/connectors-bundle:8.9.14` |
| Docker Compose | `v5.3.1` |
| Start command | `docker compose up -d` from `infra/local/camunda-8.9/` |
| Toolchain | Nothing installed globally; mise is scoped to the repo (see `.mise.toml`) |

## B2 — Version fidelity (ADR-0002 pins Camunda 8.9.x)

`GET http://localhost:8080/v2/topology`

```json
{
  "brokers": [
    {
      "nodeId": 0,
      "host": "172.21.0.2",
      "port": 26501,
      "partitions": [ { "partitionId": 1, "role": "leader", "health": "healthy" } ],
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

Observed: broker and gateway both report `8.9.22`, single-node cluster, one partition
acting as leader, partition health `healthy`. The version in the evidence matches the
version pinned in `infra/local/camunda-8.9/.env` and the version decided in ADR-0002.

## B3 — Where execution state lives vs. what H2 actually is

**This check was corrected before execution.** An earlier version of the check claimed
Camunda 8 "has no database". That claim is wrong for this topology and is not repeated
here. What the run actually shows is a **split of responsibilities across two separate
local stores**, and no external database at all.

### B3.1 — No database service exists in the stack

```
$ docker compose ps
NAME            IMAGE                              STATUS
connectors      camunda/connectors-bundle:8.9.14   Up About a minute (healthy)
orchestration   camunda/camunda:8.9.22             Up 2 minutes (healthy)
```

### B3.2 — The broker's process opens no database connection

```
$ docker compose exec -T orchestration netstat -tn | grep -E ":(5432|3306|1521|27017|1433) "
NONE - no external database connection
```

The only established TCP connections of the Camunda process are to itself on the
partition port `26501` and to the sibling containers on `8080`/`26500`. There is no
JDBC connection to PostgreSQL, MySQL, Oracle or MongoDB.

### B3.3 — Two distinct data stores, two different mechanisms

Config that produced this (`infra/local/camunda-8.9/configuration/application-h2.yaml`):

```yaml
camunda:
  data:
    secondary-storage:
      type: rdbms
      rdbms:
        url: jdbc:h2:file:./camunda-data/h2db
        username: sa
        password:
        flushInterval: PT0.5S
        queueSize: 1000
```

**Store 1 — broker / execution state** (volume `camunda-89_camunda` →
`/usr/local/camunda/data`):

```
/usr/local/camunda/data/raft-partition
/usr/local/camunda/data/raft-partition/partitions/1/.raft-partition-partition-1.lock
/usr/local/camunda/data/raft-partition/partitions/1/raft-partition-partition-1.conf
/usr/local/camunda/data/raft-partition/partitions/1/snapshots/472-1-539-540-0-13ae4353.checksum
/usr/local/camunda/data/raft-partition/partitions/1/snapshots/472-1-539-540-0-13ae4353/MANIFEST-000005
/usr/local/camunda/data/raft-partition/partitions/1/snapshots/472-1-539-540-0-13ae4353/CURRENT
/usr/local/camunda/data/raft-partition/partitions/1/snapshots/472-1-539-540-0-13ae4353/000008.sst
/usr/local/camunda/data/raft-partition/partitions/1/snapshots/472-1-539-540-0-13ae4353/000009.sst
/usr/local/camunda/data/raft-partition/partitions/1/snapshots/472-1-539-540-0-13ae4353/zeebe.metadata
```

**Store 2 — H2 secondary storage** (volume `camunda-89_camunda-data` →
`/usr/local/camunda/camunda-data`):

```
h2db.mv.db
h2db.trace.db

-rw-r--r-- 1 camunda camunda 409600 /usr/local/camunda/camunda-data/h2db.mv.db
-rw-r--r-- 1 camunda camunda  67581 /usr/local/camunda/camunda-data/h2db.trace.db
```

### B3.4 — The startup log names the mechanism

```
io.camunda.application.commons.rdbms.MyBatisConfiguration - Detected databaseId: h2
io.camunda.application.commons.rdbms.MyBatisConfiguration - Initializing Liquibase for RDBMS with global table trimmedPrefix ''.
io.camunda.zeebe.broker.system - Provide ExporterDescriptor for RDBMS Exporter
io.camunda.exporter.rdbms.RdbmsExporter - [RDBMS Exporter P1] RdbmsExporter created with Configuration: flushInterval=PT0.5S, queueSize=1000
io.camunda.exporter.rdbms.RdbmsExporter - [RDBMS Exporter P1] Opening exporter with broker position -1
io.camunda.exporter.rdbms.RdbmsExporter - [RDBMS Exporter P1] Exporter opened with last exported position -1
```

Observed, and this is the point of the lesson:

- H2 exists and is a real relational file, **but it is secondary storage**, fed by the
  `RdbmsExporter`. An exporter is a one-way, asynchronous projection out of the log.
- Execution state is in Store 1, as a Raft log plus RocksDB snapshots
  (`*.sst`, `MANIFEST-*`, `zeebe.metadata`). It is not queried with SQL.
- The two stores are in **two different Docker volumes**, so the separation is physical,
  not just conceptual.

## B4 — Components reachable from the host

| URL | Status |
| --- | --- |
| `http://localhost:8080/operate` | `200` |
| `http://localhost:8080/tasklist` | `200` |
| `http://localhost:8080/v2/topology` | `200` |
| `http://localhost:9600/actuator/health` | `200` |

Published host ports: `8080` (Operate, Tasklist, Gateway REST), `9600` (Prometheus-style
actuator), `26500` (Zeebe gateway gRPC). Ports `26501`/`26502` (gateway + broker internal
gRPC) are intentionally **not** published to the host.

## Reproduction

```bash
cd infra/local/camunda-8.9
docker compose up -d
docker compose ps
curl -s http://localhost:8080/v2/topology
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:9600/actuator/health
docker compose exec -T orchestration ls -R /usr/local/camunda/data
docker compose exec -T orchestration ls -l /usr/local/camunda/camunda-data
```

To remove the environment:

```bash
cd infra/local/camunda-8.9
docker compose down -v
```

## Out of scope note

`connectors` is part of the official lightweight compose and therefore runs here, but
connectors are **not** part of this lesson. It is only mentioned to avoid a reader
assuming the container belongs to the lesson scope.
