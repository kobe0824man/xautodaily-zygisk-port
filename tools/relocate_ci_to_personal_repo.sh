#!/usr/bin/env bash
# Publish this port's CI build to the owner's personal GitHub account.
#
# The organisation repository cannot schedule a runner (see
# source/docs/ZYGISK_PORT_3.1.0-RC6.md), so the build workflows are hosted in the owner's
# personal account instead. This script performs the relocation and verifies the result.
#
# Requirements:
#   * a GitHub personal access token for the target account, with the `repo` scope (create and
#     push the repository) and the `workflow` scope (push the files under .github/workflows -
#     GitHub rejects workflow updates without it);
#   * the token in the GITHUB_PERSONAL_TOKEN environment variable or in .env next to this
#     repository. It is never echoed and never written to the repository.
#
# Usage:
#   GITHUB_PERSONAL_TOKEN=github_pat_xxx bash tools/relocate_ci_to_personal_repo.sh
#
# Environment overrides:
#   OWNER      target account                    (default kobe0824man)
#   REPO_NAME  target repository                (default xautodaily-zygisk-port)
#   BRANCH     branch that carries the port     (default zygisk-port-3.1.0-rc6)
#   VISIBILITY private | public                 (default private)
#   WORKSPACE  path to the working tree         (default the repository root)
#   DRY_RUN    set to 1 to report what would happen without changing anything
set -euo pipefail

OWNER="${OWNER:-kobe0824man}"
REPO_NAME="${REPO_NAME:-xautodaily-zygisk-port}"
BRANCH="${BRANCH:-zygisk-port-3.1.0-rc6}"
VISIBILITY="${VISIBILITY:-private}"
DRY_RUN="${DRY_RUN:-0}"
WORKSPACE="${WORKSPACE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
API="${GH_API:-https://api.github.com}"

if [[ -z "${GITHUB_PERSONAL_TOKEN:-}" && -f "$WORKSPACE/.env" ]]; then
  GITHUB_PERSONAL_TOKEN="$(sed -n 's/^GITHUB_PERSONAL_TOKEN=//p' "$WORKSPACE/.env" | tail -1)"
fi
TOKEN="${GITHUB_PERSONAL_TOKEN:-}"
if [[ -z "$TOKEN" ]]; then
  echo "GITHUB_PERSONAL_TOKEN is not set. Export it or add it to $WORKSPACE/.env." >&2
  exit 2
fi

AUTH=(-H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json")

say() { printf '\n== %s ==\n' "$*"; }

say "identity"
LOGIN="$(curl -sS -m 30 "${AUTH[@]}" "$API/user" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("login","?"))')"
echo "token belongs to: $LOGIN"
if [[ "$LOGIN" != "$OWNER" ]]; then
  echo "Refusing to continue: the token is not the target account ($OWNER)." >&2
  exit 3
fi

# GitHub rejects a push that creates or updates a file under .github/workflows when the token
# lacks the `workflow` scope, and it rejects the whole push rather than the workflow files
# alone. Check the scopes before pushing so the cause is not buried in a ref update error.
# The header carries the whole list ("repo, workflow"), so take everything after the colon and
# drop the spaces GitHub puts after the commas.
SCOPES="$(curl -sS -m 30 -D - -o /dev/null "${AUTH[@]}" "$API/user" \
          | tr -d '\r' \
          | awk 'tolower($1) == "x-oauth-scopes:" {sub(/^[^:]*:[[:space:]]*/, ""); print; exit}')"
SCOPES="${SCOPES// /}"
if [[ -z "$SCOPES" ]]; then
  echo "scopes: not reported (fine-grained token) - the push itself is the only check"
elif [[ ",$SCOPES," != *",workflow,"* ]]; then
  echo "Refusing to continue: the token reports scopes [$SCOPES] but not 'workflow'." >&2
  echo "GitHub would reject the push of .github/workflows/*. Reissue the token with the" >&2
  echo "'workflow' scope (classic PAT), or with Contents + Workflows read/write" >&2
  echo "(fine-grained PAT), or the CI workflows will never reach the personal repository." >&2
  exit 5
else
  echo "scopes: $SCOPES"
fi

say "repository $OWNER/$REPO_NAME"
CODE="$(curl -sS -m 30 -o /tmp/relocate-repo.json -w '%{http_code}' "${AUTH[@]}" "$API/repos/$OWNER/$REPO_NAME")"
if [[ "$CODE" == "404" ]]; then
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "would create $OWNER/$REPO_NAME ($VISIBILITY)"
  else
    curl -sS -m 30 -X POST "${AUTH[@]}" \
      -d "{\"name\":\"$REPO_NAME\",\"private\":$([[ "$VISIBILITY" == "private" ]] && echo true || echo false),\"description\":\"XAutoDaily 3.1.0-rc6 standalone Zygisk port - build and CI\",\"has_issues\":false,\"has_projects\":false,\"has_wiki\":false,\"auto_init\":false}" \
      "$API/user/repos" \
      | python3 -c 'import json,sys; d=json.load(sys.stdin); print("created:", d.get("full_name"), d.get("visibility"), d.get("message",""))'
  fi
elif [[ "$CODE" == "200" ]]; then
  echo "already exists"
else
  echo "unexpected status $CODE"; cat /tmp/relocate-repo.json; exit 4
fi

say "push"
if [[ "$DRY_RUN" == "1" ]]; then
  echo "would push $BRANCH and main to $OWNER/$REPO_NAME"
else
  git -C "$WORKSPACE" remote remove personal >/dev/null 2>&1 || true
  git -C "$WORKSPACE" remote add personal "https://x-access-token:$TOKEN@github.com/$OWNER/$REPO_NAME.git"
  git -C "$WORKSPACE" push --force personal "$BRANCH:refs/heads/$BRANCH"
  git -C "$WORKSPACE" push --force personal "main:refs/heads/main"
  # The token only needs to be in the remote URL while pushing; drop it again so it does not
  # sit in .git/config on disk.
  git -C "$WORKSPACE" remote set-url personal "https://github.com/$OWNER/$REPO_NAME.git"
fi

say "verify"
if [[ "$DRY_RUN" == "1" ]]; then
  echo "would verify branch refs and workflow files on the remote"
  exit 0
fi
for REF in "$BRANCH" main; do
  SHA="$(curl -sS -m 30 "${AUTH[@]}" "$API/repos/$OWNER/$REPO_NAME/git/ref/heads/$REF" \
        | python3 -c 'import json,sys; print(json.load(sys.stdin).get("object",{}).get("sha","MISSING"))' 2>/dev/null || echo MISSING)"
  LOCAL="$(git -C "$WORKSPACE" rev-parse "$REF" 2>/dev/null || echo n/a)"
  echo "  $REF: local $LOCAL | remote $SHA"
done
for WF in xautodaily-zygisk-ci.yml actions-selfcheck.yml; do
  CODE="$(curl -sS -m 30 -o /dev/null -w '%{http_code}' "${AUTH[@]}" \
    "$API/repos/$OWNER/$REPO_NAME/contents/.github/workflows/$WF?ref=$BRANCH")"
  echo "  .github/workflows/$WF: HTTP $CODE"
done

say "runs"
curl -sS -m 30 "${AUTH[@]}" "$API/repos/$OWNER/$REPO_NAME/actions/runs?branch=$BRANCH&per_page=5" \
  | python3 -c '
import json,sys
d = json.load(sys.stdin)
for r in d.get("workflow_runs", []):
    print("  {} | {:32} | {}/{} | {}".format(r["id"], r["name"][:32], r["status"], r["conclusion"], r["html_url"]))
'
