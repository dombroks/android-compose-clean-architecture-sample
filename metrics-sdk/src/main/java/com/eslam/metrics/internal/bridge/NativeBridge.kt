package com.eslam.metrics.internal.bridge

import android.graphics.Bitmap

/**
 * NativeBridge - JNI wrapper for native C++ code
 *
 * This class provides the bridge between Kotlin and the native C++ layer.
 * ALL heavy processing is performed in C++ for zero UI lag.
 * 
 * Kotlin only:
 * - Captures bitmap (Android API requirement)
 * - Calls native functions
 * - Receives processed data (base64 strings)
 */
internal class NativeBridge {

    companion object {
        init {
            System.loadLibrary("metrics_sdk")
        }
    }

    // ==================== Initialization ====================
    
    external fun nativeInit(storagePath: String)
    external fun nativeSetEventCallback(callback: NativeEventCallback)

    // ==================== Session Management ====================
    
    external fun nativeStartSession(): String
    external fun nativeEndSession()
    external fun nativePauseSession()
    external fun nativeResumeSession()
    external fun nativeGetSessionId(): String
    external fun nativeGetSessionDuration(): Long
    external fun nativeSetUserInfo(userId: String, email: String?)

    // ==================== Metrics ====================
    
    external fun nativeRecordMemoryMetrics(totalMb: Long, usedMb: Long, availableMb: Long)
    external fun nativeRecordCpuMetrics(usagePercentage: Float, coreCount: Int)
    external fun nativeIsMemorySpike(): Boolean

    // ==================== Events ====================
    
    external fun nativeStartWatchdog()
    external fun nativeStopWatchdog()
    external fun nativePingWatchdog()
    external fun nativeRecordEvent(eventType: Int, name: String, metadata: String)
    external fun nativeRecordHeavyAction(name: String, metadata: String)
    external fun nativeRecordCrash(stackTrace: String)

    // ==================== Image Processing (ALL in C++) ====================
    
    /**
     * Process bitmap in C++ and return base64-encoded JPEG string.
     * ALL processing happens in native code:
     * 1. Read bitmap pixels
     * 2. Downscale to configured dimensions
     * 3. Compress to JPEG
     * 4. Encode to base64
     * 
     * @param bitmap The bitmap to process (from PixelCopy)
     * @return Base64-encoded JPEG string, or null on failure
     */
    external fun nativeProcessBitmapToBase64(bitmap: Bitmap): String?
    
    /**
     * Decode base64 string back to JPEG bytes.
     * Used for extracting/viewing screenshots.
     * 
     * @param base64String Base64-encoded JPEG data
     * @return JPEG bytes, or null on failure
     */
    external fun nativeDecodeBase64ToBytes(base64String: String): ByteArray?
    
    /**
     * Set image processing configuration.
     * 
     * @param targetWidth Target width for downscaling
     * @param targetHeight Target height for downscaling  
     * @param quality JPEG quality (0-100)
     */
    external fun nativeSetImageConfig(targetWidth: Int, targetHeight: Int, quality: Int)
    
    /**
     * Set low memory state - native code will skip processing if true.
     */
    external fun nativeSetLowMemory(isLowMemory: Boolean)
    
    /**
     * Check if in low memory state.
     */
    external fun nativeIsLowMemory(): Boolean

    // ==================== Cleanup ====================
    
    external fun nativeReset()
}

/**
 * Callback interface for native events
 */
internal interface NativeEventCallback {
    fun onEvent(eventType: Int, name: String, metadata: String, timestampMs: Long)
}
