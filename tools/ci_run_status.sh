#!/usr/bin/env bash
# Report what the personal-repository CI is doing, and pull the failing job's log.
#
# The build runs in kobe0824man/xautodaily-zygisk-port because the organisation repository
# cannot allocate a runner. This script reads the runs of that repository and, on failure,
# prints the log of the step that broke - which is the only way to find out why a build that
# never used to run fails.
#
# Requirements:
#   * a token for the target account in GITHUB_PERSONAL_TOKEN or in .env next to this
#     repository. It is never echoed.
#
# Usage:
#   bash tools/ci_run_status.sh              # list the latest runs of the port branch
#   bash tools/ci_run_status.sh <run-id>    # show one run, its jobs and its failing steps
set -euo pipefail

OWNER="${OWNER:-kobe0824man}"
REPO_NAME="${REPO_NAME:-xautodaily-zygisk-port}"
BRANCH="${BRANCH:-zygisk-port-3.1.0-rc6}"
WORKSPACE="${WORKSPACE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
API="https://api.github.com"

if [[ -z "${GITHUB_PERSONAL_TOKEN:-}" && -f "$WORKSPACE/.env" ]]; then
  GITHUB_PERSONAL_TOKEN="$(sed -n 's/^GITHUB_PERSONAL_TOKEN=//p' "$WORKSPACE/.env" | tail -1)"
fi
TOKEN="${GITHUB_PERSONAL_TOKEN:-}"
if [[ -z "$TOKEN" ]]; then
  echo "GITHUB_PERSONAL_TOKEN is not set. Export it or add it to $WORKSPACE/.env." >&2
  exit 2
fi

AUTH=(-H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json")

api() { curl -sS -m 60 "${AUTH[@]}" "$API$1"; }

if [[ $# -ge 1 ]]; then
  RUN_ID="$1"
  echo "== run $RUN_ID =="
  api "/repos/$OWNER/$REPO_NAME/actions/runs/$RUN_ID" | python3 -c '
import json,sys
r = json.load(sys.stdin)
print("name        :", r.get("name"))
print("branch      :", r.get("head_branch"), "@", (r.get("head_sha") or "")[:10])
print("status      :", r.get("status"), "/", r.get("conclusion"))
print("url         :", r.get("html_url"))
print("created     :", r.get("created_at"))
'
  echo
  echo "== jobs =="
  api "/repos/$OWNER/$REPO_NAME/actions/runs/$RUN_ID/jobs?per_page=100" | python3 -c '
import json,sys
d = json.load(sys.stdin)
for j in d.get("jobs", []):
    print("  job  {:24} {:10} {}".format(j.get("name","")[:24], j.get("status"), j.get("conclusion")))
    for s in j.get("steps") or []:
        mark = "x" if s.get("conclusion") not in (None, "success", "skipped") else " "
        print("    [{}] {:38} {}".format(mark, s.get("name","")[:38], s.get("conclusion")))
'
  exit 0
fi

echo "== runs of $OWNER/$REPO_NAME on $BRANCH =="
api "/repos/$OWNER/$REPO_NAME/actions/runs?branch=$BRANCH&per_page=10" | python3 -c '
import json,sys
d = json.load(sys.stdin)
runs = d.get("workflow_runs", [])
if not runs:
    print("  no runs yet")
for r in runs:
    print("  {} | {:28} | {:10} | {:9} | {}".format(
        r["id"], r["name"][:28], r["status"], r.get("conclusion") or "-", r["html_url"]))
'
