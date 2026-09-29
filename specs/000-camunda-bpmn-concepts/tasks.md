# SPEC-000 Tasks

## Evidence

- [x] Capture the running stack, image versions and health.
- [x] Capture `/v2/topology` to separate broker from gateway.
- [x] Capture `/actuator/partitions` to expose log position, snapshot and exporter phase.
- [x] Sample the log position over time to show the log advancing.
- [x] Capture `/actuator/exporters` to show the exporter is enabled.
- [x] Capture `/actuator/cluster` to show partition routing strategy.
- [x] Capture both storage volumes to show the separation.
- [x] Confirm no external database connection.
- [x] Confirm zero process instances via the v2 search API.
- [x] Record the failed `/v1/*` attempt and the SPA false positive, so the correct v2
      method is documented rather than rediscovered.

## Content

- [x] Write the problem that motivates a workflow engine.
- [x] Define a workflow engine and explain why state must outlive the code.
- [x] Explain BPMN as an OMG standard rather than a Camunda invention.
- [x] Build the vocabulary table: definition, instance, task, user task, service task,
      job, job worker, variable.
- [x] Write the service task → job → job worker chain explicitly.
- [x] Explain what Camunda is and how 7 and 8 relate as engine generations.
- [x] Build the component table with "what / why it must exist / live proof".
- [x] Explain the log and what a position means.
- [x] Define primary versus secondary storage with Camunda's terminology.
- [x] Record the tension between Camunda's marketing framing and the observed reality.
- [x] Add a "terms not to conflate" section and an interview-question set.

## Registration

- [x] Add Module 0 to `docs/learning-roadmap.md`.
- [x] Add Module 0 to `docs/course-structure.md`.
- [x] Add item 0 to the knowledge-base progression in `docs/camunda/README.md`.
- [x] Create `docs/modules/00-orientation/README.md`.
- [x] Mark Lesson 001 as depending on Lesson 000.

## Verification

- [x] All relative links resolve.
- [x] Every factual claim carries the 8.9 label.
- [x] Every observed claim has a verbatim command in `evidence.md`.
- [x] No BPMN file added under `processes/`.
- [ ] Independent review of the lesson.
