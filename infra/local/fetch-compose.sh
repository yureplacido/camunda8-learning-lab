#!/usr/bin/env bash
# Re-vendor the official Camunda 8.9 Docker Compose distribution used by Lesson 001.
#
# Provenance:
#   release: https://github.com/camunda/camunda-distributions/releases/tag/docker-compose-8.9
#   asset:   docker-compose-8.9.zip
#
# Only the subset required by the *lightweight* configuration is kept, so that the
# repository carries no BPMN models, no JavaScript test harness and no
# management/analytics stack that Lesson 001 deliberately excludes.
#
# Nothing is installed globally. The distribution is downloaded into the repository.

set -euo pipefail

DIST_TAG="docker-compose-8.9"
ASSET_URL="https://github.com/camunda/camunda-distributions/releases/download/${DIST_TAG}/docker-compose-8.9.zip"
# sha256 of docker-compose-8.9.zip, observed 2026-09-29. If a future re-fetch fails
# this check, the upstream asset changed: inspect the diff before updating the pin.
ASSET_SHA256="cb66ac2ff130075a7a679c5f01b06a879bafea5eca08741daf475e2904d6639b"
DEST="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/camunda-8.9"

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

echo "==> downloading ${ASSET_URL}"
curl -fsSL -o "${WORK}/dist.zip" "${ASSET_URL}"

echo "==> verifying sha256"
echo "${ASSET_SHA256}  ${WORK}/dist.zip" | sha256sum --check --status
echo "    sha256 ok: ${ASSET_SHA256}"

echo "==> extracting into ${DEST}"
rm -rf "${DEST}"
mkdir -p "${DEST}"
unzip -q "${WORK}/dist.zip" -d "${WORK}/dist"

# Files required by docker-compose.yaml (lightweight).
for f in docker-compose.yaml .env connector-secrets.txt; do
  cp -a "${WORK}/dist/${f}" "${DEST}/"
done
cp -a "${WORK}/dist/configuration" "${DEST}/"
cp -a "${WORK}/dist/driver-lib" "${DEST}/"
cp -a "${WORK}/dist/camunda-data" "${DEST}/"
cp -a "${WORK}/dist/README.md" "${DEST}/"

# Configuration files for secondary storages this lesson does not use.
rm -f "${DEST}"/configuration/application-postgresql.yaml \
      "${DEST}"/configuration/application-mysql.yaml \
      "${DEST}"/configuration/application-mariadb.yaml \
      "${DEST}"/configuration/application-opensearch.yaml \
      "${DEST}"/configuration/application-oracle.yaml \
      "${DEST}"/configuration/application-mssql.yaml

echo "==> vendored subset:"
find "${DEST}" -type f | sort
