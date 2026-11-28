# FRAMEFORGE Testing Documentation

## Automated Test Suite Summary

The automated test suite runs via JUnit 4 on JVM through `:app:testDebugUnitTest`.

### 1. Snapshot Serialization & History (`TimelineSnapshotCodecTest`, `PhotoEditSnapshotCodecTest`)
- Timeline snapshot TSH4 round-trip includes video fade-in/out while retaining TSH1, TSH2 and TSH3 defaults.
- Photo snapshot round-trip preserves source URI, crop, tone, sharpness, overlay text and rotation; malformed payloads and out-of-range values are rejected or clamped.
- Round-trip encoding and decoding of empty and populated clip lists.
- Backward compatibility verification for legacy `TSH1` (14-field) and `TSH2` (16-field) snapshots.
- New `TSH3` format testing for `speed`, `filterEffect`, `rotationDegrees`, and `textOverlay`.
- Rejection of unknown version headers or truncated payloads.

### 2. Timeline Offsets & Multitrack Mixing (`TimelineTimingTest`, `TimelineMultitrackMixingTest`)
- Offset reflow across single and multiple clips with user-defined gaps.
- Independent track timelines ensuring audio track offsets do not interfere with visual tracks.
- Multi-track grouping by `trackIndex` for simultaneous audio sequence creation.
- Playback speed scaling to compute effective clip duration on timeline.

### 3. Audio Processing Bounds (`TimelineAudioVolumeTest`, `TimelineAudioFadesTest`)
- Volume normalization clamping values between 0.0f and 1.0f.
- Fade normalization ensuring total fade time does not exceed visible clip duration.
- Symmetrical shortening when combined fades exceed clip boundaries.

### 4. Photo Crop Geometry (`PhotoCropGeometryTest`)
- Centered crop calculations for square (1:1), portrait (4:5, 9:16), landscape (16:9, 3:2), and original ratios.
- Horizontal and vertical offset panning with boundary clamping.
- Quarter-turn rotation handling applying inverse aspect ratios prior to rotation.

### 5. Export Profiles & Canvas (`ExportConfigurationTest`, `AspectRatioValidationTest`)
- Validating default resolution (1080p), frame-rate (30 fps), and codec (H.264).
- Custom export configurations (4K 60fps HEVC, 720p 24fps AVC).
- Syntax validation for custom canvas ratio strings (`width:height`).

---

## Execution Verification Command

```bash
./gradlew :app:testDebugUnitTest --info
```
Status: **PASSED (100% Success)**
