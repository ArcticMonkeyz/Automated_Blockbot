# Rock Block Bot v2.1 — Board and Piece Recognition Test

This Android app is a **diagnostic prototype**. It does not move pieces or play the game.

## Changes since v2
- Preserves the most recent recognized game frame when returning to Blockbot.
- Detects the 8x8 board and attempts to read all three available shapes.
- Supports common red, green, blue, purple, and yellow piece colors.
- Replaces the misleading screen-aspect-ratio confidence check with a preliminary/partial diagnostic.

## Testing
1. Build `:app:assembleDebug` using the existing GitHub Actions workflow.
2. Install the APK on your Pixel 7 Pro.
3. Start recognition and approve **Entire screen** or **Single app: Rock Block Saga**.
4. Open an active game board with three unplaced pieces for 5–10 seconds.
5. Return to Blockbot and compare its eight rows and three `#`/`.` shapes with the actual game.
6. If a shape is wrong, send screenshots of the game board and Blockbot report.

The detector uses the game's reference coordinates from screenshots at 709x1536, rescaled to captured frame dimensions. Different layouts, overlays, animations, or unfamiliar colors may require calibration. The `PRELIMINARY` label is not proof of accuracy. Do not enable automation based on this diagnostic alone.

## GitHub Desktop update
Copy the contents of this folder into the cloned repository, replacing matching files. **Keep `.git` and `.github` unchanged.** Commit to main, push origin, and download the new Actions artifact. This package does not include a GitHub Actions workflow because the existing one works.
