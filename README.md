# RockBlockBot v4.4.10 — occluded-purple calibration

**Experimental Android source project. This is NOT a prebuilt APK and is NOT proven on the phone yet.** Build with your existing Android/GitHub Actions workflow, install the resulting APK as an update without clearing app data, then run a supervised CAL DRAG. Do not run unattended.

## What changed

The user's Oct 14 v4.4.9 diagnostic contains a 4-cell horizontal purple piece that hides three translucent placement-preview cells. The legacy shadow scanner saw only 1/4 and correctly refused to guess. The new `OccludedPurpleShadow` detector combines **three newly raised opaque purple squares** with **one exposed translucent preview square at the end**. It excludes any cells known occupied in the baseline board, so the purple block already on the board cannot count as a moving part. The real diagnostic frame is checked in `tests/RealOccludedPurpleTest.kt` and yields shadow anchor **row 2, column 4** (1-based), whereas the old detector rejected it.

**A screenshot by itself is insufficient to authorize placement.** Only the purple horizontal four-strip uses a new, one-stroke two-stop probe. The finger dwells at one safe spot, shifts exactly one board cell to another legal spot, dwells again and returns to the tray before releasing. The preview must move by the corresponding exact cell; at least two agreeing frames must occur at EACH stop; and the game board/tray must be verified unchanged afterward. Otherwise NO CALIBRATION is saved. If a safe second probe spot does not exist, CAL DRAG is blocked for this shape.

The single-stroke placement approach and previous shape calibration remain unchanged for all **other** shapes and colors. Older calibration for the purple four-strip is intentionally **not trusted** until it passes the two-stop check. The existing EXPORT DIAGNOSTICS button now saves both observed frames (`latest_probe.png` / `latest_probe_b.png`) with matching `.txt` analyses, when a two-stop probe was performed. Only the first pair appears for other shapes.

## How to test (supervised, phone)

1. Build a debug APK from this project using your existing GitHub repository/workflow. Install over the previous app, **do not uninstall or clear app data**.
2. Open Rock Block Saga and start capture in RockBlockBot. Use **single-app screen sharing**, overlay and accessibility permissions.
3. Tap **PREVIEW**; verify the tray piece, blue pickup tile and green placement cells are correct. If any mismatch, STOP.
4. Tap **CAL DRAG** once and wait for the ~5.7 second single uninterrupted out-two-stops-back gesture plus confirmation. This is intended to return the piece to its tray, but an accidental placement is still possible.
5. If the report says **CALIBRATED**, and the board and tray really are unchanged, you may review a new PREVIEW. Do **not** blindly tap NEXT MOVE; the final release is still open-loop and cannot be proven safe by offline screenshot tests.
6. If the report says NO CALIBRATION or UNVERIFIED, **do not use NEXT MOVE**. Open the app and choose **EXPORT DIAGNOSTICS (save ZIP to phone)**, then upload that ZIP for analysis. If only one screenshot is present, mention whether the probe was interrupted.

## Host-side regression checks

`sh tests/CompileRecognitionSmoke.sh`

`kotlinc app/src/main/java/com/example/rockblockbot/{ShadowTracker,OccludedPurpleShadow}.kt tests/{CellStub,RealOccludedPurpleTest}.kt -include-runtime -d /tmp/occlusion.jar`

`java -jar /tmp/occlusion.jar /path/to/your/latest_probe.png`

`kotlinc app/src/main/java/com/example/rockblockbot/{ShadowTracker,DualProbe,ProbeTiming}.kt tests/{CellStub,DualProbeTest}.kt -include-runtime -d /tmp/twostop.jar`

`java -jar /tmp/twostop.jar`

`kotlinc app/src/main/java/com/example/rockblockbot/{TouchService,ShadowTracker,DualProbe,ProbeTiming}.kt tests/{CellStub,TouchServiceStubs,TouchGraphicsStubs,TouchEventStubs,SingleStrokeTest}.kt -include-runtime -d /tmp/touch.jar`

`java -jar /tmp/touch.jar`

All listed host tests passed here. **Android Gradle APK compilation and on-device calibration performance have not been performed** in this environment.

### Limits

- Special occlusion reconstruction is implemented only for purple horizontal four-strips, where the supplied real frame supports it. Other shapes retain existing conservative detection.
- A prospective ghost can be detected at row 2 / column 4 in the supplied frame, but the proposed two-stop geometry has not yet been verified on live game frames.
- During an Android accessibility gesture, Android can cancel, and a game may snap or place a piece unexpectedly. Visual inspection remains essential.
- A valid calibration does not prove every later MOVE succeeds. NEXT MOVE uses an independent single stroke and post-release verification.
