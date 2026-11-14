#!/usr/bin/env bash
# Builds the release APK: no keys, signed with the project's key, named MyOwnTV-<version>.apk, with its SHA-256.
#   RELEASE_KEYSTORE=<file> RELEASE_KEYSTORE_PASS=<password> tools/release.sh
# Needs the same key for every release: an APK signed with another key cannot update an installed one.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${RELEASE_KEYSTORE:?set RELEASE_KEYSTORE to the keystore file}"
: "${RELEASE_KEYSTORE_PASS:?set RELEASE_KEYSTORE_PASS to its password}"
VERSION="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' app/AndroidManifest.xml)"
[ -n "$VERSION" ] || { echo "versionName not found in app/AndroidManifest.xml"; exit 1; }
tools/test/run.sh
FREEMYTV_KEYSTORE="$RELEASE_KEYSTORE" FREEMYTV_KEYSTORE_PASS="$RELEASE_KEYSTORE_PASS" ./build.sh --generic
mkdir -p release
cp MyOwnTV.apk "release/MyOwnTV-$VERSION.apk"
(cd release && shasum -a 256 "MyOwnTV-$VERSION.apk" > "MyOwnTV-$VERSION.apk.sha256")
echo "release/MyOwnTV-$VERSION.apk"
cat "release/MyOwnTV-$VERSION.apk.sha256"
