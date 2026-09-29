# SPEC-001 Tasks

Narrowed to Lesson 001, 2026-09-29. Nothing was deleted: the tasks that belong to later
lessons are preserved in [Deferred](#deferred-not-discarded) below.

## Lesson 001

- [x] Pin the Camunda 8 version and record the decision (ADR-0002).
- [x] Research Camunda 7 architecture.
- [x] Research Camunda 8 architecture and Zeebe.
- [x] Write `docs/camunda/camunda-7-vs-8.md`, labelling fact / observed / interpretation.
- [x] Vendor the official lightweight compose, trimmed, with a SHA-256-verified fetch
      script (`infra/local/fetch-compose.sh`).
- [x] Add project-scoped mise policy with no global installs (`.mise.toml`,
      `infra/local/mise-env.sh`, `infra/local/mise.sh`).
- [x] Start the cluster and verify it reaches healthy.
- [x] Verify B2 — version fidelity against the pin.
- [x] Verify B3 — where execution state lives versus what the database is for.
- [x] Verify B4 — component and endpoint reachability.
- [x] Record verbatim evidence in
      `docs/lessons/001-camunda7-8-mental-model/evidence.md`.
- [x] Correct the false "Camunda 8 has no database" claim and make B3 container-scoped.
- [x] Draw the C4 context view.
- [x] Draw the C4 container view.
- [x] Write the lesson (`docs/lessons/001-camunda7-8-mental-model/lesson.md`).
- [x] Document how to run the environment (`infra/local/README.md`).
- [x] Answer the interview questions from repository material.
- [ ] Independent review of the lesson.
- [ ] Verify the environment from a clean checkout on a machine with no pre-existing
      Camunda tooling.

## Deferred (not discarded)

These require lessons that first establish the execution model, so they are not part of
Lesson 001. They stay here so the trail is visible.

- [ ] Add the first BPMN process.
- [ ] Add the first Java job worker.
- [ ] Pin a Java toolchain in `.mise.toml` (first lesson that genuinely needs a JVM).
- [ ] Add tests around worker behaviour.
- [ ] Document retries and idempotency.
- [ ] Review architecture with the BPMN and distributed-systems reviewers.
- [ ] Run an interview-lead simulation.
