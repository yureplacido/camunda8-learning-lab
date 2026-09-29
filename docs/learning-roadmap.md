# Camunda 8 Developer Lead Learning Roadmap

## Module 0 — Orientation
000 What Camunda is, and what its parts do

Rationale: the original roadmap began at 001, whose deliverable presupposed that the
reader already knew what a workflow engine is, what BPMN is, and the difference between
a process definition and a process instance. Lesson 000 establishes that vocabulary
before anything compares or builds on it.

## Module 1 — Foundation
001 Camunda 7 → 8 mental model
002 Zeebe execution model
003 BPMN execution
004 Process instances and variables
005 Jobs and job workers
006 Retries, incidents and recovery

Requires: Module 0. Lesson 001 in particular assumes the vocabulary introduced in 000.

## Module 2 — BPMN and workflow behavior
007 Messages and correlation
008 Timers
009 Errors and boundary events
010 Subprocesses and call activities
011 Advanced BPMN patterns

## Module 3 — Java/Spring Boot
012 Java/Spring Boot workers
013 Worker testing
014 Worker lifecycle, concurrency and backpressure

## Module 4 — Distributed systems
015 Idempotency and duplicate delivery
016 Consistency and transactional boundaries
017 Outbox/inbox and integration patterns
018 Kafka integration
019 REST and connectors
020 Timeouts, retries and compensation

## Module 5 — Human workflow
021 User tasks and Tasklist
022 Assignment, authorization and human latency

## Module 6 — Platform and operations
023 Observability
024 Operate and troubleshooting
025 Scaling and partitioning
026 Security and Identity
027 Kubernetes/OpenShift/AWS
028 Production readiness

## Module 7 — Architecture and leadership
029 Camunda 7 → 8 migration
030 Orchestration vs choreography
031 Production architecture case
032 Governance and architecture decisions

## Module 8 — Capstone and interview
033 Banking loan-origination capstone
034 Developer Lead architecture review
035 Troubleshooting simulation
036 Developer Lead interview simulation

## Course rule

The repository structure is established first. Implementation follows the learning sequence.