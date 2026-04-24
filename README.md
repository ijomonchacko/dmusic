# Dmusic Music Player

> Partial codebase for reference only. Check the Releases tab to test the app.

A Flutter-based high-fidelity music player with a native BASS audio engine, cloud sync support, local library management, and offline playback.

## Overview

This project combines a custom native audio pipeline with modern Flutter UI to deliver reliable playback across mobile and desktop targets. It includes cloud upload/download workflows, queue-based playback, and a feature-rich settings stack for audio, storage, and interface control.

## Core Features

### Audio and Playback

- Native BASS audio engine integration through platform channels.
- High-quality playback for common audio formats (MP3, FLAC, WAV, OGG, Opus, M4A, AAC, WebM).
- Queue management with dedicated queue screen.
- Background playback and media session support.
- Multiple audio controls: equalizer, presets, loudness, output routing, and diagnostics screens.

### Cloud Sync API Features

- Cloud library fetch with pagination-aware handling and resilient parsing.
- Upload local tracks to cloud with metadata support (title, artist, album, duration).
- Per-file upload progress, cancel single upload, and cancel-all upload flows.
- Cloud track streaming support with authenticated session headers.
- Cloud track download to local storage with progress tracking.
- Cloud library actions: search, multi-select, play selected, download selected, delete selected.
- Optimistic cloud library updates so newly uploaded tracks appear quickly.

### Library, Search, and Discovery

- Local library scanning and refresh workflows.
- Search surfaces for local and cloud content.
- Artist, album, and genre browsing flows.
- Favorites management.

### Offline and Downloads

- Offline downloads screen and offline track access.
- Download manager with progress and persistence.
- Connectivity-aware offline alerts and graceful offline behavior.



## Tech Stack

- Flutter and Dart
- Native Android bridge with Kotlin + C++ audio layer
- BASS native audio libraries
- Dio and HTTP networking
- Provider, Bloc, Riverpod, and Hooks (feature-dependent usage)
- GetIt + Injectable dependency injection
- SharedPreferences, secure storage, and local file storage utilities

## Project Structure

```text
lib/
  main.dart
  src/
    core/
    features/
      auth/
      home/
      player/
      cloud_sync/
      local_library/
      downloads/
      offline/
      settings/
android/
  app/src/main/kotlin/
  app/src/main/cpp/
```

## Notes

- The app uses authenticated cloud session headers for cloud operations.
- Cloud upload and library flows include fallback handling for API shape differences.
- Native audio integration is implemented in both Kotlin bridge and C++ engine layers.

## License

See LICENSE or license.txt for licensing and disclaimer information.
