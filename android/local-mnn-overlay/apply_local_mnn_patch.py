#!/usr/bin/env python3
import sys
from pathlib import Path

root = Path(sys.argv[1])
main = root / "app/src/main/java/ai/frameflow/app/MainActivity.kt"
text = main.read_text()
text = text.replace("import android.content.Intent\n", "")
text = text.replace("import androidx.compose.ui.text.input.PasswordVisualTransformation\n", "")
text = text.replace('    var hordeKey by remember { mutableStateOf("") }\n', '''
    val modelPreferences = remember { context.getSharedPreferences("frameflow-local-models", 0) }
    var modelInstalled by remember(project.id) { mutableStateOf(LocalImageModelManager.isInstalled(context)) }
    var modelLicenseAccepted by remember {
        mutableStateOf(modelPreferences.getBoolean("majicmix_openrail_accepted", false))
    }
    var showModelLicense by remember { mutableStateOf(false) }
''')

section_start = text.index('                SectionHeading("04 · VIDEO FORMAT"')
section_end = text.index('                SectionHeading("05 · VISUAL CONTINUITY"', section_start)
video_section = '''
                SectionHeading("04 · VIDEO FORMAT", "Choose the platform for the final MP4")
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FrameAspect.values().forEach { option ->
                        val selected = option == aspect
                        Surface(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).clickable(enabled = !isWorking) {
                                aspect = option
                                onChange(project.copy(script = script, languageTag = languageTag, aspect = option))
                            },
                            color = if (selected) AccentSoft else SurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Accent else Stroke),
                            shape = RoundedCornerShape(11.dp)
                        ) {
                            Column(Modifier.padding(vertical = 15.dp, horizontal = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(option.label, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (selected) Accent else Color.White)
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    when (option) {
                                        FrameAspect.PORTRAIT -> "Reels / Shorts"
                                        FrameAspect.LANDSCAPE -> "YouTube"
                                        FrameAspect.SQUARE -> "Square"
                                    },
                                    color = SecondaryText,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable { showAdvanced = !showAdvanced },
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Stroke)
                ) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("ON-DEVICE IMAGE MODEL", fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
                            Text(
                                if (modelInstalled) "Stable Diffusion 1.5 · downloaded · offline ready"
                                else "Stable Diffusion 1.5 · first download about 1.18 GB",
                                color = if (modelInstalled) Success else SecondaryText,
                                fontSize = 10.sp
                            )
                        }
                        Text(if (showAdvanced) "−" else "+", color = Accent, fontSize = 20.sp)
                    }
                }
                if (showAdvanced) {
                    Spacer(Modifier.height(8.dp))
                    Text("The model runs on this phone with MNN (OpenCL when available, CPU fallback). It is downloaded once, stored in app storage, and used locally for every frame. No inference API or API key. The first download needs internet; after installation, image generation works offline.", color = SecondaryText, fontSize = 10.sp, lineHeight = 15.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("Model download: about 1.18 GB; extracted files about 1.3 GB. Model card recommends 6 GB+ RAM and 5 GB free storage (4 GB RAM / 3 GB free minimum).", color = SecondaryText, fontSize = 10.sp, lineHeight = 15.sp)
                    TextButton(onClick = { showModelLicense = true }, enabled = !isWorking) {
                        Text("Model licence & attribution", color = Accent, fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(20.dp))
'''
text = text[:section_start] + video_section + text[section_end:]

button_start = text.index('                    Button(\n                        onClick = {', text.index('if (isWorking)'))
modifier_at = text.index('                        modifier = Modifier.fillMaxWidth().height(54.dp)', button_start)
button_prefix = '''                    Button(
                        onClick = {
                            if (script.isBlank()) {
                                errorMessage = "Paste your script first."
                            } else if (!modelLicenseAccepted) {
                                showModelLicense = true
                            } else {
                                startGeneration()
                            }
                        },
'''
text = text[:button_start] + button_prefix + text[modifier_at:]

old_footer = 'Text("AI Horde generates the visuals online. Each image uses the previous frame as a visual reference to maintain continuity. Narration audio, captions and the finished video stay on this phone. Free community workers may queue slowly.", color = SecondaryText, fontSize = 9.sp, lineHeight = 14.sp)'
new_footer = 'Text("One image is generated for every narration second. Frame one establishes the scene; each later image edits the previous frame using the next script moment. The model runs locally after its first download.", color = SecondaryText, fontSize = 9.sp, lineHeight = 14.sp)'
text = text.replace(old_footer, new_footer)

back_handler = '    BackHandler(enabled = isWorking) { errorMessage = "Video creation is running. Keep FrameFlow open until it finishes." }\n'
if back_handler not in text:
    raise SystemExit("Could not find EditorScreen BackHandler anchor")

helper = r'''
    fun startGeneration() {
        if (isWorking) return
        scope.launch {
            isWorking = true
            progress = 0f
            exportedUri = null
            var preparedTemp: File? = null
            var output: File? = null
            try {
                stage = if (modelInstalled) "Checking the offline image model…" else "Downloading the offline image model for first use…"
                progress = 0.02f
                val modelDirectory = LocalImageModelManager.ensureInstalled(context) { downloaded, total, message ->
                    withContext(Dispatchers.Main) {
                        stage = message
                        val fraction = if (total > 0L) downloaded.toFloat() / total.toFloat() else 0f
                        progress = 0.02f + 0.22f * fraction.coerceIn(0f, 1f)
                    }
                }
                modelInstalled = true
                stage = "Creating narration from your script…"
                progress = 0.25f
                val projectDirectory = File(context.filesDir, "projects/" + project.id)
                check(projectDirectory.mkdirs() || projectDirectory.isDirectory) { "Could not create the project folder." }
                val audio = NarrationGenerator.synthesize(
                    context = context,
                    script = script,
                    languageTag = languageTag,
                    outputFile = File(projectDirectory, "narration.wav")
                )
                stage = "Narration ready · planning a continuous visual sequence"
                progress = 0.32f
                val generated = LocalImageGenerator.generateProjectFrames(
                    context = context,
                    modelDirectory = modelDirectory,
                    projectId = project.id,
                    script = script,
                    narrationDurationMs = audio.durationMs,
                    aspect = aspect
                ) { done, total, message ->
                    withContext(Dispatchers.Main) {
                        stage = message
                        progress = 0.32f + 0.47f * (done.toFloat() / total.coerceAtLeast(1))
                    }
                }
                var videoProject = project.copy(
                    script = script,
                    languageTag = languageTag,
                    aspect = aspect,
                    narrationPath = audio.file.absolutePath,
                    actualDurationMs = audio.durationMs,
                    frames = generated.frames,
                    exportedPath = null
                )
                onChange(videoProject)
                stage = "Compositing " + generated.frames.size + " captioned frames and syncing narration…"
                progress = 0.84f
                val prepared = MediaExporter.prepare(context, videoProject)
                preparedTemp = prepared.temporaryDirectory
                output = File(context.cacheDir, "frameflow_" + project.id + ".mp4")
                if (output.exists()) output.delete()
                val completed = CompletableDeferred<Unit>()
                val transformer = Transformer.Builder(context.applicationContext)
                    .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                            if (!completed.isCompleted) completed.complete(Unit)
                        }
                        override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (!completed.isCompleted) completed.completeExceptionally(exportException)
                        }
                    })
                    .build()
                stage = "Rendering final MP4 on your phone…"
                progress = 0.88f
                transformer.start(prepared.composition, output.absolutePath)
                val holder = ProgressHolder()
                while (!completed.isCompleted) {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        withContext(Dispatchers.Main) { progress = 0.88f + holder.progress / 100f * 0.11f }
                    }
                    delay(300L)
                }
                completed.await()
                check(output.exists() && output.length() > 0L) { "The render finished without creating an MP4 file." }
                stage = "Saving finished video…"
                val saved = MediaExporter.saveToGallery(context, output, videoProject.title)
                videoProject = videoProject.copy(exportedPath = saved.toString())
                onChange(videoProject)
                exportedUri = saved
                progress = 1f
                stage = "Video saved to Movies / FrameFlow AI"
                errorMessage = "Your script video is ready. " + generated.frames.size + " connected visuals, captions and narration are synced in the MP4."
            } catch (failure: Throwable) {
                errorMessage = failure.message ?: "FrameFlow could not finish this video."
            } finally {
                preparedTemp?.deleteRecursively()
                output?.delete()
                isWorking = false
            }
        }
    }
'''
text = text.replace(back_handler, back_handler + helper + "\n", 1)

license_dialog = r'''
    if (showModelLicense) {
        AlertDialog(
            onDismissRequest = { showModelLicense = false },
            title = { Text("On-device model licence") },
            text = {
                Text(
                    "FrameFlow will download majicMIX Realistic v6 (Stable Diffusion 1.5, MNN format) from Hugging Face. The model is licensed under CreativeML OpenRAIL-M: attribution is required and its use restrictions apply. The MNN inference engine is separately licensed under LGPL-3.0. The model is not sent to a cloud inference provider. First-time download is about 1.18 GB; after installation, generation runs locally. Read the model card and full model licence before accepting.",
                    color = SecondaryText
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    modelLicenseAccepted = true
                    modelPreferences.edit().putBoolean("majicmix_openrail_accepted", true).apply()
                    showModelLicense = false
                    startGeneration()
                }) { Text("Accept & continue", color = Accent) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(LocalImageModelManager.MODEL_CARD_URL)))
                    }) { Text("Model card", color = Accent) }
                    TextButton(onClick = { showModelLicense = false }) { Text("Cancel", color = SecondaryText) }
                }
            },
            containerColor = SurfaceDark
        )
    }

'''
anchor = '    errorMessage?.let { message ->\n'
if anchor not in text:
    raise SystemExit("Could not find EditorScreen error dialog anchor")
text = text.replace(anchor, license_dialog + anchor, 1)

if "falKey" in text or "OpenModelImageGenerator" in text:
    raise SystemExit("Hosted inference references remain in MainActivity")
main.write_text(text)

gradle = root / "app/build.gradle.kts"
g = gradle.read_text().replace("minSdk = 29", "minSdk = 31")
g = g.replace('''    packaging {
        resources {''', '''    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {''')
gradle.write_text(g)

(root / "README.md").write_text("""# FRAMEFLOW AI — Android script-to-video studio

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
""")
(root / "THIRD_PARTY_NOTICES.md").write_text("""# Third-party notices

## MNN
MNN is an open-source deep learning inference engine. It is distributed under Apache License 2.0. Source: https://github.com/mouse0329/MNN

## Nezumi Kiln / mnn-sd-engine
FrameFlow integrates the mnn-sd-engine native image-generation engine from https://github.com/mouse0329/nezumi-ai/tree/main/mnn-sd-engine. The upstream project is dual-licensed under GNU Lesser General Public License v3.0 or a separate commercial licence. FrameFlow uses the LGPL-3.0 option. Users may replace the LGPL library with a compatible modified build as permitted by LGPL-3.0.

Full licence: https://www.gnu.org/licenses/lgpl-3.0.html

## majicMIX Realistic v6 model weights
Model: https://huggingface.co/Mouserat/majicMIX_realistic_v6-mnn
Base checkpoint: https://huggingface.co/emilianJR/majicMIX_realistic_v6

The MNN-format model weights are distributed under CreativeML OpenRAIL-M. Attribution is required and the use-based restrictions in that licence apply. FrameFlow does not bundle the model weights; users download the model directly from the publisher and are shown the licence information before downloading it.

Full licence: https://huggingface.co/spaces/CompVis/stable-diffusion-license

## AndroidX / Media3
AndroidX and AndroidX Media3 are provided by Google under their respective Apache License 2.0 terms. Sources and notices: https://github.com/androidx/androidx
""")

print("Applied local-only MNN integration patch.")
