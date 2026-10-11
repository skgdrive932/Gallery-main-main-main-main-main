# Symbian Gallery

A classic Symbian/Nokia-inspired Android Gallery for local photos and videos.

## Features

- Symbian-inspired compact blue/grey interface
- Folder-based media browsing
- Photos and videos from MediaStore
- All Media view
- Folder counts
- Photo thumbnails
- Video thumbnails with play badge
- Full-screen photo viewer
- Built-in Media3 video playback
- Android 13+ media permissions
- Android 6+ compatibility target
- GitHub Actions debug APK build
- No external account or server required

## Build

The repository contains `gradlew` and `gradlew.bat`.

```bash
./gradlew assembleDebug
```

The APK is generated at:

`app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions

Push the repository to GitHub. The workflow at:

`.github/workflows/main.yml`

checks out the project, installs JDK 17 and the required Android SDK packages, makes `gradlew` executable, and builds the debug APK.

The APK is uploaded as the workflow artifact:

`SymbianGallery-debug-apk`

## Permissions

On Android 13+, the app requests:

- READ_MEDIA_IMAGES
- READ_MEDIA_VIDEO

On Android 12 and below it requests:

- READ_EXTERNAL_STORAGE

Media is read locally through Android MediaStore.

## Developer

- Name: Sk kaushal
- Gmail: skgdrive932@gmail.com
- Contact No.: +919779371866

The same information is available from the app's **About** screen.

## License

Copyright © 2026 Sk kaushal.

This project is licensed under the **Apache License 2.0**.
See the `LICENSE` file for the license terms.


## V2 Gallery UI

The V2 main screen is gallery-first rather than file-manager-first:
- Photos opens directly to a 3-column photo grid.
- Videos opens directly to a 3-column video grid.
- Albums is the separate folder/album browser.
- Tapping a photo opens the full-screen photo viewer.
- Tapping a video opens the Media3 video player.
- The UI keeps a compact Symbian/Nokia-inspired blue/grey visual style.
- Developer/About and Apache License 2.0 are included.


### V2.1 final safety update
The media query safely skips collections for which Android has not granted access.


## Recent gallery controls
- Video gesture handling now uses a touch-slop threshold, smoother seek sensitivity, fractional volume steps, and a short fade-out HUD.
- Photos and Videos grids support Select mode, long-press selection, Select All/None, Share, Move to a chosen folder, and Delete with Android's confirmation flow where required.
- Move copies selected media into a folder chosen with the Android folder picker and then requests deletion of the originals.
