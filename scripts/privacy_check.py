#!/usr/bin/env python3
"""Lightweight repository privacy checks; not a substitute for secret rotation."""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
IGNORED_PARTS = {".git", ".gradle", ".idea", ".android-sdk", "build", "dist", "signing"}
CONTENT_EXCLUSIONS = {Path("scripts/privacy_check.py")}
MAX_TEXT_BYTES = 2 * 1024 * 1024

SENSITIVE_NAMES = re.compile(
    r"(^|[._-])(\.env|credential|password|private|secret|token|keystore|signing)([._-]|$)",
    re.IGNORECASE,
)
SENSITIVE_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx", ".pem", ".key"}
CONTENT_RULES = {
    "private key": re.compile(rb"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    "GitHub token": re.compile(rb"\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{20,}\b"),
    "AWS access key": re.compile(rb"\bAKIA[0-9A-Z]{16}\b"),
    "Google API key": re.compile(rb"\bAIza[0-9A-Za-z_-]{30,}\b"),
    "Windows user path": re.compile(rb"\b[A-Za-z]:\\Users\\[^\\\r\n]+", re.IGNORECASE),
    "Unix user path": re.compile(rb"(?:/Users|/home)/[^/\s]+/"),
    "email address": re.compile(rb"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", re.IGNORECASE),
}
ALLOWED_EMAIL = re.compile(r"^(?:[^@]+@users\.noreply\.github\.com|noreply@github\.com)$", re.IGNORECASE)


def find_git() -> str:
    discovered = shutil.which("git")
    if discovered:
        return discovered
    program_files = os.environ.get("ProgramFiles")
    if program_files:
        candidate = Path(program_files) / "Git" / "cmd" / "git.exe"
        if candidate.is_file():
            return str(candidate)
    raise FileNotFoundError("Git executable was not found")


def git(*args: str, binary: bool = False) -> bytes | str:
    completed = subprocess.run(
        [find_git(), "-c", f"safe.directory={ROOT}", *args],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=not binary,
    )
    return completed.stdout


def workspace_paths() -> list[Path]:
    paths: list[Path] = []
    for path in ROOT.rglob("*"):
        relative = path.relative_to(ROOT)
        if path.is_file() and not any(part in IGNORED_PARTS for part in relative.parts):
            paths.append(relative)
    return paths


def staged_paths() -> list[Path]:
    raw = git("diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z", binary=True)
    assert isinstance(raw, bytes)
    return [Path(item.decode("utf-8")) for item in raw.split(b"\0") if item]


def staged_content(path: Path) -> bytes:
    result = git("show", f":{path.as_posix()}", binary=True)
    assert isinstance(result, bytes)
    return result


def check_paths(paths: list[Path], staged: bool) -> list[str]:
    failures: list[str] = []
    for relative in paths:
        lowered_suffix = relative.suffix.lower()
        if SENSITIVE_NAMES.search(relative.name) or lowered_suffix in SENSITIVE_SUFFIXES:
            failures.append(f"sensitive filename: {relative.as_posix()}")

        if relative in CONTENT_EXCLUSIONS:
            continue
        try:
            data = staged_content(relative) if staged else (ROOT / relative).read_bytes()
        except (OSError, subprocess.CalledProcessError):
            failures.append(f"unable to inspect: {relative.as_posix()}")
            continue
        if len(data) > MAX_TEXT_BYTES or b"\0" in data:
            continue
        for label, pattern in CONTENT_RULES.items():
            if pattern.search(data):
                failures.append(f"{label}: {relative.as_posix()}")
    return failures


def check_history() -> list[str]:
    try:
        output = git("log", "--all", "--format=%H%x09%ae%x09%ce")
    except subprocess.CalledProcessError:
        return []
    assert isinstance(output, str)
    failures: list[str] = []
    for line in output.splitlines():
        commit, author, committer = line.split("\t", 2)
        if not ALLOWED_EMAIL.match(author):
            failures.append(f"non-noreply author email: {commit}")
        if not ALLOWED_EMAIL.match(committer):
            failures.append(f"non-noreply committer email: {commit}")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--workspace", action="store_true")
    parser.add_argument("--staged", action="store_true")
    parser.add_argument("--history", action="store_true")
    args = parser.parse_args()
    if not (args.workspace or args.staged or args.history):
        parser.error("select at least one check")

    failures: list[str] = []
    if args.workspace:
        failures.extend(check_paths(workspace_paths(), staged=False))
    if args.staged:
        failures.extend(check_paths(staged_paths(), staged=True))
    if args.history:
        failures.extend(check_history())

    if failures:
        print("Privacy check failed:")
        for failure in sorted(set(failures)):
            print(f"- {failure}")
        return 1
    print("Privacy check passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
