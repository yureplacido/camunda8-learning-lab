# SPEC-000 — Camunda and BPMN Concepts

Status: accepted
Date: 2026-09-29

## Goal
Remove the implicit prerequisites of Lesson 001 by documenting, in one place and without
assuming prior knowledge, what a workflow engine is, what BPMN is, what Camunda is, what
its components are and why each one must exist.

## Why this SPEC exists
`docs/learning-roadmap.md` began Module 1 with `001 Camunda 7 → 8 mental model` and
declared the module deliverable as "explain how a BPMN process becomes distributed
executable work". That deliverable presupposes vocabulary the course never established:
what BPMN is, what a workflow engine is, and the difference between a process
*definition* and a process *instance*.

Lesson 001 was therefore written at a level that made its own reasoning unreachable. This
SPEC creates the missing layer as Lesson 000, so Lesson 001 becomes a comparison instead
of an introduction to an unfamiliar vocabulary.

## Scope
- The causal chain: problem → workflow engine → BPMN notation → vocabulary → Camunda →
  Camunda 8 components → the log.
- Every Camunda 8 component explained by the constraint that forces it to exist, not
  listed.
- The core vocabulary that later lessons assume, including the
  service task → job → job worker chain.
- Primary storage versus secondary storage, using Camunda's own terminology.
- Each concept anchored in the local 8.9.22 cluster that is already running.

## Out of scope
| Excluded | Reason |
| --- | --- |
| Deploying or executing a BPMN process | The notation is explained; nothing runs. Later lessons. |
| Writing a job worker | Requires the execution model lesson first |
| Retry, incident and recovery mechanics | Vocabulary is introduced; behaviour is not |
| Camunda 7 versus 8 comparison | That is Lesson 001 |
| DMN, FEEL expressions, Kafka, Kubernetes, multi-tenancy | Separate lessons |
| Any Java, Spring Boot or build tooling | This lesson produces documentation only |

## Requirements
- Every factual claim about Camunda 8 is labelled **Fact (8.9)** and sourced to the
  official documentation version pinned in ADR-0002.
- Every claim about the running environment is labelled **Observed** and backed by a
  verbatim command and its output in `evidence.md`.
- Reasoning that is neither is labelled **Interpretation**.
- Every introduced term is accompanied by what would break without it.
- Every component is justified by a constraint before it is named as a feature.
- The learner is told which popular simplifications are wrong.

## Constraints
- No global tool installation. Use the existing vendored compose and project-scoped mise.
- Read-only against the running cluster. Lesson 000 deploys nothing and changes nothing.
- No BPMN file may be added to `processes/`; the notation is shown as a diagram in the
  lesson document only.
- Additive numbering: Lesson 000 is inserted without renumbering existing lessons.

## Verification
- All relative links in the new documents resolve.
- Every `Fact (8.9)` claim traces to a documentation page for version 8.9.
- Every `Observed` claim has a matching verbatim command and output in `evidence.md`.
- The evidence was produced by a real run, not reconstructed.
- `processes/` contains no new files.
- Lesson 000 remains `ready`, not `completed`, until independent review.

## Dependencies
- ADR-0002, which pins Camunda 8.9.x.
- The vendored local environment in `infra/local/camunda-8.9/`.

## Related lesson
`docs/lessons/000-camunda-bpmn-concepts/lesson.md`, which unblocks
`docs/lessons/001-camunda7-8-mental-model/lesson.md`.
