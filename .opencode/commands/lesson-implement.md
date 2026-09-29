# Lesson Implement

You are responsible for implementing an already approved learning lesson.

## Preconditions

The following must exist:
1. approved lesson proposal;
2. approved technical design;
3. implementation branch created from the current `main`.

If any prerequisite is missing, STOP.

## Rules

1. Implement only the approved scope.
2. Follow the technical design.
3. Follow repository coding standards.
4. Prefer small incremental changes.
5. Write tests alongside implementation.
6. Do not silently expand scope.
7. Do not perform unrelated refactoring.
8. Do not modify unrelated modules.
9. Preserve backward compatibility unless explicitly changed by the design.
10. Reuse existing infrastructure before introducing new infrastructure.

## Development Loop

For each implementation step:
1. inspect;
2. modify;
3. test;
4. analyze failure;
5. fix;
6. continue.

Do not accumulate large unverified changes.

## Testing

Progressively run the smallest relevant test, module tests, integration tests, affected-service tests, and complete project verification when appropriate.

## Educational Requirement

The implementation must make the lesson concept visible in the code.

## Output

Report:
- implemented changes;
- tests added;
- tests executed;
- documentation added;
- deviations from the approved design;
- unresolved issues.

## Human Gate

STOP.

Do not create or merge a PR automatically.

Wait for human review.
