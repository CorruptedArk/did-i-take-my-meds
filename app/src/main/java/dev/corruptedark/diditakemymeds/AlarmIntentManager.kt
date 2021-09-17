package dev.corruptedark.diditakemymeds

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmIntentManager {

    fun build(context: Context, medication: Medication): PendingIntent{
        return Intent(context, AlarmReceiver::class.java).let { innerIntent ->
            innerIntent.action = AlarmReceiver.NOTIFY_ACTION
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

    fun set(alarmManager:AlarmManager?, alarmIntent: PendingIntent, timeInMillis: Long) {
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