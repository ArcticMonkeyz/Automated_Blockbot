# RockBlockBot Android prototype

An Android **manual-entry solver prototype** for the 8x8 Rock Block Saga puzzle. It is **not** an unattended bot yet: automatic image recognition, screen capture, game interactions, and continuous play are not implemented. The accessibility service is a non-operational gesture helper.

## Build without a computer

This repository includes `.github/workflows/build-apk.yml`. Once the *contents* of this project (not the ZIP itself) are committed to the root of a GitHub repository, GitHub Actions builds an unsigned-for-distribution but installable **debug APK** on a hosted runner. Go to **Actions > Build RockBlockBot APK > latest successful run > Artifacts > RockBlockBot-debug-APK**. Download and unzip the artifact to get the `.apk` file, then install it on Android after approving the browser/file manager as an install source.

If the repository is empty and you're on mobile: extract the ZIP on your phone, then use GitHub's browser interface (Desktop site may be required) to upload the extracted files and directories, preserving the directory tree. GitHub does not automatically unpack a ZIP committed to a repository. Alternatively, use a computer to upload the extracted project.

## Solver inputs

Tap the 8x8 board cells to mark occupied cells. Enter each piece using `#` for a filled cell and `.` for empty, with `/` separating rows, e.g. `.#/##/#.`. Pieces cannot rotate. The solver searches orders and legal placements for all three pieces and favors survival. It outputs row/column placements (1-indexed). It cannot guarantee optimal play against random future pieces.

## Privacy and reward terms

The app doesn't request network access or upload gameplay. If you eventually automate gameplay, check the game's and reward provider's applicable rules: mission terms that don't explicitly mention bots aren't necessarily permission to use them.
