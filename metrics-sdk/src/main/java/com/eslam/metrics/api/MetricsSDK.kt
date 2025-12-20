package com.eslam.metrics.api

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import com.eslam.metrics.data.repository.MetricsRepository
import com.eslam.metrics.internal.bridge.EventType
import com.eslam.metrics.internal.bridge.NativeBridge
import com.eslam.metrics.internal.bridge.NativeEventCallback
import com.eslam.metrics.internal.capture.ScreenshotManager
import com.eslam.metrics.internal.capture.TouchInterceptor
import com.eslam.metrics.internal.detection.AnrWatchdog
import com.eslam.metrics.internal.detection.CrashHandler
import com.eslam.metrics.internal.detection.MemoryMonitor
import com.eslam.metrics.internal.lifecycle.LifecycleTracker
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * MetricsSDK - Public API for Session Recording & Metrics
 *
 * A high-performance, lightweight SDK for capturing user sessions,
 * performance bottlenecks, and visual context without blocking the UI thread.
 *
 * Features:
 * - Automatic screenshot capture on touch/click events
 * - Screenshot on page/activity changes
 * - Screenshot on tracked actions and heavy actions
 * - Throttling to prevent excessive screenshots (configurable interval)
 *
 * Usage:
 * ```
 * // Initialize in Application.onCreate()
 * MetricsSDK.init(this)
 *
 * // Set user info after login
 * MetricsSDK.setUserInfo("user123", "user@example.com")
 *
 * // Track custom actions
 * MetricsSDK.trackAction("checkout_clicked")
 * MetricsSDK.trackHeavyAction("payment_processed", mapOf("amount" to 99.99))
 * ```
 */
object MetricsSDK {

    private const val TAG = "MetricsSDK"

    private var isInitialized = false
    private var config = MetricsConfig()

    private lateinit var application: Application
    private lateinit var nativeBridge: NativeBridge
    private lateinit var repository: MetricsRepository
    private lateinit var lifecycleTracker: LifecycleTracker
    private lateinit var screenshotManager: ScreenshotManager
    private lateinit var memoryMonitor: MemoryMonitor
    private lateinit var anrWatchdog: AnrWatchdog
    private lateinit var crashHandler: CrashHandler
    private lateinit var touchInterceptor: TouchInterceptor

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val mapAdapter = moshi.adapter<Map<String, Any>>(Map::class.java)

    private var currentSessionId: String? = null
    private var userId: String? = null
    private var userEmail: String? = null

    /**
     * Initialize the SDK with default configuration.
     *
     * @param context Application context
     */
    @JvmStatic
    fun init(context: Context) {
        init(context, MetricsConfig())
    }

    /**
     * Initialize the SDK with custom configuration.
     *
     * @param context Application context
     * @param config SDK configuration
     */
    @JvmStatic
    fun init(context: Context, config: MetricsConfig) {
        if (isInitialized) {
            log("SDK already initialized")
            return
        }

        this.application = context.applicationContext as Application
        this.config = config

        try {
            initializeComponents()
            isInitialized = true
            log("SDK initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SDK", e)
        }
    }

    private fun initializeComponents() {
        // Initialize native bridge
        nativeBridge = NativeBridge()
        nativeBridge.nativeInit(application.filesDir.absolutePath)

        // Set event callback
        nativeBridge.nativeSetEventCallback(object : NativeEventCallback {
            override fun onEvent(eventType: Int, name: String, metadata: String, timestampMs: Long) {
                handleNativeEvent(EventType.fromValue(eventType), name, metadata)
            }
        })

        // Initialize repository
        repository = MetricsRepository(application)

        // Set native image processing config
        nativeBridge.nativeSetImageConfig(
            config.screenshotWidth,
            config.screenshotHeight,
            config.screenshotQuality
        )

        // Initialize screenshot manager - all processing in C++
        screenshotManager = ScreenshotManager(
            nativeBridge = nativeBridge,
            minIntervalMs = config.screenshotIntervalMs
        )

        // Initialize lifecycle tracker
        lifecycleTracker = LifecycleTracker(
            application = application,
            onForeground = ::onAppForeground,
            onBackground = ::onAppBackground,
            onActivityResumedCallback = ::onActivityResumed,
            gracePeriodMs = config.gracePeriodMs
        )
        lifecycleTracker.start()

        // Initialize touch interceptor for automatic screenshot on touch/click
        if (config.enableScreenshots && config.enableTouchTracking) {
            touchInterceptor = TouchInterceptor(
                application = application,
                onTouchEvent = ::onTouchEvent
            )
            touchInterceptor.start()
        }

        // Initialize memory monitor
        if (config.enableMemoryMonitoring) {
            memoryMonitor = MemoryMonitor(
                context = application,
                nativeBridge = nativeBridge,
                onMemorySpike = ::onMemorySpike,
                onLowMemory = ::onLowMemory
            )
            memoryMonitor.start()
        }

        // Initialize ANR watchdog
        if (config.enableAnrDetection) {
            anrWatchdog = AnrWatchdog(nativeBridge)
            anrWatchdog.start()
        }

        // Initialize crash handler
        if (config.enableCrashReporting) {
            crashHandler = CrashHandler(nativeBridge) { stackTrace ->
                onCrashCaptured(stackTrace)
            }
            crashHandler.install()
        }

        // Cleanup old data
        repository.cleanupOldData(config.dataRetentionDays)
    }

    /**
     * Set user identification info.
     *
     * @param userId Unique user identifier
     * @param email Optional user email
     */
    @JvmStatic
    fun setUserInfo(userId: String, email: String? = null) {
        ensureInitialized()
        this.userId = userId
        this.userEmail = email
        nativeBridge.nativeSetUserInfo(userId, email)
        log("User info set: $userId")
    }

    /**
     * Track a simple action/event with automatic screenshot capture.
     *
     * @param name Action name
     */
    @JvmStatic
    fun trackAction(name: String) {
        ensureInitialized()
        val sessionId = currentSessionId ?: return
        
        nativeBridge.nativeRecordEvent(
            EventType.CUSTOM.value,
            name,
            "{}"
        )
        
        // Capture screenshot for actions if enabled
        if (config.enableScreenshots) {
            captureScreenshotForEvent(
                eventName = "action_$name",
                sessionId = sessionId,
                metadata = null,
                eventType = EventType.CUSTOM,
                force = false
            )
        } else {
            repository.recordEvent(
                sessionId = sessionId,
                eventType = EventType.CUSTOM,
                eventName = name,
                metadata = null,
                screenshotData = null,
                memoryUsageMb = null,
                cpuUsagePercent = null
            )
        }
        
        log("Action tracked: $name")
    }

    /**
     * Track a heavy action with metadata and screenshot capture.
     *
     * @param name Action name
     * @param meta Additional metadata
     */
    @JvmStatic
    fun trackHeavyAction(name: String, meta: Map<String, Any> = emptyMap()) {
        ensureInitialized()
        val sessionId = currentSessionId ?: return
        
        val metadataJson = try {
            mapAdapter.toJson(meta)
        } catch (e: Exception) {
            "{}"
        }

        nativeBridge.nativeRecordHeavyAction(name, metadataJson)

        // Capture screenshot if enabled (force=true for heavy actions)
        if (config.enableScreenshots) {
            captureScreenshotForEvent(
                eventName = "heavy_$name",
                sessionId = sessionId,
                metadata = metadataJson,
                eventType = EventType.HEAVY_ACTION,
                force = true // Heavy actions bypass throttling
            )
        } else {
            repository.recordEvent(
                sessionId = sessionId,
                eventType = EventType.HEAVY_ACTION,
                eventName = name,
                metadata = metadataJson,
                screenshotData = null,
                memoryUsageMb = null,
                cpuUsagePercent = null
            )
        }

        log("Heavy action tracked: $name")
    }

    /**
     * Get the current session ID.
     *
     * @return Current session ID or null if no active session
     */
    @JvmStatic
    fun getSessionId(): String? {
        if (!isInitialized) return null
        return currentSessionId
    }

    /**
     * Check if the SDK is initialized.
     *
     * @return true if initialized
     */
    @JvmStatic
    fun isInitialized(): Boolean = isInitialized

    // ==================== Screenshot Export/Decode Utilities ====================

    /**
     * Decode a base64-encoded screenshot to JPEG bytes.
     * Use this to convert stored screenshot data back to viewable images.
     * 
     * Example usage:
     * ```kotlin
     * val jpegBytes = MetricsSDK.decodeScreenshot(event.screenshotData)
     * if (jpegBytes != null) {
     *     // Save to file
     *     File("screenshot.jpg").writeBytes(jpegBytes)
     *     
     *     // Or convert to Bitmap
     *     val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
     * }
     * ```
     * 
     * @param base64Data Base64-encoded JPEG data from database
     * @return JPEG bytes, or null if decoding fails
     */
    @JvmStatic
    fun decodeScreenshot(base64Data: String?): ByteArray? {
        if (!isInitialized || base64Data.isNullOrEmpty()) return null
        return nativeBridge.nativeDecodeBase64ToBytes(base64Data)
    }

    /**
     * Decode a base64-encoded screenshot to an Android Bitmap.
     * 
     * @param base64Data Base64-encoded JPEG data from database
     * @return Bitmap, or null if decoding fails
     */
    @JvmStatic
    fun decodeScreenshotToBitmap(base64Data: String?): android.graphics.Bitmap? {
        val jpegBytes = decodeScreenshot(base64Data) ?: return null
        return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    /**
     * Save a base64-encoded screenshot to a file.
     * 
     * @param base64Data Base64-encoded JPEG data from database
     * @param outputFile File to save the JPEG to
     * @return true if saved successfully
     */
    @JvmStatic
    fun saveScreenshotToFile(base64Data: String?, outputFile: java.io.File): Boolean {
        val jpegBytes = decodeScreenshot(base64Data) ?: return false
        return try {
            outputFile.writeBytes(jpegBytes)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save screenshot", e)
            false
        }
    }

    // Internal methods

    private fun onAppForeground() {
        log("App entered foreground")
        val sessionId = nativeBridge.nativeStartSession()
        currentSessionId = sessionId
        
        repository.createSession(
            sessionId = sessionId,
            userId = userId,
            userEmail = userEmail
        )
    }

    private fun onAppBackground() {
        log("App entered background")
        val sessionId = currentSessionId ?: return
        val duration = nativeBridge.nativeGetSessionDuration()
        
        nativeBridge.nativeEndSession()
        repository.endSession(sessionId, duration)
        
        currentSessionId = null
    }

    private fun onActivityResumed(activity: Activity) {
        val activityName = activity.javaClass.simpleName
        log("Activity resumed: $activityName")
        
        // Capture screenshot on page/activity change
        if (config.enableScreenshots && config.captureOnPageChange) {
            val sessionId = currentSessionId ?: return
            captureScreenshotForEvent(
                eventName = "page_$activityName",
                sessionId = sessionId,
                metadata = """{"activity":"$activityName"}""",
                eventType = EventType.CUSTOM,
                force = false
            )
        }
    }

    private fun onTouchEvent(activity: Activity, viewInfo: String) {
        log("Touch event: $viewInfo on ${activity.javaClass.simpleName}")
        
        if (config.enableScreenshots) {
            val sessionId = currentSessionId ?: return
            captureScreenshotForEvent(
                eventName = viewInfo,
                sessionId = sessionId,
                metadata = """{"view":"$viewInfo","activity":"${activity.javaClass.simpleName}"}""",
                eventType = EventType.CUSTOM,
                force = false
            )
        }
    }

    private fun onMemorySpike() {
        log("Memory spike detected")
        val sessionId = currentSessionId ?: return

        if (config.enableScreenshots) {
            captureScreenshotForEvent(
                eventName = "memory_spike",
                sessionId = sessionId,
                metadata = null,
                eventType = EventType.MEMORY_SPIKE,
                force = true // Critical event - bypass throttling
            )
        } else {
            repository.recordEvent(
                sessionId = sessionId,
                eventType = EventType.MEMORY_SPIKE,
                eventName = "memory_spike",
                metadata = null,
                screenshotData = null,
                memoryUsageMb = null,
                cpuUsagePercent = null
            )
        }
    }

    private fun onLowMemory() {
        log("Low memory state")
        screenshotManager.setLowMemoryState(true)
    }

    private fun onCrashCaptured(stackTrace: String) {
        log("Crash captured")
        val sessionId = currentSessionId ?: return

        repository.recordEvent(
            sessionId = sessionId,
            eventType = EventType.CRASH,
            eventName = "app_crash",
            metadata = stackTrace,
            screenshotData = null,
            memoryUsageMb = null,
            cpuUsagePercent = null
        )
    }

    private fun handleNativeEvent(eventType: EventType, name: String, metadata: String) {
        val sessionId = currentSessionId ?: return
        
        when (eventType) {
            EventType.ANR -> {
                log("ANR detected from native")
                if (config.enableScreenshots) {
                    captureScreenshotForEvent(
                        eventName = "anr",
                        sessionId = sessionId,
                        metadata = metadata,
                        eventType = EventType.ANR,
                        force = true // Critical event - bypass throttling
                    )
                } else {
                    repository.recordEvent(
                        sessionId = sessionId,
                        eventType = eventType,
                        eventName = name,
                        metadata = metadata,
                        screenshotData = null,
                        memoryUsageMb = null,
                        cpuUsagePercent = null
                    )
                }
            }
            else -> {
                repository.recordEvent(
                    sessionId = sessionId,
                    eventType = eventType,
                    eventName = name,
                    metadata = metadata,
                    screenshotData = null,
                    memoryUsageMb = null,
                    cpuUsagePercent = null
                )
            }
        }
    }

    private fun captureScreenshotForEvent(
        eventName: String,
        sessionId: String,
        metadata: String?,
        eventType: EventType = EventType.HEAVY_ACTION,
        force: Boolean = false
    ) {
        val activity = lifecycleTracker.getCurrentActivity() ?: return

        screenshotManager.captureScreenshot(
            activity = activity,
            callback = object : ScreenshotManager.ScreenshotCallback {
                override fun onSuccess(base64Data: String) {
                    log("Screenshot captured (${base64Data.length} chars) for event: $eventName")
                    repository.recordEvent(
                        sessionId = sessionId,
                        eventType = eventType,
                        eventName = eventName,
                        metadata = metadata,
                        screenshotData = base64Data,
                        memoryUsageMb = null,
                        cpuUsagePercent = null
                    )
                }

                override fun onFailure(reason: String) {
                    log("Screenshot skipped ($reason) for event: $eventName")
                    // Only record event if it's a critical event or not throttled
                    if (force || !reason.contains("Throttled")) {
                        repository.recordEvent(
                            sessionId = sessionId,
                            eventType = eventType,
                            eventName = eventName,
                            metadata = metadata,
                            screenshotData = null,
                            memoryUsageMb = null,
                            cpuUsagePercent = null
                        )
                    }
                }
            },
            force = force
        )
    }

    private fun ensureInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("MetricsSDK not initialized. Call init() first.")
        }
    }

    private fun log(message: String) {
        if (config.debugLogging) {
            Log.d(TAG, message)
        }
    }

    /**
     * Shutdown the SDK and release resources.
     * Usually not needed - SDK manages its lifecycle automatically.
     */
    @JvmStatic
    fun shutdown() {
        if (!isInitialized) return

        try {
            lifecycleTracker.stop()
            
            if (config.enableScreenshots && config.enableTouchTracking && ::touchInterceptor.isInitialized) {
                touchInterceptor.stop()
            }
            
            if (config.enableMemoryMonitoring && ::memoryMonitor.isInitialized) {
                memoryMonitor.stop()
            }
            
            if (config.enableAnrDetection && ::anrWatchdog.isInitialized) {
                anrWatchdog.stop()
            }
            
            if (config.enableCrashReporting && ::crashHandler.isInitialized) {
                crashHandler.uninstall()
            }
            
            screenshotManager.shutdown()
            nativeBridge.nativeReset()
            
            isInitialized = false
            log("SDK shutdown complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
    }
}
