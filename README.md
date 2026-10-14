# Rock Block Bot v2.3 — one-move hold/release test

Experimental Android project for an 8x8 block-placement game. **Not an unattended player.**

## Changes from v2.2

- Dragging is now a two-stage accessibility gesture: 850 ms travel, continued 350 ms hold, then release.
- The move is attempted **only once per capture session**; there is no automatic retry.
- The report records whether Android completed both stages or cancelled/rejected either stage.
- STOP preserves the latest gesture outcome in the report rather than replacing it.
- The board/piece recognition and solver remain unchanged. Placement coordinates have not been changed.

## Upload/update

Copy the contents of this directory into your existing `Automated_Blockbot` checkout, replacing matching files. **Keep `.git` and `.github` untouched.** Commit and push with GitHub Desktop. Your existing GitHub Actions workflow should compile the APK.

## Test

1. Install the new debug APK, then open Rock Block Bot v2.3.
2. Allow overlays and enable the accessibility service if not already enabled.
3. Start capture; choose **Entire screen** for this drag test.
4. Open a fresh Rock Block Saga puzzle, leave all three pieces visible, and wait a few seconds.
5. Tap floating **MOVE 1 once**. Watch whether the piece locks into place.
6. Tap STOP and return to the bot. The **Last gesture result** should remain visible.
7. Send a screenshot of the result and explain whether the piece stayed on the board.

A completed Android gesture does **not** guarantee the game accepted the placement. If it still snaps back, we will inspect the game's release behavior and coordinate calibration rather than blindly repeating moves.

Only enable accessibility for testing and disable it afterward. Use at your own risk: gameplay automation may be restricted by the game's or rewards program's rules.
