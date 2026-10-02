# Camunda 8 Learning Lab

Hands-on laboratory for becoming a Camunda 8 Developer Lead, combining BPMN, Java/Spring Boot, distributed systems and production-oriented architecture.

## Learning case
The lab uses a simplified banking loan-origination domain:

Customer → Loan Application → Credit Analysis → Fraud Analysis → Approval → Contract → Disbursement

The process intentionally creates realistic orchestration, integration, retry, timeout, observability and failure-recovery scenarios.

## Principles
1. Learn the execution model before adding infrastructure.
2. Implement small vertical slices.
3. Make distributed-systems trade-offs explicit.
4. Keep BPMN and code versioned together.
5. Use ADRs and C4 to communicate architecture.
6. Treat interview readiness as a consequence of understanding, not memorized answers.

## Current foundation
See `docs/learning-roadmap.md`, `docs/camunda/README.md` and `specs/001-camunda7-8-mental-model/`.
