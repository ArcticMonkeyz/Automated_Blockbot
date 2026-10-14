# Rock Block Bot v3.0 — one-cell placement calibration test

Experimental Android assistant for Rock Block Saga. Every placement requires manual PREVIEW, then NEXT MOVE confirmation. **No unattended gameplay.** The survival-focused solver and v2.9 independent tile recognition are unchanged.

## Why this version exists

In the Pixel 7 Pro screenshots, v2.9 correctly identified and picked up the green four-block horizontal strip. Its preview showed the destination at row 2, columns 2–5 (1-based), but the resulting placement was near row 1, columns 1–4. The user confirmed the piece was placed (not merely hovering). This is a **single observed displacement**, not proof all pieces have the exact same drag bias.

## v3.0 corrections

1. **Shift the finger-release calibration one full displayed board cell right and one down.** The pickup coordinate, previewed cells, gesture duration, and board solver are unchanged. The correction is computed from the Android full-screen display dimensions, not a hardcoded 84/86 px. On a 1080 x 2400 display, old X=0/Y=400 becomes approximately **X=84/Y=486**. This directly compensates for the one-cell left/up error observed in v2.9. It is an *initial test calibration*, not a guaranteed placement fix.
2. **One-time migration of existing saved offsets.** The app automatically applies the +1/+1 cell correction once when first run as v3.0, including after updating over v2.9. It will NOT apply it again on future starts. Updated offsets persist, and X/Y +/- buttons still adjust in 20px steps. The Y range is expanded from the old +400 ceiling to +900, so the correction is actually possible.
3. **Better post-release verification.** The bot requires a newly captured, settled game frame after releasing the piece. It waits up to 6 seconds; it stops rather than trying again when the result isn't verified. If it detects a simple displaced placement, the status proposes next-test offsets without silently applying them.
4. **Keep a readable error after STOP.** Main report now includes a separate "Last placement check" line, so stopping the service does not overwrite the result of the prior move.
5. **Resume partial trays explicitly.** If an earlier test already used one or two of the three pieces, use `RESUME 1/2 PIECES` before PREVIEW. This requires a stable detection of 1 or 2 pieces and your visual confirmation that *no additional tray piece is missing due to recognition errors*. Don't use it when all three pieces are actually present.

## Installation via GitHub Desktop

1. Extract the ZIP on your computer.
2. In GitHub Desktop, choose `Repository -> Show in Explorer` for `ArcticMonkeyz/Automated_Blockbot`.
3. Copy extracted project files into the existing cloned repo, replacing matching files. **Keep .git and .github intact.**
4. Commit `Blockbot v3.0 placement alignment calibration` and click `Push origin`.
5. Wait for the existing GitHub Actions workflow to build the Android debug APK; install the resulting APK over the existing version (same app package, so calibration migrates).

## First device test

1. Open game. Choose `Share a single app -> Rock Block Saga` when starting screen recognition.
2. Confirm overlay displays approximately `X 84px, Y 486px` on a 1080 x 2400 display (actual values depend on Android's reported display size). **Do not manually set back to X 0/Y 400.**
3. If your game still shows only two unused tray pieces from the earlier green placement, tap `RESUME 1/2 PIECES` first. If all three are present, don't tap it.
4. Tap PREVIEW and check the number of blocks, green destination cells, and blue pickup ring. Do **not** move if any shape is wrong.
5. Tap NEXT MOVE **once**. Wait for the overlay to finish verification. Screenshot the landed piece. If the app reports UNVERIFIED, STOP; do not attempt another move that session.
6. After STOP, send both the game screenshot and the Blockbot "Latest game report", including "Last placement check". Avoid manual offset changes until the actual displacement is confirmed.

## Offline checks and limitations

- `tests/CalibrationTest.kt`: Kotlin/JVM math check for a 1080x2400 display. One-cell correction = X +84, Y +86 px. Old 0/+400 -> 84/+486.
- v2.9 tile-recognition regression examples are retained in `tests/recognition_regression.txt`, including all three correct shapes in the user's latest screenshots.
- The Android APK has **not** been compiled or physically tested in this environment. The GitHub Actions build is still required. Placement behavior after the calibration change is unverified until the next on-phone test.
- This game-specific recognizer, coordinate reference and migration assume the same orientation/display settings as the reported Pixel 7 Pro run. If the game responds differently to a new piece type or screen layout, verification should block the next move and guide another calibration test.
