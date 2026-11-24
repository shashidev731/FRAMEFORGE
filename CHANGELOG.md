# CHANGELOG — FRAMEFORGE

## [0.1.0] - 2026-11-24

### Added
- **Premium Dark Material 3 Design System**: Custom charcoal and deep black canvas theme enabled by default with dynamic theme switching in Settings.
- **Multitrack Audio Mixing**: Supported concurrent audio tracks (tracks 1, 2, 3...) in AndroidX Media3 timeline composition and transformer export.
- **Video Visual Effects & Filters**: Added B&W (grayscale), Invert, High Contrast, Brightness boost, Warmth, Cool, and Sepia shaders.
- **Playback Speed Control**: Supported 0.5x (slow-mo), 0.75x, 1.0x, 1.25x, 1.5x, and 2.0x playback speed with automatic duration scaling.
- **Text & Subtitle Overlays**: Per-clip subtitle text input rendered directly onto the video preview and exported MP4 via `TextOverlay` and `OverlayEffect`.
- **Advanced Export Engine**: Configurable export dialog offering 720p, 1080p, and 4K resolutions, 24/30/60 FPS, H.264/H.265 codecs, real-time progress reporting, cancellation, and direct Android share sheet invocation.
- **AI Studio**:
  - On-device smart scene detection using frame variance analysis across video keyframes.
  - On-device photo luminance and histogram analyzer with automated enhancement recommendations.
  - Secure Gemini API key configuration stored privately in SharedPreferences with fallback states.
- **Photo Studio Enhancements**: Added filter presets (Vivid, B&W, Vintage, Cool, Warm, Moody), 16:9 and 3:2 aspect ratio crops, horizontal scroll UI, and high-quality JPEG export.
- **Project Metadata & Settings**: Stored project resolution and frame rate in Room (version 10 migration), cache cleanup utility in Settings, and system storage info.
- **Release Build Configuration**: Production release build type with ProGuard optimization and signed release APK generation (`app-release.apk`).
- **Comprehensive Test Suite**: Added `TimelineMultitrackMixingTest` and `ExportConfigurationTest`, expanded `TimelineSnapshotCodecTest` with version `TSH3` coverage.
