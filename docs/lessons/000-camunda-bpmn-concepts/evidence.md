# Lesson 000 — Evidence (Camunda and BPMN Concepts)

Status: collected

Every output below is verbatim from a real run on **2026-09-29** against the local
Camunda **8.9.22** Orchestration Cluster. Commands are read-only: this lesson deployed
nothing, changed nothing, and wrote no BPMN.

The lesson text is [lesson.md](lesson.md). The point of this file is that a reader can
re-run every claim and get the same answer.

## Environment

| Item | Value |
| --- | --- |
| Orchestration image | `camunda/camunda:8.9.22` |
| Connectors image | `camunda/connectors-bundle:8.9.14` |
| Docker Compose | `v5.3.1` |
| Started with | `docker compose up -d` in `infra/local/camunda-8.9/` |
| Toolchain | Nothing installed globally; mise scoped to the repo |

### E1 — The stack is two containers, both healthy

```
$ docker compose ps
NAME            IMAGE                              STATUS
connectors      camunda/connectors-bundle:8.9.14   Up About an hour (healthy)
orchestration   camunda/camunda:8.9.22             Up About an hour (healthy)
```

Note what is *absent*: no database container, no Kafka, no Elasticsearch. The cluster is
self-contained.

## Proving the components exist

### E2 — Broker and gateway are reported separately

```
$ curl -s http://localhost:8080/v2/topology
{"brokers":[{"nodeId":0,"host":"172.21.0.2","port":26501,"partitions":[
  {"partitionId":1,"role":"leader","health":"healthy"}],"version":"8.9.22"}],
 "clusterId":"a3311626-9779-454f-ab80-5d9a3d36077f","clusterSize":1,
 "partitionsCount":1,"replicationFactor":1,"gatewayVersion":"8.9.22",
 "lastCompletedChangeId":"-1"}
```

**Interpretation.** The document reports *two* version fields and *two* roles. `version`
belongs to the broker on internal port `26501`; `gatewayVersion` belongs to the gateway
that answered this HTTP request. The engine runs behind a front door. This is the
gateway's whole job.

### E3 — The four user-facing surfaces answer

```
200  http://localhost:8080/operate
200  http://localhost:8080/tasklist
200  http://localhost:8080/v2/topology
200  http://localhost:9600/actuator/health
```

### E4 — There is exactly one exporter, and it is the RDBMS one

```
$ curl -s http://localhost:9600/actuator/exporters
[{"exporterId":"rdbms","status":"ENABLED"}]
```

## The log, made visible

This is the most important evidence in the lesson, because it turns "event log" from
a phrase into a number you can watch.

### E5 — A partition is a log with a position, a snapshot, and a pipeline

```
$ curl -s http://localhost:9600/actuator/partitions
{
  "1": {
    "role": "LEADER",
    "processedPosition": 6403,
    "snapshotId": "6326-1-6393-6394-0-417adda0",
    "processedPositionInSnapshot": 6393,
    "streamProcessorPhase": "PROCESSING",
    "exporterPhase": "EXPORTING",
    "exportedPosition": 6404,
    "health": {
      "status": "HEALTHY",
      "children": [
        { "id": "SnapshotDirector-1",            "status": "HEALTHY" },
        { "id": "ZeebePartitionHealth-1",        "status": "HEALTHY" },
        { "id": "StreamProcessor-1",             "status": "HEALTHY" },
        { "id": "Exporter-1",                     "status": "HEALTHY" },
        { "id": "MigrationSnapshotDirector",     "status": "HEALTHY" },
        { "id": "RaftPartition-1",               "status": "HEALTHY" }
      ]
    }
  }
}
```

What each field demonstrates:

| Field | What it proves |
| --- | --- |
| `role: LEADER` | A partition has a leader. One writer at a time. |
| `processedPosition: 6403` | The log is addressed by an integer offset. Record 6403 has been processed. |
| `snapshotId` | The log is periodically folded into a snapshot, so replay does not start at zero. |
| `streamProcessorPhase: PROCESSING` | Records stream through the engine. |
| `exporterPhase: EXPORTING` | A **second, independent pipeline** ships records to secondary storage. |
| `exportedPosition: 6404` | The database has its own position. It is *behind or alongside* the log, never authoritative. |
| `RaftPartition-1`, `StreamProcessor-1`, `Exporter-1` | One partition is not a row in a table; it is a running subsystem with its own components. |

### E6 — The log is genuinely advancing

Three samples, twelve seconds apart:

```
sample 1 @ 18:05:08: processedPosition=6419 exportedPosition=6418
sample 2 @ 18:05:20: processedPosition=6435 exportedPosition=6436
sample 3 @ 18:05:32: processedPosition=6443 exportedPosition=6444
```

**Interpretation.** The position only ever moves forward and the two counters stay within
one or two records of each other. That gap *is* the exporter lag, measured. When the
`exportedPosition` stalls while `processedPosition` climbs, the database is behind the log
and Operate is showing stale data. The lesson in Lesson 001 about "Operate is empty, now
what?" is answered directly by this pair of numbers.

### E7 — How a partition is chosen: the cluster's routing config

```
$ curl -s http://localhost:9600/actuator/cluster
{"version":1,"brokers":[{"id":0,"state":"ACTIVE","partitions":[
  {"id":1,"state":"ACTIVE","priority":1,
   "config":{"exporting":{"exporters":[{"id":"rdbms","state":"ENABLED"}]}}}]}],
 "routing":{"version":1,
   "requestHandling":{"strategy":"AllPartitions","partitionCount":1},
   "messageCorrelation":{"strategy":"HashMod","partitionCount":1}},
 "clusterId":"a3311626-9779-454f-ab80-5d9a3d36077f"}
```

**Interpretation.** `messageCorrelation.strategy: HashMod` is the answer to "how does a
message know which partition it belongs to". The correlation key is hashed modulo the
partition count, and that decides the destination. This is why the partition count cannot
be changed freely after data exists: changing it re-hashes every key. It also explains
why "one process instance lives entirely in one partition" and why you cannot address a
process instance without its key.

## The two stores

### E8 — Primary storage: the log, on disk

```
$ docker compose exec -T orchestration sh -lc \
    'ls -1 /usr/local/camunda/data; find /usr/local/camunda/data -maxdepth 3 | head'
/usr/local/camunda/data
/usr/local/camunda/data/raft-partition
/usr/local/camunda/data/raft-partition/partitions
/usr/local/camunda/data/raft-partition/partitions/1
/usr/local/camunda/data/.topology.meta
```

With snapshots beneath it:

```
/usr/local/camunda/data/raft-partition/partitions/1/snapshots/
  6326-1-6393-6394-0-417adda0/MANIFEST-000005
  6326-1-6393-6394-0-417adda0/000008.sst
  6326-1-6393-6394-0-417adda0/zeebe.metadata
  6326-1-6393-6394-0-417adda0.checksum
```

`.sst` files are RocksDB SSTables and `zeebe.metadata` marks it as the Zeebe stream. This
is not a relational directory layout; it is an embedded log-structured store.

### E9 — Secondary storage: the database, on disk

```
$ docker compose exec -T orchestration sh -lc 'ls -lh /usr/local/camunda/camunda-data'
total 432K
-rw-r--r-- 1 camunda camunda 364K Sep 29 21:04 h2db.mv.db
-rw-r--r-- 1 camunda camunda  66K Sep 29 19:48 h2db.trace.db
```

A single H2 file. The corresponding configuration, from
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

### E10 — The database receives data from an exporter, not from the engine directly

```
$ docker compose logs orchestration | grep -iE "exporterdescriptor|rdbms.?exporter"
io.camunda.zeebe.broker.system - Provide ExporterDescriptor for RDBMS Exporter
io.camunda.exporter.rdbms.RdbmsExporter - [RDBMS Exporter P1] RdbmsExporter created with
  Configuration: flushInterval=PT0.5S, queueSize=1000
io.camunda.exporter.rdbms.RdbmsExporter - [RDBMS Exporter P1] Opening exporter with
  broker position -1
io.camunda.exporter.rdbms.RdbmsExporter - [RDBMS Exporter P1] Exporter opened with last
  exported position -1

$ docker compose logs orchestration | grep MyBatisConfiguration
io.camunda.application.commons.rdbms.MyBatisConfiguration - Detected databaseId: h2
io.camunda.application.commons.rdbms.MyBatisConfiguration - Initializing Liquibase for
  RDBMS with global table trimmedPrefix ''.
```

### E11 — And there is no external database at all

```
$ docker compose exec -T orchestration sh -lc \
    'netstat -tn | grep -E ":(5432|3306|1521|27017|1433) " || echo NONE'
NONE
```

## Definition versus instance, observed

### E12 — The cluster is healthy, the log is growing, and there are zero instances

```
$ curl -s -X POST -H 'Content-Type: application/json' -u demo:demo \
    -d '{"filter":{}}' http://localhost:8080/v2/process-instances/search
{"page":{"totalItems":0,"hasMoreTotalItems":false,"startCursor":null,
  "endCursor":null},"items":[]}
```

**Interpretation.** This is the cleanest possible demonstration of the distinction the
lesson has to establish. The engine is running, the partition is `LEADER`, the log has
passed 6400 records, and the number of process instances is **zero**. The traffic in the
log is cluster-internal bookkeeping, not process execution. A running engine and an
absent business process are independent facts. Nothing has been deployed, because this
lesson deploys nothing.

## Two dead ends worth recording

Both cost time, and both are the kind of thing a reader will hit. Recording them is more
useful than pretending they did not happen.

1. **`/v1/*` is gone.** `GET /v1/processes` and `/v1/process-instances` both return
   `404`. The API was superseded; use `/v2/...` and note that the v2 instance endpoint
   is a `POST` search, not a `GET` list.
2. **A `200` can be a lie.** `GET /operate/v2/processes` returns `200`, but the body is
   `<!doctype html>`: the Operate single-page app, not an API. Any check that only asserts
   on status code would have recorded "processes endpoint working" and concluded that
   processes exist. **Assert on the body, not only the status.**

## Reproduction

```bash
cd infra/local/camunda-8.9
docker compose up -d

# Components
curl -s http://localhost:8080/v2/topology
curl -s http://localhost:9600/actuator/exporters
curl -s http://localhost:9600/actuator/cluster

# The log
curl -s http://localhost:9600/actuator/partitions

# The two stores
docker compose exec -T orchestration ls -1R /usr/local/camunda/data
docker compose exec -T orchestration ls -lh /usr/local/camunda/camunda-data

# Zero instances
curl -s -X POST -H 'Content-Type: application/json' -u demo:demo \
  -d '{"filter":{}}' http://localhost:8080/v2/process-instances/search
```

## To clean up

```bash
cd infra/local/camunda-8.9
docker compose down -v
```
