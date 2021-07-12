package dev.corruptedark.diditakemymeds

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AlarmReceiver : BroadcastReceiver() {
    private var alarmManager: AlarmManager? = null
    private lateinit var alarmIntent: PendingIntent
    val executorService: ExecutorService = Executors.newSingleThreadExecutor()

    companion object {
        const val NOTIFY_ACTION = "NOTIFY"
    }

    private fun createNotificationChannel(context: Context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.channel_name)
            val descriptionText = context.getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(MainActivity.CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        createNotificationChannel(context)
        executorService.execute {
            val medications = MedicationDB.getInstance(context).medicationDao().getAll()

            if (intent.action == "android.intent.action.BOOT_COMPLETED") {
                medications.forEach { medication ->
                    if (medication.notify) {
                        //Create alarm
                        alarmIntent =
                            Intent(context, AlarmReceiver::class.java).let { innerIntent ->
                                innerIntent.action = NOTIFY_ACTION
                                innerIntent.putExtra(context.getString(R.string.med_id_key), medication.id)
                                PendingIntent.getBroadcast(
                                    context,
                                    medication.id.toInt(),
                                    innerIntent,
                                    0
                                )
                            }

                        val calendar = Calendar.getInstance().apply {
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                            set(Calendar.HOUR_OF_DAY, medication.hour)
                            set(Calendar.MINUTE, medication.minute)
                        }

                        alarmManager?.setInexactRepeating(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            AlarmManager.INTERVAL_DAY,
                            alarmIntent
                        )

                    }
                }
            } else if (intent.action == NOTIFY_ACTION){
                //Handle alarm
                val medication =
                    MedicationDB.getInstance(context).medicationDao().get(intent.getLongExtra(context.getString(R.string.med_id_key),-1))
                var builder = NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(medication.name)
                    .setContentText("Time for your dose")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                with(NotificationManagerCompat.from(context.applicationContext)) {
                    notify(
                        (System.currentTimeMillis() + medication.name.hashCode()).toInt(),
                        builder.build()
                    )
                }
            }
        }
    }
}
