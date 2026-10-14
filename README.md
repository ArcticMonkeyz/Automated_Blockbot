# Rock Block Bot v2.4 - visual drag calibration

Experimental Android project for the 8x8 Rock Block Saga puzzle. This is a **single-move diagnostic build**, not a continuous player. It is not yet verified on a Pixel 7 Pro.

## Changes from v2.3

- **PREVIEW** calculates the solver's first placement and shows a noninteractive overlay: green squares for the planned board cells, blue ring for drag start, red crosshair for the finger release endpoint. PREVIEW does **not** touch the game.
- **X - / X + / Y - / Y +** shift the release endpoint by 20 *screen* pixels per press. Adjustments persist across sessions. They do not alter the solver's board-cell target.
- The old estimated +95 reference-pixel vertical drag offset is removed. New offsets start at 0 and can be tuned experimentally.
- **MOVE 1** becomes available only after a fresh, stable recognition and a complete three-piece solution. A preview expires after 30 seconds.
- A single move is attempted per session. No automatic retries. STOP preserves the gesture result.
- The v2.3 two-stage accessibility drag (850ms travel + 350ms hold/release) is retained.

## Build

Copy everything *inside* this extracted folder into the existing Automated_Blockbot checkout, replacing matching files. Keep `.git` and `.github` untouched. Commit and push in GitHub Desktop; the existing GitHub Actions workflow builds the debug APK. Java 17 is retained. The application ID is unchanged and versionCode is 6.

## Test on phone

1. Install the updated debug APK. Enable the app's overlay and accessibility permissions only while testing.
2. Start capture and choose **Entire screen**. Open Rock Block Saga with all three pieces visible; wait for recognition.
3. Tap **PREVIEW**. Green squares indicate where the solver wants the piece; blue circle is pickup point; red crosshair is where the simulated finger will release. The green squares are *not* the actual finger endpoint.
4. If the preview looks implausible, tap STOP. You may adjust X/Y using the floating buttons to change the endpoint marker **before** dragging. Adjustments are persisted.
5. If you decide to test a move, tap **MOVE 1 only once**. Wait for it to finish, then tap STOP. Inspect the game and the preserved report. Do not test on a level you don't want to risk.
6. Send screenshots of the preview and the result. If the piece snaps back, we can adjust offsets without rebuilding the APK.

## Limitations

- Touch coordinate mapping is based on a reference 709x1536 screenshot and full-display dimensions; Android's capture size, status bar and insets can differ. The visual overlay itself may need calibration.
- A successful Android gesture does not prove the game accepted the move.
- Recognition is experimental; some shapes, obstacles and layouts may be misread. No unattended gameplay is enabled.
- Android accessibility and screen capture are sensitive permissions; disable them after testing. Check the game's and rewards platform's rules before using automation.
