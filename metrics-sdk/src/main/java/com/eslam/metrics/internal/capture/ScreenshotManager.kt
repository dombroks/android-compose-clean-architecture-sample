package com.eslam.metrics.internal.capture

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.Window
import com.eslam.metrics.internal.bridge.NativeBridge
import java.util.concurrent.atomic.AtomicLong

/**
 * ScreenshotManager - Captures screenshots and processes them in C++
 *
 * Kotlin only:
 * - Captures bitmap using PixelCopy (Android API requirement)
 * - Passes bitmap to C++ for ALL processing
 * - Receives base64-encoded JPEG string
 * 
 * C++ handles:
 * - Downscaling
 * - JPEG compression
 * - Base64 encoding
 */
internal class ScreenshotManager(
    private val nativeBridge: NativeBridge,
    private val minIntervalMs: Long = 1000 // Minimum 1 second between screenshots
) {
    
    private val handlerThread = HandlerThread("ScreenshotThread").apply { start() }
    private val handler = Handler(handlerThread.looper)
    private val lastCaptureTime = AtomicLong(0)

    interface ScreenshotCallback {
        fun onSuccess(base64Data: String)
        fun onFailure(reason: String)
    }

    /**
     * Capture a screenshot and return base64-encoded JPEG data.
     * If force is true, throttling is bypassed (for critical events like ANR/crash).
     */
    fun captureScreenshot(
        activity: Activity,
        callback: ScreenshotCallback,
        force: Boolean = false
    ) {
        // Check throttling (unless forced for critical events)
        if (!force) {
            val now = SystemClock.elapsedRealtime()
            val lastCapture = lastCaptureTime.get()
            if (now - lastCapture < minIntervalMs) {
                callback.onFailure("Throttled - too soon since last screenshot")
                return
            }
        }
        
        // Skip if in low memory state (checked in native)
        if (nativeBridge.nativeIsLowMemory()) {
            callback.onFailure("Low memory - screenshot skipped")
            return
        }

        // Update last capture time
        lastCaptureTime.set(SystemClock.elapsedRealtime())

        val window = activity.window
        val decorView = window.decorView

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            captureWithPixelCopy(window, decorView, callback)
        } else {
            captureWithViewDraw(decorView, callback)
        }
    }

    private fun captureWithPixelCopy(
        window: Window,
        view: View,
        callback: ScreenshotCallback
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            callback.onFailure("PixelCopy requires API 26+")
            return
        }

        val width = view.width
        val height = view.height

        if (width <= 0 || height <= 0) {
            callback.onFailure("Invalid view dimensions")
            return
        }

        // Create bitmap - this is the only Kotlin processing, unavoidable Android API
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            nativeBridge.nativeSetLowMemory(true)
            callback.onFailure("Out of memory creating bitmap")
            return
        }

        val srcRect = Rect(0, 0, width, height)

        try {
            PixelCopy.request(
                window,
                srcRect,
                bitmap,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        processInNative(bitmap, callback)
                    } else {
                        bitmap.recycle()
                        callback.onFailure("PixelCopy failed with code: $copyResult")
                    }
                },
                handler
            )
        } catch (e: Exception) {
            bitmap.recycle()
            callback.onFailure("PixelCopy exception: ${e.message}")
        }
    }

    private fun captureWithViewDraw(
        view: View,
        callback: ScreenshotCallback
    ) {
        // Fallback for older devices
        handler.post {
            try {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                view.draw(canvas)
                processInNative(bitmap, callback)
            } catch (e: OutOfMemoryError) {
                nativeBridge.nativeSetLowMemory(true)
                callback.onFailure("Out of memory")
            } catch (e: Exception) {
                callback.onFailure("View.draw failed: ${e.message}")
            }
        }
    }

    /**
     * Send bitmap to C++ for ALL processing.
     * C++ handles: downscaling, JPEG compression, base64 encoding.
     */
    private fun processInNative(
        bitmap: Bitmap,
        callback: ScreenshotCallback
    ) {
        handler.post {
            try {
                // All processing happens in C++
                val base64Data = nativeBridge.nativeProcessBitmapToBase64(bitmap)
                bitmap.recycle()
                
                if (base64Data != null) {
                    callback.onSuccess(base64Data)
                } else {
                    callback.onFailure("Native processing failed")
                }
            } catch (e: Exception) {
                bitmap.recycle()
                callback.onFailure("Processing exception: ${e.message}")
            }
        }
    }

    fun setLowMemoryState(isLowMemory: Boolean) {
        nativeBridge.nativeSetLowMemory(isLowMemory)
    }

    fun shutdown() {
        handlerThread.quitSafely()
    }
}
