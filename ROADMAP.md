# FRAMEFORGE Product Roadmap

This roadmap distinguishes implemented code from CI and physical-device verification. A green build does not mean every product feature is complete.

## Implemented core

- [x] Android Kotlin/Compose foundation, Material 3 theme and navigation.
- [x] Room-backed project management and media import/library.
- [x] Timeline clip data, tracks, gaps, trims, split, move/reorder, duplicate and delete.
- [x] Timeline snapshot undo/redo with Room persistence and legacy snapshot decoding.
- [x] Concurrent audio tracks, volume and fade-in/fade-out.
- [x] Photo crop, filters and tone adjustments, including highlights, shadows and sharpness.
- [x] Video rotation, speed, color filters and text overlays.
- [x] Media3 Transformer export and WorkManager-based background export with progress, cancellation and retry input.
- [x] CI debug/release APK builds, lint, unit tests and APK validation (latest fully verified run #246).

## Incomplete or partially implemented

- [ ] Real video transitions. Fade-through-black/crossfade must affect both preview and export.
- [ ] Arbitrary visual overlay positioning, scaling and layer controls.
- [ ] State-restoring undo/redo for non-timeline/photo editing operations.
- [ ] Physical-device validation of preview/export synchronization, audio mix, recovery and codec compatibility.
- [ ] Room migration testing on Android API 26 through current Android versions.
- [ ] Full accessibility, performance, privacy and security audit.
- [ ] Production-signed release APK/AAB using an owner-controlled private keystore.
- [ ] Real AI integration through a validated secure provider or on-device model. Current scene/luminance tools are heuristics, not learned AI.
- [ ] Speech-to-text and automatic captions remain disabled until a real integration is implemented and tested.

## Later enhancements

- Keyframe animation and custom pan/zoom.
- Rich sticker/graphic overlays.
- On-device transcription model integration.
- Additional codecs and export profiles, subject to device support.
