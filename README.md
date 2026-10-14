# Rock Block Bot v2.5 — Guided Multi-Move + Shape-Aware Solver

Android application for Pixel 7 Pro and similar devices. This is a **test build**: it is not unattended gameplay.

## Changes since v2.4

- Re-plans from the currently visible 1, 2, or 3 pieces. After one piece is placed, the remaining pieces retain their original screen slot IDs.
- PREVIEW shows planned cells and the finger release location; NEXT MOVE requires an explicit tap for each placement.
- Verifies each move by checking the expected board AND the disappearance of the selected piece slot (except when a fresh set of three appears after the final piece). Failed verification locks further moves until the next capture session.
- Uses a bounded beam search to evaluate possible piece orders and placements, rewarding line clears and consecutive clears while penalizing crowded, isolated boards.
- Includes 41 fixed-orientation piece footprints from Rock Block Saga v1.1.45 asset analysis to evaluate **future board flexibility**. Exact appearance probabilities and game scoring rules are not known, so the future-piece score is a heuristic, not a guarantee.
- Retains calibration settings from previous versions, including X=0 and Y=+400 if those were saved on the same installation.
- Adjusts cyan/blue block recognition to avoid missing blue pieces on the board while rejecting teal background water.
- Keeps the 350 ms gesture hold/release used in the successful tests.

## Install/build

Keep your existing `.github/workflows/build-apk.yml` in the GitHub repository. Extract this ZIP, copy the **contents** into the existing `Automated_Blockbot` GitHub Desktop checkout, preserving `.git` and `.github`, then Commit to main and Push origin. Download the resulting debug APK from GitHub Actions > Artifacts. The Android APK has **not been compiled or tested in this environment**.

## Test safely

1. Enable floating overlay and accessibility permissions (if Android disabled them after updating).
2. Start screen recognition and choose **Entire screen**. Open an active Rock Block Saga board.
3. Wait for stable recognition; tap **PREVIEW**. Inspect the proposed cells and adjust X/Y if necessary.
4. Tap **NEXT MOVE** once. Wait for **VERIFIED** before tapping PREVIEW again.
5. Repeat for the remaining visible pieces. If you see **UNVERIFIED**, STOP and inspect the board; the app will not make more moves in that session.
6. After three pieces are placed, wait for the next set to appear and tap PREVIEW again. There is **no automatic loop** in v2.5.

## Limitations

- The board recognizer uses calibration from a 709x1536 reference game screenshot, scaled to the captured image. Display zoom, different layouts, or different screen sharing modes may break detection or dragging.
- Some shapes or colors may still be recognized incorrectly. Check PREVIEW before each placement.
- Piece-generation weights, combo formulas, and special level mechanics are not fully reverse-engineered.
- The solver uses a bounded beam search, not a proven optimal or clairvoyant policy. It cannot know the next three random pieces.
- Android reports gesture completion independently from whether the game accepts a placement; board verification is required.
- Automation may be restricted by the game's or Ero's reward rules.
