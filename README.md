# Rock Block Bot v3.7 — tall-piece direct-grip placement trial

This update addresses a **placement failure**, not the recognition system.

## Evidence from v3.6 test

- At the start of the round, all three pieces were available: green horizontal 5, blue L-shaped 4 in the middle slot, and purple vertical 5. **The solver selected Piece 2 (blue) first.** That is intentional: the strategy can select any of the three, not just the leftmost piece.
- Blue `###/#..` was moved to the upper-right board area. The following report counted 10 occupied board cells, compared with the original six; the blue placement appears to have succeeded.
- The next preview correctly recognized Piece 3 (purple) as `#/#/#/#/#` (five vertical blocks). Its red finger-release crosshair was visibly below the board. Android completed the drag, but the settled report indicated **0 new cells** and the piece remained in its tray slot. It therefore did not place.

## Change

**For tall pieces (height >= 3 cells AND height > width):** compute the finger release by aligning the detected *pickup block* directly over its target tile on the board, without the +484px Y compensation used by shorter pieces. This moves the purple five-vertical preview release marker upward by 484 physical pixels on the tested setup. Short/horizontal pieces retain ALL prior calibration and shape-specific corrections. The saved global calibration remains X=84 Y=484.

This correction is geometry-driven, not a special-case piece-library entry. It also affects other taller-than-wide pieces, which must be tested separately. It is an **experimental placement fix**, not proof of accurate snapping; if a tall piece lands incorrectly, the bot stops rather than retrying.

If verification sees 0 new board cells after a no-clear placement, the report now explicitly says `NO PLACEMENT` and warns the piece may have snapped back instead of suggesting the piece was recognized with an incorrect number of cells.

## Offline checks

- `tests/CalibrationTest.kt` passes with `kotlinc` and tests the new tall-piece release calculation, plus the original 4-wide, purple 5-wide, yellow 5-wide, and green L corrections.
- Source package/ZIP verified. No Android SDK compilation in this environment; build in GitHub Actions.
- `tests/tall_grip_v37.txt` records calculation and expected visual changes.

## Install

1. Unzip and copy project files into the existing GitHub Desktop checkout, overwriting matching files; preserve `.git` and `.github`.
2. Commit, push, build **Debug APK** in GitHub Actions, and install the APK.
3. Choose **Share a single app → Rock Block Saga**. Keep global calibration **X=84, Y=484**.
4. With all three tray pieces visible, PREVIEW without RESUME. If the selected shape/count is correct, try one NEXT MOVE. Wait at least seven seconds, then inspect its final location and report.
5. For a **purple five-vertical** test, the red release crosshair should now be much closer to/inside the board than in v3.6. If the red crosshair is clearly misaligned with the planned preview, or the actual piece has a different shape, **do not press NEXT MOVE**. Send the preview screenshot.
6. If you test a movement, send both the preview, the settled board and the Last placement check report. Stop on `UNVERIFIED` or `NO PLACEMENT`.

## Still unresolved

Correct release alignment for every piece type is not established. Game-side touch anchoring has varied with shape. The app stays in manual PREVIEW → NEXT MOVE mode and blocks subsequent movements when post-drag verification fails. Do not assume unattended play works.
