# Plan: restyle fork of Free My TV with Couch TV's look and scenes

Status: scaffold only. Nothing here has been built or run on a TV yet (no Android SDK where it was made).

## Decisions made
- Base: Free My TV (native Java, light, already has shader glass + a glass clock). Android TV / Google TV only.
- Scope: **restyle only**. Keep Free My TV's layout (picks, shelf, drawer). Do not port Couch TV's layout, phone remote, bridge or assistant.
- Borrow from Couch TV: look (fonts, colours, spacing, motion, glass material), the liquid-glass clock, and the 5 generated scenes.
- Dropped from the copy: LG / Samsung ports (web/, webos/, tizen/), upstream website workflow, tests, git history.

## Placeholders to replace (one pass, then rebuild)
| Placeholder | Where | Replace with |
|---|---|---|
| `dev.tvhome.fork` / `dev/tvhome/fork` | `app/AndroidManifest.xml`, every Java file, `build.sh`, `app/assets/i18n/*.json` | your package id (rename the folder `app/src/dev/tvhome/fork` too) |
| `TV Home` | manifest labels, Java strings, `app/res/values*/strings.xml`, i18n keys AND values | your app name. Change keys and values together, or translations stop matching |
| `TVHome.apk`, `tvhome.keystore` | `build.sh`, `Updates.java` | your names. Keep the keystore safe: updates need the same key |
| `MuneneCalvin/myowntv` (updater repo) | `app/src/dev/tvhome/fork/Updates.java` | already set to this repo; it only offers releases signed with the key in `RELEASE_CERT`, so set that or turn updates off |
| `RELEASE_CERT` (all zeros) | `Updates.java` | SHA-256 of your release certificate (`apksigner verify --print-certs`) |

When renaming, use a Unicode-safe tool (Python). A byte-wise `sed` with non-ASCII characters corrupted three translation files during this scaffold.

## Phase 0: build and install next to the original (about half a day)
1. On the Mac: JDK 17+, Android SDK (build-tools 34, platform android-34). `./build.sh`; it creates `config.properties` on first run.
2. Install: `adb connect <tv>:5555 && adb install -r TVHome.apk`. The package id differs, so it installs beside the original app.
3. Do NOT switch Home Screen yet. First settle the safety decision below.

## Phase 1: safety decision before it becomes the home screen
Free My TV's switch turns off Google TV's home (`com.google.android.apps.tv.launcherx`) and setup wizard (`setupwraith`). Couch TV's docs report this combination leaving some Google TVs in a boot loop after a restart, and Free My TV refuses it when the setup wizard owns the saved Wi-Fi (this happened on the Skyworth). Options:
- A (recommended): in `HomeMode.java`, stop disabling those two packages and rely on set-home-activity plus a Home-button watch, as Couch TV 0.11+ does. More work, safer.
- B: keep upstream behaviour; forget and re-save the Wi-Fi on the TV first; keep `undo` ready.

## Phase 2: restyle (about 1.5 to 2 days)
Design values taken from Couch TV's `tv-home/web/app.css`:

| Element | Value | Where in this code |
|---|---|---|
| Fonts | Inter text (already bundled), Antonio 300 for big clock digits (OFL; add to `app/assets/fonts`) | `Ui.java` (`Ui.numbers`, `Ui.thin`) |
| Colours | bg `#0b0b0e`, text `#f5f5f7`, text-2 62%, text-3 42%, tile `#1c1c21` | `Ui.java`, `HomeView.java` |
| Spacing | safe area 80x60, gap 40, app tile 184x104 (16:9), shelf card 320x180 | `HomeView.java` constants |
| Motion | easing (0.2, 0.8, 0.2, 1), tile focus lift 240 ms, slow glint sweep on the focused tile | `Spring.java`, `HomeView.java` |
| Glass | tint: 160deg gradient white 24% / 6% / 12% over rgba(20,20,28,.22); blur about 28 px, saturate 170%, light rim | `Scene.java`, `Frost.java`, `Shaders.java` (AGSL on Android 13+, bitmap path below) |
| Liquid glass clock | digits filled with the blurred photo; dark outer edge (stroke 11 px, black 16%, blurred); bright rim (2.5 px, white 80%); 165deg sheen (white 62% to 0 to 30%) | `DreamView.java` glass-clock code (~lines 86, 647, 847) |

Keep a lite path (no blur) for slow GPUs; the TV has about 1.8 GB RAM and a 32-bit chip.

## Phase 3: scenes (about 1.5 days)
Couch TV scene = 3 images: photo 1920x1080 JPEG, blurred "glass" copy (fills the clock digits), optional foreground cut-out WebP (sky transparent, drawn over the clock).
Draw order: photo, glass clock, foreground cut-out, dimming scrim, then the UI.
1. Bundle only the five generated scenes at 1080p (night, golden, water, fog, dawn). Skip the 4K copies and skip the Wikimedia photos (CC BY-SA, share-alike).
2. Load one scene at a time as a HARDWARE bitmap (`Backdrop.java` already does this); crossfade every 20 s; honour the existing 720p UI scale on low-RAM TVs.
3. Add the cut-out layer after the clock in `DreamView.java` and the home clock.
4. Add a "Scenes" option in `SettingsActivity.java` and `Prefs.java`, plus strings in all i18n catalogs.
Quick check before any code: Free My TV's own-photos feature (`tools/photos.py`) can already show the scene JPEGs as backgrounds.

## Phase 4: test on the Skyworth (about 1 day)
- Cold boot, then Home press from Netflix and YouTube; no flash of the old home.
- Memory with scenes running for an hour; no stutter on focus moves.
- Remote keys (Skyworth key code in `SkyworthRemote.java`), screensaver, inputs, settings.
- Reboot test, then check port 5555 / adb recovery and that `Original` restores the stock home.

## Open risks
- `HomeView.java` is about 5,000 lines and was not fully read; clock integration on the home screen may take longer than estimated.
- Scene assets: AI-generated, "use however you like" per Couch TV's credits. Confirm this before shipping publicly.
- No Android SDK was available here, so nothing has been compiled.
