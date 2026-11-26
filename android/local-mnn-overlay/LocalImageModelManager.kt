package ai.frameflow.app

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/** Downloads and installs a community MNN-format SD 1.5 model once for offline use. */
object LocalImageModelManager {
    const val MODEL_NAME = "majicMIX Realistic v6 · SD 1.5"
    const val MODEL_CARD_URL = "https://huggingface.co/Mouserat/majicMIX_realistic_v6-mnn"
    const val LICENSE_URL = "https://huggingface.co/spaces/CompVis/stable-diffusion-license"
    private const val MODEL_URL = "https://huggingface.co/Mouserat/majicMIX_realistic_v6-mnn/resolve/main/majicMIX_realistic_v6-int8-block32-img2img.zip?download=true"
    private const val MIN_FREE_BYTES = 3L * 1024L * 1024L * 1024L

    fun modelDirectory(context: Context): File = File(context.filesDir, "models/majicmix-realistic-v6-mnn")

    fun isInstalled(context: Context): Boolean {
        val dir = modelDirectory(context)
        return File(dir, "model.json").isFile &&
            File(dir, "unet.mnn").isFile &&
            File(dir, "unet.mnn.weight").isFile &&
            (File(dir, "vae_encoder_fp16.mnn").isFile || File(dir, "vae_encoder.mnn").isFile)
    }

    suspend fun ensureInstalled(
        context: Context,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long, message: String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val installed = modelDirectory(context)
        if (isInstalled(context)) return@withContext installed

        val available = StatFs(context.filesDir.absolutePath).availableBytes
        if (available < MIN_FREE_BYTES) {
            throw IOException("FrameFlow needs at least 3 GB free storage to download and unpack the offline image model. Free up space and try again.")
        }
        val modelParent = installed.parentFile ?: throw IOException("Could not find app model storage.")
        check(modelParent.mkdirs() || modelParent.isDirectory) { "Could not create the local model folder." }
        val archive = File(context.cacheDir, "majicmix-realistic-v6-download.zip")
        val extracting = File(modelParent, "majicmix-realistic-v6-extracting")
        archive.delete()
        if (extracting.exists()) extracting.deleteRecursively()
        check(extracting.mkdirs() || extracting.isDirectory) { "Could not prepare model extraction storage." }

        try {
            downloadArchive(archive, onProgress)
            onProgress(archive.length(), archive.length(), "Unpacking the model onto your phone…")
            extractZipSafely(archive, extracting)
            val root = findModelRoot(extracting)
                ?: throw IOException("The model archive is missing its UNet, VAE encoder or model.json files.")
            if (installed.exists()) check(installed.deleteRecursively()) { "Could not replace the incomplete local model folder." }
            if (root == extracting) {
                check(extracting.renameTo(installed)) { "Could not finish installing the local image model." }
            } else {
                root.copyRecursively(installed, overwrite = true)
                extracting.deleteRecursively()
            }
            if (!isInstalled(context)) throw IOException("The model installation did not pass its file checks. Please download it again.")
            installed
        } catch (failure: Throwable) {
            extracting.deleteRecursively()
            throw failure
        } finally {
            archive.delete()
        }
    }

    private suspend fun downloadArchive(
        destination: File,
        onProgress: suspend (Long, Long, String) -> Unit
    ) {
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 90_000
            setRequestProperty("User-Agent", "FrameFlow-AI-Android")
        }
        try {
            val response = connection.responseCode
            if (response !in 200..299) throw IOException("Hugging Face model download failed (HTTP $response). Please retry when internet is stable.")
            val total = connection.contentLengthLong
            if (total > 0L && total > 2_500_000_000L) throw IOException("The model archive is unexpectedly large; download cancelled for safety.")
            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(FileOutputStream(destination)).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var downloaded = 0L
                    var lastReport = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (downloaded - lastReport >= 4L * 1024L * 1024L) {
                            val pct = if (total > 0L) "${(downloaded * 100L / total).coerceIn(0L, 100L)}%" else "${downloaded / (1024L * 1024L)} MB"
                            onProgress(downloaded, total, "Downloading offline image model · $pct")
                            lastReport = downloaded
                        }
                    }
                    output.flush()
                    if (downloaded < 900_000_000L) throw IOException("The model download was incomplete (${downloaded / (1024L * 1024L)} MB). Please retry.")
                    onProgress(downloaded, if (total > 0L) total else downloaded, "Model download complete · preparing offline files…")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractZipSafely(zipFile: File, destination: File) {
        val root = destination.canonicalFile
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val output = File(destination, entry.name).canonicalFile
                if (output != root && !output.path.startsWith(root.path + File.separator)) {
                    throw IOException("The model archive contains an unsafe file path.")
                }
                if (entry.isDirectory) {
                    if (!output.mkdirs() && !output.isDirectory) throw IOException("Could not create a model directory.")
                } else {
                    output.parentFile?.let { if (!it.mkdirs() && !it.isDirectory) throw IOException("Could not create a model folder.") }
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(output).use { out -> input.copyTo(out, 1024 * 1024) }
                    }
                }
            }
        }
    }

    private fun findModelRoot(root: File): File? {
        val dirs = sequenceOf(root) + root.walkTopDown().filter { it.isDirectory }.take(40)
        return dirs.firstOrNull { dir ->
            File(dir, "model.json").isFile &&
                File(dir, "unet.mnn").isFile &&
                File(dir, "unet.mnn.weight").isFile &&
                (File(dir, "vae_encoder_fp16.mnn").isFile || File(dir, "vae_encoder.mnn").isFile)
        }
    }
}
