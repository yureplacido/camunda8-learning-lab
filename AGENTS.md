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
- Preserve the learning loop: concept → mental model → experiment → execution → failure → investigation → correction → architecture → interview.

## Engineering rules
- Java 21+ unless a specific exercise requires another version.
- Spring Boot for Java services where it adds value.
- Maven as the default build tool.
- Tests are mandatory for meaningful behavior.
- BPMN models are versioned together with source code.
- Use ADRs for architectural decisions and C4 diagrams for architecture communication.
- Design explicitly for idempotency, retries, timeouts, concurrency and failure recovery.
- Avoid assuming exactly-once delivery in distributed systems.
- Do not implement future roadmap lessons while working on the current lesson.

## Lesson lifecycle
Every lesson follows:

SKELETAL → DRAFTING → READY → IMPLEMENTING → REVIEW → COMPLETED

A lesson is not completed merely because code compiles or tests pass. The lesson must contain evidence that the intended concept was understood, exercised and reviewed.

## Pull request workflow
- main is the consolidated learning baseline.
- Every lesson starts from the latest main.
- Use one focused branch and PR per lesson.
- The lesson and its SPEC are prepared before implementation.
- Implementation is limited to the approved lesson scope.
- Verification and independent review happen before merge.
- After merge, main becomes the baseline for the next lesson.
- Avoid mixing unrelated lessons or structural refactors into a lesson PR.

## Repository structure
- apps/: executable services and workers.
- processes/: BPMN process definitions and related artifacts.
- libs/: reusable Java libraries.
- infra/: local/cloud infrastructure.
- docs/: learning material, architecture and decisions.
- specs/: specification-driven learning and implementation work.
- .opencode/agents/: specialized engineering and learning agents.

## Agent behavior
Agents must inspect the existing repository before proposing changes, preserve established decisions, explain Camunda 8 choices, never treat Camunda 7 and 8 as interchangeable, and verify version-sensitive behavior before stating it as fact.

Use specialized responsibilities:
- course-orchestrator: lifecycle and routing.
- course-architect: course structure and roadmap integrity.
- lesson-author: learning material and experiment definition.
- lesson-implementer: bounded implementation and verification.
- lesson-reviewer: independent technical and learning review.
- Existing mentor/architect/reviewer agents provide domain-specific support.

## Definition of Done for a lesson
- Lesson objective and mental model are explicit.
- Relevant C7 → C8 differences are documented.
- Implementation scope is defined by a SPEC.
- Runnable experiment exists when applicable.
- Meaningful tests exist.
- Relevant failure behavior is exercised when applicable.
- Observations are recorded from actual execution.
- Architecture implications and trade-offs are documented.
- Interview questions are answered/reviewed.
- Independent review has no blocking findings.
