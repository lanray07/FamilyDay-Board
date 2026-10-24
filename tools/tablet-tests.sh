#!/usr/bin/env bash
# Preserve genuine screen captures even when a different test fails.
set -uo pipefail
./gradlew connectedDebugAndroidTest --console=plain
test_status=$?
adb pull /sdcard/Android/data/com.familyday.board/files/screenshots captures || true
exit "$test_status"
