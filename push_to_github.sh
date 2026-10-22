#!/usr/bin/env bash
# Creates a NEW GitHub repo from this folder and pushes it. Needs the GitHub CLI: gh auth login
set -euo pipefail
REPO="${1:-OpenEar}"
gh repo create "$REPO" --private --source=. --remote=origin --push
echo "Now open the repo's Actions tab → Android CI → download artifact OpenEar-debug-apk"
