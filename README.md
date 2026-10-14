# Rock Block Bot v3.6 — color-separated full tray recognition

This is a **targeted recognition fix** after the yellow and green strips appeared side by side in the tray. The five-cell middle yellow strip was detected live as `####` (four cells), then the game actually placed **five** yellow cells. The app correctly reported `UNVERIFIED: Detected 5 new cells but expected 4.`

## What's fixed

- **No rigid per-slot cropping for oversized pieces.** The bot now uses narrow, nonoverlapping slot **seeds** to determine each tray piece's color, then widens its observation window only when substantial *matching-color* pixels appear beyond the old slot boundary. The photographed yellow strip extends outside the old middle-zone X=275..440 bounds, so its full length is included.
- **Touching but different-colored pieces stay separate.** The wider regions overlap by design, but the detected mask is restricted to the color found in that slot's seed. Thin antialiasing fringes from neighboring colors are ignored when a region must be widened.
- **Adjacent same-color pieces stay conservative.** The bot does not join them across the shared slot boundary. If a piece gets cut at a guarded boundary, it rejects the uncertain result rather than inventing a shorter shape.
- **Preserve reliable paths.** The old per-slot scan remains active unless the same-color evidence indicates that a piece extends beyond it. The blue water background cannot trigger an automatic wide scan.
- **Purple-only correction for five-wide strips.** A previous release-offset experiment was based on a *purple* `#####` strip landing one column to the right. The new screenshots show a *yellow* five-strip landing at its intended origin when it was misrecognized as four and therefore had no correction. Thus v3.6 applies that empirical -1 column correction to purple `#####` only, not to yellow or other colors. The global calibration remains unchanged.

## Offline tests

- `1000010922.png` (touching red/yellow/green): red `###/#..` 4, yellow `#####` 5, green `####` 4.
- Checked **65 saved screenshot images** against v3.5's recognizer: 64 outputs identical. The one changed case is the touching-piece image's yellow five-strip, whose count stayed 5 but tile pitch estimation improved (33px → 39px). The captured app's live misreading of 4 is **not** reproduced by the saved screenshot; real-device confirmation is still required.
- `tests/CalibrationTest.kt` confirms existing movement compensation remains, and yellow five-wide does **not** inherit the unverified purple-only correction.
- Tested the modified `BoardRecognizer.kt` through a JVM screenshot harness with Android Bitmap stubs, **not** a full Android APK build.

## Install and use

1. Unzip and copy project files over matching files in your existing GitHub Desktop checkout; **do not remove `.git` or `.github`**.
2. Commit and push; use GitHub Actions **Build Debug APK** to create and install the new APK.
3. Select **Share a single app → Rock Block Saga**. Leave the saved calibration at **X=84, Y=484** unless a settled verification report proves otherwise.
4. Start with three actual tray pieces. Tap **PREVIEW**; the count, highlighted cells, and blue pickup marker **must** match the real piece before pressing NEXT MOVE. If any piece is unrecognized, do **not** use RESUME to bypass it.
5. After a single trial NEXT MOVE, wait for settled verification (at least 7 seconds), then send a screenshot of the final placement and the full **Last placement check** report.

## Still unresolved

- Color/shape detection is based on screenshot captures, not image/object recognition trained on the full game. It may reject or misread new visual variants.
- The game has shown shape-dependent snapping. **Accurate recognition doesn't prove accurate placement** for all shapes. The app remains user-confirmed PREVIEW → NEXT MOVE, one gesture at a time, with verification and halt on mismatch. Do not assume unattended/autonomous play is safe.
- No full Android SDK compilation performed in the sandbox. GitHub Actions is the required APK build test.
