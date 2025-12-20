package com.eslam.metrics.internal.capture

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import java.lang.ref.WeakReference

/**
 * TouchInterceptor - Detects user touch/click events globally
 *
 * Intercepts touch events on activities to trigger screenshot capture
 * without modifying individual views or activities.
 */
internal class TouchInterceptor(
    private val application: Application,
    private val onTouchEvent: (Activity, String) -> Unit
) : Application.ActivityLifecycleCallbacks {

    private val handler = Handler(Looper.getMainLooper())
    private val trackedActivities = mutableMapOf<Int, WeakReference<Activity>>()

    fun start() {
        application.registerActivityLifecycleCallbacks(this)
    }

    fun stop() {
        application.unregisterActivityLifecycleCallbacks(this)
        trackedActivities.clear()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // Not used
    }

    override fun onActivityStarted(activity: Activity) {
        // Not used
    }

    override fun onActivityResumed(activity: Activity) {
        // Install touch listener on the root view
        installTouchListener(activity)
        trackedActivities[activity.hashCode()] = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        // Remove touch listener
        removeTouchListener(activity)
        trackedActivities.remove(activity.hashCode())
    }

    override fun onActivityStopped(activity: Activity) {
        // Not used
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // Not used
    }

    override fun onActivityDestroyed(activity: Activity) {
        trackedActivities.remove(activity.hashCode())
    }

    private fun installTouchListener(activity: Activity) {
        val window = activity.window ?: return
        val decorView = window.decorView as? ViewGroup ?: return

        // Set a touch listener on the decor view to intercept all touch events
        decorView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // Detect the touched view for more context
                val viewInfo = findTouchedViewInfo(decorView, event.rawX, event.rawY)
                onTouchEvent(activity, viewInfo)
            }
            false // Don't consume the event
        }
    }

    private fun removeTouchListener(activity: Activity) {
        val decorView = activity.window?.decorView as? ViewGroup ?: return
        decorView.setOnTouchListener(null)
    }

    private fun findTouchedViewInfo(root: ViewGroup, x: Float, y: Float): String {
        val location = IntArray(2)
        
        // Try to find the most specific view that was touched
        fun findView(view: View): View? {
            view.getLocationOnScreen(location)
            val left = location[0]
            val top = location[1]
            val right = left + view.width
            val bottom = top + view.height

            if (x >= left && x <= right && y >= top && y <= bottom) {
                if (view is ViewGroup) {
                    for (i in view.childCount - 1 downTo 0) {
                        val child = view.getChildAt(i)
                        val found = findView(child)
                        if (found != null) return found
                    }
                }
                return view
            }
            return null
        }

        val touchedView = findView(root)
        return touchedView?.let { view ->
            buildString {
                append("touch")
                
                // Get view class name
                val className = view.javaClass.simpleName
                if (className.isNotEmpty()) {
                    append("_$className")
                }
                
                // Get content description if available
                view.contentDescription?.let { desc ->
                    append("_${desc.toString().take(20).replace(" ", "_")}")
                }
                
                // Get resource ID name if available
                if (view.id != View.NO_ID) {
                    try {
                        val idName = view.resources.getResourceEntryName(view.id)
                        append("_$idName")
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        } ?: "touch_unknown"
    }
}
