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
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.content.res.ResourcesCompat
import android.text.format.DateFormat
import android.view.*
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.widget.LinearLayoutCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.TimeFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayList

class AddMedActivity() : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var nameInput: TextInputEditText
    private lateinit var asNeededSwitch: SwitchMaterial
    private lateinit var repeatScheduleButton: MaterialButton
    private lateinit var notificationSwitch: SwitchMaterial
    private lateinit var detailInput: TextInputEditText
    private lateinit var scheduleButtonsLayout: LinearLayoutCompat
    private lateinit var scheduleButtonsRows: ArrayList<LinearLayoutCompat>
    private lateinit var extraDoseButton: MaterialButton
    private var isSystem24Hour: Boolean = false
    private var clockFormat: Int = TimeFormat.CLOCK_12H
    private lateinit var schedulePicker: RepeatSheduleDialog
    private var schedulePickerCaller: View? = null
    private val repeatScheduleList: ArrayList<RepeatSchedule> = ArrayList()

    @Volatile var pickerIsOpen = false
    var hour = -1
    var minute = -1
    var notify = true
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    private var alarmManager: AlarmManager? = null
    private lateinit var alarmIntent: PendingIntent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_med)
        alarmManager = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        nameInput = findViewById(R.id.med_name)
        asNeededSwitch = findViewById(R.id.as_needed_switch)
        repeatScheduleButton = findViewById(R.id.repeat_schedule_button)
        notificationSwitch = findViewById(R.id.notification_switch)
        detailInput = findViewById(R.id.med_detail)
        toolbar = findViewById(R.id.toolbar)

        scheduleButtonsLayout = findViewById(R.id.schedule_buttons_layout)
        scheduleButtonsRows = ArrayList()
        extraDoseButton = findViewById(R.id.extra_dose_button)
        extraDoseButton.visibility = View.GONE

        setSupportActionBar(toolbar)
        toolbar.background = ColorDrawable(ResourcesCompat.getColor(resources, R.color.purple_700, null))

        asNeededSwitch.setOnCheckedChangeListener { switchView, isChecked ->
            if (isChecked) {
                notificationSwitch.isChecked = false
                notify = false
                notificationSwitch.visibility = View.GONE

                scheduleButtonsLayout.removeAllViews()
                scheduleButtonsRows.clear()
                repeatScheduleList.clear()

                extraDoseButton.visibility = View.GONE

                repeatScheduleButton.text = getText(R.string.schedule_dose)
                repeatScheduleButton.visibility = View.GONE

                hour = -1
                minute = -1
            }
            else {
                notificationSwitch.visibility = View.VISIBLE
                repeatScheduleButton.visibility = View.VISIBLE
            }
        }

        repeatScheduleButton.setOnClickListener {
            openTimePicker(it)
        }

        notificationSwitch.setOnCheckedChangeListener { switchView, isChecked ->
            notify = isChecked
        }

        extraDoseButton.setOnClickListener {
            var view = LayoutInflater.from(this).inflate(R.layout.extra_dose_template, scheduleButtonsLayout, false)
            repeatScheduleList.add(RepeatSchedule(-1, -1, -1, -1, -1))
            scheduleButtonsRows.add(view as LinearLayoutCompat)
            scheduleButtonsLayout.addView(view)

            var selectButton: MaterialButton = view.findViewById(R.id.schedule_dose_button)
            var deleteButton: ImageButton = view.findViewById(R.id.delete_dose_button)

            selectButton.setOnClickListener {
                openTimePicker(it)
            }

            deleteButton.setOnClickListener {
                val callingIndex = scheduleButtonsRows.indexOf(view)
                if (repeatScheduleList.count() > callingIndex)
                    repeatScheduleList.removeAt(callingIndex)
                scheduleButtonsRows.remove(view)
                scheduleButtonsLayout.removeView(view)
            }

        }

        isSystem24Hour = DateFormat.is24HourFormat(this)
        clockFormat = if (isSystem24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H

        schedulePicker = RepeatSheduleDialog.newInstance(this)

        /*
        timePicker = RepeatSheduleDialog.Builder()
            .setTimeFormat(clockFormat)
            .setTitleText(getString(R.string.select_a_time))
            .build()

        schedulePicker.addOnPositiveButtonClickListener {
            val calendar = Calendar.getInstance()
            if (schedulePickerCaller == repeatScheduleButton) {
                hour = schedulePicker.hour
                minute = schedulePicker.minute
            }
            else {
                val callingIndex = scheduleButtonsRows.indexOf(schedulePickerCaller!!.parent as LinearLayoutCompat)

                if (repeatScheduleList.count() > callingIndex) {
                    repeatScheduleList[callingIndex].hour = schedulePicker.hour
                    repeatScheduleList[callingIndex].minute = schedulePicker.minute
                }
                else {
                    repeatScheduleList.add(RepeatSchedule(schedulePicker.hour, schedulePicker.minute, -1, -1, -1))
                }

            }
            calendar.set(Calendar.HOUR_OF_DAY, schedulePicker.hour)
            calendar.set(Calendar.MINUTE, schedulePicker.minute)
            val formattedTime = if (isSystem24Hour) DateFormat.format(getString(R.string.time_24), calendar)
            else DateFormat.format(getString(R.string.time_12), calendar)
            (schedulePickerCaller as MaterialButton).text = formattedTime
            extraDoseButton.visibility = View.VISIBLE
            pickerIsOpen = false
        }
        schedulePicker.addOnDismissListener {
            pickerIsOpen = false
            schedulePickerCaller = null
        }*/
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.add_med_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.save -> {
                executorService.execute {
                    if (saveMedication())
                        finish()
                }
                true
            }
            R.id.cancel -> {
                Toast.makeText(this, getString(R.string.cancelled), Toast.LENGTH_SHORT).show()
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveMedication(): Boolean {
        return if (nameInput.text.isNullOrBlank()) {
            runOnUiThread {
                Toast.makeText(this, getString(R.string.fill_fields), Toast.LENGTH_SHORT).show()
            }
            false
        }
        else if ((hour !in 0..23 || minute !in 0..59) && !asNeededSwitch.isChecked) {
            runOnUiThread {
                Toast.makeText(this, getString(R.string.select_a_time), Toast.LENGTH_SHORT).show()
            }
            false
        }
        else {
            var medication = Medication(nameInput.text.toString(), hour, minute, detailInput.text.toString(), -1, -1, -1, notify= notify)
            medication.moreDosesPerDay = repeatScheduleList
            MedicationDB.getInstance(this).medicationDao().insertAll(medication)
            medication = MedicationDB.getInstance(this).medicationDao().getAll().last()
            MainActivity.medications!!.add(medication)


            alarmIntent = Intent(this, AlarmReceiver::class.java).let { innerIntent ->
                innerIntent.action = AlarmReceiver.NOTIFY_ACTION
                innerIntent.putExtra(getString(R.string.med_id_key), medication.id)
                PendingIntent.getBroadcast(this, medication.id.toInt(), innerIntent, 0)
            }

            if (notify) {
                //Set alarm

                val calendar = Calendar.getInstance().apply {
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    set(Calendar.HOUR_OF_DAY, medication.hour)
                    set(Calendar.MINUTE, medication.minute)
                }

                /*alarmManager?.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    alarmIntent
                )*/

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager?.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calculateNextDose(medication),
                        alarmIntent
                    )
                }
                else {
                    alarmManager?.set(
                        AlarmManager.RTC_WAKEUP,
                        calculateNextDose(medication),
                        alarmIntent
                    )
                }

                val receiver = ComponentName(this, AlarmReceiver::class.java)

                this.packageManager.setComponentEnabledSetting(
                    receiver,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )

            }

            runOnUiThread {
                Toast.makeText(this, getString(R.string.med_saved), Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun openTimePicker(view: View) {
        if (!pickerIsOpen) {
            pickerIsOpen = true
            schedulePickerCaller = view
            schedulePicker.show(supportFragmentManager, getString(R.string.schedule_picker_tag))
        }

        //Toast.makeText(this, "onClick works", Toast.LENGTH_SHORT).show()
    }
}