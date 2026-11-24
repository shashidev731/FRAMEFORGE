# FRAMEFORGE Development Guide

## Prerequisites

- **Java Development Kit (JDK)**: JDK 17 or JDK 21 (JDK 21 LTS verified).
- **Android SDK**: Platform 36 (`platforms;android-36`), Build-Tools 35.0.0 (`build-tools;35.0.0`).
- **Gradle**: Gradle 8.11.1 (via bundled `gradlew.bat` / `gradlew`).
- **Android Gradle Plugin (AGP)**: 8.10.1.
- **Kotlin**: 2.0.21 with Compose Compiler Plugin 2.0.21.

---

## Building the Project

### Debug Build
```bash
./gradlew :app:assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build
```bash
./gradlew :app:assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

---

## Running Automated Tests

### Unit Tests
```bash
./gradlew :app:testDebugUnitTest
```
Tests cover:
- Room database snapshot serialization & version compatibility (`TimelineSnapshotCodecTest`).
- Timeline sequential offset & gap calculations (`TimelineTimingTest`).
- Multi-track audio grouping & speed scaling (`TimelineMultitrackMixingTest`).
- Audio volume & fade calculation bounds (`TimelineAudioVolumeTest`, `TimelineAudioFadesTest`).
- Photo crop geometry clamping and inverse rotation ratios (`PhotoCropGeometryTest`).
- Export resolution, codec, and frame-rate configurations (`ExportConfigurationTest`).
- Aspect ratio string validation (`AspectRatioValidationTest`).

---

## Environment Setup & `local.properties`

Ensure `local.properties` contains your Android SDK path:
```properties
sdk.dir=C:/Users/<Username>/AppData/Local/Android/Sdk
```
Or define `ANDROID_HOME` in your environment.
