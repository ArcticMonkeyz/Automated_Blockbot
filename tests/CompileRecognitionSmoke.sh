#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TARGET="${TMPDIR:-/tmp}/blockbot-recognition-smoke.jar"
kotlinc \
 "$ROOT/tests/GraphicsStubs.kt" \
 "$ROOT/app/src/main/java/com/example/rockblockbot/BoardRecognizer.kt" \
 "$ROOT/app/src/main/java/com/example/rockblockbot/Solver.kt" \
 "$ROOT/app/src/main/java/com/example/rockblockbot/ShapeLibrary.kt" \
 -d "$TARGET"
printf 'PASS: BoardRecognizer and its Kotlin dependencies compile (%s)\n' "$TARGET"
