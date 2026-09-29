#!/usr/bin/env bash
# Project-scoped mise wrapper.
#
#   ./infra/local/mise.sh install
#   ./infra/local/mise.sh exec -- java -version
#   ./infra/local/mise.sh current
#
# Equivalent to running mise inside the project with all of its state redirected
# to <project>/.mise/, so nothing is ever installed globally.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./mise-env.sh
source "${HERE}/mise-env.sh"

cd "${MISE_CONFIG_ROOT}"
exec mise "$@"
