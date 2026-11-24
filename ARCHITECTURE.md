# FRAMEFORGE Architecture Documentation

## 1. System Overview

FRAMEFORGE is a modern, high-performance Android video and photo editing application built with Jetpack Compose, AndroidX Media3, Room, Kotlin Coroutines, and Clean Architecture principles.

```
┌────────────────────────────────────────────────────────┐
│                   UI Layer (Compose)                   │
│   • HomeDashboard    • VideoStudio    • PhotoStudio    │
│   • TimelinePanel    • AIStudio       • Settings       │
└───────────────────────────▲────────────────────────────┘
                            │ StateFlow / Events
┌───────────────────────────┴────────────────────────────┐
│                    Domain Layer                        │
│   • TimelineTimingCalculations   • PhotoCropGeometry   │
│   • ExportConfiguration          • SceneDetection      │
└───────────────────────────▲────────────────────────────┘
                            │
┌───────────────────────────┴────────────────────────────┐
│                     Data Layer                         │
│   • ProjectRepository        • MediaRepository         │
│   • TimelineRepository       • EditHistoryRepository   │
│   • FrameforgeDatabase (Room v10)                     │
└───────────────────────────▲────────────────────────────┘
                            │
┌───────────────────────────┴────────────────────────────┐
│             Media & Pipeline Engines                   │
│   • AndroidX Media3 (ExoPlayer + Transformer 1.9.0)    │
│   • MediaCodec / MediaExtractor / MediaMuxer           │
│   • Bitmap Shaders & ColorMatrix Engine                │
│   • Scoped Storage Document Contract APIs              │
└────────────────────────────────────────────────────────┘
```

---

## 2. Key Modules & Subsystems

### 2.1 UI Layer (`com.frameforge.app.ui`)
- **Jetpack Compose + Material 3**: Fully reactive UI with custom dark theme as default (`CanvasDark = #0F1115`, `SurfaceDark = #171A21`, `AccentViolet = #6366F1`).
- **FrameforgeRoot**: Root navigation host orchestrating bottom navigation across Home, Projects, Video, Photo, AI Studio, Library, and Settings.
- **ProjectTimelinePanel**: Multitrack interactive timeline strip, clip cards, drag/slider trims, speed selectors, filter chips, audio mix controls, and rotation.
- **TimelineCompositionPreview**: Embedded AndroidX Media3 `PlayerView` with real-time scrub synchronized to the playhead, and Transformer-powered background MP4 exporter with progress callbacks.
- **PhotoStudioScreen**: Real-time non-destructive bitmap transformations with live original-vs-edited split comparison, geometric crop calculation, and JPEG export.
- **AiStudioScreen**: On-device smart scene cut detection, frame luminance analysis, and secure Gemini API configuration.

### 2.2 Domain & Data Layer (`com.frameforge.app.data`)
- **Room SQLite Engine (`FrameforgeDatabase` v10)**:
  - `projects`: Project metadata including name, aspect ratio (9:16, 16:9, 1:1, 4:5, custom), resolution (720p, 1080p, 4K), and target frame rate (24, 30, 60 fps).
  - `media`: Scoped storage media records with display names and MIME types.
  - `timeline_clips`: Track assignments (video track 0, audio tracks 1..n), trim boundaries, gaps, volume, fades, playback speed, filter effects, rotation degrees, and text overlays.
  - `edit_operations`: Persisted history records with atomic serialized timeline snapshots for robust Undo and Redo operations.
- **TimelineSnapshotCodec (TSH3)**: Base64-encoded serialized snapshot codec supporting version negotiation and backward compatibility (`TSH1`, `TSH2`, and `TSH3`).

### 2.3 Media Processing Engine
- **AndroidX Media3 Transformer**:
  - `Composition.Builder`: Supports concurrent visual and multitrack audio sequences.
  - `EditedMediaItem`: Encapsulates start/end clipping, duration stretching, and effect chains.
  - `GainProcessor`: Applies per-clip volume and linear fade-in/fade-out curves.
  - `ScaleAndRotateTransformation`: Fast hardware-accelerated 90°/180°/270° rotation.
  - `RgbFilter & RgbAdjustment`: Color transformations (B&W, Invert, Vivid, Warm, Cool, Sepia).
  - `TextOverlay & OverlayEffect`: Real-time text subtitle compositing.
  - Codec & container support: H.264 (AVC) and H.265 (HEVC) MP4 encoding.
