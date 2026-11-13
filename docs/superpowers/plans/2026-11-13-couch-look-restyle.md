# Couch TV look on the Free My TV fork: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A light native home screen for the Skyworth Google TV that looks like Couch TV (scenes, liquid-glass clock, glass material, motion) and later becomes the home screen without disabling Google's home or setup wizard.

**Architecture:** Keep Free My TV's layout and code. Restyle through the shared design values (`Ui`, `Spring`, `Scene`, `Shaders`), add a scene layer (`SceneLayer`, new) that DreamView and HomeView draw behind the clock, then replace the upstream home switch in `HomeMode` with a non-destructive one (`HomeSwitch`, new). Look ships first as a normal app beside the original; the switch comes after the look is judged on the TV.

**Tech Stack:** Java (no Gradle; `./build.sh`: aapt2, javac, d8, apksigner), AGSL shaders on Android 13+ with a bitmap fallback, adb. Python 3 for tools.

**Spec:** `docs/superpowers/specs/2026-11-13-couch-look-restyle-design.md` (values and file pointers also in `PLAN.md`).

## Global Constraints

- Target: Skyworth Google TV, Android TV 14, about 1.8 GB RAM, 32-bit CPU. min-sdk 26, target-sdk 33 (from `build.sh`).
- Source root `app/src/dev/tvhome/fork` (renamed in Task 1); package id and app name are chosen in Task 1 and used everywhere after.
- Colours: bg `#0b0b0e`, text `#f5f5f7`, text-2 62% alpha, text-3 42% alpha, tile `#1c1c21`.
- Spacing: safe area 80x60, gap 40, app tile 184x104, shelf card 320x180.
- Motion: easing (0.2, 0.8, 0.2, 1); tile focus lift 240 ms; slow glint sweep on the focused tile.
- Glass: 160deg gradient white 24% / 6% / 12% over rgba(20,20,28,.22); blur about 28 px; saturate 170%; light rim.
- Clock digits: Antonio weight 300 (OFL); fill with blurred photo; dark outer edge stroke 11 px black 16% blurred; bright rim 2.5 px white 80%; 165deg sheen white 62% to 0 to 30%.
- Scenes: five generated only (night, golden, water, fog, dawn), 1080p; no 4K, no Wikimedia photos. Crossfade every 20 s. One scene in memory at a time, as a HARDWARE bitmap. Draw order: photo, glass clock, foreground cut-out, dimming scrim, UI.
- A lite path (no blur) must exist for slow GPUs.
- Never disable `com.google.android.apps.tv.launcherx` or `com.google.android.tungsten.setupwraith` (Task 11 onward).
- Rename edits use Python, never byte-wise `sed` (it corrupted three translation files before). Strings changed in code must change in all `app/assets/i18n/*.json` keys and values together.
- No Android SDK was present when this scaffold was made: the first build (Task 1) may expose compile errors that must be fixed before any other task.

## Review Focus

- Wi-Fi saved by the setup wizard: the upstream switch refuses to run; Task 11 must not trigger that guard at all.
- TV restart: port 5555 closes; app must still start as a normal home candidate and `Original` must restore the stock home.
- A scene file that is missing or corrupt: home and screensaver must still draw (aurora fallback), not blank.
- Low memory (`onTrimMemory`, 720p UI scale): scene layer drops its bitmap and recovers.
- Locale/RTL (ar) and every i18n catalog: new "Scenes" strings exist in all 28 files, or the app shows a key.
- Clock format 12/24 h and a minute rollover during a crossfade: no glitch or stale digits.
- Installing or removing another app after Task 11: home activity is restored.

---

### Task 1: Rename pass and first build

**Files:**
- Create: `tools/rename.py`
- Modify (via script): `app/AndroidManifest.xml`, `build.sh`, `app/src/dev/tvhome/fork/*.java` (move to the new package folder), `app/res/values*/strings.xml`, `app/assets/i18n/*.json`, `app/src/.../Updates.java`

**Interfaces:**
- Produces: `tools/rename.py <old_pkg> <new_pkg> <old_name> <new_name>`; the new package id and app name that every later task uses. Pick them with the owner before running (ask: "package id and app name?").

- [ ] **Step 1:** Install the toolchain on the Mac: JDK 17+, Android SDK with `build-tools;34.0.0` and `platforms;android-34`. Run `./build.sh`. Expected: it creates `config.properties` and exits.
- [ ] **Step 2:** Write `tools/rename.py` (reads and writes UTF-8, replaces package id in text and in directory path, replaces the app name in keys and values together, replaces keystore/APK names). It prints each changed file and fails if the old id remains anywhere after the pass.
- [ ] **Step 3:** Run it. Check: `grep -rn "dev.tvhome.fork\|TV Home" app build.sh` returns nothing; `python3 -c "import json,glob;[json.load(open(f,encoding='utf-8')) for f in glob.glob('app/assets/i18n/*.json')]"` exits 0.
- [ ] **Step 4:** Fill `config.properties` (city Nairobi; latitude -1.2921, longitude 36.8219; owner's TMDB key if given, kept out of git). Set `RELEASE_CERT` to the new key's SHA-256 after the first keystore exists, or turn updates off in `Updates.java`.
- [ ] **Step 5:** Run `./build.sh`. Expected: `<AppName>.apk` produced. Fix compile errors; each fix is its own commit.
- [ ] **Step 6:** Install beside the original: `adb connect <tv>:5555 && adb install -r <AppName>.apk`. Launch it from the app list. Do NOT use any Home Screen switch. Expected: the app opens and shows the stock Free My TV look.
- [ ] **Step 7: Commit** (`git commit -m "chore: rename fork and first build"`).

### Task 2: Design tokens (fonts, colours, spacing)

**Files:**
- Modify: `Ui.java` (typefaces at line ~71, `numbers` at ~358), `HomeView.java` constants, `Ui.java` colour constants
- Create: `app/assets/fonts/Antonio.ttf`, `app/assets/fonts/OFL-Antonio.txt`

**Interfaces:**
- Produces: `Ui.Tokens` constants `BG`, `TEXT`, `TEXT2`, `TEXT3`, `TILE` (int ARGB), `SAFE_X=80`, `SAFE_Y=60`, `GAP=40`, `TILE_W=184`, `TILE_H=104`, `CARD_W=320`, `CARD_H=180`; `Ui.antonio` (Typeface, weight 300) beside `Ui.thin`.

- [ ] **Step 1:** Add Antonio 300 (OFL) and its license file; load it in the same place the other typefaces load.
- [ ] **Step 2:** Add `Ui.Tokens` with the Global Constraints values. Replace the hard-coded equivalents in `Ui.java` and `HomeView.java` with the constants (find by value, one at a time, rebuild after each file).
- [ ] **Step 3:** Run `./build.sh`, install, and check the home screen shows the new colours and spacing: `adb exec-out screencap -p > scratch/home-tokens.png` and compare by eye with Couch TV's home.
- [ ] **Step 4: Commit.**

### Task 3: Motion (easing, focus lift, glint)

**Files:**
- Modify: `Spring.java`, `HomeView.java` (focus handling)

**Interfaces:**
- Produces: `Spring.easeOut(float t) -> float` implementing cubic-bezier(0.2, 0.8, 0.2, 1) over t in [0,1]; focus lift 240 ms using it; a glint sweep painted on the focused tile.

- [ ] **Step 1:** Write a JVM test `tools/test/EaseTest.java` asserting `easeOut(0)==0`, `easeOut(1)==1` (within 1e-4), monotonic over 100 samples, and `easeOut(0.5)>0.5` (fast start). Compile it with `javac` against `Spring.java` (copy the pure function into a test-visible class if `Spring` depends on Android).
- [ ] **Step 2:** Run it; expected FAIL (function missing).
- [ ] **Step 3:** Implement `easeOut` (numeric solve of the Bezier x for t, then y; 8 Newton iterations).
- [ ] **Step 4:** Run the test; expected PASS.
- [ ] **Step 5:** Wire focus lift (240 ms) and the glint into `HomeView` tile focus; do not allocate in `onDraw`. Build, install, move focus on the TV: no stutter.
- [ ] **Step 6: Commit.**

### Task 4: Glass material

**Files:**
- Modify: `Scene.java`, `Shaders.java`, `Frost.java`

**Interfaces:**
- Produces: glass tint, blur radius (28 px at 1080p, scaled by `uiScale`), saturation (1.7) and rim as named constants in `Shaders.java` (`GLASS_TINT_TOP`, `GLASS_TINT_MID`, `GLASS_TINT_BOTTOM`, `GLASS_BLUR_PX`, `GLASS_SATURATE`); the existing bitmap fallback path uses the same values.

- [ ] **Step 1:** Add the constants with the Global Constraints values; use them in the AGSL source and in the fallback path (`Scene` SOFT paint).
- [ ] **Step 2:** Add a "lite" flag: when `Capabilities` reports low RAM or AGSL missing, skip blur and draw tint plus rim only.
- [ ] **Step 3:** Build, install; screenshot the shelf and a drawer; check the tint and rim by eye; on the Skyworth, record `adb shell dumpsys gfxinfo <pkg> framestats` after focus moves and note jank percentage in `docs/test-notes.md`.
- [ ] **Step 4: Commit.**

### Task 5: Liquid-glass clock

**Files:**
- Modify: `DreamView.java` (`Glass`, clock paints near lines 86, 251, 263, 647, 847), `HomeView.java` (home clock)

**Interfaces:**
- Consumes: `Ui.antonio`, `Ui.Tokens`.
- Produces: `DreamView` and the home clock draw digits in Antonio 300 with the glass fill, 11 px dark edge, 2.5 px rim and 165deg sheen.

- [ ] **Step 1:** Change `clockPaint` and `cornerClock` to `Ui.numbers(Ui.antonio, ...)`.
- [ ] **Step 2:** Set the edge, rim and sheen values from Global Constraints in the `Glass` rendering code.
- [ ] **Step 3:** Home clock: reuse `DreamView.Glass` rendering for the home screen clock (extract to `GlassClock` only if HomeView cannot call it as is; one class, `GlassClock.render(String text, ...) -> Bitmap`).
- [ ] **Step 4:** Check in the screensaver and home: digits readable on light and dark photos; 12 h and 24 h; minute rollover redraws within one second.
- [ ] **Step 5: Commit.**

### Task 6: Scene assets (build tool and files)

**Files:**
- Create: `tools/make_scenes.py`, `app/assets/scenes/<name>.jpg`, `<name>_glass.jpg`, `<name>_fg.webp` for `night, golden, water, fog, dawn`, `app/assets/scenes/CREDITS.txt`

**Interfaces:**
- Produces: `tools/make_scenes.py <couch-tv-scene-dir> <out-dir>` writes per scene: 1920x1080 JPEG (quality 85), a 480x270 blurred glass copy (the size `Backdrop.GW x GH` expects), and a foreground cut-out WebP if the source has one.

- [ ] **Step 1:** Locate the five generated scenes in `~/couch-tv` (`find ~/couch-tv -iname '*golden*'`). Record the paths in `tools/make_scenes.py`'s docstring. If their license text is not in the repo, record where the "use however you like" statement is in `CREDITS.txt`.
- [ ] **Step 2:** Implement the tool (Pillow). Check: each output exists; sizes are exactly 1920x1080 and 480x270; total of `app/assets/scenes` is under 8 MB (`du -sh`).
- [ ] **Step 3:** Run it, then `./build.sh`; APK size grows by roughly that amount.
- [ ] **Step 4: Commit.**

### Task 7: SceneCycle and SceneLayer

**Files:**
- Create: `SceneCycle.java` (pure, no Android imports), `SceneLayer.java`, `tools/test/SceneCycleTest.java`

**Interfaces:**
- Produces: `SceneCycle(int count, long periodMs)` with `int indexAt(long elapsedMs)`, `int nextIndex(int i)`, `float fade(long elapsedMs)` (0..1 over the last 1500 ms of each period, else 0). `SceneLayer(Context c)` with `void attach(int uiScale)`, `void draw(Canvas c, long nowMs)`, `void drawForeground(Canvas c)`, `void trim()` (drop bitmaps), `Bitmap glassCopy()` (current scene's glass copy for the clock fill).
- Consumes: scene files from Task 6 via `AssetManager`.

- [ ] **Step 1:** Write `SceneCycleTest`: `new SceneCycle(5, 20000)`: `indexAt(0)==0`; `indexAt(20000)==1`; `indexAt(100000)==0` (wraps); `fade(18000)==0`; `fade(19999)>0.99`; `nextIndex(4)==0`.
- [ ] **Step 2:** Run `javac tools/test/SceneCycleTest.java SceneCycle.java && java -cp . SceneCycleTest`; expected FAIL.
- [ ] **Step 3:** Implement `SceneCycle`. Run the test; expected PASS.
- [ ] **Step 4:** Implement `SceneLayer`: load JPEG as `Bitmap.Config.HARDWARE`, hold current and (only while fading) next; `trim()` recycles; a missing or undecodable file skips that scene and logs via `AppLog.w`; with no scenes available `draw` does nothing (caller falls back to the aurora).
- [ ] **Step 5:** Build. Commit.

### Task 8: Draw order and integration

**Files:**
- Modify: `DreamView.java`, `HomeView.java`

**Interfaces:**
- Consumes: `SceneLayer` from Task 7, `GlassClock`/`DreamView.Glass` from Task 5.

- [ ] **Step 1:** In DreamView and in HomeView's background draw, when scenes are on: `SceneLayer.draw`, glass clock using `SceneLayer.glassCopy()`, `SceneLayer.drawForeground`, dimming scrim, then the UI. When scenes are off or unavailable: existing behaviour.
- [ ] **Step 2:** Call `SceneLayer.trim()` from `onTrimMemory` and on detach; confirm it redraws after.
- [ ] **Step 3:** Install; watch two full cycles (40 s) on the TV; check the crossfade at 20 s and the clock stays correct across it. Check `adb shell dumpsys meminfo <pkg>` once at start, once after 10 minutes; record both in `docs/test-notes.md`.
- [ ] **Step 4: Commit.**

### Task 9: "Scenes" setting and strings

**Files:**
- Modify: `Prefs.java`, `SettingsActivity.java`, `app/assets/i18n/*.json` (all 28), `app/res/values/strings.xml` if used

**Interfaces:**
- Produces: `Prefs.scenes() -> boolean` (default true), `Prefs.setScenes(boolean)`; a settings row "Scenes" with a switch.

- [ ] **Step 1:** Add the pref and the row beside the existing background option; use the existing row helper.
- [ ] **Step 2:** Add the key `Scenes` and `Animated wallpaper scenes` to every i18n JSON with a translation (English fallback text allowed; owner can improve later), via a Python script that loads and dumps UTF-8 with `ensure_ascii=False`.
- [ ] **Step 3:** Verify: every i18n file parses, contains both keys, and the app starts in Arabic without showing a raw key.
- [ ] **Step 4: Commit.**

### Task 10: Test the look on the Skyworth

**Files:**
- Create/Modify: `docs/test-notes.md`

- [ ] **Step 1:** Run for an hour with scenes on. Record `dumpsys meminfo` at 0, 30, 60 min. Pass: no growth of more than 50 MB; no OOM in `adb logcat -b crash`.
- [ ] **Step 2:** Focus moves across shelf and drawer: no visible stutter. If jank, switch to the lite glass path and re-test; note the result.
- [ ] **Step 3:** Remote keys, screensaver start/stop, settings open/close, 12/24 h clock.
- [ ] **Step 4:** Report to the owner; get a go/no-go before Task 11. **Stop here if the look is not acceptable.**

### Task 11: Safe home switch (A)

**Files:**
- Create: `HomeSwitch.java`
- Modify: `HomeMode.java` (the disable path near lines 621-690; the guard around `GOOGLE_HOME` and `GOOGLE_SETUP` at lines 96-101), `Watchdog.java`

**Interfaces:**
- Produces: `HomeSwitch.enable(Adb adb) -> boolean` runs `cmd package set-home-activity <pkg>/<HomeActivity>` and `appops set <pkg> AUTO_START allow` (or `APP_AUTO_START` if the first fails), and enables the Home-button watch accessibility service; `HomeSwitch.restore(Adb adb)` re-applies set-home-activity (for after app installs); `HomeSwitch.original(Adb adb)` clears our home and leaves the stock one. None of them calls `pm disable-user`.
- Consumes: `Adb.shell(String) -> String` (existing).

- [ ] **Step 1:** Write a JVM test with a fake `Adb` recording shell commands: `enable` issues the set-home-activity and AUTO_START commands and never a command containing `disable-user`; `original` issues no `disable-user` either; if the first appop fails, the second runs.
- [ ] **Step 2:** Run it; expected FAIL.
- [ ] **Step 3:** Implement `HomeSwitch`. Run the test; expected PASS.
- [ ] **Step 4:** In `HomeMode`, route the Home Screen switch through `HomeSwitch`; remove the Wi-Fi-wipe refusal for this path (it exists because of `pm disable-user` of the setup wizard, which this path never does). Keep the old code path reachable only behind an explicit "Upstream mode" setting (Option B fallback).
- [ ] **Step 5:** `Watchdog`: when the Google home comes to front, bring ours back within 300 ms; after any package install/remove broadcast, call `HomeSwitch.restore`.
- [ ] **Step 6:** Build, install, switch on the TV. Verify: `adb shell pm list packages -d` shows neither `launcherx` nor `setupwraith`; Home press from Netflix/YouTube returns to ours (record flash duration by eye or `adb shell dumpsys activity activities | grep mResumed`).
- [ ] **Step 7: Commit.**

### Task 12: Boot and recovery tests

**Files:**
- Modify: `docs/test-notes.md`

- [ ] **Step 1:** Cold boot the TV; confirm it reaches our home (or the stock home with ours one Home press away) and Wi-Fi reconnects. Do this with `adb disconnect` done and the TV within reach.
- [ ] **Step 2:** Home press from Netflix and YouTube, five times each; record any flash of the stock home.
- [ ] **Step 3:** Install and uninstall any small app; confirm `HomeSwitch.restore` brings the home back.
- [ ] **Step 4:** Check port 5555 after reboot; reconnect via Wireless debugging and `adb tcpip 5555`; confirm `Original` restores the stock home.
- [ ] **Step 5:** If the flash is unacceptable, write the owner a short note offering Option B with undo ready; do not switch to B without their yes.
- [ ] **Step 6: Commit** `docs/test-notes.md`.

---

## Self-review notes

- Spec coverage: goals map to Tasks 2-5 (look, motion, glass, clock), 6-9 (scenes), 11 (safe switch), 10 and 12 (tests, anti-goals). Non-goals appear only as exclusions.
- Types: `SceneLayer.glassCopy()` (Task 7) is consumed in Task 8; `Ui.antonio` and `Ui.Tokens` (Task 2) are consumed in Task 5; `HomeSwitch.restore` (Task 11) is consumed by `Watchdog` in the same task.
- Estimates in `PLAN.md` may move once `HomeView.java` is read; Task 5 and 8 are where that risk sits.
