#!/bin/sh
# Copies the five generated Couch TV scenes (1080p photo, blurred glass copy, cut-out) into app/assets/scenes.
#   tools/copy_scenes.sh ~/couch-tv/tv-home/web/backgrounds
# Skips the @2x (4K) copies and the Wikimedia photos (CC BY-SA, share-alike).
set -e
SRC="${1:?usage: copy_scenes.sh <couch-tv>/tv-home/web/backgrounds}"
cd "$(dirname "$0")/.."
OUT=app/assets/scenes
mkdir -p "$OUT"
for n in night golden water fog dawn; do
  cp "$SRC/scene-$n.jpg" "$OUT/$n.jpg"
  cp "$SRC/scene-$n-glass.jpg" "$OUT/$n-glass.jpg"
  [ -f "$SRC/scene-$n-fg.webp" ] && cp "$SRC/scene-$n-fg.webp" "$OUT/$n-fg.webp"
done
cat > "$OUT/CREDITS.txt" <<'TXT'
Scenes: night, golden, water, fog, dawn.
From couch-tv (github.com/Seagey/couch-tv, tv-home/web/backgrounds): made with an AI image model from text only (no
source photo); not real places. That project's credits say "Use them however you like", and the cut-outs (-fg) and
blurred glass copies (-glass) are made from the same scenes, so the same terms apply. The code is MIT.
Resized copies only at 1080p; the 4K copies and the Wikimedia photos are not included.
TXT
