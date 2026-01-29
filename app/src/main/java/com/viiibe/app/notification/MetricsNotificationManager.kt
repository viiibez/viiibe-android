package com.viiibe.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.viiibe.app.MainActivity
import com.viiibe.app.R
import com.viiibe.app.bluetooth.BikeConnectionService
import com.viiibe.app.data.model.RideMetrics

class MetricsNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "freespin_ride_metrics"
        const val NOTIFICATION_ID = 1001
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ride Metrics",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live ride metrics while app is in background"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        metrics: RideMetrics,
        elapsedSeconds: Int
    ): android.app.Notification {
        // Intent to open app when notification is tapped
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_RETURN_FROM_OVERLAY
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to stop the overlay
        val stopIntent = Intent(context, BikeConnectionService::class.java).apply {
            action = BikeConnectionService.ACTION_STOP_OVERLAY
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build custom notification views
        val collapsedView = RemoteViews(context.packageName, R.layout.notification_metrics_collapsed).apply {
            setTextViewText(R.id.notification_summary, formatSummary(metrics, elapsedSeconds))
            setOnClickPendingIntent(R.id.btn_stop, stopPendingIntent)
        }

        val expandedView = RemoteViews(context.packageName, R.layout.notification_metrics_expanded).apply {
            setTextViewText(R.id.metric_time, formatTime(elapsedSeconds))
            // Peloton reports watts * 10, so divide by 10 for actual value
            setTextViewText(R.id.metric_power, "${metrics.power / 10}")
            setTextViewText(R.id.metric_cadence, "${metrics.cadence}")
            setTextViewText(R.id.metric_resistance, "${metrics.resistance}")
            setTextViewText(R.id.metric_heart_rate, if (metrics.heartRate > 0) "${metrics.heartRate}" else "--")
            setTextViewText(R.id.metric_calories, "${metrics.calories}")
            setOnClickPendingIntent(R.id.btn_stop, stopPendingIntent)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bike_notification)
            .setContentTitle("FreeSpin Ride")
            .setContentText(formatSummary(metrics, elapsedSeconds))
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun updateNotification(metrics: RideMetrics, elapsedSeconds: Int) {
        val notification = buildNotification(metrics, elapsedSeconds)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

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

    private fun formatSummary(metrics: RideMetrics, elapsedSeconds: Int): String {
        // Peloton reports watts * 10, so divide by 10 for actual value
        return "${formatTime(elapsedSeconds)} | ${metrics.power / 10}W | ${metrics.cadence} rpm"
    }
}
