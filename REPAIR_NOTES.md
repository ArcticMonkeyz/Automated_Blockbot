# RockBlockBot v4.4.13: guarded v4.4.4 rollback

This is a **conservative repair** of v4.4.12, not another experimental purple-shadow implementation.
The original `BoardRecognizer`, `ShadowTracker`, `Solver`, `TouchService`, `GripMapping`,
`PlacementGeometry`, `DragApproach`, `ProbeTiming`, and `PieceCalibration` files are unchanged.

## Corrections

1. **A failed recalibration cannot silently reuse an older learned grip.** Starting CAL DRAG
   invalidates the saved grip for that shape and color on disk. A failed, cancelled, or
   unverified probe never re-enables NEXT MOVE for it. The new v4.4.13 namespace also ignores
   previous learned grips whose history may be uncertain.
2. **Manual adjustments now change a learned release in a controlled way.** A successful
   calibration stores a snapshot of the global, tall, bias and per-piece manual offsets.
   Only changes *after* that successful probe shift the release coordinate. The offsets
   present at probe time are not counted twice. An edit beyond about one cell requires
   a new CAL DRAG rather than allowing a large unverified jump.
3. **Guards on stored shadow positions and planned releases.** A shadow position must be legal
   for the full recognized footprint, plausibly near the probe's planned position, and
   physically consistent with the touch position. The computed release must also remain
   inside the phone display. The finger itself may legitimately be BELOW the visible board
   because the game's raised piece and landing shadow are not at the finger location.
4. **Independent-time frame requirement.** Two agreeing readings must be at least 200ms
   apart, with no equally strong competing location. This is still a *single-stop probe*,
   and cannot absolutely prove visual correctness. The original successful gesture is
   deliberately unchanged.
5. **Optional reset for inherited manual settings.** The main screen includes a confirmed
   `RESET MANUAL TUNING (optional)` action. It clears old global/tall/per-piece values and
   the v4.4.13 learned grips. This is NOT automatic; previously tuned settings might be
   helpful. STOP recognition before using reset, then start and calibrate again.

## Known limitations

- Purple-shadow recognition remains the original v4.4.4 detector. In the previously
  supplied purple four-strip image, it could not reliably recognize a sufficiently
  exposed landing shadow. v4.4.13 will correctly refuse an unsupported calibration;
  it does not claim to solve this remaining recognition issue.
- A single return probe can fail to reveal whether the game shows a static unrelated
  colored patch. Time-separated readings and plausibility checks reduce, but do not
  eliminate, that possibility. NEXT MOVE is still a user-authorized open-loop release.
- Android SDK/Gradle and live device testing were not available in this environment.
  Kotlin host tests and fake-accessibility tests are not a substitute for installing
  the APK and validating one real placement.

## Recommended supervised test

1. Install over the existing app. Do **not** clear application data or uninstall.
2. Keep manual settings initially. PREVIEW a shape that worked previously and confirm
   all detected tile centers and green intended cells.
3. CAL DRAG once, check that the piece returns to the tray and `CALIBRATED` appears.
4. PREVIEW again and verify the red release marker. Use NEXT MOVE just once.
5. Only proceed if the real game agrees with `VERIFIED`. If any doubt, STOP and export the
   last report plus a screenshot. Don't experiment with the purple shape during this test.
6. If inherited manual tuning is visibly corrupt, STOP first, use the optional reset,
   and start the supervised sequence again.

Source ZIP, **not an installable APK**. Build using the existing GitHub Actions workflow.
