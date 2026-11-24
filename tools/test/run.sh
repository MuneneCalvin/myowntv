#!/bin/sh
# All host-side tests (no Android needed): Tokens, Spring.easeOut, SceneCycle, scene assets, i18n catalogues.
set -e
cd "$(dirname "$0")/../.."
S=app/src/com/shawnxe7en/myowntv
OUT=$(mktemp -d)
javac -d "$OUT" $S/SceneChoice.java tools/test/SceneChoiceTest.java $S/Mix.java tools/test/MixTest.java $S/ClearIcon.java tools/test/ClearIconTest.java $S/Tokens.java $S/Spring.java $S/SceneCycle.java $S/HomeSwitch.java tools/test/TokensTest.java tools/test/EaseTest.java tools/test/SceneCycleTest.java tools/test/HomeSwitchTest.java $S/WeatherFx.java tools/test/WeatherFxTest.java $S/Holidays.java tools/test/HolidaysTest.java $S/Sayings.java tools/test/SayingsTest.java $S/UpdateRules.java tools/test/UpdateRulesTest.java $S/Heartbeat.java tools/test/HeartbeatTest.java $S/MyList.java tools/test/MyListTest.java $S/NowPlayingText.java tools/test/NowPlayingTextTest.java
java -cp "$OUT" TokensTest
java -cp "$OUT" com.shawnxe7en.myowntv.EaseTest
java -cp "$OUT" com.shawnxe7en.myowntv.SceneCycleTest
java -cp "$OUT" com.shawnxe7en.myowntv.HomeSwitchTest
java -cp "$OUT" com.shawnxe7en.myowntv.ClearIconTest
java -cp "$OUT" com.shawnxe7en.myowntv.MixTest
java -cp "$OUT" com.shawnxe7en.myowntv.SceneChoiceTest
java -cp "$OUT" com.shawnxe7en.myowntv.WeatherFxTest
java -cp "$OUT" com.shawnxe7en.myowntv.HolidaysTest
java -cp "$OUT" com.shawnxe7en.myowntv.SayingsTest
java -cp "$OUT" com.shawnxe7en.myowntv.UpdateRulesTest
java -cp "$OUT" com.shawnxe7en.myowntv.HeartbeatTest
java -cp "$OUT" com.shawnxe7en.myowntv.MyListTest
java -cp "$OUT" com.shawnxe7en.myowntv.NowPlayingTextTest
tools/test/scenes_test.sh
python3 tools/test/i18n_test.py
rm -rf "$OUT"
