# Rock Block Bot v3.5 - horizontal five-block placement correction

This is a small, targeted hotfix based on the two screenshots of the purple five-block strip (`#####`). In the first screenshot, **PREVIEW** correctly indicated five cells in the second board row. In the second, the real purple strip landed **one column to the right**. Recognition was correct; the game's snap/release positioning was not.

## Changes from v3.4

- `PlacementGeometry.shapeCorrectionCells("#####")` now returns `(-1, 0)` (one board-cell left release correction). At a display width of 1080, that means approximately -84 physical screen pixels relative to the v3.4 release endpoint.
- The global saved calibration **X=84, Y=484 is NOT changed or migrated**. In effect the `#####` footprint releases with X=0, Y=484; the green L `###/#..` keeps its earlier (-1,-1) shape-specific compensation; `####` stays (0,0).
- Recognition, 41+ shape library, board solving, single-app capture, compact controls, gesture duration, and verification rules are unchanged.
- Version incremented to `0.3.5` (versionCode 18) so the installed APK can be distinguished from v3.4.

## Known limitations

This is an **empirical shape-specific correction**, NOT proof that all pieces now land on the highlighted cells. Five-wide placement is inferred from screenshots; the post-release verification report should still be checked after an attempted move. The screenshots show a placement, but an exact completed-frame report was not supplied. The game may use shape-dependent pickup/snap behavior. Never alter the global X/Y based on a single shape, or press NEXT MOVE if the previewed count or footprint is wrong.

## Install and controlled test

1. Extract the ZIP, copy its project files over the corresponding files in your GitHub Desktop repository. Preserve the `.git` and `.github` folders.
2. Commit and push; wait for GitHub Actions **Build Debug APK** to finish successfully, then install v3.5.
3. Choose **Share a single app -> Rock Block Saga**. Begin with three visible pieces, and **do not press RESUME** if all three remain.
4. Tap **PREVIEW**, verify every highlighted tile and pickup marker matches the real selected piece. Never place if a block is missing.
5. On a correctly recognized horizontal `#####` only, test **NEXT MOVE once**, wait at least 7 seconds for verification, then STOP and send both the landed-board screenshot and the **Last placement check** report.
6. If the new tray doesn't contain a five-wide piece, don't assume other pieces are recalibrated. Other shapes keep their v3.4 behavior.

## Local validation

- `tests/CalibrationTest.kt` can be compiled with the pure `PlacementGeometry.kt` using the Kotlin CLI. It checks the existing red and green cases, vertical-five unchanged, and the new horizontal-five correction at a 1080x2400 display.
- ZIP contents/integrity can be checked with `unzip -t`.
- A full Android APK build is **not** performed locally; the GitHub Actions build is still required.
