/*
 * Did I Take My Meds? is a FOSS app to keep track of medications
 * Did I Take My Meds? is designed to help prevent a user from skipping doses and/or overdosing
 *     Copyright (C) 2021  Noah Stanford <noahstandingford@gmail.com>
 *
 *     Did I Take My Meds? is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Did I Take My Meds? is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.corruptedark.diditakemymeds

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textview.MaterialTextView
import java.lang.StringBuilder
import kotlin.math.*
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.NoSuchElementException

class MedDetailActivity : AppCompatActivity() {
    private lateinit var nameLabel: MaterialTextView
    private lateinit var timeLabel: MaterialTextView
    private lateinit var notificationSwitch: SwitchMaterial
    private lateinit var detailLabel: MaterialTextView
    private lateinit var closestDoseLabel: MaterialTextView
    private lateinit var justTookItButton: MaterialButton
    private lateinit var previousDosesList: ListView
    private lateinit var medication: Medication
    private lateinit var doseRecordAdapter: DoseRecordListAdapter
    private val calendar = Calendar.getInstance()
    private var isSystem24Hour: Boolean = false
    private var closestDose: Long = -1L
    private lateinit var executorService: ExecutorService
    private val editResultStarter = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        runOnUiThread {
            executorService.execute {
                MedicationDB.getInstance(this).medicationDao().updateMedications(medication)
            }

            calendar.set(Calendar.HOUR_OF_DAY, medication.hour)
            calendar.set(Calendar.MINUTE, medication.minute)

            nameLabel.text = medication.name
            timeLabel.text = if (isSystem24Hour)
                DateFormat.format(getString(R.string.time_24), calendar)
            else
                DateFormat.format(getString(R.string.time_12), calendar)

            detailLabel.text = medication.description

            closestDose = calculateClosestDose(medication)
            closestDoseLabel.text = closestDoseString(closestDose)

            val lastDose: Long = try {
                medication.doseRecord.first().closestDose
            }
            catch (except: NoSuchElementException) {
                -1L
            }
            closestDose = calculateClosestDose(medication)
            if (lastDose == closestDose) {
                justTookItButton.text = getString(R.string.took_this_already)
            }
            else {
                justTookItButton.text = getString(R.string.i_just_took_it)
            }

            alarmIntent = Intent(this, AlarmReceiver::class.java).let { innerIntent ->
                innerIntent.action = AlarmReceiver.NOTIFY_ACTION
                innerIntent.putExtra(getString(R.string.med_id_key), medication.id)
                PendingIntent.getBroadcast(this, medication.id.toInt(), innerIntent, 0)
            }

            if (medication.notify) {
                //Set alarm
                alarmManager?.cancel(alarmIntent)

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

                val receiver = ComponentName(this, AlarmReceiver::class.java)

                this.packageManager.setComponentEnabledSetting(
                    receiver,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            else {
                //Cancel alarm
                alarmManager?.cancel(alarmIntent)
            }
        }
    }
    private var alarmManager: AlarmManager? = null
    private lateinit var alarmIntent: PendingIntent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_med_detail)
        supportActionBar?.setBackgroundDrawable(ColorDrawable(ResourcesCompat.getColor(resources, R.color.purple_700, null)))
        alarmManager = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        isSystem24Hour = DateFormat.is24HourFormat(this)
        medication = MainActivity.medications!![intent.getIntExtra(getString(R.string.med_position_key), -1)]

        alarmIntent = Intent(this, AlarmReceiver::class.java).let { innerIntent ->
            innerIntent.action = AlarmReceiver.NOTIFY_ACTION
            innerIntent.putExtra(getString(R.string.med_id_key), medication.id)
            PendingIntent.getBroadcast(this, medication.id.toInt(), innerIntent, 0)
        }

        executorService = Executors.newSingleThreadExecutor()

        calendar.set(Calendar.HOUR_OF_DAY, medication.hour)
        calendar.set(Calendar.MINUTE, medication.minute)

        nameLabel = findViewById(R.id.name_label)
        timeLabel = findViewById(R.id.time_label)
        notificationSwitch = findViewById(R.id.notification_switch)
        detailLabel = findViewById(R.id.detail_label)
        closestDoseLabel = findViewById(R.id.closest_dose_label)
        justTookItButton = findViewById(R.id.just_took_it_button)
        previousDosesList = findViewById(R.id.previous_doses_list)

        nameLabel.text = medication.name
        timeLabel.text = if (isSystem24Hour)
            DateFormat.format(getString(R.string.time_24), calendar)
        else
            DateFormat.format(getString(R.string.time_12), calendar)

        notificationSwitch.isChecked = medication.notify
        notificationSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            medication.notify = isChecked

            executorService.execute {
                MedicationDB.getInstance(this).medicationDao().updateMedications(medication)
            }

            if (isChecked) {
                //Set alarm

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

                val receiver = ComponentName(this, AlarmReceiver::class.java)

                this.packageManager.setComponentEnabledSetting(
                    receiver,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                Toast.makeText(this, getString(R.string.notifications_enabled), Toast.LENGTH_SHORT).show()
            }
            else {
                //Cancel alarm
                alarmManager?.cancel(alarmIntent)
                Toast.makeText(this, getString(R.string.notifications_disabled), Toast.LENGTH_SHORT).show()
            }
        }

        detailLabel.text = medication.description

        closestDose = calculateClosestDose(medication)
        closestDoseLabel.text = closestDoseString(closestDose)
        doseRecordAdapter = DoseRecordListAdapter(this, medication.doseRecord)

        previousDosesList.adapter = doseRecordAdapter
        ViewCompat.setNestedScrollingEnabled(previousDosesList, true)

        justTookItButton.setOnClickListener {
            justTookItButtonPressed()
        }

        val lastDose: Long = try {
            medication.doseRecord.first().closestDose
        }
        catch (except: NoSuchElementException) {
            -1L
        }
        closestDose = calculateClosestDose(medication)
        if (lastDose == closestDose) {
            justTookItButton.text = getString(R.string.took_this_already)
        }
        else {
            justTookItButton.text = getString(R.string.i_just_took_it)
        }
        medication.doseRecord.sortWith { o1, o2 -> (o2.closestDose - o1.closestDose).sign }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.med_detail_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.delete -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.are_you_sure))
                    .setMessage(getString(R.string.delete_warning))
                    .setNegativeButton(getString(R.string.cancel)) { dialog, which ->
                        dialog.dismiss()
                    }
                    .setPositiveButton(getString(R.string.confirm)) { dialog, which ->
                        executorService.execute {
                            val db = MedicationDB.getInstance(this)
                            db.medicationDao().delete(medication)
                            MainActivity.medications?.remove(medication)
                            alarmManager?.cancel(alarmIntent)
                            finish()
                        }
                    }
                    .show()
                true
            }
            R.id.edit -> {
                val editIntent = Intent(this, EditMedActivity::class.java)
                editIntent.putExtra(getString(R.string.med_position_key), intent.getIntExtra(getString(R.string.med_position_key), -1))
                editResultStarter.launch(editIntent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun calculateClosestDose(medication: Medication): Long {
        val currentTime = System.currentTimeMillis()
        calendar.timeInMillis = currentTime
        calendar.set(Calendar.HOUR_OF_DAY, medication.hour)
        calendar.set(Calendar.MINUTE, medication.minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayDose = calendar.timeInMillis
        calendar.add(Calendar.DATE, -1)
        val yesterdayDose = calendar.timeInMillis
        calendar.add(Calendar.DATE, 2)
        val tomorrowDose = calendar.timeInMillis

        return when (minOf(abs(currentTime - todayDose), abs(currentTime - yesterdayDose), abs(currentTime - tomorrowDose))) {
            abs(currentTime - todayDose) -> todayDose
            abs(currentTime - yesterdayDose) -> yesterdayDose
            else -> tomorrowDose
        }

    }

    private fun closestDoseString(closestDose: Long): String {
        val doseCal: Calendar = Calendar.getInstance()
        doseCal.timeInMillis = closestDose
        calendar.timeInMillis = System.currentTimeMillis()
        calendar.set(Calendar.HOUR_OF_DAY, doseCal.get(Calendar.HOUR_OF_DAY))
        calendar.set(Calendar.MINUTE, doseCal.get(Calendar.MINUTE))
        val todayDose = calendar.timeInMillis
        calendar.add(Calendar.DATE, -1)
        val yesterdayDose = calendar.timeInMillis
        calendar.add(Calendar.DATE, 2)
        val tomorrowDose = calendar.timeInMillis

        val dayString: String =
            when (minOf(abs(closestDose - todayDose), abs(closestDose - yesterdayDose), abs(closestDose - tomorrowDose))) {
                abs(closestDose - todayDose) -> getString(R.string.today)
                abs(closestDose - yesterdayDose) -> getString(R.string.yesterday)
                else -> getString(R.string.tomorrow)
            }

        val time = if (isSystem24Hour)
            DateFormat.format(getString(R.string.time_24), calendar)
        else
            DateFormat.format(getString(R.string.time_12), calendar)

        val builder: StringBuilder = StringBuilder()
            .append(getString(R.string.closest_dose_label))
            .append("  ")
            .append(time)
            .append(" ")
            .append(dayString)

        return builder.toString()
    }

    private fun justTookItButtonPressed() {
        val lastDose: Long = try {
            medication.doseRecord.first().closestDose
        }
        catch (except: NoSuchElementException)
        {
            -1L
        }
        closestDose = calculateClosestDose(medication)
        if (lastDose == closestDose)
        {
            Toast.makeText(this, getString(R.string.already_took_dose), Toast.LENGTH_SHORT).show()
        }
        else
        {
            val newDose = DoseRecord(System.currentTimeMillis(), closestDose)
            medication.doseRecord.add(newDose)
            medication.doseRecord.sortWith { o1, o2 -> (o2.closestDose - o1.closestDose).sign }
            doseRecordAdapter.notifyDataSetChanged()
            justTookItButton.text = getString(R.string.took_this_already)

            executorService.execute {
                val db = MedicationDB.getInstance(this)
                db.medicationDao().updateMedications(medication)
            }
        }
    }
}