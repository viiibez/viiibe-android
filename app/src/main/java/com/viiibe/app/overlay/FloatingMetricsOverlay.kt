package com.viiibe.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.viiibe.app.MainActivity
import com.viiibe.app.R
import com.viiibe.app.data.model.RideMetrics
import kotlin.math.abs

class FloatingMetricsOverlay(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isShowing = false
    private var isExpanded = false
    private var isPaused = false

    private var timeText: TextView? = null
    private var powerText: TextView? = null
    private var cadenceText: TextView? = null
    private var resistanceText: TextView? = null
    private var heartRateText: TextView? = null
    private var caloriesText: TextView? = null
    private var playPauseButton: ImageView? = null

    private var currentMetrics: RideMetrics = RideMetrics()
    private var currentElapsedSeconds: Int = 0

    // Callback when pause state changes
    var onPauseChanged: ((Boolean) -> Unit)? = null

    @SuppressLint("InflateParams")
    fun show(initialPaused: Boolean = false) {
        if (isShowing) return

        isPaused = initialPaused
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showCompactView()
        isShowing = true
    }

    @SuppressLint("InflateParams")
    private fun showCompactView() {
        removeCurrentView()

        val inflater = LayoutInflater.from(context)
        floatingView = inflater.inflate(R.layout.floating_metrics_overlay, null)
        isExpanded = false

        setupViewReferences()
        addViewToWindow()
        updateMetrics(currentMetrics, currentElapsedSeconds)
    }

    @SuppressLint("InflateParams")
    private fun showExpandedView() {
        removeCurrentView()

        val inflater = LayoutInflater.from(context)
        floatingView = inflater.inflate(R.layout.floating_metrics_overlay_expanded, null)
        isExpanded = true

        setupViewReferences()
        addViewToWindow()
        updateMetrics(currentMetrics, currentElapsedSeconds)
    }

    private fun setupViewReferences() {
        timeText = floatingView?.findViewById(R.id.overlay_time)
        powerText = floatingView?.findViewById(R.id.overlay_power)
        cadenceText = floatingView?.findViewById(R.id.overlay_cadence)
        resistanceText = floatingView?.findViewById(R.id.overlay_resistance)
        heartRateText = floatingView?.findViewById(R.id.overlay_heart_rate)
        caloriesText = floatingView?.findViewById(R.id.overlay_calories)

        playPauseButton = floatingView?.findViewById(R.id.overlay_play_pause)
        playPauseButton?.setOnClickListener {
            togglePause()
        }
        updatePauseIcon()
    }

    private fun togglePause() {
        isPaused = !isPaused
        updatePauseIcon()
        onPauseChanged?.invoke(isPaused)

        val message = if (isPaused) "Workout Paused" else "Workout Resumed"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun updatePauseIcon() {
        val iconRes = if (isPaused) {
            android.R.drawable.ic_media_play
        } else {
            android.R.drawable.ic_media_pause
        }
        playPauseButton?.setImageResource(iconRes)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addViewToWindow() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val width = if (isExpanded) {
            WindowManager.LayoutParams.MATCH_PARENT
        } else {
            WindowManager.LayoutParams.WRAP_CONTENT
        }

        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 20 // Small margin from bottom
            if (isExpanded) {
                x = 0
                horizontalMargin = 0.02f // 2% margin on each side
            }
        }

        setupTouchListener(params)
        windowManager?.addView(floatingView, params)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener(params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var touchStartTime = 0L
        val clickThreshold = 15 // pixels
        val longClickThreshold = 500L // ms

        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isExpanded) {
                        // Only allow dragging in compact mode
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY - (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = abs(event.rawX - initialTouchX)
                    val deltaY = abs(event.rawY - initialTouchY)
                    val touchDuration = System.currentTimeMillis() - touchStartTime

                    if (deltaX < clickThreshold && deltaY < clickThreshold) {
                        if (touchDuration >= longClickThreshold) {
                            // Long press - toggle expanded/compact
                            toggleSize()
                        } else {
                            // Short tap - return to app
                            openApp()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleSize() {
        if (isExpanded) {
            showCompactView()
        } else {
            showExpandedView()
        }
    }

    private fun removeCurrentView() {
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // View might not be attached
            }
        }
        floatingView = null
    }

    private fun openApp() {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_RETURN_FROM_OVERLAY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }

    fun hide() {
        if (!isShowing) return

        removeCurrentView()
        isShowing = false
    }

    fun updateMetrics(metrics: RideMetrics, elapsedSeconds: Int) {
        currentMetrics = metrics
        currentElapsedSeconds = elapsedSeconds

        if (!isShowing) return

        timeText?.text = formatTime(elapsedSeconds)
        // Peloton reports watts * 10, so divide by 10 for actual value
        powerText?.text = "${metrics.power / 10}"
        cadenceText?.text = "${metrics.cadence}"
        resistanceText?.text = "${metrics.resistance}"
        heartRateText?.text = if (metrics.heartRate > 0) "${metrics.heartRate}" else "--"
        caloriesText?.text = "${metrics.calories}"
    }

    fun isShowing(): Boolean = isShowing

    private fun formatTime(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
