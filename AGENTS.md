# AGENTS.md — Camunda 8 Learning Lab

## Purpose
This repository is a hands-on learning lab for Camunda 8, BPMN, Java/Spring Boot, workflow orchestration and distributed systems. The goal is not only to make code run, but to build Developer Lead-level reasoning for production architecture and interviews.

## Language
- All learning material, lesson text, specs, evidence and architecture docs are written in **PT-BR**.
- Technical terms, API names, table names, log lines and CLI flags stay in English, because that is how they appear in the official documentation, in the logs and in the code.
- Verbatim command output is never translated or reformatted.
- The only exception is `infra/local/camunda-8.9/`, which is vendored upstream: it is read-only reference material and is not translated.

## Learning rules
- Prefer implementation over passive reading.
- For each major concept, provide a concise explanation, a runnable example and tests.
- Explicitly compare Camunda 7 and Camunda 8 when the distinction matters.
- Separate verified behavior, interpretation and hypothesis.
- Prefer official Camunda documentation for version-sensitive facts.
- Do not add infrastructure complexity before the underlying concept is understood.
- Preserve the learning loop: concept → mental model → experiment → execution → failure → investigation → correction → architecture → interview.
- Teach in causal order — problem, category, notation, vocabulary, form — not in dictionary order.
- State what a lesson deliberately does **not** teach, and where the concept is deferred.
- Register dead ends. A recorded failed path is more useful than a polished happy path.

## Evidence rules
- Only what was actually executed goes into `evidence.md`. If it was not run, it does not belong.
- Every version-sensitive claim carries the version and a **verified** URL. Unverified sources are marked as such; never claim a fetch that did not happen.
- Every file path, filename, port, container name, process count and command output must come from a real command (`ls`, `find`, `ps`, `docker compose ps`, `curl`), not from memory of documentation.
- Correct the record explicitly. When a claim in a previous version was wrong, leave the correction visible next to the claim. Silently fixing it destroys the lesson.
- Distinguish three labels in text: **Fato** (official docs, with version and URL), **Observado** (real run output), **Interpretação** (author's reasoning, not Camunda behavior).
- Do not invent tests, measurements, timings or comparisons. A behavior that is not applicable to the lesson is written as `N/A` **with the reason**, not omitted and not faked.

## Component, container and storage rules
- Distinguish **logical component** from **running process/container**. From Camunda 8.9.12 the separate `camunda/zeebe`, `camunda/operate` and `camunda/tasklist` images are gone in favor of the unified `camunda/camunda` image; the local Orchestration Cluster runs a single Java process. Never equate a component count with a container count.
- Distinguish **Gateway** (stateless entry point) from **Broker** (stateful engine core). "Zeebe" alone is not precise enough to answer "who stores the state" or "who receives the command".
- Primary storage is the authoritative replicated log. Secondary storage (RDBMS exporter target) is a rebuildable projection, never the system of record.
- Never write "Camunda 8 has no database". The H2 secondary storage exists, is monitored (`rdbmsStatus.database`), and is what Operate and Tasklist query. The accurate claim is that Camunda 8 does not use a database as its source of truth.
- A `200` status code is not proof that an API responded. Assert on the body; the Operate single-page app answers `200` on API paths.
- Assert on what proves the concept, and be explicit about the limitation of the environment. A single-partition cluster does not demonstrate partition-count-dependent routing behavior.

## Architecture communication
- Use C4 for architecture communication, and record architectural decisions in ADRs.
- C4 documents use Mermaid `flowchart` with textual labels identifying the level. Rendering successfully does not prove architectural conformance; `flowchart` does not enforce C4 rules.
- C4 L1 shows people and logical systems, not containers or components. C4 L2 may show containers, and within a container may show logical components that actually run inside it.
- Every diagram must have a **Diagram Review** section: syntax validated by `docs/validate-mermaid.sh`, plus the semantic checklist in `.opencode/agents/diagram-reviewer.md`.
- No diagram may contain a component that is not explained in the text, or that does not exist in the real `docker compose ps`.
- The C7 → 8 axis is the **lost shared ACID transaction boundary** between application and engine. "The database became a log" is the consequence, not the cause. Teach the cause.

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
- Install nothing globally. Tooling is pinned per repository via `.mise.toml`, and the `toolchain` section of `infra/local/camunda-8.9/` declares the rest.
- `infra/local/camunda-8.9/` is vendored from upstream and stays in sync via `infra/local/fetch-compose.sh`, which records a SHA-256. Do not hand-edit it.

## Lesson lifecycle
Every lesson follows:

SKELETAL → DRAFTING → READY → IMPLEMENTING → REVIEW → COMPLETED

A lesson is not completed merely because code compiles or tests pass. The lesson must contain evidence that the intended concept was understood, exercised and reviewed.

A `ready` lesson must end with an **"O que ainda não é verdade sobre esta lesson"** section, naming the remaining gate and the honest open questions for the reviewer. A `completed` lesson does not have that section, because it passed the gate.

## Pull request workflow
- main is the consolidated learning baseline.
- Every lesson starts from the latest main.
- Use one focused branch and PR per lesson.
- The lesson and its SPEC are prepared before implementation.
- Implementation is limited to the approved lesson scope.
- Verification and independent review happen before merge.
- After merge, main becomes the baseline for the next lesson.
- Avoid mixing unrelated lessons or structural refactors into a lesson PR.
- Never commit partial work. A lesson lands as one commit set that passes validation.

## Repository structure
- apps/: executable services and workers.
- processes/: BPMN process definitions and related artifacts.
- libs/: reusable Java libraries.
- infra/: local/cloud infrastructure. `infra/local/camunda-8.9/` is vendored upstream.
- docs/: learning material, architecture and decisions.
  - `docs/lessons/NNN-slug/lesson.md` and `evidence.md`, from `docs/lessons/_TEMPLATE.md`.
  - `docs/architecture/c4/`, `docs/adr/`, `docs/camunda/`.
- specs/: specification-driven learning and implementation work. One directory per lesson: `spec.md`, `plan.md`, `tasks.md`.
- .opencode/agents/: specialized engineering and learning agents.

## Agent behavior
Agents must inspect the existing repository before proposing changes, preserve established decisions, explain Camunda 8 choices, never treat Camunda 7 and 8 as interchangeable, and verify version-sensitive behavior before stating it as fact.

Use specialized responsibilities:
- course-orchestrator: lifecycle and routing.
- course-architect: course structure and roadmap integrity.
- lesson-author: learning material and experiment definition.
- lesson-implementer: bounded implementation and verification.
- lesson-reviewer: independent technical and learning review.
- diagram-reviewer: Mermaid syntax validation and architectural semantic review.
- Existing mentor/architect/reviewer agents provide domain-specific support.

## Definition of Done for a lesson
- Lesson objective and mental model are explicit.
- Relevant C7 → C8 differences are documented, with the transactional boundary as the axis.
- Implementation scope is defined by a SPEC.
- Runnable experiment exists when applicable.
- Meaningful tests exist, or `N/A` is stated with a reason.
- Relevant failure behavior is exercised when applicable.
- Observations are recorded from actual execution, and uncorrected wrong claims are left visible with their correction.
- Logical component and running container are never conflated.
- Diagrams pass `docs/validate-mermaid.sh` and carry a completed Diagram Review.
- All documentation is in PT-BR, with technical terms preserved.
- Architecture implications and trade-offs are documented.
- Interview questions are answered/reviewed.
- Independent review has no blocking findings.
