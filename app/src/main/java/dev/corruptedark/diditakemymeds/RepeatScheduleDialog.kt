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

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.os.Bundle
import android.text.format.DateFormat
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.*


class RepeatSheduleDialog : DialogFragment() {

    lateinit var timePickerButton: MaterialButton
    private @Volatile var pickerIsOpen = false
    var hour = 0
    var minute = 0
    private lateinit var callingContext: Context

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_repeat_shedule_dialog, container, false)
        timePickerButton = view.findViewById(R.id.time_picker_button)

        timePickerButton.setOnClickListener {
            openTimePicker(it)
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setWidthPercent(85)
    }

    companion object {

        @JvmStatic
        fun newInstance(context: Context) =
            RepeatSheduleDialog().apply {
                callingContext = context
                arguments = Bundle().apply {

                }
            }
    }

    private fun openTimePicker(view: View) {
        if (!pickerIsOpen) {
            pickerIsOpen = true
            val isSystem24Hour = DateFormat.is24HourFormat(callingContext)
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
            timePicker.show((callingContext as AppCompatActivity).supportFragmentManager, getString(R.string.time_picker_tag))
        }
    }

    /**
     * Call this method (in onActivityCreated or later) to set
     * the width of the dialog to a percentage of the current
     * screen width.
     */
    private fun DialogFragment.setWidthPercent(percentage: Int) {
        val percent = percentage.toFloat() / 100
        val dm = Resources.getSystem().displayMetrics
        val rect = dm.run { Rect(0, 0, widthPixels, heightPixels) }
        val percentWidth = rect.width() * percent
        dialog?.window?.setLayout(percentWidth.toInt(), WRAP_CONTENT)
    }

}