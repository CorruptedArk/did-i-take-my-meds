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

import android.graphics.drawable.ColorDrawable
import  androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class EditMedActivity : AppCompatActivity() {
    lateinit var nameInput: TextInputEditText
    lateinit var timePickerButton: MaterialButton
    lateinit var detailInput: TextInputEditText

    @Volatile var pickerIsOpen = false
    var hour = -1
    var minute = -1
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    lateinit var medication: Medication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_med)

        medication = MainActivity.medications!![intent.getIntExtra(getString(R.string.med_position_key), -1)]
        nameInput = findViewById(R.id.med_name)
        timePickerButton = findViewById(R.id.time_picker_button)
        detailInput = findViewById(R.id.med_detail)

        val calendar = Calendar.getInstance()
        hour = medication.hour
        minute = medication.minute
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        val isSystem24Hour = DateFormat.is24HourFormat(this)
        val formattedTime = if (isSystem24Hour) DateFormat.format(getString(R.string.time_24), calendar)
            else DateFormat.format(getString(R.string.time_12), calendar)

        nameInput.setText(medication.name)
        timePickerButton.text = formattedTime
        detailInput.setText(medication.description)

        timePickerButton.setOnClickListener {
            openTimePicker(it)
        }
        supportActionBar?.setBackgroundDrawable(ColorDrawable(ResourcesCompat.getColor(resources, R.color.purple_700, null)))
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
                Toast.makeText(this, getString(R.string.edit_cancelled), Toast.LENGTH_SHORT).show()
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
        else if (hour !in 0..23 || minute !in 0..59) {
            runOnUiThread {
                Toast.makeText(this, getString(R.string.select_a_time), Toast.LENGTH_SHORT).show()
            }
            false
        }
        else {
            medication.name = nameInput.text.toString()
            medication.hour = hour
            medication.minute = minute
            medication.description = detailInput.text.toString()
            MedicationDB.getInstance(this).medicationDao().updateMedications(medication)
            runOnUiThread {
                Toast.makeText(this, getString(R.string.med_saved), Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun openTimePicker(view: View) {
        if (!pickerIsOpen) {
            pickerIsOpen = true
            val isSystem24Hour = DateFormat.is24HourFormat(this)
            val clockFormat = if (isSystem24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(clockFormat)
                .setHour(hour)
                .setMinute(minute)
                .setTitleText(getString(R.string.select_a_time))
                .build()
            timePicker.addOnPositiveButtonClickListener {
                val calendar = Calendar.getInstance()
                hour = timePicker.hour
                minute = timePicker.minute
                calendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
                calendar.set(Calendar.MINUTE, timePicker.minute)
                val formattedTime = if (isSystem24Hour) DateFormat.format(getString(R.string.time_24), calendar)
                else DateFormat.format(getString(R.string.time_12), calendar)
                timePickerButton.text = formattedTime
            }
            timePicker.addOnDismissListener {
                pickerIsOpen = false
            }
            timePicker.show(supportFragmentManager, getString(R.string.time_picker_tag))
        }
    }
}