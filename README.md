# Rock Block Bot v3.2 — compact controls

This is v3.1.1 with the floating controls moved to the very top of the usable Android window and reduced in height. The previous controls partially covered the first row of the game board, so their location was a possible source of interference with board recognition.

## Changes from v3.1.1

- Main PREVIEW / NEXT MOVE / STOP row remains visible at the top.
- RESUME 1/2 is preserved as a shorter button.
- X/Y adjustment buttons are hidden behind **SHOW X/Y**, with current offsets always visible. Tap SHOW X/Y to adjust, HIDE X/Y to collapse.
- Position is now `gravity=TOP|START, x=4, y=0` (previously x=8, y=45). Shape/status text is smaller and limited to three lines to stay above the board.
- Saved calibration, recognizer, solver, accessibility drag timing, and the existing green-L-specific correction are unchanged.
- The provided blue zigzag screenshots show a preview/drag mismatch, but both were captured while the display said **Moving one piece**. Do not infer a settled outcome or modify blue-piece calibration until the post-release report is available. Do not press another NEXT MOVE after an UNVERIFIED result.

## Install / test

Replace the matching files in your existing GitHub Desktop working tree; preserve `.git` and `.github`. Commit and push to use the existing GitHub Actions APK build. Install the new APK; enable overlay and accessibility services as necessary. Select **Share a single app → Rock Block Saga**.

Check that floating controls stay above the board. Tap PREVIEW to inspect all four blue-piece cells, then NEXT MOVE only if shape and preview are right. On any unverified move, STOP and send the **Last placement check** report. Keep the X=84, Y=484 saved offsets unchanged for now.

**Testing limits:** Kotlin calibration source test and structural checks only. An actual Android APK build and in-game placement must be validated via GitHub Actions and device.
