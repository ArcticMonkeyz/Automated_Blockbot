# RockBlockBot v4.3 - individual tile-center recognition

This is a **guided test build**, not guaranteed hands-free placement. It preserves the survival solver and the existing movement/per-piece calibration system. The Android APK must be built in GitHub Actions.

## What changed

1. **One detector for horizontal, vertical, and irregular pieces.** The primary recognizer builds an image-color mask for each tray slot, samples each proposed square's **interior** independently, finds its center, and checks the dark seam between every pair of neighboring occupied tiles. It considers competing grid sizes (for example purple vertical **three** versus phantom **four**) and chooses a well-supported explanation. No shape-library whitelist is needed.
2. **Overlaid source-tile markers.** During PREVIEW, cyan **numbered rings 1, 2, 3, ...** show precisely which squares were counted in the selected piece. Green squares still indicate the planned board placement; blue ring marks pickup, red crosshair marks release. The new markers appear **only if the individual-tile detector succeeded**. A legacy fallback might have no numbered rings: examine its shape carefully.
3. **Visible error text.** PREVIEW requires three identical analyzed frames, and if it cannot find all visible pieces, the floating controls display the block reason rather than seeming unresponsive. Do not bypass this by pressing RESUME when all three physical pieces are present.
4. **Stronger ambiguity protection.** Each candidate must cover essentially all visible colored pixels, form an orthogonally connected shape, and be supported by real tile seams. If the new method cannot verify it, existing conservative recognition checks still apply.

Nothing here changes the drag offsets, saved per-color/per-shape calibrations, or strategic solver. Some shapes still take the legacy recognition path, and physical on-phone results **must be verified**.

## Install and first test

1. Extract this ZIP to your PC.
2. In **GitHub Desktop -> Repository -> Show in Explorer**, copy the **contents** of this extracted folder into the existing repository, replacing matching project files. Keep hidden `.git` and `.github` directories unchanged.
3. Commit, push, and download the new **debug APK** produced by your existing GitHub Actions build workflow.
4. Install over the previous app. Start capture using **Share a single app -> Rock Block Saga**.
5. With three actual tray pieces visible, press PREVIEW **without RESUME**. Verify the piece count, shape, and **numbered tile rings** before NEXT MOVE. Do **not** move a misrecognized piece. If recognition still fails, STOP and send the report/screenshot.
6. If you test a placement, wait about 8-10 seconds for VERIFIED/UNVERIFIED; never continue an unverified sequence.

Saved calibration defaults are unchanged (previously X=+84 px, Y=+484 px, tall delta=-240 px unless you modified them). Correct identification **does not prove accurate placement**: the game's finger-to-piece lift behavior remains experimental.

## Local validation

- The recognizer compiled with Kotlin and a host `Bitmap` substitute.
- Against 94 prior screenshots, its shape output differed in only one previously missing purple 5-block case; the purple vertical **3**, blue **T**, red **3x3**, green **5**, and touching yellow/green pieces retained their expected shapes.
- Against 22 PNG/JPEG/blur/stretched/rescaled diagnostic variants of the purple-3 frame, the purple shape stayed correct in 21 variants, and it **rejected** the most extreme vertical stretch rather than inventing another square. It also recognized a synthetic horizontal 3-tile example.
- Local per-piece calibration/placement diagnostics tests pass.
- The **full Android Gradle build and physical capture** have not been run locally. Screen-capture visuals can differ from the user's screenshots.

The host image runner is provided in `tests/RunScreenshotRecognition.sh` for reproducible tests with image paths passed as arguments (requires a JDK and `kotlinc`). The screenshot assets themselves are **not bundled**.
