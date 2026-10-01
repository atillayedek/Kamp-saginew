#!/usr/bin/env python3
"""Fails when a tracked file (or, with --history, any commit) contains something that looks like a secret.

Publishable values (the Supabase anon/publishable key, URLs) are allowed; a JWT is only flagged when its
payload says service_role. Matches are reported by file and line, never printed.
"""
import base64
import json
import re
import subprocess
import sys

PATTERNS = {
    # Header followed by key material (a PEM assembled at run time in a test is not a secret).
    "private key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |PGP )?PRIVATE KEY-----(?:\\n|\s)+[A-Za-z0-9+/]{60,}"),
    "service account": re.compile(r'"private_key_id"\s*:\s*"[0-9a-f]{16,}"'),
    "Google API key": re.compile(r"AIza[0-9A-Za-z_\-]{35}"),
    "Resend key": re.compile(r"\bre_[A-Za-z0-9]{8,}_[A-Za-z0-9]{16,}"),
    "Supabase secret key": re.compile(r"\bsb_secret_[A-Za-z0-9_\-]{20,}"),
    "Supabase access token": re.compile(r"\bsbp_[0-9a-f]{40}"),
    "GitHub token": re.compile(r"\b(?:ghp|gho|ghs|ghu)_[A-Za-z0-9]{36}|\bgithub_pat_[A-Za-z0-9_]{60,}"),
    "Stripe live key": re.compile(r"\b[sr]k_live_[A-Za-z0-9]{20,}"),
    "Slack token": re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{10,}"),
    "OpenAI key": re.compile(r"\bsk-(?:proj-)?[A-Za-z0-9_\-]{32,}"),
}
JWT = re.compile(r"eyJ[A-Za-z0-9_\-]{10,}\.(eyJ[A-Za-z0-9_\-]{10,})\.[A-Za-z0-9_\-]{10,}")
SKIP = (".png", ".jpg", ".jpeg", ".webp", ".jar", ".pdf", ".ttf", ".otf", ".ico")


def jwt_role(payload: str):
    try:
        data = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
        return data.get("role") if isinstance(data, dict) else None
    except ValueError:
        return None


def scan_text(name: str, text: str):
    findings = []
    for label, pattern in PATTERNS.items():
        for match in pattern.finditer(text):
            findings.append(f"{name}:{text.count(chr(10), 0, match.start()) + 1}: {label}")
    for match in JWT.finditer(text):
        if jwt_role(match.group(1)) == "service_role":
            findings.append(f"{name}:{text.count(chr(10), 0, match.start()) + 1}: service_role JWT")
    return findings


def main() -> int:
    findings = []
    if "--history" in sys.argv:
        log = subprocess.run(["git", "log", "--all", "-p", "--no-color", "--format=commit %H"],
                             capture_output=True, text=True, errors="replace", check=True).stdout
        added: dict[str, list[str]] = {}
        commit = "?"
        for line in log.splitlines():
            if line.startswith("commit "):
                commit = line[7:19]
            elif line.startswith("+") and not line.startswith("+++"):
                added.setdefault(commit, []).append(line[1:])
        for commit, lines in added.items():
            findings += scan_text(f"commit {commit}", "\n".join(lines))
    else:
        files = subprocess.run(["git", "ls-files", "-z"], capture_output=True, check=True).stdout.decode().split("\0")
        for path in filter(None, files):
            if path.lower().endswith(SKIP):
                continue
            try:
                with open(path, encoding="utf-8", errors="replace") as handle:
                    findings += scan_text(path, handle.read())
            except (IsADirectoryError, FileNotFoundError):
                continue
    for finding in sorted(set(findings)):
        print(f"POSSIBLE SECRET {finding}")
    print(f"secret scan: {len(set(findings))} finding(s)")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
