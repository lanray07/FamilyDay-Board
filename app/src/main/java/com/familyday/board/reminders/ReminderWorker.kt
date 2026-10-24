package com.familyday.board.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.familyday.board.MainActivity
import com.familyday.board.R
import com.familyday.board.data.*
import com.familyday.board.domain.*
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.*

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val db: BoardDatabase by inject()
    private val preferences: Preferences by inject()
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val household = db.boardDao().household() ?: return Result.success()
        val now = System.currentTimeMillis()
        val hour = Instant.ofEpochMilli(now).atZone(ZoneId.of(household.zone)).hour
        if (preferences.quiet.first() && (hour >= 21 || hour < 7)) return Result.success()
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(NotificationChannel("reminders", context.getString(R.string.reminders), NotificationManager.IMPORTANCE_DEFAULT))
        val delivered = context.getSharedPreferences("reminder_delivery", Context.MODE_PRIVATE)
        db.boardDao().allEntries().filter { !it.deleted && it.reminderMinutes >= 0 && it.kind in listOf(Kind.EVENT, Kind.CHORE) }.forEach { e ->
            val date = Instant.ofEpochMilli(now).atZone(ZoneId.of(e.zone)).toLocalDate()
            // Tomorrow matters when a reminder falls before the occurrence's local date.
            listOf(date.minusDays(1), date, date.plusDays(1)).forEach { day ->
                if (e.on(day) && !e.completed(day)) {
                    val due = Schedule.occurrenceStart(e.start, e.zone, day) - e.reminderMinutes * 60_000L
                    val key = "${e.id}:$day:${e.version}"
                    if (now >= due && now - due < 86_400_000 && !delivered.getBoolean(key, false)) {
                        val pending = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                        // No child names, event titles or medical details on the shared device lock screen.
                        notificationManager.notify(key.hashCode(), NotificationCompat.Builder(context, "reminders")
                            .setSmallIcon(R.drawable.ic_familyday).setContentTitle(context.getString(R.string.app_name))
                            .setContentText(context.getString(R.string.private_reminder)).setContentIntent(pending)
                            .setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_SECRET).build())
                        delivered.edit().putBoolean(key, true).apply()
                    }
                }
            }
        }
        return Result.success()
    }
}
