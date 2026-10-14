# Rock Block Bot v3.4 — four-block strip capture geometry fix

This update is based on the three screenshots where the left purple tray piece was visibly **four** blocks (`####`) but the live preview read **three** (`###`), then dragged the real four-block piece farther right than the three-cell preview.

## Reproduced issue and fix

- The v3.3 detector inferred the horizontal strip count from its colored bounding width divided by its colored height. On a slightly vertically stretched capture, a 4×1 strip looked like 3 oversized cells.
- We reproduced this with the provided screenshot `1000010910.png` after stretching only the purple tray area by +8%, +15% and +22%. **v3.3 read `###` (3); v3.4 reads `####` (4)** for each case, and also recognizes the correct 4-block strip at +30% stretch. Higher distortion can be rejected rather than guessed.
- v3.4 counts dark vertical tile seams directly (alongside color coverage), with a bounded tile width. A fallback shape must fit the actual colored bounding box rather than ignore an extra tile-width segment.
- A valid five-block purple strip on `1000010843.png` / `1000010845.png` is now recognized as `#####` instead of incorrectly `####`.
- A false-positive detection from the non-game menu screenshot is now rejected.
- Rechecked all 61 available historical screenshot PNGs: only the two legitimate 5-strip corrections and the menu false-positive changed; irregular pieces remain unchanged.

## What did NOT change

- Global saved calibration X=84px, Y=484px (if that is your current setting); no automatic or cumulative offset migration in this version.
- Green `###/#..` shape-specific (-1,-1) correction from v3.1; no new shape-specific correction is applied to the purple strip because the preview itself was already incorrect. Do not calibrate against a false preview.
- Survival solver, drag timing, accessibility permissions, board-grid geometry, and compact floating controls remain unchanged.
- PREVIEW / NEXT MOVE remain manual, with every real tray piece required to be recognized. RESUME may not skip a visible unknown piece.
- Placement report now says PENDING during the gesture; if STOP is pressed before verification completes, it explicitly says INCOMPLETE instead of falsely suggesting no attempted placement.

## Test boundaries

Host-JVM Kotlin/Java screenshot and calibration tests passed. The ZIP passes integrity checking; **the Android APK has NOT been compiled or tested locally**. Build with your existing GitHub Actions workflow, and verify the preview on your phone before any drag. The actual placement still needs on-device validation, especially for 2D shapes; do not assume recognition fixes calibrate all drag locations.

## Install/test

1. Extract this ZIP and copy the project files into your existing GitHub Desktop clone, replacing matching files. Leave `.git` and `.github` intact.
2. Commit, push, and wait for GitHub Actions **Build Debug APK** to succeed. Install the new APK.
3. Use **Share a single app → Rock Block Saga**. With three actual pieces, do NOT use RESUME.
4. Tap PREVIEW. The displayed shape count and green highlighted cells must equal the physical blocks. For a four-block horizontal piece it MUST say `#### (4 blocks)` and show four green board cells. If it says `###`, STOP.
5. Only then attempt ONE manual NEXT MOVE. Wait at least 7 seconds for post-release verification to finish, then STOP and send a screenshot of the game's final position and the **Last placement check** report.

Do not press NEXT MOVE on a known wrong preview or try to compensate for a wrong piece count by changing X/Y.
