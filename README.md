# RockBlockBot v2 — recognition diagnostics

This is a **screen-recognition test build**, not an unattended game-playing bot.

## Changes
- Uses Android MediaProjection with a foreground service and visible notification.
- Requests permission for each screen capture session (Android 14+ compliant).
- Reads live 8x8 board pixels using the fixed screenshot geometry from the user's 709x1536 images.
- Reports detected cells and preliminary piece-zone signals in the app.
- Keeps the solver from v1, but does **not** dispatch automated moves. Recognition must be calibrated first.

## Test
1. Build the APK using the existing `.github/workflows/build-apk.yml` workflow in the repository.
2. Install over v1.
3. Tap Start screen recognition and choose **Entire screen**.
4. Switch to Rock Block Saga with an active puzzle. Wait a few seconds.
5. Return to Blockbot and review the 8x8 detection report. Compare with the actual game.
6. Tap Stop screen recognition to end capture.

If the screenshot aspect ratio differs due to status/nav bars, the reported confidence will be low and the coordinates need calibration. **No screen data is uploaded anywhere**; recognition happens on-device.

## Build note
The source is uncompiled in this environment. The Android CI workflow previously built v1 successfully. A new CI run is needed to validate this revision.

## Security
The app asks for Android's screen-capture permission and runs a foreground notification while capturing. Do not grant capture while viewing passwords, banking, or other sensitive screens.
