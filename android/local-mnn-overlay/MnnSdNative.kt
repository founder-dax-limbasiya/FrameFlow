package com.nezumi_ai.sd

/** JNI bridge for the LGPL-3.0 Nezumi Kiln / MNN Stable Diffusion engine. */
class MnnSdNative private constructor() {
    companion object {
        init { System.loadLibrary("mnn_sd_jni") }
        @JvmStatic external fun create(): Long
        @JvmStatic external fun destroy(handle: Long)
        @JvmStatic external fun load(handle: Long, modelDir: String, backend: Int, openclSafeMaxSide: Int): Boolean
        @JvmStatic external fun unload(handle: Long)
        @JvmStatic external fun isLoaded(handle: Long): Boolean
        @JvmStatic external fun getCapabilitiesNative(handle: Long): String?
        @JvmStatic external fun generateNative(
            handle: Long, prompt: String, negativePrompt: String, width: Int, height: Int,
            steps: Int, cfg: Float, seed: Long, scheduler: Int, initImageRgb: ByteArray?,
            initImageWidth: Int, initImageHeight: Int, denoiseStrength: Float,
            progressCallback: Any?
        ): ByteArray?
        @JvmStatic external fun cancel(handle: Long)
        @JvmStatic external fun getLastError(): String
        const val BACKEND_CPU = 0
        const val BACKEND_OPENCL = 1
        const val SCHEDULER_DPM_PP_2M = 3
    }
}

/** JNI resolves this exact method name/signature: onNativeProgress(IIF)V. */
class MnnProgressCallback(private val onProgress: (Int, Int, Float) -> Unit) {
    @Suppress("unused")
    fun onNativeProgress(step: Int, total: Int, elapsedSeconds: Float) {
        onProgress(step, total, elapsedSeconds)
    }
}
