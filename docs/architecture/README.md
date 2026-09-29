# Architecture

Architecture documentation uses C4 diagrams and concise decision records.

Expected views:
- Context
- Container
- Component, when useful
- Deployment

Integration diagrams should make orchestration boundaries, external services, Kafka and worker interactions explicit.

## Current views

| View | Document | Source |
|---|---|---|
| C4 L1 Context | [c4/001-context.md](c4/001-context.md) | Observed run, 2026-09-29 |
| C4 L2 Container | [c4/001-container.md](c4/001-container.md) | Observed run, 2026-09-29 |

Component and Deployment views are not written yet. They are deferred until a lesson
introduces job workers, Kafka and a Kubernetes deployment, at which point the boundaries
they would draw actually exist.
