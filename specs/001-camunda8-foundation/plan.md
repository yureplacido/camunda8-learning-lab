# SPEC-001 Plan

Narrowed to Lesson 001, 2026-09-29. Steps 1–2 and 7–8 are done; 3–6 moved to a later SPEC.

## Done

1. Pin a supported Camunda 8 version and record the decision (ADR-0002).
2. Research Camunda 7 and Camunda 8 architecture from official documentation, labelling
   each version-sensitive claim.
3. Build a self-contained local environment: vendored, checksum-verified official
   lightweight compose plus project-scoped mise. No global installs.
4. Start the cluster and record verbatim evidence: version, components, endpoints.
5. Verify the persistence claim by inspecting the running system, not the documentation —
   which forced a correction of a wrong claim.
6. Document the C7 → 8 model, separating fact / observation / interpretation.
7. Draw C4 context and container views from the observed topology.
8. Answer the interview questions from repository material only.

## Deferred to a later SPEC

9. Create a minimal BPMN process.
10. Implement the smallest useful Java worker.
11. Add tests around worker behaviour.
12. Document failure and retry semantics.

Deferred items are preserved, not deleted. They require lessons that first establish the
execution model this SPEC verifies.
