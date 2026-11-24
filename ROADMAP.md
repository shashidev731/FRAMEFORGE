# FRAMEFORGE Product Roadmap

## Milestone Roadmap & Completion Status

- [x] **Milestone 0 — Repository and Environment Audit**: Baseline code evaluated, Git repository tracked, Java 21 LTS verified, Gradle wrapper configured.
- [x] **Milestone 1 — Stable Foundation**: Material 3 dark design system, Room database schema, Clean Architecture, navigation structure, buildable Gradle scripts.
- [x] **Milestone 2 — Projects and Media Library**: Project CRUD, Scoped Storage document picker, thumbnail generation, persistent media references, non-destructive file handling.
- [x] **Milestone 3 — Video Preview and Timeline Foundation**: AndroidX Media3 `CompositionPlayer`, timeline strip, waveform visualization, playhead scrubbing, duration calculations.
- [x] **Milestone 4 — Core Editing & Undo/Redo**: Trimming start/end, splitting at playhead, move clip, reorder in track, duplicate, delete, and snapshot-based Undo/Redo.
- [x] **Milestone 5 — Audio and Overlays**: Concurrent multitrack audio mixing (BGM, voiceover, sound effects), volume adjustment, fade-in/out, and text overlay rendering.
- [x] **Milestone 6 — Effects and Photo Editing**: Speed scaling (0.5x - 2.0x), visual filters (B&W, Invert, Vivid, Warm, Cool, Sepia), rotation (90°/180°/270°), non-destructive Photo Studio with live before/after comparison.
- [x] **Milestone 7 — Export Engine**: Media3 `Transformer` export with resolution presets (720p, 1080p, 4K), frame rates (24, 30, 60 fps), codec selection (H.264, H.265), progress bar, cancellation, and sharing.
- [x] **Milestone 8 — AI Studio**: On-device smart scene cut detection, image luminance analyzer, secure Gemini API key management, offline fallback states.
- [x] **Milestone 9 — Quality and Optimization**: Unit test suite covering all critical math, codecs, and mixing logic. Memory safety limits on media decoders.
- [x] **Milestone 10 — Release Preparation**: Production release build type, Proguard rules, signed release APK validation, comprehensive developer and architecture documentation.

---

## Future Enhancements
- Hardware-accelerated Keyframe animation engine for custom pan & zoom (Ken Burns effect).
- Lottie animated sticker overlays via `LottieOverlay`.
- On-device Whisper transcription model integration.
