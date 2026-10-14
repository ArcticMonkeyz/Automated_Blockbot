# RockBlockBot v4.4 — Live game-shadow guided placement (experimental)

**Research/test update. Not a guarantee of accurate placement.** The v4.3 source-tile recognition, solver, preview, and per-piece saved calibration have been retained. The movement controller has been replaced with a serial held-touch visual-feedback controller.

## What changed

- When you press NEXT MOVE, the bot starts an accessibility gesture with `willContinue=true` instead of releasing on its first guessed endpoint.
- While the finger stays down, MediaProjection samples actual **game-produced translucent placement shadows**. The overlay is hidden during tracking so it does not contaminate image analysis.
- The shadow detector uses the **detected piece color** (red/green/blue/purple/yellow), ignores previously occupied board cells, and compares candidate shadow *footprints* to the solver's target coordinates (the same grid cells displayed as green during PREVIEW).
- It only calls `releaseHeldDrag` after **two separate, sufficiently confident held-shadow observations** match the target. When the ghost is at another valid placement, it corrects the held pointer by up to two board cells and checks again.
- An initial missing shadow triggers a limited search toward the board centre. Tracking stops after at most five corrections or 18 seconds. It then **attempts to drag back to the tray and release there**. Android may still cancel unexpectedly, so return is **not guaranteed**.
- STOP pressed during a gesture requests a safe return-to-tray; after it finishes, press STOP again to remove the controls. The app still requires a fresh user-confirmed PREVIEW before each move; no unattended play.
- New report line **Last game shadow tracking** describes color, measured position, confidence, and tracking failures. After an aligned release, the post-release board is still independently checked.

## Critical limitation

Only a screenshot of a partially occluded **red** placement shadow has been supplied; ghosts in other colors and different states have **not yet been verified on a running device**. The detector's five-color thresholds currently use general color characteristics and synthetic tests. A lifted solid tile may obscure too many ghost cells, in which case the tracker deliberately refuses to release and attempts a return. The game or Android may cancel ongoing touches. Do not rely on this for high-stakes or unattended automation.

## Install & safe first test

1. Extract ZIP; copy this folder's **contents** to your existing GitHub Desktop repo. Keep hidden `.git` and `.github` untouched. Push changes and build debug APK using GitHub Actions.
2. Install over your current app; use **Share a single app > Rock Block Saga**.
3. PREVIEW. All three pieces must be recognized if all are present. Inspect numbered source tile rings and **green target squares**. Do not NEXT MOVE for an inaccurate shape.
4. NEXT MOVE once. Wait up to **30 seconds** while it holds, scans, adjusts and verifies. DO NOT manually drag another piece during this time.
5. If the game shadow cannot be confidently tracked, the app should attempt to return the piece to the tray and show UNVERIFIED. If that happens, STOP and send the status and **Last game shadow tracking** report. Do not use RESUME to skip an unidentified piece.
6. If VERIFIED, PREVIEW before NEXT MOVE again.

Your old saved X/Y and per-piece calibrations are retained as **first-guess positioning only**; they no longer authorize unconditional release.

## Validation performed

- Host-compiled `ShadowTracker.kt` and tested synthetic color shadows, varied shapes (horizontal, vertical, L, T, square, five-high), wrong-position detection and missing-shadow refusal.
- Verified the supplied partially occluded held-red example is **not incorrectly authorized** as a high-confidence placement.
- Host-compiled the new continued-touch `TouchService.kt` against Android API signature stand-ins.
- Existing recognizer and calibration tests are packaged unchanged; the full Android build and live touch/capture synchronization **must be tested through your GitHub Actions build and phone**.

This design is based on standard visual servoing: the target is the bot's planned green board footprint, but the feedback signal is **the game's actual predicted landing shadow**, rather than the raised solid tile.
