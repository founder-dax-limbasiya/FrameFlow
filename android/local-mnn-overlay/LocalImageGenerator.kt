package ai.frameflow.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.nezumi_ai.sd.MnnSdNative
import com.nezumi_ai.sd.MnnProgressCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Fully on-device MNN Stable Diffusion generation. No inference API or API key. */
object LocalImageGenerator {
    private const val TAG = "FrameFlowLocalSD"
    private const val NEGATIVE_PROMPT = "low quality, blurry, deformed, distorted face, extra limbs, inconsistent clothing, text, watermark, logo, random collage, multiple panels"
    private const val STEPS = 16
    private const val CFG = 5.8f
    private const val SEED = 774921L
    private const val DENOISE_STRENGTH = 0.42f

    data class Result(val frames: List<StoryFrame>, val completedImages: Int, val totalImages: Int)

    suspend fun generateProjectFrames(
        context: Context,
        modelDirectory: File,
        projectId: String,
        script: String,
        narrationDurationMs: Long,
        aspect: FrameAspect,
        onProgress: suspend (completed: Int, total: Int, message: String) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        require(modelDirectory.isDirectory) { "Download the offline image model before generating the video." }
        val beats = ScriptPlanner.plan(script, narrationDurationMs)
        val outputDir = File(context.filesDir, "projects/$projectId/frames")
        if (outputDir.exists()) check(outputDir.deleteRecursively()) { "Could not clear the previous storyboard." }
        check(outputDir.mkdirs() || outputDir.isDirectory) { "Could not create the storyboard folder." }

        val (width, height) = dimensionsFor(aspect)
        val handle = createLoadedEngine(modelDirectory)
        try {
            val capabilities = runCatching {
                JSONObject(MnnSdNative.getCapabilitiesNative(handle).orEmpty())
            }.getOrElse { throw IOException("Could not read local image model capabilities.", it) }
            if (capabilities.optInt("supports_img2img", 0) != 1) {
                throw IOException("This model package has no VAE image encoder, so frame-to-frame continuity is unavailable.")
            }

            val generated = ArrayList<StoryFrame>(beats.size)
            var previousRgb: ByteArray? = null
            beats.forEachIndexed { index, beat ->
                val prompt = ScriptPlanner.visualPrompt(beats, index)
                onProgress(index, beats.size, "Generating visual ${index + 1} of ${beats.size} on this phone…")
                val rgb = generateRgbFrame(handle, prompt, previousRgb, width, height)
                val file = File(outputDir, "frame_${beat.second.toString().padStart(4, '0')}.jpg")
                writeJpeg(rgb, width, height, file)
                generated += StoryFrame(
                    imagePath = file.absolutePath,
                    durationMs = beat.endMs - beat.startMs,
                    caption = beat.caption,
                    visualPrompt = prompt,
                    second = beat.second
                )
                previousRgb = rgb
                onProgress(index + 1, beats.size, "Created ${index + 1} of ${beats.size} connected offline images")
            }
            check(generated.size == beats.size && generated.map { it.second } == (1..beats.size).toList()) {
                "The local image model did not create one frame for every narration second."
            }
            Result(generated, generated.size, beats.size)
        } finally {
            runCatching { MnnSdNative.unload(handle) }
            runCatching { MnnSdNative.destroy(handle) }
        }
    }

    private fun createLoadedEngine(modelDirectory: File): Long {
        var handle = MnnSdNative.create()
        if (handle == 0L) throw IOException("The on-device MNN image engine could not start.")
        val loadedOpenCl = runCatching {
            MnnSdNative.load(handle, modelDirectory.absolutePath, MnnSdNative.BACKEND_OPENCL, 448)
        }.getOrDefault(false)
        if (loadedOpenCl) return handle

        val openClError = MnnSdNative.getLastError()
        runCatching { MnnSdNative.destroy(handle) }
        handle = MnnSdNative.create()
        if (handle == 0L) throw IOException("The on-device image engine could not start after OpenCL fallback.")
        val loadedCpu = runCatching {
            MnnSdNative.load(handle, modelDirectory.absolutePath, MnnSdNative.BACKEND_CPU, 0)
        }.getOrDefault(false)
        if (!loadedCpu) {
            val cpuError = MnnSdNative.getLastError()
            runCatching { MnnSdNative.destroy(handle) }
            throw IOException("The local image model could not load. OpenCL: $openClError. CPU: $cpuError")
        }
        Log.i(TAG, "OpenCL was unavailable; using CPU backend.")
        return handle
    }

    private fun generateRgbFrame(
        handle: Long,
        prompt: String,
        previousRgb: ByteArray?,
        width: Int,
        height: Int
    ): ByteArray {
        val callback = MnnProgressCallback { step, total, _ ->
            if (step == total) Log.d(TAG, "Native image denoising reached $step/$total steps")
        }
        val bytes = MnnSdNative.generateNative(
            handle = handle,
            prompt = prompt,
            negativePrompt = NEGATIVE_PROMPT,
            width = width,
            height = height,
            steps = STEPS,
            cfg = CFG,
            seed = SEED,
            scheduler = MnnSdNative.SCHEDULER_DPM_PP_2M,
            initImageRgb = previousRgb,
            initImageWidth = if (previousRgb == null) 0 else width,
            initImageHeight = if (previousRgb == null) 0 else height,
            denoiseStrength = if (previousRgb == null) 0f else DENOISE_STRENGTH,
            progressCallback = callback
        ) ?: throw IOException("On-device image generation failed: ${MnnSdNative.getLastError()}")

        if (bytes.size < 8) throw IOException("The local image model returned an invalid frame buffer.")
        val outWidth = readInt32LE(bytes, 0)
        val outHeight = readInt32LE(bytes, 4)
        val expected = outWidth.toLong() * outHeight.toLong() * 3L
        if (outWidth != width || outHeight != height || expected != (bytes.size - 8).toLong()) {
            throw IOException("The local image engine returned an unexpected image size (${outWidth}×${outHeight}).")
        }
        return bytes.copyOfRange(8, bytes.size)
    }

    private fun dimensionsFor(aspect: FrameAspect): Pair<Int, Int> = when (aspect) {
        FrameAspect.PORTRAIT -> 256 to 448
        FrameAspect.LANDSCAPE -> 448 to 256
        FrameAspect.SQUARE -> 448 to 448
    }

    private fun writeJpeg(rgb: ByteArray, width: Int, height: Int, output: File) {
        val pixels = IntArray(width * height)
        var offset = 0
        for (i in pixels.indices) {
            val red = rgb[offset++].toInt() and 0xFF
            val green = rgb[offset++].toInt() and 0xFF
            val blue = rgb[offset++].toInt() and 0xFF
            pixels[i] = Color.rgb(red, green, blue)
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        try {
            output.parentFile?.let { check(it.mkdirs() || it.isDirectory) }
            output.outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 91, stream)) { "Could not save the generated local frame." }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun readInt32LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
