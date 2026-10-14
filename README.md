# Rock Block Bot v3.1.1 - Android compilation hotfix

This is v3.1 with the Kotlin build failure corrected. In CaptureService.kt, the PREVIEW status line referenced an undefined variable `plan` (GitHub Actions `:app:compileDebugKotlin` at line 350). That line now uses the properly scoped `observed` variable created from the selected piece. No movement, recognition, strategy, or calibration behavior was changed.

**Install:** extract this ZIP into your existing Automated_Blockbot repository, replacing matching files while preserving `.git` and `.github`; commit and push. GitHub Actions will compile and produce the APK. Keep app data when installing to preserve X/Y calibration.

**Verification:** The calibration Kotlin/JVM test was run. An Android build still needs to run in GitHub Actions, since the local environment lacks the Android SDK.

---

# Rock Block Bot v3.1 — shape-aware release and blue-piece recognition

Android source project for Rock Block Saga. Each move requires **PREVIEW**, a visual shape check, and **NEXT MOVE**. No unattended gameplay. Survival-focused solver and v2.9 direct tray-geometry recognition remain enabled.

## Confirmed v3.0 problem and v3.1 fix

The user tested two moves on Pixel 7 Pro:

- The horizontal red **`####`** piece landed at its intended preview position using global calibration **X=84, Y=484**.
- The green L **`###/#..`** piece was recognized and picked up correctly but landed **one column right and one row down** of its preview. v3.0's saved report confirmed the +1,+1 displacement and suggested effective X=0,Y=400 for a subsequent test of that shape.

**Do not change the global calibration.** v3.1 retains the device's existing global X/Y values for red horizontal and every other shape. Only the *observed green `###/#..` footprint* receives an extra **-1 column, -1 row** endpoint correction in screen-scaled cell pixels. On the observed device, its effective release offset is approximately X=0,Y=398 instead of 84,484; this is a controlled next-test hypothesis, **not** a confirmed physical placement yet. The pickup coordinate, drag timing, preview cells, and survival solver are unchanged.

The exact-footprint correction deliberately does **not** affect differently oriented L pieces or unknown irregular shapes. A per-shape verification error will still stop the session; the report now includes the **effective** rather than just global suggested offset.

## Remaining blue zigzag recognition

The v3.0 report after the green L incorrectly said `recognized 0/3 pieces` while a blue `#./##/.#` piece was visibly still in the tray. Reproduced against screenshot `1000010873.png` with offline Kotlin recognition: the algorithm matched blue-tinted sky/sea background and expanded the piece's bounding rectangle.

v3.1 rejects tiny disconnected patches of blue color and trims extreme color-mask tails before fitting the grid. The same screenshot now reports **Piece 3: `#./##/.#` (4 blocks)**; screenshot `1000010872.png` now reports both remaining pieces (`###/#..` and `#./##/.#`) instead of a distorted nine-block shape.

## Install/update via GitHub Desktop

1. Extract this ZIP.
2. Open `ArcticMonkeyz/Automated_Blockbot` using GitHub Desktop → Repository → Show in Explorer.
3. Copy extracted project files into the existing repository and replace matching files. **Preserve the existing `.git` and `.github` folders.**
4. Commit `Blockbot v3.1 shape-specific release + blue recognition`, then **Push origin**.
5. When GitHub Actions finishes, download/install its debug APK over the current app. Keep app data to preserve calibration and the v3.0 one-time migration flag.

## Device test

1. Use **Share a single app → Rock Block Saga**.
2. The global calibration should still show **X 84, Y 484** or the same saved values as in v3.0. **Do not reset them**. The preview text now includes the shape-specific correction; for `###/#..` it shows `(-1,-1)` cells.
3. If a new game has all three pieces, use PREVIEW directly. If the existing game has just the blue piece left, first use **RESUME 1/2 PIECES** after a stable screenshot, verify that the blue piece is recognized, then PREVIEW. Note: this blue piece has **not** yet had its physical landing calibrated. You may alternatively start a fresh game to test the corrected green L when it appears.
4. Check each preview's shape and location before NEXT MOVE. Test only **one** placement at a time, wait for VERIFIED/UNVERIFIED, and send both landing screenshot and saved report after STOP. No automatic retries.

## Offline tests

- `tests/CalibrationTest.kt`: pure Kotlin/JVM test for horizontal/green L offsets at reference 1080×2400 resolution and unchanged treatment of the blue zigzag.
- `tests/recognition_v31.txt`: screenshot regression results including `1000010873.png` (blue restored), `1000010872.png` (two remaining pieces), and earlier straight/irregular shapes.
- The project ZIP was tested for ZIP integrity. **This is not an Android APK**; no on-device placement success is claimed for v3.1 until tested.
