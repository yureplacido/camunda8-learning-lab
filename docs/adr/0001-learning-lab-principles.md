# ADR-0001 — Learning Lab Principles

## Status
Accepted

## Context
The repository is both executable code and educational material. Mixing those concerns without structure would make the lab harder to evolve and harder to use for interview preparation.

## Decision
Keep production-like implementation in executable directories and learning/architecture material in `docs/`. Use ADRs for durable architectural decisions and specifications for planned learning increments.

## Consequences
- Concepts can be studied independently from implementation.
- Architectural trade-offs remain documented.
- The repository can grow into a realistic reference project without becoming a monolithic learning note.
