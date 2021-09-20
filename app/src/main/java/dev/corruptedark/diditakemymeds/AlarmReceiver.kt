package dev.corruptedark.diditakemymeds

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AlarmReceiver : BroadcastReceiver() {
    private var alarmManager: AlarmManager? = null
    private lateinit var alarmIntent: PendingIntent
    val executorService: ExecutorService = Executors.newSingleThreadExecutor()

    companion object {
        const val NOTIFY_ACTION = "NOTIFY"
        const val TOOK_MED_ACTION = "TOOK_MED"
        const val REMIND_ACTION = "REMIND"
        const val REMIND_DELAY = 15
        private const val NO_ICON = 0

        fun configureNotification(context: Context, medication: Medication): NotificationCompat.Builder {
            val calendar = Calendar.getInstance()
            MedicationDB.getInstance(context).medicationDao().updateMedications(medication)

            val actionIntent = Intent(context, MedDetailActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(context.getString(R.string.med_id_key), medication.id)
            }

            val pendingIntent = PendingIntent.getActivity(context, medication.id.toInt(), actionIntent, 0)

            val closestDose = medication.calculateClosestDose()
            val hour = closestDose.schedule.hour
            val minute = closestDose.schedule.minute
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            val isSystem24Hour = DateFormat.is24HourFormat(context)

            val formattedTime = if (isSystem24Hour) DateFormat.format(
                context.getString(R.string.time_24),
                calendar
            )
            else DateFormat.format(context.getString(R.string.time_12), calendar)

            //Start building "took med" notification action
            val tookMedIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = TOOK_MED_ACTION
                putExtra(context.getString(R.string.med_id_key), medication.id)
            }
            val tookMedPendingIntent = PendingIntent.getBroadcast(context, medication.id.toInt(), tookMedIntent, 0)
            //End building "took med" notification action

            //Start building "remind" notification action
             val remindIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = REMIND_ACTION
                putExtra(context.getString(R.string.med_id_key), medication.id)
            }
            val remindPendingIntent = PendingIntent.getBroadcast(context, medication.id.toInt(), remindIntent, 0)
            //End building "remind" notification action


            return NotificationCompat.Builder(
                context,
                context.getString(R.string.channel_name)
            )
                .setSmallIcon(R.drawable.ic_small_notification)
                .setColor(
                    ResourcesCompat.getColor(
                        context.resources,
                        R.color.purple_500,
                        context.theme
                    )
                )
                .setContentTitle(medication.name)
                .setSubText(formattedTime)
                .setContentText(context.getString(R.string.time_for_your_dose))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .addAction(NO_ICON, context.getString(R.string.took_it), tookMedPendingIntent)
                .addAction(NO_ICON, context.getString(R.string.remind_in_15), remindPendingIntent)
        }
    }

    private fun createNotificationChannel(context: Context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.channel_name)
            val descriptionText = context.getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(name, name, importance).apply {
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
            val medications = MedicationDB.getInstance(context).medicationDao().getAllRaw()

            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED -> {
                    medications.forEach { medication ->
                        medication.updateStartsToFuture()
                        if (medication.notify) {
                            //Create alarm
                            alarmIntent = AlarmIntentManager.build(context, medication)
                            AlarmIntentManager.set(alarmManager, alarmIntent, medication.calculateNextDose().timeInMillis)
                        }
                    }
                    MedicationDB.getInstance(context).medicationDao()
                        .updateMedications(*medications.toTypedArray())
                }
                NOTIFY_ACTION -> {
                    //Handle alarm
                    val medication =
                        MedicationDB.getInstance(context).medicationDao()
                            .get(intent.getLongExtra(context.getString(R.string.med_id_key), -1))

                    medication.updateStartsToFuture()
                    alarmIntent = AlarmIntentManager.build(context, medication)
                    AlarmIntentManager.set(alarmManager, alarmIntent, medication.calculateNextDose().timeInMillis)

                    if (!medication.closestDoseAlreadyTaken()) {
                        val notification = configureNotification(context, medication).build()
                        with(NotificationManagerCompat.from(context.applicationContext)) {
                            notify(
                                medication.id.toInt(),
                                notification
                            )
                        }
                    }
                }
                TOOK_MED_ACTION -> {
                    val medId = intent.getLongExtra(context.getString(R.string.med_id_key), -1L)

                    if (MedicationDB.getInstance(context).medicationDao().medicationExists(medId)) {
                        val medication: Medication =
                            MedicationDB.getInstance(context).medicationDao().get(medId)

                        if (!medication.closestDoseAlreadyTaken()) {
                            val takenDose = DoseRecord(
                                System.currentTimeMillis(),
                                medication.calculateClosestDose().timeInMillis
                            )
                            medication.addNewTakenDose(takenDose)
                            MedicationDB.getInstance(context).medicationDao()
                                .updateMedications(medication)
                        }

                        val notification = configureNotification(context, medication)
                            .setContentText(context.getString(R.string.taken))
                            .clearActions()
                            .build()

                        with(NotificationManagerCompat.from(context.applicationContext)) {
                            notify(
                                medication.id.toInt(),
                                notification
                            )

                        }
                    }
                    else {
                        with(NotificationManagerCompat.from(context.applicationContext)) {
                            cancel(medId.toInt())
                        }
                    }
                }
                REMIND_ACTION-> {
                    val medId = intent.getLongExtra(context.getString(R.string.med_id_key), -1L)

                    with(NotificationManagerCompat.from(context.applicationContext)) {
                        cancel(medId.toInt())
                    }

                    if (MedicationDB.getInstance(context).medicationDao().medicationExists(medId)) {
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.MINUTE, REMIND_DELAY)
                        val medication: Medication =
                            MedicationDB.getInstance(context).medicationDao().get(medId)
                        alarmIntent = AlarmIntentManager.build(context, medication)
                        AlarmIntentManager.set(alarmManager, alarmIntent, calendar.timeInMillis)
                    }
                }
            }
        }
    }
}
