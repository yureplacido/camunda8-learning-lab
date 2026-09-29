# Local Infrastructure

Self-contained local environments for the lab. Nothing here installs anything globally.

## Contents

| Path | What it is |
| --- | --- |
| `mise-env.sh` | Exports `MISE_*` so all mise state lives in `<project>/.mise/` |
| `mise.sh` | Wrapper: sources the env, then runs mise |
| `fetch-compose.sh` | Downloads the official Camunda 8.9 compose and trims it |
| `camunda-8.9/` | The vendored, trimmed lightweight Orchestration Cluster compose |

## Toolchain policy: mise, scoped to this repository

Goal: **no global installs.** Every tool the lab needs is pinned inside this repo, so the
lab is reproducible without touching host configuration.

```bash
./infra/local/mise.sh install              # installs everything pinned in .mise.toml
./infra/local/mise.sh which java
./infra/local/mise.sh exec -- java -version
```

`mise-env.sh` redirects:

| Variable | Value |
| --- | --- |
| `MISE_DATA_DIR` | `<project>/.mise/installs` |
| `MISE_CACHE_DIR` | `<project>/.mise/cache` |
| `MISE_STATE_DIR` | `<project>/.mise/state` |
| `MISE_CONFIG_DIR` | `<project>/.mise/config` |

`.mise/` is gitignored. **Lesson 001 pins no tools**, because it produces no code and
needs no JVM. The first lesson that genuinely needs Java pins it in `.mise.toml` at a
version Camunda 8.9 supports — see [ADR-0002](../../docs/adr/0002-camunda-8-version-pin.md).

## Camunda 8.9.22 local cluster

The vendored compose is the **official lightweight** distribution
(`camunda/camunda:8.9.22` + `camunda/connectors-bundle:8.9.14`), trimmed by
`fetch-compose.sh` to drop BPMN examples, e2e/Playwright and the management stack.

### Start

```bash
cd infra/local/camunda-8.9
docker compose up -d
docker compose ps
```

### Inspect

```bash
curl -s http://localhost:8080/v2/topology
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:9600/actuator/health

# Where the two data stores live
docker compose exec -T orchestration ls -R /usr/local/camunda/data
docker compose exec -T orchestration ls -l /usr/local/camunda/camunda-data

# Proof that no external database is involved
docker compose exec -T orchestration netstat -tn | grep -E ":(5432|3306|1521|27017) " \
  || echo "no external database connection"
```

### Stop and clean

```bash
cd infra/local/camunda-8.9
docker compose down -v     # -v also removes the two data volumes
```

### Ports

| Host port | Purpose |
| --- | --- |
| 8080 | Gateway REST, Operate, Tasklist |
| 9600 | Actuator |
| 26500 | Gateway gRPC |
| 8086 | Connectors (part of official lightweight; unused by this lab) |

Ports `26501`/`26502` are intentionally not published.

## Known constraint

The official compose hardcodes the container names `orchestration` and `connectors` and
the network name `camunda`. Two copies of this compose cannot run side by side without
editing those names. Left unmodified on purpose, to keep the vendored artifact
recognisable against upstream.

## Refreshing the vendored compose

```bash
./infra/local/fetch-compose.sh
```

The script pins the release `docker-compose-8.9`, verifies the asset's SHA-256, extracts,
and keeps only the subset the lab uses. Re-run it deliberately, then re-read the diff
before committing.

The runtime version pin lives in `camunda-8.9/.env` (`CAMUNDA_VERSION`,
`CAMUNDA_CONNECTORS_VERSION`); the decision behind it is
[ADR-0002](../../docs/adr/0002-camunda-8-version-pin.md).

Verified 2026-09-29: re-running the script reproduces the vendored tree with an identical
checksum (`103c0f85c4b3ab2928a9cdb6c74e2ad9c00f8b9cc3ff45fc1ae419be279d8546`).

## Where the evidence went

The observations from the 2026-09-29 run are recorded in
[Lesson 001 evidence](../../docs/lessons/001-camunda7-8-mental-model/evidence.md).
