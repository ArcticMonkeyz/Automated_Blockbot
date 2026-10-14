# Rock Block Bot v3.3 — missing-piece recognition and safe RESUME

Fixes based on the game screenshot and game report showing 3 tray pieces physically visible but only 2/3 recognized in v3.2.

## Diagnosis

Screenshot: Piece 1 is the red `###/..#` 4-cell piece; Piece 2 is a green `###/#..` 4-cell piece; Piece 3 is the yellow horizontal `##` 2-cell piece. Previous report showed Piece 1 NOT RECOGNIZED, so ordinary PREVIEW required all 3 and did nothing. The RESUME 1/2 button bypassed that requirement and planned using only two detected shapes. That would be unsafe for survival planning.

## Changes

- Better multi-cell grid ranking: refuse low-precision dense pseudo-grid fits, favor pitches consistent with the measured piece bounds, and modestly relax the fallback threshold for otherwise well-fit irregular shapes.
- Independent colored-pixel presence check for each tray slot. A physically visible but unrecognized piece cannot be treated as an empty/used slot via RESUME.
- The report distinguishes `NOT RECOGNIZED (colored piece still visible)` from `NOT RECOGNIZED (empty/uncertain)`.
- Full three-piece PREVIEW still requires 3 accurately identified shapes. RESUME is only for genuine partial trays (one or two physically remaining pieces).
- Unchanged: compact floating controls, X/Y calibration, known green-L shape correction, solver heuristics, gesture timing, manual confirmation, and post-placement verification.

## Test results and limits

Offline Kotlin screenshot tests on provided 709×1536 screenshot `1000010905.png`:
- Piece 1: `###/..#`, 4 cells
- Piece 2: `###/#..`, 4 cells
- Piece 3: `##`, 2 cells
- Board: 6 occupied squares

Additional screenshots correctly identified horizontal 4, vertical 4/5, purple 4-cell zigzag, and horizontal 2. Comparison against 58 historical screenshots found no other changed shape footprints aside from removal of a low-confidence dense false fit; some unrecognized pieces remain deliberately unrecognized.

**Caveat:** The previous detector reads the red shape from the posted PNG but did not read the red shape in the on-device MediaProjection report. The revised detector is more tolerant, but this is NOT yet proof that the device capture difference is fixed. If the red piece still fails, STOP and send the newest report; do not use RESUME to bypass it.

No complete Android APK build was run locally; build through existing GitHub Actions workflow and verify with a screenshot before dragging.

## Install

Extract ZIP; copy contents into the existing GitHub Desktop clone, replacing matching files; keep `.git` and `.github`. Commit and Push origin. Wait for Build Debug APK action to succeed, then install APK.

Open Rock Block Saga through Android `Share a single app` capture. Leave existing X=84, Y=484 calibration unchanged. Tap PREVIEW and check the three pieces and all green preview cells. Do not use RESUME with three visible pieces. Do not use NEXT MOVE until the preview is correct.
