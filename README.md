# FRAMEFLOW AI — Local script-to-video studio

FRAMEFLOW turns a written script into a narrated MP4 using an on-device image-generation model.

## Actual generation flow

1. Paste a script and choose the output aspect ratio.
2. Android Text-to-Speech creates the narration and FrameFlow measures its actual duration.
3. The script planner maps the story's sentence-level beats onto that measured narration timeline.
4. Frame one establishes the characters, setting and visual style. Every later second receives the preceding generated image as its img2img reference, and the current complete script sentence tells the model how the scene advances.
5. FrameFlow creates one image for every narration second, burns the matching short caption into each frame, and combines all frames with the narration into one H.264 MP4.

The captions are evenly allocated across the measured narration timeline; this is not phoneme-level forced alignment. Character consistency is encouraged through a previous-frame reference and low image-to-image denoising, but no diffusion model can guarantee pixel-identical faces in every frame.

## Offline image generation

- Native runtime: MNN / Nezumi Kiln Stable Diffusion 1.5 engine.
- Model: majicMIX Realistic v6 in MNN format, including a VAE encoder for image-to-image continuity.
- The APK contains the native arm64 runtime, not the model weights.
- On first use, the model downloads once from its publisher to private app storage. After installation, image inference does not use an API, API key or cloud generation provider; it can run offline.
- The model archive is approximately 1.18 GB. The app checks for at least 3 GB free storage; the model publisher recommends 5 GB free and 6 GB+ RAM, with 4 GB RAM / 3 GB storage as minimum guidance.
- Runtime tries MNN OpenCL and falls back to CPU if OpenCL model loading fails. Generation can be slow on a mid-range phone and may take substantially longer than the final video's duration.

## Licences

- FrameFlow-authored code: MIT.
- MNN runtime: Apache-2.0.
- Nezumi Kiln / mnn-sd-engine: LGPL-3.0.
- majicMIX Realistic v6 model weights: CreativeML OpenRAIL-M. Attribution is required and use-based restrictions apply.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and review the linked model card and licence before downloading the weights.

## Build

GitHub Actions builds MNN and the JNI inference engine for Android arm64, packages the native libraries, runs unit tests, and assembles the debug APK.
