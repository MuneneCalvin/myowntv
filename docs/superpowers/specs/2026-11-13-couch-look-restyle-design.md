# Couch TV look on a Free My TV fork: design

Status: draft for approval. Builds on `PLAN.md` (phases, values and file pointers live there).

## Intent
Skyworth Google TV (Android TV 14, about 1.8 GB RAM, 32-bit) runs a light native home screen that looks like Couch TV: animated wallpaper scenes, a liquid-glass clock, the glass material and motion. Why: the owner liked Couch TV's look but it is heavy, flashes the Google TV home on Home press, and needs a bridge for its extras. Free My TV is light and native.

## Goals
- Free My TV's layout (picks, shelf, drawer) restyled with Couch TV's fonts, colours, spacing, motion and glass.
- Five bundled generated scenes (night, golden, water, fog, dawn) at 1080p, crossfading every 20 s, with the glass clock drawn between photo and foreground cut-out.
- Liquid-glass clock on the home screen and screensaver.
- Smooth on the Skyworth: lite path (no blur) for slow GPUs.
- Becomes the real home screen without disabling Google TV's home or setup wizard.

## Direction confirmed by the owner (2026-11-13, with a photo of the TV and Couch TV's home screenshots)
Copy from Couch TV only the HOME SCREEN LOOK: the scene wallpaper, the big glass clock behind the scenery (about 330 px
Antonio at 1080p, top about 172 px, the cut-out overlapping the bottom of the digits), and the app dock (rounded-square
app icons in a dark glass pill) plus the iOS feel. Everything else (layout, cards, widgets, settings, features) stays
Free My TV's. Home is the main target; the screensaver already has the scenes. Reference: `~/couch-tv/docs/screens/home-golden.jpg`,
`home-night.jpg`.

## Non-goals
- Phone remote, bridge, assistant, Keep Watching, Couch TV's layout (phone remote is a later, separate project).
- LG / Samsung / Fire TV.
- 4K scene copies; Wikimedia photos (CC BY-SA share-alike).

## Anti-goals (failure even if it runs)
- TV stutters on focus moves, or runs out of memory with scenes on.
- TV loses Wi-Fi or fails to boot after a restart.
- Home press regularly shows the old Google TV home.

## Constraints
- Nothing compiled yet; no Android SDK was available at scaffold. Phase 0 must get a first build.
- Placeholders (package id `dev.tvhome.fork`, name `TV Home`, keystore, `RELEASE_CERT`) replaced in one pass with a Unicode-safe tool (Python, not byte-wise sed).
- Scene assets are AI-generated, "use however you like" per Couch TV's credits; confirm before any public release.
- One scene held in memory at a time, as a hardware bitmap.

## Approaches considered (home-screen switch)
- A. Safe switch: no disabling of `launcherx`/`setupwraith`; set-home-activity plus Home-button watch. Safest; more work; possible brief flash.
- B. Upstream switch: no flash, least work; boot-loop risk reported by Couch TV docs; Wi-Fi-wipe guard bites on this TV.
- C. Look first, switch later: zero risk now; decision deferred.
- **Chosen: C then A.** Ship and judge the look as a normal app beside the original, then build A. If A flashes too much on this TV, fall back to B knowingly with undo ready. (Owner's choice.)

## Build order
1. Phase 0: rename pass, first build via `./build.sh`, install beside the original with adb. Home screen is NOT switched.
2. Phase 2: restyle (fonts incl. Antonio for clock digits, colours, spacing, motion, glass tint/blur, liquid-glass clock).
3. Phase 3: scenes (bundle five at 1080p, crossfade, cut-out layer after the clock, "Scenes" setting, i18n strings in all catalogs).
4. Phase 4a: test the look on the Skyworth (memory over an hour, focus smoothness, remote keys, screensaver).
5. Phase 1 (A): safe switch in `HomeMode.java`; AUTO_START appop; restore home activity after installs.
6. Phase 4b: cold boot, Home press from Netflix/YouTube, reboot and port 5555 recovery, `Original` restores stock home.

## Left to the builder
Exact blur radius and fallback thresholds, how the home clock integrates into the ~5,000-line `HomeView.java`, asset sizes, timing. Estimates in `PLAN.md` may move once `HomeView.java` is read.

## Open risks
`HomeView.java` not fully read; no compile yet; scene licence unconfirmed for public release; Home-button flash under A is unproven on this TV.
