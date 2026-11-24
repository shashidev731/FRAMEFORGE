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

### 🤖 AI Studio
- **Smart Scene Detection**: On-device video frame variance analyzer that identifies cut points and shot transitions without cloud dependencies.
- **AI Image Enhancer**: Dynamic range and luminance analyzer offering tailored adjustment recommendations (heuristic analysis, not generative image editing).
- **Speech-to-Text & Subtitles**: Gemini provider configuration UI is present; end-to-end timeline transcription and caption insertion are not yet implemented.

### 🚀 Media Export Engine
- Output resolution presets: `720p` (HD), `1080p` (Full HD), and `4K` (Ultra HD).
- Target frame rates: `24 fps`, `30 fps`, and `60 fps`.
- Video codecs: `H.264` (AVC) and `H.265` (HEVC) in MP4 containers.
- Real-time progress bar, cancellation support, and direct Android system share sheet invocation.

### 🎨 Design System
- Material 3 with a custom dark theme enabled by default.
- Dark/light theme switching in Settings.
- Cache cleanup utility.

---

## Technical Specifications

- **Target SDK**: 35
- **Compile SDK**: 36
- **Min SDK**: 26
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose + Material 3
- **Media Engine**: AndroidX Media3 1.9.0
- **Database**: Room 2.6.1 (schema v10)
- **Build System**: Gradle / Android Gradle Plugin

---

## Build & Test

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Build release APK (unsigned unless release signing is configured)
./gradlew :app:assembleRelease

# Run unit tests
./gradlew :app:testDebugUnitTest

# Run Android lint
./gradlew :app:lintDebug
```

### Release signing (recommended before distribution)

The release build must never use the debug certificate. Configure a private upload/release keystore through environment variables or Gradle project properties:

- `FRAMEFORGE_RELEASE_STORE_FILE`: path to the keystore file
- `FRAMEFORGE_RELEASE_STORE_PASSWORD`: keystore password
- `FRAMEFORGE_RELEASE_KEY_ALIAS`: key alias
- `FRAMEFORGE_RELEASE_KEY_PASSWORD`: key password

For local builds, pass the same values as `-PFRAMEFORGE_RELEASE_STORE_FILE=...` etc., or export them as environment variables. For GitHub Actions, store the values as encrypted repository Actions secrets and write the keystore file from a base64-encoded secret during the workflow. Never commit the keystore, passwords, or private key to Git.

If all four values are absent, `assembleRelease` produces an unsigned APK that is suitable for build validation only, not installation as a trusted production release. If only some values are supplied, the build remains unsigned; supply all four values together.

Minification remains disabled until release-specific keep rules and runtime smoke tests are verified.

---

## Current verification status

A successful CI build confirms compilation and the automated tests included in that workflow; it does not establish full device compatibility or feature completeness. Physical-device testing, export verification across representative codecs, end-to-end speech transcription, and production signing remain release gates.

