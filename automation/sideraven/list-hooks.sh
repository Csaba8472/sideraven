#!/usr/bin/env bash
# List every Sideraven hook in upstream (Iceraven/Fenix) files.
# Run after a rebase to see where conflicts are likely and what to re-check.
cd "$(dirname "$0")/../.."
git grep -n "SIDERAVEN-HOOK" -- . ':!automation/sideraven' ':!docs/sideraven-hooks.md' ':!AGENTS.md'
