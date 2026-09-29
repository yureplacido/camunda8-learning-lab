# Camunda 7 vs Camunda 8

Verified against: **Camunda 8.9** documentation (see [ADR-0002](../../docs/adr/0002-camunda-8-version-pin.md)).
Observed evidence for every claim marked **Observed** is in
[Lesson 001 evidence](../lessons/001-camunda7-8-mental-model/evidence.md).

This note separates three things on purpose, because mixing them is the main source of
wrong answers in interviews:

- **Fact** — stated by official documentation, with the version it belongs to.
- **Observed** — actually seen running a 8.9.22 Orchestration Cluster on 2026-09-29.
- **Interpretation** — the author's reasoning. Not Camunda behaviour.

## 1. The one-sentence difference

Camunda 7 is a **database-backed workflow engine**: the database is the engine's state.
Camunda 8 is a **log-backed distributed engine**: an append-only replicated log is the
state, and the database was demoted to an optional, rebuildable projection.

Everything else in this document is a consequence of that single difference.

## 2. Architecture

### Camunda 7 (Fact)

A single Process Engine instance, deployed as a library inside your application or as a
shared engine, connects with JDBC to **one** database. The engine writes its runtime state
to `ACT_RU_*` tables, its definitions to `ACT_RE_*` tables, and history to `ACT_HI_*`
tables **in the same database**.

API, REST, Tasklist, Cockpit and Optimize are all consumers of that same database. There
is no separate replication mechanism for engine state.

### Camunda 8 (Fact + Observed)

The Orchestration Cluster is a set of distinct components:

| Component | Role |
| --- | --- |
| **Zeebe broker** | Runs the process instances. Owns execution state. Partitions + replicates with Raft. |
| **Gateway** | gRPC/REST entry point. Proxies commands to the correct partition. Stateless. |
| **Operate** | Monitoring and incident management. Reads a projection, never writes state. |
| **Tasklist** | Human tasks. Reads a projection, never writes state. |
| **Connectors** | Outbound integration to external systems via job workers. |
| **Job worker** | Your code. Polls for work, does the work, completes or fails the job. |

**Observed** in this lab's environment:

- The broker is reached through the gateway; `/v2/topology` shows a single-node cluster
  with one partition acting as `leader`, reported `healthy`, version `8.9.22`.
- The Zeebe process opened **no** database connection at all — no listener on 5432, 3306,
  1521, 27017 or 1433. It only talks to itself on `26501` and to its sibling containers.
- Execution state exists on disk as a Raft log plus RocksDB snapshots
  (`raft-partition/partitions/1/snapshots/...`, `*.sst`, `MANIFEST-*`, `zeebe.metadata`).

## 3. Storage — the distinction that actually matters

This is where most Camunda 7 → 8 mental models break, so it is worth being precise.

### Camunda 7 (Fact)

One database, two concerns mixed:

- **runtime/authoritative state** — `ACT_RU_*`
- **history/operational data** — `ACT_HI_*`

If the database is unavailable, the engine cannot run **and** you cannot report on what
ran. The same outage hits both concerns simultaneously.

### Camunda 8 (Fact + Observed)

Two stores with different jobs, and in this topology two different Docker volumes:

| | Camunda 7 | Camunda 8 (observed) |
| --- | --- | --- |
| Authoritative state | `ACT_RU_*` tables, via JDBC | Raft log + RocksDB snapshots |
| Failure when store is down | Engine cannot run | Broker cannot elect / cannot run |
| Operational/history data | `ACT_HI_*` tables, same DB | RDBMS exporter projection |
| Who writes it | The engine itself | An **exporter**, one-way, async |
| Can you lose it? | No — it *is* the system of record | Yes — it can be **rebuilt** from the log |

**Observed** in the lab's H2 configuration (`camunda.data.secondary-storage`):

```
io.camunda.application.commons.rdbms.MyBatisConfiguration - Detected databaseId: h2
io.camunda.zeebe.broker.system - Provide ExporterDescriptor for RDBMS Exporter
io.camunda.exporter.rdbms.RdbmsExporter - [RDBMS Exporter P1] RdbmsExporter created with
    Configuration: flushInterval=PT0.5S, queueSize=1000
io.camunda.exporter.rdbms.RdbmsExporter - [RDBMS Exporter P1] Exporter opened with
    last exported position -1
```

And on disk, two separate volumes:

- volume `camunda-89_camunda` → `/usr/local/camunda/data/raft-partition` (execution state)
- volume `camunda-89_camunda-data` → `/usr/local/camunda/camunda-data/h2db.mv.db` (H2)

> **Correction worth remembering.** A very common and very wrong summary is "Camunda 8 has
> no database". In the observed 8.9.22 environment there *is* a relational database — an
> H2 file. What is wrong is not the database, it is the *role*: H2 is **secondary
> storage**, written by an exporter, and losing it loses projections, not process state.
> The accurate statement is *"Camunda 8 does not use a database as its source of truth"*.

## 4. Scaling and availability

| | Camunda 7 | Camunda 8 |
| --- | --- | --- |
| Scale up | More engine instances, **but** they contend on one database | More partitions, replicated by Raft |
| Scale out | Database replication is the scaling story | Add brokers/partitions; replication is built in |
| High availability | Database HA + app HA | Raft replication across brokers |
| Concurrency limit | Effectively your database's capacity | Partition count (1 per partition) |

**Interpretation.** In Camunda 7, adding engine instances does not buy throughput if they
share one database — you mostly buy redundancy, and you add contention. In Camunda 8 the
unit of parallelism *is* the partition, and replication comes with it. The operational
question moves from "is the database healthy" to "are all partitions and their leaders
healthy", which is exactly what `/v2/topology` reports.

## 5. Integration and scale-out of applications

| | Camunda 7 | Camunda 8 |
| --- | --- | --- |
| Work execution | The engine calls **you**, inside the same JVM | **You** pull work, out of process |
| Coupling | In-process client, same trust boundary | gRPC job protocol, separate trust boundary |
| Failure unit | The engine's transaction | The **job** |

**Observed** — this is a direct consequence of the fact that the engine no longer lives in
your JVM. In Camunda 7 a job is a row processed inside your transaction. In Camunda 8 a
job is a message on a partition, and "the work failed" becomes a *durable, retryable
event* rather than a stack trace inside the engine.

> This is why the lab's later lessons on retries, idempotency and incidents only make
> sense *after* this one. There is no `try/catch` boundary to protect in Camunda 8; the
> system is built to assume the boundary will be crossed more than once.

## 6. Multi-tenancy

| | Camunda 7 | Camunda 8 (Fact, 8.9) |
| --- | --- | --- |
| Mechanism | One **engine instance per tenant** | A **tenant identifier** on data |
| Cost per tenant | New engine, new datasource, new deployment | A logical boundary, shared cluster |
| Isolation | Physical process/DB isolation | Logical, checked at runtime |

**Fact (8.9).** Multi-tenancy in Camunda 8 is *logical* tenancy, available on both SaaS
and Self-Managed. A tenant is created through the Orchestration Cluster API
(`POST /v2/tenants`, added in 8.8) and identified by a `tenantId`. Multi-tenancy is
*enabled* by default, but multi-tenancy **checks are disabled by default**, and all data
maps to the `<default>` tenant until checks are enabled.

**Interpretation.** This is a genuine trade-off, not a pure win. C7's per-tenant engine is
expensive but the isolation is hard. C8's logical tenancy is cheap to provision, but the
isolation now depends on every call site passing the right `tenantId` correctly — a
missing or wrong identifier is a data-exposure bug, not a crash. If you migrate C7
tenancy to C8 you must decide, per tenant, whether "logically isolated" is good enough.

## 7. What did **not** change

- BPMN is still the modelling language, and most of the vocabulary carries over:
  process definition, process instance, task, user task, service task, incident.
- You still do not write SQL against engine tables, in either version.
- Operate remains the monitoring and incident tool. Its role is more central in C8, not
  less, precisely because the engine is distributed.

## 8. Consequences for a migration

**Interpretation**, derived from the points above:

1. **Your database problem changes shape.** If you chose Camunda 7 to reuse an existing
   operational database, that is no longer the model. You are choosing a broker topology
   plus an optional exporter target.
2. **Availability reviews change questions.** "Is the DB up" is replaced by "is the leader
   of every partition healthy, and is my exporter keeping up".
3. **Scaling reviews change unit.** Partitions, not engine instances.
4. **Failure handling becomes first-class design** instead of an error-handling detail,
   because the work boundary is now a network boundary.
5. **Operations tooling moves into the product.** Operate/Tasklist are the primary
   interfaces, so enablement and RBAC become a migration workstream.

## 9. Interview traps

- "Camunda 8 has no database" — **false.** Secondary storage exists; it is not the source
  of truth. See section 3.
- "Camunda 8 is stateless" — **false.** The broker is explicitly stateful; the *gateway*
  is the stateless part.
- "Camunda 8 replaced the database" — **half true.** It replaced the database as the
  *system of record*, and moved SQL to a derived, rebuildable role.
- "Camunda 8 calls your code" — **false.** Your code calls the gateway and pulls jobs.
- "Camunda 7 and 8 differ only in deployment" — **false.** The persistence model, the
  execution boundary and the scaling unit all changed.

## Related

- [Lesson 001 — Camunda 7 → 8 Mental Model](../lessons/001-camunda7-8-mental-model/lesson.md)
- [Lesson 001 evidence](../lessons/001-camunda7-8-mental-model/evidence.md)
- [ADR-0002 — version pin](../adr/0002-camunda-8-version-pin.md)
- [C4 context](../architecture/c4/001-context.md) · [C4 container](../architecture/c4/001-container.md)
