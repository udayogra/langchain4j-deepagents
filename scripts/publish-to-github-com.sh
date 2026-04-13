#!/usr/bin/env bash
# Run once after: gh auth login -h github.com  (browser login as udayogra)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

REPO_SLUG="udayogra/langchain4j-deepagents"

if ! gh auth status --hostname github.com &>/dev/null; then
  echo "Not logged in to github.com. Run:"
  echo "  gh auth login -h github.com -w -p https -s repo"
  exit 1
fi

echo "Logged in to github.com as: $(gh api user -q .login)"
echo "Tip (once): gh auth setup-git   # makes git HTTPS use this login"

if gh repo view "$REPO_SLUG" &>/dev/null; then
  echo "Remote repo $REPO_SLUG already exists."
else
  echo "Creating public repo $REPO_SLUG ..."
  gh repo create "$REPO_SLUG" --public --description "Deepagents-style agent harness on LangChain4j (todos, workspace files, task sub-agents)"
fi

if git remote get-url origin &>/dev/null; then
  git remote set-url origin "https://github.com/${REPO_SLUG}.git"
else
  git remote add origin "https://github.com/${REPO_SLUG}.git"
fi

echo "Pushing main ..."
if git push -u origin main; then
  echo "Done: https://github.com/${REPO_SLUG}"
  exit 0
fi

echo "Push failed (e.g. unrelated histories on remote). Retrying with --force-with-lease ..."
git push --force-with-lease -u origin main
echo "Done: https://github.com/${REPO_SLUG}"
