#!/bin/sh
# All JVM-side tests (no Android needed): Tokens, Spring.easeOut, SceneCycle, scene assets.
set -e
cd "$(dirname "$0")/../.."
S=app/src/com/shawnxe7en/myowntv
OUT=$(mktemp -d)
javac -d "$OUT" $S/Tokens.java $S/Spring.java $S/SceneCycle.java tools/test/TokensTest.java tools/test/EaseTest.java tools/test/SceneCycleTest.java
java -cp "$OUT" TokensTest
java -cp "$OUT" com.shawnxe7en.myowntv.EaseTest
java -cp "$OUT" com.shawnxe7en.myowntv.SceneCycleTest
tools/test/scenes_test.sh
rm -rf "$OUT"
