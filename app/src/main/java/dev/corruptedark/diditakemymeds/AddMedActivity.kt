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
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.content.res.ResourcesCompat
import android.text.format.DateFormat
import android.view.*
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.widget.LinearLayoutCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.TimeFormat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.ArrayList

class AddMedActivity() : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var nameInput: TextInputEditText
    private lateinit var rxNumberInput : TextInputEditText
    private lateinit var typeInput: AutoCompleteTextView
    private lateinit var asNeededSwitch: SwitchMaterial
    private lateinit var requirePhotoProofSwitch: SwitchMaterial
    private lateinit var repeatScheduleButton: MaterialButton
    private lateinit var notificationSwitch: SwitchMaterial
    private lateinit var detailInput: TextInputEditText
    private lateinit var scheduleButtonsLayout: LinearLayoutCompat
    private lateinit var scheduleButtonsRows: ArrayList<LinearLayoutCompat>
    private lateinit var extraDoseButton: MaterialButton
    private var isSystem24Hour: Boolean = false
    private var clockFormat: Int = TimeFormat.CLOCK_12H
    private lateinit var schedulePicker: RepeatScheduleDialog
    private var schedulePickerCaller: View? = null
    private val repeatScheduleList: ArrayList<RepeatSchedule> = ArrayList()

    @Volatile
    private var pickerIsOpen = false
    private var hour = -1
    private var minute = -1
    private var startDay = -1
    private var startMonth = -1
    private var startYear = -1
    private var daysBetween = 1
    private var weeksBetween = 0
    private var monthsBetween = 0
    private var yearsBetween = 0
    private var notify = true
    private var requirePhotoProof = true
    private val lifecycleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var alarmManager: AlarmManager? = null
    private lateinit var alarmIntent: PendingIntent
    private val mainScope = MainScope()
    private val context = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_med)
        alarmManager = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        nameInput = findViewById(R.id.med_name)
        rxNumberInput = findViewById(R.id.rx_number_input)
        typeInput = findViewById(R.id.med_type_input)
        asNeededSwitch = findViewById(R.id.as_needed_switch)
        requirePhotoProofSwitch = findViewById(R.id.require_photo_proof_switch)
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

        lifecycleScope.launch(lifecycleDispatcher) {
            val medTypeListAdapter = MedTypeListAdapter(context, medicationTypeDao(context).getAllRaw())

            mainScope.launch {
                typeInput.setAdapter(medTypeListAdapter)
                typeInput.setOnItemClickListener { parent, view, position, id ->
                    typeInput.setText((typeInput.adapter.getItem(position) as MedicationType).name)
                }
            }
        }

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
                startDay = -1
                startMonth = -1
                startYear = -1
                daysBetween = 1
                weeksBetween = 0
                monthsBetween = 0
                yearsBetween = 0
            }
            else {
                notificationSwitch.visibility = View.VISIBLE
                repeatScheduleButton.visibility = View.VISIBLE
            }
        }

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            requirePhotoProofSwitch.visibility = View.VISIBLE
        }
        else {
            requirePhotoProofSwitch.visibility = View.GONE
            requirePhotoProof = false
        }

        requirePhotoProofSwitch.isChecked = requirePhotoProof
        requirePhotoProofSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            requirePhotoProof = isChecked
        }

        repeatScheduleButton.setOnClickListener {   view ->
            openSchedulePicker(view)
        }

        notificationSwitch.setOnCheckedChangeListener { switchView, isChecked ->
            notify = isChecked
        }

        extraDoseButton.setOnClickListener {
            val view = LayoutInflater.from(this).inflate(R.layout.extra_dose_template, scheduleButtonsLayout, false)
            repeatScheduleList.add(RepeatSchedule(-1, -1, -1, -1, -1))
            scheduleButtonsRows.add(view as LinearLayoutCompat)
            scheduleButtonsLayout.addView(view)

            val selectButton: MaterialButton = view.findViewById(R.id.schedule_dose_button)
            val deleteButton: ImageButton = view.findViewById(R.id.delete_dose_button)

            selectButton.setOnClickListener {
                openSchedulePicker(it)
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

        schedulePicker = RepeatScheduleDialog.newInstance(this)

        schedulePicker.addConfirmListener {
            if (schedulePicker.scheduleIsValid()) {
                val calendar = Calendar.getInstance()
                if (schedulePickerCaller == repeatScheduleButton) {
                    hour = schedulePicker.hour
                    minute = schedulePicker.minute
                    startDay = schedulePicker.startDay
                    startMonth = schedulePicker.startMonth
                    startYear = schedulePicker.startYear
                    daysBetween = schedulePicker.daysBetween
                    weeksBetween = schedulePicker.weeksBetween
                    monthsBetween = schedulePicker.monthsBetween
                    yearsBetween = schedulePicker.yearsBetween
                } else {
                    val callingIndex =
                        scheduleButtonsRows.indexOf(schedulePickerCaller!!.parent as LinearLayoutCompat)

                    if (repeatScheduleList.count() > callingIndex) {
                        repeatScheduleList[callingIndex].hour = schedulePicker.hour
                        repeatScheduleList[callingIndex].minute = schedulePicker.minute
                        repeatScheduleList[callingIndex].startDay = schedulePicker.startDay
                        repeatScheduleList[callingIndex].startMonth = schedulePicker.startMonth
                        repeatScheduleList[callingIndex].startYear = schedulePicker.startYear
                        repeatScheduleList[callingIndex].daysBetween = schedulePicker.daysBetween
                        repeatScheduleList[callingIndex].weeksBetween = schedulePicker.weeksBetween
                        repeatScheduleList[callingIndex].monthsBetween = schedulePicker.monthsBetween
                        repeatScheduleList[callingIndex].yearsBetween = schedulePicker.yearsBetween
                    } else {
                        repeatScheduleList.add(
                            RepeatSchedule(
                                schedulePicker.hour,
                                schedulePicker.minute,
                                schedulePicker.startDay,
                                schedulePicker.startMonth,
                                schedulePicker.startYear,
                                schedulePicker.daysBetween,
                                schedulePicker.weeksBetween,
                                schedulePicker.monthsBetween,
                                schedulePicker.yearsBetween
                            )
                        )
                    }

                }
                calendar.set(Calendar.HOUR_OF_DAY, schedulePicker.hour)
                calendar.set(Calendar.MINUTE, schedulePicker.minute)
                calendar.set(Calendar.DAY_OF_MONTH, schedulePicker.startDay)
                calendar.set(Calendar.MONTH, schedulePicker.startMonth)
                calendar.set(Calendar.YEAR, schedulePicker.startYear)
                val formattedTime =
                    if (isSystem24Hour) DateFormat.format(getString(R.string.time_24), calendar)
                    else DateFormat.format(getString(R.string.time_12), calendar)
                val formattedDate = DateFormat.format(getString(R.string.date_format), calendar)
                (schedulePickerCaller as MaterialButton).text = getString(
                    R.string.schedule_format,
                    formattedTime,
                    formattedDate,
                    schedulePicker.daysBetween,
                    schedulePicker.weeksBetween,
                    schedulePicker.monthsBetween,
                    schedulePicker.yearsBetween
                )
                extraDoseButton.visibility = View.VISIBLE
                pickerIsOpen = false
                schedulePicker.dismiss()
            }
            else {
                Toast.makeText(this, getString(R.string.fill_out_schedule), Toast.LENGTH_SHORT).show()
            }
        }
        schedulePicker.addDismissListener {
            pickerIsOpen = false
            schedulePickerCaller = null
        }

        medicationDao(applicationContext).getAll()
            .observe(this, {})
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.add_med_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.save -> {
                lifecycleScope.launch(lifecycleDispatcher) {
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
            mainScope.launch {
                Toast.makeText(context, getString(R.string.fill_fields), Toast.LENGTH_SHORT).show()
            }
            false
        }
        else if (!allSchedulesAreValid() && !asNeededSwitch.isChecked) {
            mainScope.launch {
                Toast.makeText(context, getString(R.string.fill_out_all_schedules), Toast.LENGTH_SHORT).show()
            }
            false
        }
        else {
            val medTypeExists = medicationTypeDao(context).typeExists(typeInput.text.toString())

            val typeId = if (medTypeExists) {
                medicationTypeDao(context).get(typeInput.text.toString()).id
            }
            else {
                var medicationType = MedicationType(typeInput.text.toString())
                medicationTypeDao(context).insertAll(medicationType)
                medicationType = medicationTypeDao(context).getAllRaw().last()
                medicationType.id
            }

            var medication = Medication(
                nameInput.text.toString(),
                hour,
                minute,
                detailInput.text.toString(),
                startDay,
                startMonth,
                startYear,
                notify = notify,
                requirePhotoProof = requirePhotoProof,
                typeId = typeId,
                rxNumber = rxNumberInput.text.toString()
            )
            medication.moreDosesPerDay = repeatScheduleList
            medicationDao(this).insertAll(medication)
            medication = medicationDao(this).getAllRaw().last()

            alarmIntent = AlarmIntentManager.buildNotificationAlarm(this, medication)

            if (notify) {
                //Set alarm

                AlarmIntentManager.setExact(alarmManager, alarmIntent, medication.calculateNextDose().timeInMillis)

                val receiver = ComponentName(this, ActionReceiver::class.java)

                this.packageManager.setComponentEnabledSetting(
                    receiver,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }

            mainScope.launch {
                Toast.makeText(context, getString(R.string.med_saved), Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun openSchedulePicker(view: View) {
        if (!pickerIsOpen) {
            pickerIsOpen = true
            schedulePickerCaller = view

            if (view == repeatScheduleButton) {
                schedulePicker.hour = hour
                schedulePicker.minute = minute
                schedulePicker.startDay = startDay
                schedulePicker.startMonth = startMonth
                schedulePicker.startYear = startYear
                schedulePicker.daysBetween = daysBetween
                schedulePicker.weeksBetween = weeksBetween
                schedulePicker.monthsBetween = monthsBetween
                schedulePicker.yearsBetween = yearsBetween
            }
            else {
                val callingIndex = scheduleButtonsRows.indexOf(schedulePickerCaller!!.parent as LinearLayoutCompat)

                schedulePicker.hour = repeatScheduleList[callingIndex].hour
                schedulePicker.minute = repeatScheduleList[callingIndex].minute
                schedulePicker.startDay = repeatScheduleList[callingIndex].startDay
                schedulePicker.startMonth = repeatScheduleList[callingIndex].startMonth
                schedulePicker.startYear = repeatScheduleList[callingIndex].startYear
                schedulePicker.daysBetween = repeatScheduleList[callingIndex].daysBetween
                schedulePicker.weeksBetween = repeatScheduleList[callingIndex].weeksBetween
                schedulePicker.monthsBetween = repeatScheduleList[callingIndex].monthsBetween
                schedulePicker.yearsBetween = repeatScheduleList[callingIndex].yearsBetween
            }

            schedulePicker.show(supportFragmentManager, getString(R.string.schedule_picker_tag))
        }
        //Toast.makeText(this, "onClick works", Toast.LENGTH_SHORT).show()
    }

    private fun allSchedulesAreValid(): Boolean {
        var timesAreValid = true

        if (hour < 0 || minute < 0 || startDay < 0 || startMonth < 0 || startYear < 0) {
            timesAreValid = false
        }

        var i = 0
        var schedule: RepeatSchedule
        while (timesAreValid && i < repeatScheduleList.size) {
            schedule = repeatScheduleList[i]
            if (schedule.hour < 0 || schedule.minute < 0 || schedule.startDay < 0 || schedule.startMonth < 0 || schedule.startYear < 0) {
                timesAreValid = false
            }
            i++
        }

        return timesAreValid
    }
}