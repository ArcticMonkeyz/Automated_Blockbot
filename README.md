# Rock Block Bot v3.8 — adjustable tall-piece calibration

**Purpose:** Fix a v3.7 bug where tall pieces ignored the Y +/- buttons because their release Y was forced to zero. The yellow 3-row piece visibly traveled above the board and snapped back (0 new board cells); a 5-high piece previously ended too low with the old full +484px offset. The right release bias is not yet proven for every shape.

## Changes from v3.7

- Every piece now responds to the **global X/Y** controls. The v3.7 `TALL DIRECT GRIP` / forced-Y-zero rule is removed.
- For pieces with **height >= 3 and height > width**, a separately adjustable **Tall Y delta** applies *in addition to* global Y and any shape-specific correction. Horizontal/short-piece alignment stays unchanged.
- Initial tall delta is **-240 screen pixels**, a cautious unverified midpoint between the previous too-high 0 and too-low +484 release offsets. With saved global Y +484, the effective tall Y starts at **+244 px** (before footprint-specific corrections).
- Tap **SHOW CAL** to reveal global **X +/-**, **Y +/-**, and **TALL Y +/-** (20 screen-pixel steps). Settings persist across sessions.
- PREVIEW now shows the **exact pickup and release finger coordinates in screen pixels**, as well as effective X/Y. The red crosshair is the *finger release point*, not the intended block placement. Green squares remain the planned board cells.
- The latest report includes a persistent **Last touch path**, allowing investigation even after STOP.
- Preserves v3.6 colored-piece recognition, v3.5/3.6 shape-specific corrections, existing solver, explicit PREVIEW -> NEXT MOVE workflow, and 350ms hold.

## Recommended test

1. Extract the ZIP and copy its files into your existing GitHub Desktop repository; **preserve `.git` and `.github`**.
2. Commit/push and run the existing **Build Debug APK** workflow in GitHub Actions. Install the new APK, keeping saved **X=84, Y=484** unless you have intentionally changed those values.
3. In Android, choose **Share a single app -> Rock Block Saga**. With 3 new tray pieces, tap PREVIEW (not RESUME).
4. Confirm **every highlighted green square** matches the full physical piece. For the yellow `##/#./#.` or purple 5-high vertical piece, inspect the red crosshair and the new pickup/release coordinate label.
5. If the shape is correct and the crosshair is plausibly located, test **one** NEXT MOVE, wait **at least 7 seconds** without stopping capture, and check whether the piece actually lands. If it fails or seems too high or low, **STOP**, and send the preview, settled board screenshot, and Last placement check / Last touch path. Do **not** automatically retry.
6. To tune the *next* tall piece, press SHOW CAL, then use **TALL Y +** to move the release finger **down** by 20 pixels, or **TALL Y -** to move it **up**. The **Y +/-** buttons always affect both tall and non-tall pieces. Any adjustment updates the red preview marker immediately, but never triggers a drag itself.

**Safety note:** The new tall offset is an estimate, not a verified calibration. The on-device behavior may depend on shape and game drag snapping. Keep manual confirmation enabled and do not run unattended.

## Testing

`kotlinc app/src/main/java/com/example/rockblockbot/PlacementGeometry.kt tests/CalibrationTest.kt -include-runtime -d /tmp/blockbot-calibration.jar && java -jar /tmp/blockbot-calibration.jar`

Pure Kotlin geometry tests passed locally. Android SDK is not available in this environment, so GitHub Actions still needs to compile the APK and actual placements need phone testing.
