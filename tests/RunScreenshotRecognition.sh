#!/bin/sh
set -eu
if [ "$#" -eq 0 ]; then echo "Usage: sh tests/RunScreenshotRecognition.sh screenshot.png ..."; exit 2; fi
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TARGET="${TMPDIR:-/tmp}/blockbot43-image-tests.jar"
kotlinc "$ROOT/tests/HostBufferedBitmap.kt" \
 "$ROOT/app/src/main/java/com/example/rockblockbot/BoardRecognizer.kt" \
 "$ROOT/app/src/main/java/com/example/rockblockbot/ShapeLibrary.kt" \
 "$ROOT/app/src/main/java/com/example/rockblockbot/Solver.kt" \
 "$ROOT/tests/HostImageRecognition.kt" -include-runtime -d "$TARGET"
java -Djava.awt.headless=true -jar "$TARGET" "$@"
