#!/usr/bin/env bash
# Keeps every mise-managed tool inside this repository.
#
# Source this (or use ./infra/local/mise.sh) instead of running mise directly,
# otherwise mise would install into the shared per-user store at
# ~/.local/share/mise, which the lab deliberately does not use.
#
# MISE_*_DIR values use mise's own {{ config_root }} template so the paths always
# follow the repository, even if it is moved or checked out elsewhere.

MISE_CONFIG_ROOT="${MISE_CONFIG_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

export MISE_DATA_DIR="${MISE_CONFIG_ROOT}/.mise/data"
export MISE_CACHE_DIR="${MISE_CONFIG_ROOT}/.mise/cache"
export MISE_STATE_DIR="${MISE_CONFIG_ROOT}/.mise/state"
export MISE_CONFIG_DIR="${MISE_CONFIG_ROOT}/.mise/config"
export MISE_SELF_UPDATE=none

# Trust only the repository's own config files.
export MISE_TRUSTED_CONFIG_PATHS="${MISE_CONFIG_ROOT}/.mise.toml"
