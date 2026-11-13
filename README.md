# myowntv

A home-screen app for Android TV / Google TV, forked from [Free My TV](https://github.com/simbasjungles/free-my-tv)
(MIT) and being restyled with the look of [Couch TV](https://github.com/Seagey/couch-tv) (MIT).

**Status: early scaffold. It has not been compiled or run on a TV yet.** See [PLAN.md](PLAN.md) for the phases,
the design values and the open risks, and [NOTICE.md](NOTICE.md) for credits.

Placeholder names (`dev.tvhome.fork`, "TV Home") are still in the code and are listed in PLAN.md.

## Build (on a computer)
Needs JDK 17+ and the Android SDK (build-tools 34, platform android-34).

```bash
./build.sh                         # first run creates config.properties; then run it again
adb connect <TV IP>:5555
adb install -r TVHome.apk
```

Do not commit `config.properties`, `*.keystore` or API keys (they are in `.gitignore`).
