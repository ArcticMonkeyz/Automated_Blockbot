# Rock Block Bot v2.2 — Single-move diagnostic

This is an **experimental single-placement test** for Rock Block Saga. It is **not** a finished autonomous player. The game or rewards provider may disallow automated play; check applicable rules before using.

## What's new

- Preserves last recognized game board and three pieces from v2.1.
- Plans placements using the existing three-piece survival-focused solver.
- Adds an Android accessibility gesture service and a small floating **MOVE 1 / STOP** overlay.
- A single explicit press of **MOVE 1** attempts **one** drag, then compares the next captured board against the expected result. It does not automatically retry or continue.
- Stops without moving if all three pieces aren't recognized, the frame isn't stable, accessibility isn't enabled, or the solver can't fit all three pieces.

## Install and test

1. Replace the v2.1 project files in your GitHub Desktop clone with the files in this ZIP, **preserving `.git` and `.github`**.
2. Commit to main and push. Download the debug APK from the successful GitHub Actions artifact and install it.
3. Open Blockbot. Tap **Allow floating controls** and grant the Android permission to display over other apps.
4. Tap **Enable accessibility service** and enable **Rock Block Bot v2.2**. Accessibility gives the app the ability to control touches; enable it only if you're comfortable with that permission.
5. Tap **Start screen recognition + floating controls**. For this first gesture test select **Entire screen** (single-app sharing can shift gesture coordinates).
6. Switch to Rock Block Saga and open an active 8x8 board with all three unplaced pieces. Wait two seconds.
7. Tap **MOVE 1** once. It should try only one placement. Check the actual game immediately. Tap **STOP** to end capture and remove the overlay.
8. Return to Blockbot and share the report plus a game screenshot, particularly if the piece dropped in the wrong cell.

## Important limitations

- **Not compiled or tested on a Pixel 7 Pro.** GitHub Actions compilation is still required.
- Coordinates and finger-to-piece preview offset were estimated from screenshots. The initial vertical drag offset is **+95 reference pixels**; this may be incorrect for some shapes or Android screen configurations. A failed move may lose the puzzle.
- The current image recognition handles only a limited set of tile colors and game layouts. Other piece shapes, board skins, animations, and new levels may require calibration.
- The captured game frame and Android touch coordinates can differ if using single-app capture, screen zoom, or changed display resolution. Use Entire screen for this test.
- The overlay stays on screen until STOP. You can revoke the accessibility permission after testing.
- No networking, cloud upload, account credentials, or persistent game interaction is implemented.
