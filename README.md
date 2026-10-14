# Rock Block Bot v4.4.1 — Board-entry + continuous held-touch trial

**Experimental. Not yet verified on a real phone. Do not use unattended.**

This version addresses the two separate problems in the v4.4 screenshots: green 2×2 squares were successfully identified by four cyan tile markers, but the initial drag traveled only `(512,1804) -> (499,1762)` (still inside the tray area) and a later Android continued segment was cancelled. Since the game displays its translucent placement shadow only near/over the board, there was no usable shadow to track.

## What changed

1. **Board-entry on the very first stroke:** The old calibrated finger endpoint is treated as an initial guess. If it's outside the board's playable rectangle, it is **clamped into the board**. For the specific failed screenshot `(499,1762)` becomes approximately `(499,1154)` on a 1080×2169 display. The first 1250 ms stroke moves there while holding the finger. This is **NOT** an automatic release.
2. **TouchService now owns the continuous hold:** Android's `StrokeDescription.continueStroke` is called *immediately* from each segment completion callback. A new stationary segment is dispatched before any UI or image-processing callback is run. The main app queues position changes or a final release, rather than dispatching held segments itself after delayed screen processing.
3. **Visual controller:** MediaProjection still samples the game's matching-color translucent placement shadow and compares it to Blockbot's green planned board cells. Alignment must be seen twice in distinct fresh frames before a release is queued. Offsets trigger small corrective movements. A missing/ambiguous shadow never authorizes release.
4. **No guessing from legacy shapes:** The PREVIEW is blocked when any recognized tray piece lacks an individual-tile-center count matching its reported block count. The earlier green 2×2 vs 6-block legacy fallback must not become a move.
5. **STOP/abort:** An attempted return-to-tray is prioritized over pending corrective movements. If Android cancels the touch, the piece may release; inspect the board and do not automatically retry.
6. The survival solver, existing tile-center recognizer, manually confirmed PREVIEW/NEXT MOVE flow, original per-piece saved calibration, game-shadow tracker and post-release verification remain present. Previous global X/Y values are only used to choose the initial approach and **never authorize release**.

## Safe test

1. Extract this ZIP and copy **its contents** to your existing GitHub Desktop clone, replacing matching files but retaining hidden `.git` and `.github` folders.
2. Commit, push, build the APK through your existing GitHub Actions workflow and install it.
3. Use **Share a single app > Rock Block Saga**. Press **PREVIEW**, check the cyan circles and green target squares. **Do not press NEXT MOVE** for any wrong piece count/shape.
4. For one correct preview, press **NEXT MOVE once**. Do not touch the game during the 20–30 second experiment. The piece should **travel toward/over the board immediately** instead of moving only 40px in the tray.
5. If shadow alignment succeeds, Blockbot releases and verifies. Otherwise it attempts to return the held piece and stops with **UNVERIFIED**. A cancelled Android gesture is *not* proof of placement or recovery.
6. If it fails, press STOP after movement has ended and send the overlay and the report lines **Last touch path**, **Last game shadow tracking**, **Last placement check**. These distinguish a gesture failure from an invisible shadow.

**Limitations:** Android kept-stroke continuity and the live game's shadow colors cannot be fully validated without the phone. Synthetic shadow tracking passes for all five colors, but the real red sample was partially occluded and remains intentionally inconclusive. The code's held-touch lifecycle passed a local simulated Android callback test, not a hardware test. The full APK must be built via GitHub Actions.

## Local checks

- `sh tests/CompileRecognitionSmoke.sh`
- `sh tests/RunScreenshotRecognition.sh your_game_screenshot.png`
- `kotlinc app/src/main/java/com/example/rockblockbot/DragApproach.kt tests/DragApproachTest.kt -include-runtime -d /tmp/approach.jar && java -jar /tmp/approach.jar`
- `kotlinc app/src/main/java/com/example/rockblockbot/TouchService.kt tests/TouchServiceStubs.kt tests/TouchGraphicsStubs.kt tests/TouchEventStubs.kt tests/TouchSequencerTest.kt -include-runtime -d /tmp/touch.jar && java -jar /tmp/touch.jar`
