# Rock Block Bot v4.4.2 — single-stroke shadow calibration

**Experimental hardware test. Do not use unattended.** This version addresses the repeated Android cancellation of **continued gesture segment 2** on the user's Pixel 7 Pro. The Android accessibility API supports continued gestures, but two live tests of this approach ended in cancellation and sometimes an unintended board placement. We therefore avoid continuation entirely for the first controlled alternative.

## Two-stage workflow

1. Share **Rock Block Saga only** when granting MediaProjection. Enable overlay and accessibility permissions.
2. Tap **PREVIEW**. Check all numbered cyan tile centers and the green target squares. The red crosshair shows a **probe entry point** if the piece is not yet calibrated; it is **not a validated release point**.
3. Tap **CAL DRAG** (not NEXT MOVE). A **single uninterrupted Android stroke** moves the piece into the board, oscillates within a few pixels while MediaProjection looks for its game-generated translucent shadow, then returns to the tray and releases. The result **may accidentally place** the piece; the app checks the actual board and tray afterward. Don't touch the game or press STOP during this probe.
4. A calibration is stored **only when** at least two independent frames agree on a shadow position, the board is unchanged, and the piece visibly remains in the tray. The report displays `CALIBRATED` or `NO CALIBRATION`/`UNVERIFIED PROBE`. A failed or uncertain probe does not enable a move.
5. With a successful probe, tap **PREVIEW** again. The red crosshair now shows the **measured release point**, which uses the game's own shadow position and the planned target (not the older per-piece guess). NEXT MOVE becomes available for that exact **shape+color and screen resolution**.
6. NEXT MOVE performs a **single uninterrupted, calibrated swipe** and releases. The game board is verified after release. This avoids the cancelled *second* continued stroke.

## Safety and limitations

- This is **not yet live closed-loop control during NEXT MOVE**. Android's continued gestures consistently cancelled on the test device, so we use a calibration pass followed by one single-stroke placement. Alignment is **predicted from the earlier ghost**, not rechecked immediately before release.
- A calibration probe is intended to return the piece to its tray, **but game snapping or an Android cancellation could place it unexpectedly**. The app checks for that; inspect the board before retrying.
- A ghost partly hidden by the floating piece may be too ambiguous to measure. The probe then refuses to save calibration. No blind move is enabled.
- One shape+color calibration is used only at the same display resolution. Other shapes require their own successful probe.
- No autonomous repeats. PREVIEW and NEXT MOVE still require the user's explicit confirmation every time.

## Build

Copy these project files into your existing GitHub Desktop clone, preserving `.git` and `.github`; commit and push to trigger your existing **Build debug APK** workflow. Install the debug APK over the existing app.

## Local checks

```sh
sh tests/CompileRecognitionSmoke.sh
kotlinc app/src/main/java/com/example/rockblockbot/TouchService.kt tests/TouchServiceStubs.kt tests/TouchGraphicsStubs.kt tests/TouchEventStubs.kt tests/SingleStrokeTest.kt -include-runtime -d /tmp/one-stroke.jar
java -jar /tmp/one-stroke.jar
```

**Full Android compilation and the game's real snapping behavior must be tested on the phone.** Local tests validate Kotlin touch calls and geometry logic, not platform gesture delivery or the game's visual response.
