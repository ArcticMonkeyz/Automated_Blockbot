# Rock Block Bot v3.9 — per-piece calibration

Android guided puzzle solver for Rock Block Saga. Every gesture still requires your confirmation. **Not tested on the physical device yet; use controlled one-move trials.**

## What's new in v3.9

- **Per-piece persisted offsets.** Each exact shape + color has its own `PIECE X−`, `PIECE X+`, `PIECE Y−`, and `PIECE Y+` buttons under **SHOW CAL**, available after PREVIEW. Each tap changes the release endpoint by 20 real screen pixels for **this shape and color only**. Values survive app restarts.
- **Keeps the calibrated global X/Y and tall delta.** Existing installations keep their saved defaults, including X=+84, Y=+484, Tall delta=−240px. Previously configured offsets continue to work for pieces not explicitly corrected.
- **Measured purple five-vertical preset.** Shape `#/#/#/#/#` in PURPLE starts with a one-cell release correction **left and down** compared with v3.8; matches the +1 column / −1 row displacement in the previous test. This is still experimental. Yellow `##/#./#.` (the previously verified 3-row yellow L) is unchanged. The earlier green L and purple five-horizontal presets remain.
- **Unexpected clear diagnostics.** Verification now searches possible final boards from legal placements, including row/column clears, instead of only counting newly occupied squares. If one alternative location uniquely explains the board, the report suggests a correction for that piece only; it does not silently apply corrections.
- **Floating verification status.** A VERIFIED or UNVERIFIED result now replaces the 'Wait for verification' message on the overlay. After moving, allow at least 8 seconds for game animation and post-release captures.
- **No changes** to piece recognition, the survival solver, or drag timing.

## How to install

1. Extract the ZIP. In GitHub Desktop, open the existing repository (Repository → Show in Explorer).
2. Copy extracted **contents** into the repo, replacing matching files. Keep `.git` and `.github` untouched, including the existing GitHub Actions APK build workflow.
3. Commit and push; after the debug APK build succeeds in GitHub Actions, install it over the older APK.
4. Start screen capture; choose **Share a single app → Rock Block Saga**.
5. Press PREVIEW with all physically visible pieces recognized. Check green cells and blue pickup circle before every NEXT MOVE.
6. To tune one piece, open SHOW CAL *after* PREVIEW and use the **PIECE X/Y** buttons; the exact pickup and release coordinates update instantly. These changes are specific to the shape + color currently previewed. Global X/Y and TALL Y are still present.
7. NEXT MOVE only after the preview looks correct. Wait for VERIFIED/UNVERIFIED. If UNVERIFIED, STOP and inspect the report; don't keep moving pieces. After a verified move, press PREVIEW again.

**Important:** The per-piece preset is empirically based on two phone screenshots, not a guarantee. Shapes not measured yet use the current working general/tall calibration. A valid-looking preview alone doesn't prove release alignment; test each new shape carefully.

## Local tests

From this source root:

```bash
kotlinc app/src/main/java/com/example/rockblockbot/{PlacementGeometry.kt,PieceCalibration.kt,PlacementDiagnostics.kt,Solver.kt,ShapeLibrary.kt} tests/PerPieceCalibrationTest.kt -include-runtime -d /tmp/blockbot39-tests.jar
java -jar /tmp/blockbot39-tests.jar
```

The local tests validate calculation and board-transition logic, not Android's overlay UI or AccessibilityService gesture dispatch. Android SDK/Gradle build needs GitHub Actions.
