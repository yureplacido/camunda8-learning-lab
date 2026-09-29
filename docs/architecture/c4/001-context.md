# C4 L1 — System Context: Camunda 8 Learning Lab

Scope: what the learning lab's local environment is and who talks to it.
Status: created from the observed run of 2026-09-29. See
[evidence](../../lessons/001-camunda7-8-mental-model/evidence.md).

## Context diagram

```mermaid
C4Context
    title System Context — Camunda 8 Learning Lab (local)

    Person(dev, "Learner / Developer", "Runs the lab, reads evidence, deploys processes")
    Person(sa, "Human Worker", "Resolves user tasks in Tasklist (future lesson)")

    System(oc, "Camunda 8 Orchestration Cluster",
        "Runs BPMN process instances. Execution state in a replicated Zeebe log. (8.9.22)")
    System_Ext(sec, "Secondary Storage (H2)",
        "RDBMS exporter projection for Operate and Tasklist. Rebuildable, not source of truth.")
    System_Ext(worker, "Job Worker (future lesson)",
        "Application code that pulls jobs over gRPC. Not part of this lesson.")

    Rel(dev, oc, "Deploys & inspects", "HTTP / REST")
    Rel(dev, sec, "Owns file/volume", "Docker volume")
    Rel(sa, oc, "Completes user tasks", "HTTPS")
    Rel(oc, sec, "Exports projections (one-way, async)", "RDBMS exporter")
    Rel(oc, worker, "Delivers jobs / receives completion", "gRPC (future)")
```

## What this diagram deliberately does not show

- **No external database.** Verified: the Camunda process opens no connection to 5432 /
  3306 / 1521 / 27017 / 1433, and no database service exists in the stack.
- **No Kafka.** Message correlation via Kafka is a later lesson; not implied here.
- **No Management plane** (Console, Web Modeler, Identity, Keycloak, Optimize). This is
  the *lightweight* compose; the full compose adds those.

## Key point for the context view

The single most important structural fact at this level: **the authoritative state lives
inside the Orchestration Cluster, not in a database the lab owns.** The learner operates
a cluster, not a database.
