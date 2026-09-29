# LESSON-001 — Camunda 7 → 8 Mental Model

Status: ready

Version scope: **Camunda 8.9.x** per [ADR-0002](../../adr/0002-camunda-8-version-pin.md).
Evidence: [evidence.md](evidence.md). Knowledge note: [Camunda 7 vs 8](../../camunda/camunda-7-vs-8.md).

**Prerequisite: [Lesson 000 — What Camunda is, and what its parts do](../000-camunda-bpmn-concepts/lesson.md).**
This lesson assumes the vocabulary established there: what a workflow engine is, what BPMN
is, and the chain `service task → job → job worker`. Without it, the persistence
discussion below has nothing to attach to.

## Objective

The learner must be able to explain, in their own words and without hedging, **where
Camunda 8 keeps process state and why that differs from Camunda 7** — and predict, before
running anything, what a given failure in the data layer would and would not affect.

## Context

Most Camunda 7 → 8 material is presented as a feature comparison. That framing produces
learners who can recite components but still assume "the engine lives in the database"
when they open Camunda 8. Every later concept — job workers, retries, incidents,
idempotency, partition scaling — is built on the persistence and execution model. If that
model is wrong, the later lessons are cargo cult.

So this lesson has no code. It runs a real cluster and looks at its storage.

## Concept

Three facts, in dependency order.

**1. Camunda 7 puts the engine in the database.**
Runtime state (`ACT_RU_*`), definitions (`ACT_RE_*`) and history (`ACT_HI_*`) share one
database. The engine is deployed as a library and reaches state through JDBC.

**2. Camunda 8 puts the engine on a replicated log.**
The Zeebe broker owns execution state as a partitioned, Raft-replicated log with
RocksDB snapshots. A gateway fronts it. The database is no longer the system of record.

**3. Camunda 8 still uses a database — but not as the source of truth.**
An `RdbmsExporter` streams records out of the log into a relational store, which is what
Operate and Tasklist read. It is one-way, asynchronous, and rebuildable.

Fact 3 is the one people get wrong, which is why this lesson exists.

## Mental model

Ask two questions of any Camunda 8 deployment:

> **Q1. What is authoritative?**
> The log, in the broker. Losing the database loses *visibility*, not *execution*.

> **Q2. Who crosses the boundary to do work?**
> My code, not the engine. A job is a durable event on a partition, not a call in my
> transaction.

If you can answer both for a given system, most of Camunda 8's design becomes
predictable: why partitions bound concurrency, why the exporter can lag harmlessly, why
retries must be idempotent, why losing the DB is a monitoring outage but losing the
broker is a business outage.

## Camunda 7 → 8

Full comparison in [camunda-7-vs-8.md](../../camunda/camunda-7-vs-8.md). The four rows
that carry the most weight:

| | Camunda 7 | Camunda 8 |
|---|---|---|
| Authoritative state | `ACT_RU_*` tables | Raft log + RocksDB snapshots |
| Operational/history data | `ACT_HI_*`, same database | RDBMS exporter projection |
| Who executes work | Engine, in-process | Job worker, over gRPC |
| Unit of parallelism | Database capacity | Partitions |

## Minimal example

The minimal example for this lesson is the environment itself: an official lightweight
Camunda 8.9.22 Orchestration Cluster, started from vendored compose, with no application
code. The experiment is **observation**, not construction.

## Implementation

Deliberately none. Scope agreed before writing:

- **In scope:** the C7 → 8 mental model, and observation of the local 8.9 environment.
- **Out of scope:** BPMN processes, job workers, Java, Spring Boot, retries, idempotency,
  FEEL, DMN, message correlation, Kafka, Kubernetes, observability stack.

Infrastructure delivered:

- `infra/local/camunda-8.9/` — vendored, trimmed official lightweight compose.
- `infra/local/fetch-compose.sh` — reproducible fetch + trim.
- `.mise.toml`, `infra/local/mise-env.sh`, `infra/local/mise.sh` — toolchain policy with
  **no global installs**; all mise state is redirected into `<project>/.mise/`.
- `docs/adr/0002-camunda-8-version-pin.md` — why 8.9.x.

## Execution

```bash
cd infra/local/camunda-8.9
docker compose up -d
docker compose ps
curl -s http://localhost:8080/v2/topology
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:9600/actuator/health
```

Observed results (full output in [evidence.md](evidence.md)):

- Broker and gateway report `8.9.22`; single node, one partition `leader`, `healthy`.
- `200` from `/operate`, `/tasklist`, `/v2/topology` and `/actuator/health`.
- Execution state on disk: `raft-partition/partitions/1/snapshots/...`, `*.sst`,
  `MANIFEST-*`, `zeebe.metadata`.
- H2 on disk: `h2db.mv.db`, fed by `[RDBMS Exporter P1]`.
- **No** connection to 5432/3306/1521/27017/1433. No database service in the stack.

## Failure / investigation / correction

The lesson contains a real, corrected failure, and it is the most valuable part.

**The wrong claim.** The first version of the B3 check asserted: *"Camunda 8 has no
database."* On the strength of a general architectural summary, that looked defensible.

**The observation that broke it.** The running process reported
`MyBatisConfiguration - Detected databaseId: h2` and created
`/usr/local/camunda/camunda-data/h2db.mv.db`. There *was* a relational database. The
claim was false as written.

**Investigation.** Instead of adjusting the claim to fit, the storage layout was mapped:

| Volume | Path | Contents | Role |
|---|---|---|---|
| `camunda-89_camunda` | `/usr/local/camunda/data` | `raft-partition` | Authoritative |
| `camunda-89_camunda-data` | `/usr/local/camunda/camunda-data` | `h2db.mv.db` | Secondary |

Two volumes, two mechanisms, one process. Combined with the exporter log lines, the
correct model emerged: H2 is a **rebuildable projection**, not the system of record.

**Correction.** The check now asserts the *role* of the database, not its absence. It also
became **container-scoped** rather than host-scoped, because the host runs unrelated
databases (`*:5432`, `*:3306` were in use by other projects) and a host-wide check would
have produced a confidently wrong answer for the wrong reason.

**Transferable lesson.** "Camunda 8 has no database" is a plausible-sounding summary that
a running system refutes in one line of logs. Prefer claims about *roles and mechanisms*,
which survive observation, over claims about *presence and absence*, which do not.

## Architecture implications

- **The database stops being a single point of failure, and starts being a derived
  store.** Losing secondary storage degrades Operate and Tasklist; it does not stop
  execution. Availability reviews must ask about *which* store.
- **Monitoring gains a lag dimension.** An exporter is async (`flushInterval=PT0.5S` in
  this lab). "Operate is empty" can mean "the exporter is behind", not "no work ran".
  Operate-time and broker-time are not the same clock.
- **Backup/restore splits into two different operations.** Restoring the DB alone will
  produce a cluster whose projections disagree with its log. Snapshot/restore of the
  broker partition and of the exporter target are independent decisions with an
  ordering constraint.
- **The execution boundary is now a network boundary.** In C7 a job was inside your
  transaction; in C8 it is a message. Every job handler must assume at-least-once
  delivery. This is not a hardening detail to add later — it is the default.
- **Scaling unit changed.** Concurrency is bounded by partitions, and partitions bring
  replication with them. "Add engine instances" is not a Camunda 8 scaling strategy.
- **Trade-off accepted:** logical multi-tenancy (8.9) is far cheaper than C7's engine-per
  tenant, but isolation now depends on every call site passing the right `tenantId`. A
  missing identifier is a data-exposure bug, not a crash. Worth choosing deliberately per
  tenant rather than by default.

## Interview questions

**Q. Walk me through the difference between Camunda 7 and 8 persistence.**
*A.** C7 stores runtime state in `ACT_RU_*` tables in the engine's database — the
database *is* the engine's state. C8 moves authoritative state onto a partitioned,
Raft-replicated log owned by the Zeebe broker. A relational database still commonly
exists, but it is written by an async `RdbmsExporter` and holds a projection for
Operate/Tasklist that can be rebuilt from the log.

**Q. "Camunda 8 has no database." Is that true?**
*A.** No, and it's the most common wrong answer. In this lab's 8.9.22 cluster there is an
H2 file on disk. The accurate claim is that Camunda 8 doesn't use a database as its
*source of truth*. Presence/absence claims are fragile; role/mechanism claims survive.

**Q. Our Operate dashboard is empty. What do you check first?**
*A:** Not "is there work" first. The exporter is asynchronous, so I check the exporter's
position versus the broker's, then broker health via `/v2/topology` (partition leader
election), and only then whether work actually exists. The observed config here flushes
every 0.5s, so a persistent gap is not normal lag.

**Q. If we lose the H2 file entirely, what breaks?**
*A.** Operate and Tasklist lose their data — history, incidents, user tasks. Execution
state survives in the Raft log, and the projection can be rebuilt. I would *not* treat it
as a data-loss incident for running processes, but I would treat it as a real outage for
the people who depend on operational visibility.

**Q. How do you scale Camunda 8?**
*A:** By adding brokers and partitions, not by adding application instances to a shared
database. Partitions are the unit of parallelism *and* the unit of replication, so
partition count is simultaneously a throughput and an availability decision.

**Q. Why is idempotency mandatory in C8 but usually optional in C7?**
*A:** In C7 a job commonly executes inside the engine's transaction, so the failure
boundary and the transaction boundary are largely the same. In C8 the job is a message
and the handler runs in a separate process across a network boundary, so retries and
re-deliveries are normal operation, not exceptional.

**Q. How does multi-tenancy differ?**
*A:** C7 gives each tenant its own engine instance and datasource — hard isolation,
proportionally expensive. C8 8.9 uses a logical `tenantId` on a shared cluster. Much
cheaper to provision, but isolation now depends on correct propagation at every call
site. Checks are disabled by default and data maps to `<default>` until enabled.

**Q. Where would you push back on a "C8 is simply better" migration proposal?**
*A:** I'd want to know what they lose by giving up the shared operational database model,
whether their data isolation requirements tolerate logical tenancy, and whether the team
can operate a stateful distributed cluster. The trade is usually worth it, but it is a
trade, and the failure mode shifts from "database is down" to "partition leader is
unhealthy and an exporter is lagging".

## Evidence

- [evidence.md](evidence.md) — verbatim command output from the 2026-09-29 run,
  including the corrected B3 check and its two data stores.
- [camunda-7-vs-8.md](../../camunda/camunda-7-vs-8.md) — fact / observed / interpretation
  are labelled separately.
- [ADR-0002](../../adr/0002-camunda-8-version-pin.md) — the 8.9.x pin.
- [C4 context](../../architecture/c4/001-context.md) · [C4 container](../../architecture/c4/001-container.md)

## Completion criteria

- [x] Concept understood — the log/database role split is explained, with a corrected
      false claim on record
- [x] Mental model explained — the two questions, applied to a real deployment
- [x] Relevant C7 → 8 distinction documented
- [x] Scope defined by SPEC — [SPEC-001](../../../specs/001-camunda8-foundation/spec.md)
- [x] Experiment implemented when applicable — cluster runs, self-contained via Docker
- [x] Tests executed — N/A by design: this lesson produces no code. Verification is
      observation of the running cluster (B2/B3/B4). No tests were invented to fill this.
- [x] Failure path investigated when relevant — the B3 misread and its correction
- [x] Findings documented from actual execution
- [x] Architecture implications documented
- [x] Interview review completed — Q&A above
- [ ] Independent review completed

## Not yet true about this lesson

It is `ready`, not `completed`. The remaining gate is independent review, and the honest
next step is a reviewer challenging the interpretation — in particular whether the
"rebuildable projection" claim is stated as strongly as 8.9 actually supports, and whether
the lesson earns its place without any runnable code.