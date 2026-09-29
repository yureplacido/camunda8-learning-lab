# SPEC-001 — Camunda 8 Foundation

Status: narrowed to Lesson 001 (Option A, agreed 2026-09-29)

> **Scope change record.** This SPEC originally described a foundation that included BPMN
> processes, a Java worker, tests, and retry semantics. It was narrowed to what Lesson 001
> actually delivers. The removed outcomes were **not discarded** — they are preserved in
> [Deferred scope](#deferred-scope-not-discarded) below and in `tasks.md`. A future SPEC
> will claim them when a lesson is ready to implement them.

## Goal
Establish the conceptual and repository foundation needed to begin implementing Camunda 8
examples, and record verified evidence of how a real 8.9 Orchestration Cluster stores
state.

## Outcomes

- Explain the architectural difference between Camunda 7 and Camunda 8, in particular the
  role of the database in each.
- Explain that in Camunda 8 the authoritative process state is a replicated log, and that
  a relational database present in the environment is a rebuildable projection rather than
  the system of record.
- Explain why the execution boundary moves from the engine to the application, and what
  that implies for retries and idempotency.
- Provide a reproducible, self-contained local environment pinned to Camunda 8.9.x that
  requires no global tool installation.
- Separate fact, observation and interpretation, and refuse to state a claim that the
  running system contradicts.

## Out of scope for this SPEC

Deliberately excluded, and why:

| Excluded | Reason |
| --- | --- |
| BPMN processes | Requires a modelling lesson first; no value in this lesson |
| Java / Spring Boot workers | Would pin a JVM toolchain with no payoff at this stage |
| Job workers, jobs, retries, idempotency | Depend on the execution-boundary model; that is a later lesson |
| FEEL, DMN, message correlation, Kafka | Separate lessons, separate mental models |
| Kubernetes / production topology | Premature; the local compose is sufficient to verify the model |
| Observability stack | The lightweight compose already exposes actuator; a full stack is not needed to verify persistence claims |

## Acceptance criteria

- [x] The repository contains a Camunda 7 → 8 architecture note.
- [x] A minimal execution flow is documented — *in this SPEC it is the cluster itself:
      broker, gateway, storage layout and exposed endpoints, all observed.*
- [x] At least one architecture diagram exists (C4 context and container).
- [x] Interview questions can be answered using the repository material.
- [x] Version-sensitive claims are sourced before being treated as facts (ADR-0002).
- [x] Observed evidence is recorded verbatim, not summarised from memory.
- [x] A claim that the running system contradicted is corrected on the record.
- [x] The local environment is self-contained: no global package installs.
- [x] Scope of this SPEC matches the scope of Lesson 001.

## Deferred scope (not discarded)

These remain valid outcomes of a Camunda 8 foundation. They are deferred to a future
SPEC, to be claimed when a lesson is ready to implement them:

- Define the relationship among BPMN process, process instance, job and job worker.
- Identify which concerns belong to the workflow engine and which belong to application
  services.
- Add a minimal BPMN process and the smallest useful Java worker.
- Add meaningful tests around worker behaviour.
- Document failure and retry semantics in depth.
