#!/bin/sh
# Every scene has its photo and glass copy at 1920x1080; four have a cut-out; the lot stays under 8 MB.
cd "$(dirname "$0")/../.." || exit 1
D=app/assets/scenes
for n in night golden water fog dawn; do
  for f in $n.jpg $n-glass.jpg; do
    [ -f "$D/$f" ] || { echo "FAIL missing $f"; exit 1; }
    [ "$(sips -g pixelWidth -g pixelHeight "$D/$f" | awk '/pixel/{printf "%sx",$2}')" = "1920x1080x" ] || { echo "FAIL size of $f"; exit 1; }
  done
done
for n in night golden fog dawn; do [ -f "$D/$n-fg.webp" ] || { echo "FAIL missing $n-fg.webp"; exit 1; }; done
[ ! -f "$D/water-fg.webp" ] || { echo "FAIL water has no cut-out"; exit 1; }
[ -f "$D/CREDITS.txt" ] || { echo "FAIL CREDITS.txt"; exit 1; }
KB=$(du -sk "$D" | cut -f1)
[ "$KB" -lt 8192 ] || { echo "FAIL assets are $KB KB"; exit 1; }
echo PASS
