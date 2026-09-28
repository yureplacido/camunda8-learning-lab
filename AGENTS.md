# AGENTS.md — Camunda 8 Learning Lab

## Purpose
This repository is a hands-on learning lab for Camunda 8, BPMN, Java/Spring Boot, workflow orchestration and distributed systems. The goal is not only to make code run, but to build Developer Lead-level reasoning for production architecture and interviews.

## Learning rules
- Prefer implementation over passive reading.
- For each major concept, provide a concise explanation, a runnable example and tests.
- Explicitly compare Camunda 7 and Camunda 8 when the distinction matters.
- Separate verified behavior, interpretation and hypothesis.
- Prefer official Camunda documentation for version-sensitive facts.
- Do not add infrastructure complexity before the underlying concept is understood.

## Engineering rules
- Java 21+ unless a specific exercise requires another version.
- Spring Boot for Java services where it adds value.
- Maven as the default build tool.
- Tests are mandatory for meaningful behavior.
- BPMN models are versioned together with source code.
- Use ADRs for architectural decisions and C4 diagrams for architecture communication.
- Design explicitly for idempotency, retries, timeouts, concurrency and failure recovery.
- Avoid assuming exactly-once delivery in distributed systems.

## Repository structure
- `apps/`: executable services and workers.
- `processes/`: BPMN process definitions and related artifacts.
- `libs/`: reusable Java libraries.
- `infra/`: local/cloud infrastructure.
- `docs/`: learning material, architecture and decisions.
- `specs/`: specification-driven learning and implementation work.
- `.opencode/agents/`: specialized engineering and learning agents.

## Agent behavior
Agents must inspect the existing repository before proposing changes, preserve established decisions, explain Camunda 8 choices, never treat Camunda 7 and 8 as interchangeable, and verify version-sensitive behavior before stating it as fact.
