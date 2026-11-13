#!/usr/bin/env python3
"""Every i18n catalogue parses as UTF-8 JSON and has the Scenes keys with non-empty text."""
import glob, json, os, sys
os.chdir(os.path.join(os.path.dirname(__file__), "..", ".."))
KEYS = ["Scenes"]
files = sorted(glob.glob("app/assets/i18n/*.json"))
if len(files) != 1:
    sys.exit(f"FAIL expected the English catalogue only, found {len(files)}")
bad = []
for f in files:
    d = json.load(open(f, encoding="utf-8"))
    for k in KEYS:
        if not isinstance(d.get(k), str) or not d[k].strip():
            bad.append(f"{os.path.basename(f)}:{k}")
if bad:
    sys.exit("FAIL missing: " + ", ".join(bad))
print("PASS")
