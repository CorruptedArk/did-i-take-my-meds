package dev.corruptedark.diditakemymeds

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmIntentManager {

    fun buildNotificationAlarm(context: Context, medication: Medication): PendingIntent {
        return Intent(context, ActionReceiver::class.java).let { innerIntent ->
            innerIntent.action = ActionReceiver.NOTIFY_ACTION
            innerIntent.putExtra(
                context.getString(R.string.med_id_key),
                medication.id
            )
            PendingIntent.getBroadcast(
                context,
                medication.id.toInt(),
                innerIntent,
                0
            )
        }
    }

    fun setExact(alarmManager: AlarmManager?, alarmIntent: PendingIntent, timeInMillis: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager?.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                alarmIntent
            )
        } else {
            alarmManager?.set(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                alarmIntent
            )
        }
    }
}