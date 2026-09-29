# ADR-0002 — Camunda 8 Version Pin

## Status
Accepted

Date: 2026-09-29

## Context
The learning lab documents and exercises a specific Camunda 8 version. The platform moves fast enough that an unpinned course silently rots: behaviour, configuration and APIs change between minor releases, and the lab's stated facts would stop matching the software the learner actually runs.

As of 2026-09-29, the official documentation exposes 8.9 as the current stable version, 8.10 as unreleased, and 8.6 and earlier as unmaintained. Moving from 8.9 to 8.10 involves changes that are already announced:

- The Camunda Java Client replaced the Zeebe Java Client in 8.8; the Zeebe Java Client is removed in 8.10. The Camunda Java Client defaults to REST, with gRPC configurable.
- The `zeebe.client.worker.job.activated` and `zeebe.client.worker.job.handled` metrics are deprecated and are removed in 8.10, replaced by `camunda.client.worker.job.*`.
- From patch release 8.9.12, Camunda no longer produces the `camunda/zeebe`, `camunda/operate` and `camunda/tasklist` Docker images; the unified `camunda/camunda` image is used instead.
- From 8.9, the Helm charts no longer deploy infrastructure sub-charts by default, and in 8.10 those sub-charts are removed.

The repository engineering rules require Java 21 or later, and the Camunda 8 Orchestration Cluster components are documented as requiring OpenJDK 21–25.

## Decision
The learning lab pins the course to **Camunda 8.9.x**.

- Every version-sensitive claim produced by the lab is labelled with the documentation version it was verified against, and that label is `8.9` unless stated otherwise.
- The local environment used to produce observed evidence is a 8.9.x distribution, started explicitly at that version.
- Upgrading the course to 8.10 is deferred, not scheduled. When it happens it is a deliberate increment, not a side effect, and the announced breaking changes above are treated as teaching material rather than as incidental breakage.

## Consequences
- Facts stated in the lab can be checked against a specific, currently supported documentation version, and a reader can tell which version a claim belongs to.
- The 8.10 upgrade has a defined destination. It will need its own scope: the Java client replacement, the renamed worker metrics, and the Helm infrastructure-sub-chart removal are three distinct topics, not one.
- Lessons written against 8.9 will need review when the pin moves. This is a known, accepted cost of not moving earlier.
- Any statement in the lab that is not tied to a version must be labelled as interpretation rather than presented as Camunda behaviour.
