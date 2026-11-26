# FRAMEFORGE

Android-first professional photo and video editing studio for creators, filmmakers, and mobile storytellers.

Package: `com.frameforge.app`

---

## Key Features

### 🎬 Multitrack Video Editor
- **AndroidX Media3 Composition Engine**: Live preview synchronized with a timeline playhead using `CompositionPlayer` and `PlayerView`.
- **Multitrack Timeline**: Primary visual sequence (video and photos) + concurrent audio mixing across multiple tracks (BGM, voiceover, sound effects).
- **Precision Trimming & Splitting**: Non-destructive start and end trimming, playhead splitting, and track reassignment.
- **Speed Ramping**: Adjust playback speed from `0.5x` (slow-motion) to `2.0x` (fast-motion) with automatic timeline duration scaling.
- **Visual Filters & Effects**: Built-in shaders for B&W (grayscale), Invert, High Contrast, Brightness boost, Warmth, Cool, and Sepia.
- **Rotation**: Rotate clips 90°, 180°, or 270° with hardware-accelerated transformation.
- **Subtitles & Text Overlays**: Per-clip text captions rendered in real-time preview and baked into the final export via Media3 `TextOverlay`.
- **Persistent Undo & Redo**: Transactional snapshot history stored in SQLite Room via `TimelineSnapshotCodec (TSH3)`.

### 🔊 Audio Editing & Mixing
- Simultaneous multitrack playback.
- Volume adjustments per clip (0% to 100%).
- Fade-in and fade-out curves processed via Media3 `GainProcessor` and `DefaultGainProvider`.

### 📸 Photo Studio
- Non-destructive image adjustments: Exposure, Saturation, Contrast, and Warmth.
- One-tap filter presets: Vivid, B&W, Vintage, Cool, Warm, and Moody.
- Aspect ratio crop presets: Square (`1:1`), Portrait (`4:5`, `9:16`), Landscape (`16:9`, `3:2`), and Free crop with offset panning.
- Live original-vs-edited toggle comparison.
- High-quality JPEG export directly to Android device storage.

### 🧪 On-device Creative Tools
- **Scene-change hints**: Samples video frames and compares average luminance. This is a lightweight brightness heuristic, not semantic scene recognition.
- **Photo analysis**: Samples images to a 512 px longest edge before calculating luminance-based starting suggestions.
- **Cloud AI and speech transcription**: Disabled until a real provider integration, secure credential handling, consent flow, and tests are implemented. No API keys are collected or stored; the previous plaintext Gemini preference is cleared on launch.

### 🚀 Media Export Engine
- Output resolution presets: `720p` (HD), `1080p` (Full HD), and `4K` (Ultra HD).
- Target frame rates: `24 fps`, `30 fps`, and `60 fps`.
- Video codecs: `H.264` (AVC) and `H.265` (HEVC) in MP4 containers.
- Real-time progress, cancellation, and error handling through Media3 Transformer.
- Resolution, frame-rate, codec, quality, and bitrate requests are applied to the encoder.
- Completed exports can be copied to a user-selected destination through Android's Storage Access Framework or shared through the system chooser.

### 🎨 Design System
- Material 3 with a custom dark theme enabled by default (`#0F1115` canvas).
- Clean, accessible typography and high touch-target compliance.
- Theme toggle (Dark / Light) in Settings.
- Cache cleanup utility to manage temporary device storage.

---

## Technical Specifications

- **Target SDK**: 35 (Android 15)
- **Compile SDK**: 36 (Android 16)
- **Min SDK**: 26 (Android 8.0 Oreo)
- **Language**: Kotlin 2.0.21
- **UI Framework**: Jetpack Compose (BOM 2024.12.01) + Material 3
- **Media Engine**: AndroidX Media3 1.9.0 (Transformer, Effect, UI, Common)
- **Database**: Room 2.6.1 (Schema v11 with migrations 1 through 11)
- **Build System**: Gradle 8.11.1 + AGP 8.10.1

---

## Build & Test

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Build release APK (unsigned unless release signing environment variables are configured)
./gradlew :app:assembleRelease

# Run unit tests
./gradlew :app:testDebugUnitTest

# Release signing instructions
# See RELEASE_SIGNING.md. Never commit a keystore or signing password.
```

### Artifacts Generated
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Signed release APK: `app/build/outputs/apk/release/app-release.apk` when a private release keystore is configured.
- Otherwise, CI produces `app/build/outputs/apk/release/app-release-unsigned.apk`. An unsigned APK is a build artifact, not an installable production release.
