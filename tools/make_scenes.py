#!/usr/bin/env python3
"""Turns downloaded photos into scenes: app/assets/scenes/<name>.jpg (1920x1080) and <name>-glass.jpg (the clock's glass
fill: the photo blurred 16 px and 1.3x brighter, which is how Couch TV's own glass copies are made).

  python3 -I tools/make_scenes.py <dir with the downloaded <pexels id>.jpg files>

Needs Pillow (pip install pillow). A table below says which photo becomes which scene and where to crop it to 16:9
(`top`: how far down the crop starts, 0 = the top, 0.5 = the middle; the width is always the whole photo, except for
a photo wider than 16:9, which is cropped at the sides, centred).
"""
import os, sys
from PIL import Image, ImageEnhance, ImageFilter

# name, pexels id, top, photographer (Pexels file name handle), what it is
SCENES = [
    ("amber-dusk",    "20647440", 0.38, "oscar-sanchez197",       "mountains under a warm dusk sky"),
    ("amber-ridges",  "12639653", 0.25, "meum-mare-204165854",    "layered ridges in amber haze"),
    ("sea-sunset",    "15991147", 0.50, "robert-stokoe-105922",   "sun setting on the sea"),
    ("golden-peaks",  "6915292",  0.50, "hammyx",                 "mountain silhouettes at sunset"),
    ("aurora-coast",  "6663553",  0.50, "therato",                "northern lights over snowy peaks and the sea"),
    ("aurora-lake",   "39666014", 0.50, "nicobecker",             "northern lights over a calm lake"),
    ("mountain-mist", "28436769", 0.50, "zkadoshi",               "mist on forested mountains"),
    ("dunes",         "2504713",  0.50, "reneterp",               "sand dunes and a calm sea"),
]
W, H = 1920, 1080


def crop16x9(im, top):
    w, h = im.size
    if w / h >= 16 / 9:   # wider: crop the sides, centred
        cw = round(h * 16 / 9)
        x = (w - cw) // 2
        return im.crop((x, 0, x + cw, h))
    ch = round(w * 9 / 16)
    y = round((h - ch) * top / max(0.0001, 1 - ch / h)) if ch < h else 0
    y = max(0, min(h - ch, round(top * h)))
    return im.crop((0, y, w, y + ch))


def main(src):
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "assets", "scenes")
    for name, pid, top, who, what in SCENES:
        im = Image.open(os.path.join(src, pid + ".jpg")).convert("RGB")
        im = crop16x9(im, top).resize((W, H), Image.LANCZOS)
        im.save(os.path.join(out, name + ".jpg"), quality=86, optimize=True)
        glass = ImageEnhance.Brightness(im.resize((480, 270), Image.LANCZOS).filter(ImageFilter.GaussianBlur(4))).enhance(1.3)
        glass.resize((W, H), Image.BICUBIC).save(os.path.join(out, name + "-glass.jpg"), quality=82, optimize=True)
        print(name, "ok")


if __name__ == "__main__":
    main(sys.argv[1])
