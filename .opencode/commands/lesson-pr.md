# Lesson PR

You are responsible for preparing the pull request for an already verified learning lesson.

## Preconditions

Verification must report `READY_FOR_PR`.

If verification reports `NOT_READY`, STOP.

## Checks

Before preparing the PR:
1. inspect git status;
2. inspect the complete diff;
3. verify no unintended files are present;
4. verify no debug code remains;
5. verify no temporary configuration remains;
6. verify tests pass;
7. verify documentation is included;
8. verify commit history is coherent.

## Scope Protection

Do not:
- implement additional features;
- fix unrelated issues;
- refactor unrelated code;
- add future improvements;
- silently expand the lesson.

If something is desirable but outside scope, report it as a future candidate instead.

## Definition of Done

Confirm:
- implementation complete;
- tests complete;
- documentation complete;
- architecture consistent;
- observability addressed where applicable;
- no known blocking issues;
- no unintended scope;
- no hidden follow-up work required for the lesson to be considered complete.

## Output

Prepare:
- PR Title;
- PR Description with Objective, Learning Concepts, Changes, Architecture, Tests, Documentation, Definition of Done, Known Limitations and Explicitly Deferred Improvements.

## Git

Do not merge.
Do not push unless explicitly requested.
Do not modify `main`.

## Human Gate

STOP and wait for human approval before opening or merging the PR.
