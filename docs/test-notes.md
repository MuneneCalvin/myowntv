# Test notes

## Task 10: the look on the Skyworth (2026-11-13), automated part

Skyworth Google TV, Android TV 14, 1.8 GB RAM. MyOwnTV installed beside the stock launcher, NOT the home screen.
Screensaver preview (`SettingsActivity --ez screensaver true`) with Scenes on, 7 minutes (about 21 scene changes).
Process is `com.shawnxe7en.myowntv:home` (query that name, not the package name).

| Minute | PSS | Java heap | Native heap | Frames | Janky | p50 / p90 |
|---|---|---|---|---|---|---|
| 0 | 37.0 MB | 6.2 MB | 14.6 MB | 10 | 1 | 11 / 121 ms |
| 2 | 38.0 MB | 3.0 MB | 13.9 MB | 2521 | 14 (0.56%) | 10 / 11 ms |
| 4 | 37.9 MB | 2.9 MB | 14.0 MB | 5030 | 27 (0.54%) | 10 / 11 ms |
| 6 | 38.0 MB | 2.9 MB | 14.0 MB | 7595 | 42 (0.55%) | 10 / 11 ms |

- Memory flat after the first scene (no growth across scene changes). Scene bitmaps are GPU (HARDWARE) and do not show in PSS.
- No crash in `logcat -b crash`.
- Not covered: one-hour run, focus-move jank on Home, remote keys, 12/24 h change, reboot.

Pitfalls met while testing (so they are not repeated):
- Couch TV was still the active home and used 141% CPU / 378 MB; free RAM was 98 MB and the TV hung. Fixed with Couch TV's `undo.sh`.
- Google TV's ambient dream (`dreamx`) covers the preview after about 9 idle minutes; press Home first, then start the preview.
