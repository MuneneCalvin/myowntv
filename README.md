# MyOwnTV

A home-screen app for Android TV / Google TV: [Free My TV](https://github.com/simbasjungles/free-my-tv)'s layout and
features (picks, dock, drawer, weather, Continue Watching) with the look of [Couch TV](https://github.com/Seagey/couch-tv):
scene wallpapers, a big liquid-glass clock, glass dock icons. Built for and run on a Skyworth Google TV (Android TV 14).

## What it does
- **Home:** a scene wallpaper (13 to choose from, each on or off, 5 minutes to 3 hours per picture) behind a glass clock,
  the date and weather, then either nothing, Continue Watching or TMDB picks as a row of glass cards, then the dock.
- **Dock:** app icons as dark glass squares with the icon's own glyph (cut out of the app icon by `ClearIcon`); the focused
  app shows its real icon. Add apps in Settings > Shelf.
- **Picks:** TMDB recommendations from the streaming apps installed on the TV, taking turns between services; Watchmode
  (optional) opens a title's own page in its app.
- **Screensaver:** the same scenes and clock. **Drawer** (Down): inputs, shortcuts, weather, crypto prices.
- **Home switch:** MyOwnTV becomes the home with Google TV's own home and setup wizard left on; a Home-button watch
  (`RemoteKeyService`) brings MyOwnTV back when Google's home opens.

## Build (on a computer)
Needs JDK 17+ and the Android SDK (build-tools 34+, platform android-34).

```bash
./build.sh                         # first run creates config.properties (city, TMDB key, Watchmode key...); run it again
adb connect <TV IP>:5555
adb install -r MyOwnTV.apk
```
Then on the TV: open MyOwnTV > Home Screen > MyOwnTV and accept "Allow USB debugging?" (Always allow). `Original` in the
same menu puts the stock home back.

Keys in `config.properties` are built into your own APK, so any app on the TV could read them: don't share the APK.
Do not commit `config.properties`, `*.keystore` or APKs (they are in `.gitignore`).

## Tests
`tools/test/run.sh` runs the host-side tests (no Android needed). Scenes: `tools/copy_scenes.sh` (Couch TV's five) and
`tools/make_scenes.py` (the Pexels photos; needs Pillow).

Credits and licences: [NOTICE.md](NOTICE.md). Design and plan: `docs/superpowers/`.
