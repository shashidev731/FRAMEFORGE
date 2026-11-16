# FRAMEFORGE

Android-first photo and video editing application for social creators.

## Current development status

**Phase 1 foundation source has been committed.** The current foundation includes:
- Kotlin, Jetpack Compose and Material 3 app shell
- Premium light visual theme and main navigation
- Room database for local project persistence
- Create, search and delete project records
- Project type and aspect-ratio selection
- Android document-picker entry points for photos, videos and library selection

This is an early development foundation, **not a finished editor and not yet a verified APK**. The repository must pass a real Android build and device testing before this phase can be marked complete.

## Requirements

- JDK 17
- Android SDK Platform 35
- Gradle 8.9
- Android Gradle Plugin 8.7.3

## Build

In Android Studio, open this repository and allow Gradle sync. Then run:

```bash
gradle :app:assembleDebug
```

Expected output: `app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

- `app/src/main/java/com/frameforge/app/ui`: Compose app shell and screens
- `app/src/main/java/com/frameforge/app/ui/theme`: light design system
- `app/src/main/java/com/frameforge/app/data`: Room entities, DAO, database and repository
- `app/src/main/AndroidManifest.xml`: Android app entry configuration

## Roadmap

1. Foundation, onboarding, media indexing, project recovery and tests
2. Shared non-destructive photo/video editing engine and real export
3. Cinema, Motion, Audio and Template studios
4. AI Studio with mandatory preview and explicit approval
5. Optional cloud sync, performance, privacy and release validation

## Privacy and safety

The current foundation does not upload media. Android's document picker is used for user-selected files. AI and cloud features are not implemented. Deleting a project record does not delete original media. Never commit API keys, passwords or signing credentials.
