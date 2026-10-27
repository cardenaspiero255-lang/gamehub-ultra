#!/usr/bin/env python3
"""Unprivileged explicit-owner proof for a public repo. Never writes to GitHub.

Uses GitHub's own workflow_dispatch actor identity and GitHub HTTPS read-only
metadata. No token, no secrets, no PR source checkout or dynamic execution.
A successful run is only a consent RECEIPT, not a protected-review approval.
"""
import base64
import hashlib
import json
import os
import re
import sys
import urllib.parse
import urllib.request

REPO = "cardenaspiero255-lang/gamehub-ultra"
OWNER = "cardenaspiero255-lang"
OWNER_ID = "322222186"
BASE_BLOB = "f2831ae24e9687c8a0472c9a42df05f708815be5"
HEAD_BLOB = "5d74541253fa867a7dbea9370be5facfc1be7bf8"
FILES = {
    ".github/scripts/find_sonar_coverage_run.py": ("added", "bfa756c0441a66dbe00ee943f8928537249533c2"),
    ".github/scripts/test_find_sonar_coverage_run.py": ("added", "d5cdd9357207cbe1747e05b037c117aa85c4e104"),
    ".github/scripts/test_verify_sonar_jacoco.py": ("added", "14f414e87d05385782b6493aab6c522c1ed0e81c"),
    ".github/scripts/verify_sonar_jacoco.py": ("added", "e60ace269e32fe738f05f8eefa4ccd3327054739"),
    ".github/workflows/sonar.yml": ("modified", HEAD_BLOB),
}

def api(path):
    # Constant GitHub API host; no URL comes from the untrusted PR.
    url = "https://api.github.com/repos/" + REPO + "/" + path
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "gamehub-ultra-car29-owner-proof",
                 "Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(request, timeout=25) as response:
        return json.load(response)

def verified_blob(data, expected):
    if (not isinstance(data, dict) or data.get("type") != "file"
            or data.get("encoding") != "base64" or data.get("sha") != expected
            or not isinstance(data.get("size"), int) or data["size"] > 160000):
        return False
    try:
        raw = base64.b64decode(data["content"], validate=False)
    except (ValueError, TypeError):
        return False
    digest = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
    return digest == expected and len(raw) == data["size"]

def verified_pr(pr, files, main, head):
    if not isinstance(pr, dict) or pr.get("number") != 247 or pr.get("state") != "open" or pr.get("draft") is not False:
        return False
    if pr.get("changed_files") != 5 or pr.get("author_association") != "OWNER":
        return False
    user = pr.get("user") or {}
    if user.get("id") != 322222186 or user.get("login") != OWNER or user.get("type") != "User":
        return False
    base, candidate = pr.get("base") or {}, pr.get("head") or {}
    if (base.get("sha") != main or base.get("ref") != "main"
            or (base.get("repo") or {}).get("full_name") != REPO
            or candidate.get("sha") != head
            or (candidate.get("repo") or {}).get("full_name") != REPO):
        return False
    if not isinstance(files, list) or len(files) != 5:
        return False
    observed = set()
    for f in files:
        if not isinstance(f, dict):
            return False
        path = f.get("filename")
        if path not in FILES or path in observed:
            return False
        if (f.get("status"), f.get("sha")) != FILES[path]:
            return False
        observed.add(path)
    return len(observed) == len(FILES)

def main():
    event_name = os.environ.get("GITHUB_EVENT_NAME")
    if (event_name != "workflow_dispatch"
            or os.environ.get("GITHUB_REF") != "refs/heads/main"
            or os.environ.get("GITHUB_ACTOR") != OWNER
            or os.environ.get("GITHUB_TRIGGERING_ACTOR") != OWNER
            or os.environ.get("GITHUB_ACTOR_ID") != OWNER_ID
            or os.environ.get("GITHUB_REPOSITORY") != REPO):
        raise ValueError("Authenticated owner and main context required")
    with open(os.environ["GITHUB_EVENT_PATH"], encoding="utf-8") as event_file:
        inputs = json.load(event_file).get("inputs") or {}
    head = inputs.get("pr_head_sha")
    if not isinstance(head, str) or not re.fullmatch(r"[0-9a-f]{40}", head):
        raise ValueError("Exact PR247 SHA required")
    if inputs.get("confirm") != "AUTORIZO-PR247-CAR29":
        raise ValueError("Exact manual owner confirmation required")
    trusted_main = os.environ["GITHUB_SHA"]
    if not re.fullmatch(r"[0-9a-f]{40}", trusted_main):
        raise ValueError("Invalid GitHub default branch SHA")
    get_main = lambda: api("git/ref/heads/main")["object"]["sha"]
    if get_main() != trusted_main:
        raise ValueError("Main changed before receipt")
    get_pr = lambda: api("pulls/247")
    get_files = lambda: api("pulls/247/files?per_page=100")
    if not verified_pr(get_pr(), get_files(), trusted_main, head):
        raise ValueError("PR247 identity, SHA or 5 blobs changed")
    source = urllib.parse.quote(".github/workflows/sonar.yml", safe="/")
    base = api("contents/" + source + "?ref=" + trusted_main)
    candidate = api("contents/" + source + "?ref=" + head)
    if not verified_blob(base, BASE_BLOB) or not verified_blob(candidate, HEAD_BLOB):
        raise ValueError("Unexpected Sonar workflow content")
    if not verified_pr(get_pr(), get_files(), trusted_main, head) or get_main() != trusted_main:
        raise ValueError("GitHub SHA changed during authorization")
    print("OWNER_CONSENT_VERIFIED: PR247 SHA " + head
          + "; authenticated GitHub owner, no PR approval and no merge")

if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        # Log only a fixed, safe validation reason; never print event inputs,
        # API bodies, tokens, or any arbitrary exception message.
        reasons = {
            "Authenticated owner and main context required",
            "Exact PR247 SHA required",
            "Exact manual owner confirmation required",
            "Invalid GitHub default branch SHA",
            "Main changed before receipt",
            "PR247 identity, SHA or 5 blobs changed",
            "Unexpected Sonar workflow content",
            "GitHub SHA changed during authorization",
        }
        reason = str(exc) if isinstance(exc, ValueError) and str(exc) in reasons else type(exc).__name__
        print("CAR-29 OWNER CONSENT INCOMPLETE: " + reason, file=sys.stderr)
        raise SystemExit(1)
