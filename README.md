# Rock Block Bot v2.9 — Independent Tile-Geometry Recognition

Experimental Android game assistant for Rock Block Saga. **Manual PREVIEW and NEXT MOVE confirmation required for every placement.** No unattended gameplay. Designed around the tested Pixel 7 Pro screen geometry; other phones may require recalibration.

## What's new in v2.9

- **Fixes horizontal-strip over/undercounting using colored tile cores:** the four-wide green strip on the latest user screenshot has a full colored bounding mask of about 160 × 51 reference pixels. The extra pixels at its top/bottom made the previous box-width/box-height estimator round 4 tiles down to 3. v2.9 measures the solid horizontal stripe (~40px thick), counts 4 segments using independently observed color, and rejects missing/weak segments. This works without requiring a matching pre-listed piece.
- **Avoids vertical-strip regressions:** preserved the existing vetted vertical detector after a test showed the new core thickness method could overcount a 5-block vertical line as 6.
- **Known shapes and unknown shapes:** screen recognition is independent of the 41-pattern XAPK library. The two confirmed on-screen irregular shapes `###/#..` and `#./##/.#` are included as *additional* potential shapes for the survival solver; a shape does not need to occur in this list to be visually detected.
- **Keeps strict recognition:** on a full initial tray all three pieces must be recognized before PREVIEW can issue a recommendation. Moves still require explicit user confirmation and board-verification. Ambiguous frames are rejected rather than silently moved.
- **No drag coordinate changes:** saved/default X=0px and Y=+400px endpoint calibration, bottom-row-leftmost occupied pickup anchor, the hold/drag timing, and the survival-focused solver are unchanged.
- **Screen-sharing:** Share a single app → Rock Block Saga is still recommended, because this was the most reliable selection on the user's phone.

## Build via GitHub Desktop

1. Extract this ZIP on the PC.
2. In GitHub Desktop use **Repository → Show in Explorer** for `ArcticMonkeyz/Automated_Blockbot`.
3. Copy the extracted project files into the repository, replacing matching files. **Keep the original `.git` and `.github` folders.**
4. Commit `Blockbot v2.9 independent tile geometry` and **Push origin**.
5. Wait for the repository's GitHub Actions Android APK build to succeed. Download its artifact, extract the APK and install.

## First phone test — please do NOT rush NEXT MOVE

1. Start screen recognition and share **the Rock Block Saga app**.
2. Wait 3–5 seconds, press PREVIEW.
3. Confirm green four-wide piece displays `####` with **four** preview squares, not `###` with three. Inspect the actual board occupancy and the selected piece.
4. If any preview shape is wrong, **do not press NEXT MOVE**; tap STOP and share the screenshot + Latest game report.
5. If correct, one confirmed NEXT MOVE is a separate placement test. Verify it landed exactly as previewed; stop if the app reports UNVERIFIED.

## Offline verification performed

- Kotlin compiler + minimal Android `Bitmap`/`Color` stubs; tested game image recognizer against 49 prior game screenshots and compared to v2.8.
- Specific screenshot assertions:
  - `1000010861.png`: Piece 1 `###/#..`, Piece 2 `#./##/.#`, Piece 3 `####` (all four green blocks).
  - `1000010849.png`: Same three pieces correctly detected.
  - `1000010843.png`: 4-horizontal, 5-vertical, irregular four-tile.
  - `1000010813.png`: 5-vertical, 4-vertical, 2x2.
  - `1000010844.png`: 5-horizontal purple and 5-vertical yellow.
- Only four of 49 historical screenshots changed identification relative to v2.8; all four changes were consistent with the visible tile counts. Some historical screenshots depict dragging/overlays and appropriately may fail full-tray detection.

**Limitations:** The Android APK has not been built or tested on a physical phone in this environment; GitHub Actions is still needed. Automated gestures and calibration remain unverified for v2.9. Shape detection remains game-layout-specific, and the XAPK library isn't claimed to include every shape or true generation probabilities. No auto-play was added.
