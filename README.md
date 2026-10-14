# Rock Block Bot v2.7 — Shape Identification and Preview Fix

This is an **experimental guided-move Android app**, not unattended gameplay. It is designed for the Pixel 7 Pro layout tested in this project. It requires the user's screen-capture, overlay, and accessibility permissions to perform a confirmed move.

## Fixes since v2.6

- **Four-block horizontal green piece:** Counts all four tiles, rather than reading `###`. Uses distinct vertical color bands when visible and checks that detected cells account for the full colored mask.
- **Five-block horizontal purple piece:** Fixes a separate regression in which the recognizer read `####` instead of `#####`. The left tray's crop now includes the entire piece; a seam-contrast check distinguishes small tiles that have little visible color separation.
- **Irregular pieces:** Preserves holes and orientation, e.g. `###/#..` and `#./##/.#`. The 41-shape library is used as a *tie-breaker*, not a strict whitelist, because some observed shapes are absent from the extracted list.
- **Entire-screen capture with overlay:** The game-presence check now samples an uncovered part of the blue background rather than the floating controls. Single-app sharing remains recommended and was the user's most reliable mode.
- **Responsive controls:** Moves image analysis off the Android UI thread, throttles frames, and freezes the recognized frame while PREVIEW is visible. PREVIEW, NEXT MOVE, and STOP should remain responsive during analysis.
- **Safety:** Requires all three pieces to be recognized at the start of a round (and the correct number after each verified placement). Rejects low-coverage or implausible shapes. Every move still needs PREVIEW then NEXT MOVE. If verification fails, further moves are blocked.
- **Calibration:** Keeps X=0 and Y=+400 as the default for a new installation; saved calibration takes precedence. Touch pickup remains anchored to an occupied tile in the lowest row, choosing the leftmost tile in that row. The overlay now identifies its row/column rather than ambiguously calling it the bottom-left of every shape.
- **Solver:** Keeps the survival-first, line-clearing and future-shape-mobility heuristics from v2.5. No continuous gameplay was added.

## Upload and build (GitHub Desktop)

1. Download and extract this ZIP on your PC.
2. In GitHub Desktop, open **Repository > Show in Explorer** for `ArcticMonkeyz/Automated_Blockbot`.
3. Copy **everything inside** this extracted project folder into the cloned repository, replacing matching files. Do not delete `.git` or `.github`.
4. Commit with summary `Blockbot v2.7 shape detection fix`, then **Push origin**.
5. Open the repository's GitHub Actions tab. When the workflow finishes successfully, download its APK artifact ZIP, extract `app-debug.apk`, and install it over the existing app.

## First test

1. Start capture. Prefer **Share a single app > Rock Block Saga**. If needed, try Entire screen as a separate test.
2. On a board with three available pieces, wait a few seconds, then tap **PREVIEW**.
3. Verify the displayed piece number, count, and orientation against the real piece. Green squares should show **every** occupied cell. A four-block horizontal piece must show `####`, not `###`.
4. **Do not press NEXT MOVE** if the preview differs from the actual piece. Press STOP and send the recognition report instead.
5. Only after the preview matches, confirm one NEXT MOVE, wait for VERIFIED, and inspect the board. Stop if UNVERIFIED.

## Validation and limits

- The recognizer and solver were compiled with a JVM Kotlin test harness (minimal Android graphics stubs) and tested on three prior user screenshots. The problematic four-block green piece was recognized as `####`; a previously misread five-block purple piece was recognized as `#####`; irregular four-cell shapes were also recognized. A mid-drag screenshot containing an ambiguous piece was deliberately rejected.
- The **Android APK was not compiled or run here**; GitHub Actions is the first Android build test. Screen-capture permissions, real-device gestures, and all possible game states remain unverified.
- The XAPK-derived 41-piece list is not guaranteed to contain every possible shape. Exact shape probabilities, scoring, and reward-program rules remain unknown.
- The app does not use privileged APIs or bypass Android permission prompts. Accessibility gestures are powerful; disable the accessibility service when not testing.
