# FRAMEFORGE

Android-first photo and video editor for social creators and influencers.

## Current status

**Early functional alpha — not a finished release.** The Android project builds in GitHub Actions. The app has a working project foundation, media library, basic photo adjustments and a basic video trim/export path. Device-level testing is still required.

## Implemented

### App foundation
- Kotlin, Jetpack Compose and Material 3
- Premium light interface, onboarding and Simple/Professional workspace preference
- Home, Projects, Video Studio, Photo Studio, AI Studio, Media Library and Settings navigation
- Local Room database for projects, media references and edit-history records
- Create, rename, duplicate, search, sort and delete project records
- Project aspect-ratio preferences: 9:16, 16:9, 1:1, 4:5 and Custom
- Android document picker for importing media without broad storage permissions

### Media Library
- Persistent references to selected photo, video and audio files
- Search by filename and filter by Photos, Videos and Audio
- Local photo and video preview
- Local audio playback
- Removing a library reference does not delete the original file

### Photo Studio
- Open a photo using Android's document picker
- Live preview of center-crop presets, exposure, saturation and 90-degree rotation
- Reset adjustments
- Toggle original-versus-edited comparison
- Export a new JPEG copy through Android's save-document picker
- Original source file is never overwritten
- Editing input is downsampled to a maximum 3072 px on the longest edge for phone memory safety

### Video Studio
- Local video playback with Android media controls
- Select start and end points
- Export a separate MP4 by remuxing supported audio/video tracks
- Original source is never modified
- Fast trim seeks to the previous video keyframe, so the first output frame can be slightly earlier than the selected start
- Some source codecs/containers may not be supported by the device's MP4 muxer

### Edit history foundation
- Project-specific edit-instruction history stored in Room
- Undo/redo state and atomic append operations
- Current history controls store operation metadata; they are not yet connected to the photo/video rendering pipelines

## Not implemented yet

- Shared timeline engine that applies every history operation to rendered media
- Multitrack timeline, split/reorder, transitions, keyframes and effects
- Advanced photo tools such as curves, HSL, masks, background/object removal and retouching
- Video re-encoding, advanced codec controls, frame-rate/bitrate profiles and export queue
- Cinema, Motion, Audio and Template studios beyond the current local audio preview
- AI processing, licensed example learning and approval workflow
- Cloud sync, accounts, conflict resolution and version history
- Crash recovery/autosave and comprehensive missing-file detection
- Full automated test coverage, runtime migration tests and device compatibility testing

AI and cloud features are not active. The current app does not upload media to a server.

## Requirements

- JDK 17
- Android SDK Platform 35
- Gradle 8.9
- Android Gradle Plugin 8.7.3
- Minimum Android version: API 26 (Android 8.0)

## Build

From the repository root:

```bash
./gradlew :app:assembleDebug
```

Debug APK output:

`app/build/outputs/apk/debug/app-debug.apk`

A GitHub Actions workflow builds the debug APK on pushes to `main` and uploads it as the `frameforge-debug-apk` artifact. Open the repository's Actions tab, select a successful **Android build**, then download its artifact.

## Install on Android

1. Download and extract the `frameforge-debug-apk` artifact from a successful Actions run.
2. Open `app-debug.apk` on the phone.
3. Allow installation from the app used to open the APK if Android asks.
4. Launch FRAMEFORGE and select Simple or Professional workspace mode.
5. Import media through the in-app document picker.

This is a debug build, not a signed production release.

## Architecture

- `app/src/main/java/com/frameforge/app/ui`: Compose navigation and screens
- `PhotoStudioScreen.kt`: non-destructive bitmap adjustments and JPEG export
- `VideoStudioScreen.kt`: local playback and MP4 trim/remux export
- `EditorTimelinePanel.kt`: project-specific edit-history UI
- `app/src/main/java/com/frameforge/app/ui/theme`: design system
- `app/src/main/java/com/frameforge/app/data`: Room entities, DAO, database and repositories
- `.github/workflows/android-build.yml`: Android CI build and APK artifact

## Privacy and safety

- Media is selected by the user through Android's document picker.
- Original photo/video/audio files are not modified by import or library cleanup.
- Photo and video exports create new files at a user-selected destination.
- The app currently has no AI upload or cloud-sync service.
- Do not commit API keys, passwords, keystores or signing credentials.
- Review permissions and data-handling behavior before any production release.

## Roadmap

1. Finish and stabilize project/media foundation, recovery and automated tests.
2. Connect the shared non-destructive editing engine to real photo/video operations.
3. Expand timeline editing, rendering and export quality controls.
4. Build Cinema, Motion, Audio and Template studios.
5. Add AI with explicit preview/approval and privacy controls.
6. Add optional cloud sync, conflict resolution, security review and release testing.
