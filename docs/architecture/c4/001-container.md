# C4 L2 — Container: Camunda 8 Orchestration Cluster (lightweight, local)

Scope: the containers actually observed running on 2026-09-29 via the vendored official
lightweight compose. See [evidence](../../lessons/001-camunda7-8-mental-model/evidence.md).

## Container diagram

```mermaid
C4Container
    title Container — Camunda 8 Orchestration Cluster 8.9.22 (lightweight)

    Person(dev, "Learner / Developer", "Runs the lab, inspects the cluster")

    System_Boundary(cluster, "Camunda 8 Orchestration Cluster", "Camunda 8.9.x") {
        Container(orch, "Orchestration", "camunda/camunda:8.9.22",
            "Zeebe broker + gateway + Operate + Tasklist in one process.\nHosts 8080 (REST/webapps), 9600 (actuator), 26500 (gateway gRPC).")
        Container(conn, "Connectors", "camunda/connectors-bundle:8.9.14",
            "Outbound connector runtime. Runs as part of the official lightweight compose.\nNot used by this lesson.")
        ContainerDb(vol1, "Volume: camunda-89_camunda", "Docker volume -> /usr/local/camunda/data",
            "Zeebe partition state: Raft log + RocksDB snapshots. AUTHORITATIVE.")
        ContainerDb(vol2, "Volume: camunda-89_camunda-data", "Docker volume -> /usr/local/camunda/camunda-data",
            "H2 file (h2db.mv.db) for the RDBMS exporter. SECONDARY / rebuildable.")
    }

    Rel(dev, orch, "Deploy, monitor, resolve", "HTTP :8080, :9600")
    Rel(orch, vol1, "Reads/writes execution state", "local volume")
    Rel(orch, vol2, "Writes projections via RDBMS exporter", "local volume")
    Rel(orch, conn, "gRPC (in-cluster)", "26500")
```

## Data stores side by side (the heart of the C7 -> C8 shift)

| | Execution state | Secondary storage |
|---|---|---|
| Where | volume `camunda-89_camunda` | volume `camunda-89_camunda-data` |
| Path in container | `/usr/local/camunda/data/raft-partition` | `/usr/local/camunda/camunda-data` |
| On disk | `*.sst`, `MANIFEST-*`, `zeebe.metadata` | `h2db.mv.db`, `h2db.trace.db` |
| Mechanism | Zeebe broker (Raft + RocksDB) | `RdbmsExporter` (one-way, `flushInterval=PT0.5S`) |
| Queried with SQL? | No | Yes |
| Loss impact | Process state lost | Projections lost; rebuildable from log |

## Ports

| Host port | Component | Note |
|---|---|---|
| 8080 | Gateway REST + Operate + Tasklist | Public |
| 9600 | Actuator (Prometheus-style) | Public |
| 26500 | Gateway gRPC | Public |
| 26501, 26502 | Gateway internal, broker internal gRPC | **Not** published to host |

## Notes

- `orchestration` and `connectors` use fixed container names (`orchestration`,
  `connectors`) and a fixed network name (`camunda`), inherited from the official
  compose. This means the lab cannot run two copies of this compose side by side without
  editing those names. Documented here so it is not a surprise later.
- The compose is vendored (see `infra/local/fetch-compose.sh`) and trimmed: no BPMN
  examples, no Playwright/e2e, no management stack. `connectors` is kept because it is
  part of the official lightweight definition, and is explicitly out of lesson scope.
