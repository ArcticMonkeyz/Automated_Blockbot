# Rock Block Bot v2.6 - Shape Recognition and Touch Anchor Test

This release is a cautious diagnostic update for Pixel 7 Pro. It is NOT unattended gameplay.

## Problems identified in v2.5

- The recognizer assumed every piece tile was 42 reference pixels wide. Screenshots show the game sometimes draws smaller tray pieces, around 33-38 pixels per tile. This caused a four-cell piece such as `###/#..` to be misread as only three cells.
- The drag started from the bounding-box center, which can correspond to a different occupied tile on irregular pieces. The source and target now use the same **bottom-row, leftmost occupied tile** as a grip anchor.
- The previous version reset calibration to zero on some installs. This build defaults to **X=0, Y=+400** if no saved calibration exists. Existing saved values take precedence.

## Changes

- Searches piece-specific tile spacing from 31 to 45 reference pixels instead of assuming 42.
- Scores each candidate grid by colored-pixel coverage and refuses low-fit pieces.
- Shows the recognized shape and exact block count on the floating overlay. Compare with the actual piece before moving.
- Blue circle marks the selected bottom-left occupied grip tile; red crosshair marks the intended finger release; green squares mark the full planned shape.
- The source and destination finger coordinates now use the same occupied tile, not bounding-box centers.
- Preserves the hold/release gesture and existing survival-oriented multi-move solver.
- If placement verification fails, stops and reports whether the observed new blocks are a simple row/column translation, with a suggested calibration for a *future* test. It does not automatically change offsets or retry.

## Build/update from GitHub Desktop

1. Extract this ZIP on a PC.
2. Open your existing `Automated_Blockbot` checkout in GitHub Desktop (Repository > Show in Explorer).
3. Copy the CONTENTS of this extracted folder into the checkout, replacing matching files. Preserve `.git` and `.github`.
4. Commit to main and Push origin.
5. Check GitHub Actions. Download the debug APK artifact from a successful build and install over the existing app.

## Test procedure

1. Start capture with Entire screen, open an active puzzle, and tap PREVIEW.
2. Confirm the floating piece shape and block count match the actual piece. Green squares must show every tile, not just some.
3. Check calibration. X=0, Y=+400 is the observed starting point, but a changed grip anchor may require further adjustment.
4. Only if the shape is correct, tap NEXT MOVE once. Inspect the result and the verification report.
5. If UNVERIFIED, STOP. Do not attempt further moves that session. Send a screenshot of the game and the app report.

## Limits

- Android APK has NOT been compiled or run in this environment. GitHub Actions is the build verification step.
- Piece shape fitting has been checked against a screenshot with a three-across, one-below green piece. Other colors and shapes require device tests.
- An accessibility gesture completing does not prove the game accepted it.
- Touch offset is device and game-layout specific. The new bottom-left anchor is an experimental improvement, not a proven universal calibration.
- The shape library is a future-mobility heuristic; exact piece probabilities are unknown.
- Game and reward program rules may limit automation.
