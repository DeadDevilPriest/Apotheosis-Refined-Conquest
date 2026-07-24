#!/usr/bin/env python3
"""Rebuild the Forge update-checker JSON from the repo's GitHub releases.

Forge's in-game update checker (the updateJSONURL in mods.toml) expects, per
Minecraft version, a map of mod-version -> changelog, plus a "promos" block
naming the latest and recommended versions. This regenerates that file from the
list of releases so it is always exactly what has been published — stateless, so
re-running it never drifts.

Usage:
    gen_update_json.py <releases.json> <out.json> <mc_version>

<releases.json> is the raw output of `gh api --paginate /repos/OWNER/REPO/releases`.
The repo path for the homepage link is read from the GITHUB_REPOSITORY env var.
"""
import json
import os
import re
import sys


def parse_version(tag):
    """A release tag like 'v0.2.0' -> '0.2.0'."""
    return tag[1:] if tag.startswith("v") else tag


def version_key(v):
    """Sort key that orders 0.2.0 above 0.10.0 correctly; non-numeric parts sort last."""
    parts = []
    for chunk in re.split(r"[.\-+]", v):
        parts.append((0, int(chunk)) if chunk.isdigit() else (1, chunk))
    return parts


def main():
    releases_path, out_path, mc_version = sys.argv[1], sys.argv[2], sys.argv[3]

    with open(releases_path, encoding="utf-8") as fh:
        releases = json.load(fh)

    # Skip drafts — they are not published and have no stable download.
    published = [r for r in releases if not r.get("draft")]

    changelogs = {}
    stable, latest = [], []
    for r in published:
        version = parse_version(r.get("tag_name", ""))
        if not version:
            continue
        body = (r.get("body") or "").strip()
        changelogs[version] = body or r.get("name") or version
        latest.append(version)
        if not r.get("prerelease"):
            stable.append(version)

    latest.sort(key=version_key)
    stable.sort(key=version_key)

    promos = {}
    if latest:
        # -latest includes pre-releases; -recommended is the newest stable one.
        promos[f"{mc_version}-latest"] = latest[-1]
        promos[f"{mc_version}-recommended"] = stable[-1] if stable else latest[-1]

    repo = os.environ.get("GITHUB_REPOSITORY", "OWNER/REPO")
    result = {"homepage": f"https://github.com/{repo}"}
    # Newest first reads better for a human opening the file; Forge ignores order.
    result[mc_version] = {v: changelogs[v] for v in sorted(changelogs, key=version_key, reverse=True)}
    result["promos"] = promos

    with open(out_path, "w", encoding="utf-8") as fh:
        json.dump(result, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


if __name__ == "__main__":
    main()
