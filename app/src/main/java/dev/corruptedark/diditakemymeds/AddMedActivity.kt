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
import android.app.Dialog
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.media.Image
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.timepicker.MaterialTimePicker
import android.text.format.DateFormat
import android.view.*
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.fragment.app.DialogFragment
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
    private lateinit var timePickerButton: MaterialButton
    private lateinit var notificationSwitch: SwitchMaterial
    private lateinit var detailInput: TextInputEditText
    private lateinit var timeButtonsLayout: LinearLayoutCompat
    private lateinit var timeButtonsRows: ArrayList<LinearLayoutCompat>
    private lateinit var extraTimeButton: MaterialButton
    private var isSystem24Hour: Boolean = false
    private var clockFormat: Int = TimeFormat.CLOCK_12H
    private lateinit var timePicker: MaterialTimePicker
    private var timerPickerCaller: View? = null
    private val timeOfDayList: ArrayList<TimeOfDay> = ArrayList()

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
        timePickerButton = findViewById(R.id.time_picker_button)
        notificationSwitch = findViewById(R.id.notification_switch)
        detailInput = findViewById(R.id.med_detail)
        toolbar = findViewById(R.id.toolbar)

        timeButtonsLayout = findViewById(R.id.time_buttons_layout)
        timeButtonsRows = ArrayList()
        extraTimeButton = findViewById(R.id.extra_time_button)
        extraTimeButton.visibility = View.GONE

        setSupportActionBar(toolbar)
        toolbar.background = ColorDrawable(ResourcesCompat.getColor(resources, R.color.purple_700, null))

        asNeededSwitch.setOnCheckedChangeListener { switchView, isChecked ->
            if (isChecked) {
                notificationSwitch.isChecked = false
                notify = false
                notificationSwitch.visibility = View.GONE

                timeButtonsLayout.removeAllViews()
                timeButtonsRows.clear()
                timeOfDayList.clear()

                extraTimeButton.visibility = View.GONE

                timePickerButton.text = getText(R.string.select_a_time)
                timePickerButton.visibility = View.GONE

                hour = -1
                minute = -1
            }
            else {
                notificationSwitch.visibility = View.VISIBLE
                timePickerButton.visibility = View.VISIBLE
            }
        }

        timePickerButton.setOnClickListener {
            openTimePicker(it)
        }

        notificationSwitch.setOnCheckedChangeListener { switchView, isChecked ->
            notify = isChecked
        }

        extraTimeButton.setOnClickListener {
            var view = LayoutInflater.from(this).inflate(R.layout.extra_times_template, timeButtonsLayout, false)
            timeOfDayList.add(TimeOfDay(-1, -1))
            timeButtonsRows.add(view as LinearLayoutCompat)
            timeButtonsLayout.addView(view)

            var selectButton: MaterialButton = view.findViewById(R.id.select_time_button)
            var deleteButton: ImageButton = view.findViewById(R.id.delete_time_button)

            selectButton.setOnClickListener {
                openTimePicker(it)
            }

            deleteButton.setOnClickListener {
                val callingIndex = timeButtonsRows.indexOf(view)
                if (timeOfDayList.count() > callingIndex)
                    timeOfDayList.removeAt(callingIndex)
                timeButtonsRows.remove(view)
                timeButtonsLayout.removeView(view)
            }

        }

        isSystem24Hour = DateFormat.is24HourFormat(this)
        clockFormat = if (isSystem24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
        timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(clockFormat)
            .setTitleText(getString(R.string.select_a_time))
            .build()

        timePicker.addOnPositiveButtonClickListener {
            val calendar = Calendar.getInstance()
            if (timerPickerCaller == timePickerButton) {
                hour = timePicker.hour
                minute = timePicker.minute
            }
            else {
                val callingIndex = timeButtonsRows.indexOf(timerPickerCaller!!.parent as LinearLayoutCompat)

                if (timeOfDayList.count() > callingIndex) {
                    timeOfDayList[callingIndex].hour = timePicker.hour
                    timeOfDayList[callingIndex].minute = timePicker.minute
                }
                else {
                    timeOfDayList.add(TimeOfDay(timePicker.hour, timePicker.minute))
                }

            }
            calendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
            calendar.set(Calendar.MINUTE, timePicker.minute)
            val formattedTime = if (isSystem24Hour) DateFormat.format(getString(R.string.time_24), calendar)
            else DateFormat.format(getString(R.string.time_12), calendar)
            (timerPickerCaller as MaterialButton).text = formattedTime
            extraTimeButton.visibility = View.VISIBLE
            pickerIsOpen = false
        }
        timePicker.addOnDismissListener {
            pickerIsOpen = false
            timerPickerCaller = null
        }
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
            var medication = Medication(nameInput.text.toString(), hour, minute, detailInput.text.toString(), notify)
            medication.moreDosesPerDay = timeOfDayList
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
            timerPickerCaller = view
            timePicker.show(supportFragmentManager, getString(R.string.time_picker_tag))
        }

        //Toast.makeText(this, "onClick works", Toast.LENGTH_SHORT).show()
    }
}