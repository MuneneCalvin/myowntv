#!/usr/bin/env python3
"""One-pass rename of the scaffold placeholders. UTF-8 safe (never byte-wise sed).

  tools/rename.py <old_pkg> <new_pkg> <old_name> <new_name> <old_slug> <new_slug>
  e.g. tools/rename.py dev.tvhome.fork com.example.app "TV Home" "My App" TVHome MyApp

Order matters: package first, then the display name, then the CamelCase and lowercase slugs.
Moves app/src/<old pkg dir> to <new pkg dir>. Fails if any placeholder remains afterwards.
"""
import os, shutil, sys

SKIP_DIRS = {".git", ".superpowers", "docs", "out"}
SKIP_FILES = {"PLAN.md", "tools/rename.py"}
BINARY_EXT = {".png", ".ttf", ".webp", ".jpg", ".keystore", ".apk", ".idsig"}


def text_files(root):
    for d, dirs, files in os.walk(root):
        dirs[:] = [x for x in dirs if x not in SKIP_DIRS]
        for f in files:
            p = os.path.join(d, f)
            rel = os.path.relpath(p, root)
            if rel in SKIP_FILES or os.path.splitext(f)[1].lower() in BINARY_EXT:
                continue
            yield p, rel


def main(argv):
    if len(argv) != 7:
        sys.exit(__doc__)
    old_pkg, new_pkg, old_name, new_name, old_slug, new_slug = argv[1:]
    pairs = [(old_pkg, new_pkg), (old_pkg.replace(".", "/"), new_pkg.replace(".", "/")),
             (old_name, new_name), (old_slug, new_slug), (old_slug.lower(), new_slug.lower())]
    root = os.getcwd()
    for p, rel in text_files(root):
        try:
            s = open(p, encoding="utf-8").read()
        except UnicodeDecodeError:
            continue
        t = s
        for a, b in pairs:
            t = t.replace(a, b)
        if t != s:
            with open(p, "w", encoding="utf-8", newline="") as fh:
                fh.write(t)
            print("changed", rel)
    old_dir = os.path.join(root, "app", "src", *old_pkg.split("."))
    new_dir = os.path.join(root, "app", "src", *new_pkg.split("."))
    if os.path.isdir(old_dir):
        os.makedirs(os.path.dirname(new_dir), exist_ok=True)
        shutil.move(old_dir, new_dir)
        # prune empty old parents
        d = os.path.dirname(old_dir)
        while d != os.path.join(root, "app", "src") and os.path.isdir(d) and not os.listdir(d):
            os.rmdir(d); d = os.path.dirname(d)
        print("moved", os.path.relpath(old_dir, root), "->", os.path.relpath(new_dir, root))
    left = [rel for p, rel in text_files(root)
            if any(a in open(p, encoding="utf-8", errors="ignore").read() for a, _ in pairs)]
    if left:
        sys.exit("placeholders remain in: " + ", ".join(left))


main(sys.argv)
