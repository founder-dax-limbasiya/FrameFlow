# FRAMEFLOW AI — Android script-to-video studio

Product flow: script → on-device narration → one model-generated image per narration second → captioned sequence → synchronized MP4.

FrameFlow is a native Android app. It does not use an inference API, image-generation cloud, API key, fal.ai or AI Horde. The first video requires a one-time download of the image model; after it is installed, visual generation runs locally on the phone and can work offline.

## Image generation

- Runtime: MNN / Nezumi Kiln (Stable Diffusion 1.5, OpenCL where supported, CPU fallback).
- Model: Mouserat/majicMIX_realistic_v6-mnn, MNN-format SD 1.5 with a VAE encoder included for img2img continuity.
- First download: approximately 1.18 GB compressed; extracted model files are approximately 1.3 GB.
- Storage: model publisher lists 3 GB free space and 4 GB RAM as minimum; 5 GB free and 6 GB+ RAM are recommended.
- Licence: CreativeML OpenRAIL-M for the model weights. Attribution and model-use restrictions apply. The MNN image-engine integration is LGPL-3.0. FrameFlow shows model licence information before the first download.

The first image establishes the scene. Each following second uses the immediately previous image as an img2img reference, with the next script moment guiding the change. The app does not create random independent frames. A 60-second narration therefore creates 60 sequential images, then assembles them with voiceover and captions into one MP4.

On-device generation is compute-heavy: a 60-frame video can take considerably longer than one minute, depending on phone RAM, GPU and thermal limits. Visual continuity is encouraged by the previous-frame reference but cannot guarantee every face or object remains pixel-identical.

## Narration and export

1. Paste a script, choose narration language and output aspect ratio.
2. Android Text-to-Speech creates the narration WAV and FrameFlow measures its actual duration.
3. The script planner creates one visual beat per second of measured narration.
4. MNN generates one opening image, then edits the prior frame for every next story moment.
5. Matching captions are burned into each image.
6. AndroidX Media3 Transformer combines the frames and narration into an H.264 MP4.
7. The finished video is saved under Movies / FrameFlow AI.

Caption splitting is proportional across measured narration; it is not phoneme-level forced alignment.

## Build

GitHub Actions builds MNN and the JNI image engine for arm64-v8a, packages the three native libraries, runs Android unit tests and assembles the debug APK. The model weights are not bundled in the APK; users download them once from the model publisher after accepting the model licence.

## Licences

- FrameFlow-authored code: MIT.
- MNN: Apache-2.0.
- Nezumi Kiln / mnn-sd-engine: LGPL-3.0.
- majicMIX Realistic v6 weights: CreativeML OpenRAIL-M; attribution and use restrictions apply.

See THIRD_PARTY_NOTICES.md.
