# FrameFlow

A mobile-first, browser-based image-to-video storyboard editor. The web MVP is a static page: Vercel serves the application, while images, projects and video rendering stay on the user's device.

## Features
- Multi-image import (common browser-supported image formats)
- Local project persistence with IndexedDB, including image blobs
- Per-image duration, frame reordering and remove controls
- Portrait 9:16, landscape 16:9 and square 1:1 output
- 720p / 1080p canvas, 24 / 30 / 60 fps settings
- Animated canvas preview with gentle zoom and dissolve/cut transitions
- Local video recording and download using MediaRecorder
- MP4 when a supported MP4 encoder is available; otherwise WebM

## Deploy to Vercel
Import this GitHub repository into Vercel. Choose **Other** as the framework preset, leave the build command blank, and set the output directory to `.`. There are no environment variables, build dependencies or backend services.

## Run locally
```bash
python3 -m http.server 8080
```
Open http://localhost:8080.

## Native Android app

The native Android implementation is Kotlin + Jetpack Compose, with local project storage and AndroidX Media3 Transformer for MP4 rendering. Its source archive is stored in `android/native-source.parts/`; the GitHub Actions workflow reconstructs it on the runner.

To build an installable debug APK:
1. Open the repository's **Actions** tab.
2. Select **Build FrameFlow Android APK**.
3. Select **Run workflow** on `main`, then wait for the job to finish.
4. Open the successful run and download the `frameflow-ai-debug-apk` artifact.

The workflow runs unit tests before assembling the debug APK. Artifacts are retained for 14 days. A debug APK is intended for testing and is not a Play Store release; a production release will need a release-signing key.

## Native Android MVP scope

- Kotlin + Jetpack Compose dark mobile UI
- Create, save, reopen and delete projects locally
- Multi-image import, ordering and per-image duration
- 9:16, 16:9 and 1:1 output
- Dissolve transition frames, gentle zoom and H.264 MP4 export
- Save rendered videos into Movies/FrameFlow AI

## Current limitations
- Rendering runs in real time. Keep the tab open and foregrounded until export completes.
- MP4 availability depends on the browser/device encoder; WebM is the fallback.
- Audio tracks, voiceovers, subtitles, RIFE interpolation, AI image generation, cloud sync and Android APK packaging are not part of this web MVP yet.
- Long/high-resolution videos can use substantial phone memory. Start with a short 720p sequence.
- Projects stay in the current browser profile. Clearing site data can remove them; download finished videos.

## Privacy
Images and projects are stored in browser IndexedDB and are not uploaded to a server. No account or server is required.
