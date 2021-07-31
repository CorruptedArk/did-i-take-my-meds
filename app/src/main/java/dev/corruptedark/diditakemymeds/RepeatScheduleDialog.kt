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

import android.content.Context
import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.Rect
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import it.sephiroth.android.library.numberpicker.NumberPicker
import it.sephiroth.android.library.numberpicker.doOnProgressChanged
import java.util.*


class RepeatSheduleDialog : DialogFragment() {

    private lateinit var callingContext: Context
    private lateinit var timePickerButton: MaterialButton
    private lateinit var startDateButton: MaterialButton
    private lateinit var daysBetweenPicker: NumberPicker
    private lateinit var weeksBetweenPicker: NumberPicker
    private lateinit var monthsBetweenPicker: NumberPicker
    private lateinit var yearsBetweenPicker: NumberPicker
    private lateinit var cancelButton: MaterialButton
    private var confirmButton: MaterialButton? = null
    private var confirmListener: View.OnClickListener? = null
    private var dismissListener: DialogInterface.OnDismissListener? = null
    private @Volatile var pickerIsOpen = false
    var hour = -1
    var minute = -1
    var startDay = -1
    var startMonth = -1
    var startYear = -1
    var daysBetween = 1
    var weeksBetween = 0
    var monthsBetween = 0
    var yearsBetween = 0

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
        startDateButton = view.findViewById(R.id.start_date_button)
        daysBetweenPicker = view.findViewById(R.id.days_between_picker)
        weeksBetweenPicker = view.findViewById(R.id.weeks_between_picker)
        monthsBetweenPicker = view.findViewById(R.id.months_between_picker)
        yearsBetweenPicker = view.findViewById(R.id.years_between_picker)
        cancelButton = view.findViewById(R.id.cancel_button)
        confirmButton = view.findViewById(R.id.confirm_button)



        if (confirmListener != null)
            confirmButton?.setOnClickListener(confirmListener)

        /*
        if (dismissListener != null)
            dialog?.setOnDismissListener(dismissListener)
        */

        timePickerButton.setOnClickListener {
            openTimePicker(it)
        }

        startDateButton.setOnClickListener {
            openDatePicker(it)
        }

        daysBetweenPicker.doOnProgressChanged { numberPicker, progress, formUser ->
            daysBetween = progress
        }

        weeksBetweenPicker.doOnProgressChanged { numberPicker, progress, formUser ->
            weeksBetween = progress
        }

        monthsBetweenPicker.doOnProgressChanged { numberPicker, progress, formUser ->
            monthsBetween = progress
        }

        yearsBetweenPicker.doOnProgressChanged { numberPicker, progress, formUser ->
            yearsBetween = progress
        }

        cancelButton.setOnClickListener {
            dismiss()
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

    fun addDismissListener(listener: DialogInterface.OnDismissListener) {
        dismissListener = listener
        //dialog?.setOnDismissListener(listener)
    }

    fun addConfirmListener(listener: View.OnClickListener) {
        confirmListener = listener
        confirmButton?.setOnClickListener(listener)
    }

    private fun openTimePicker(view: View) {
        if (!pickerIsOpen) {
            pickerIsOpen = true

            val calendar = Calendar.getInstance()

            val initialHour: Int
            val initialMinute: Int

            if (hour >= 0 && minute >= 0) {
                initialHour = hour
                initialMinute = minute
            }
            else
            {
                initialHour = calendar.get(Calendar.HOUR_OF_DAY)
                initialMinute = calendar.get(Calendar.MINUTE)
            }

            val isSystem24Hour = DateFormat.is24HourFormat(callingContext)
            val clockFormat = if (isSystem24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(clockFormat)
                .setHour(initialHour)
                .setMinute(initialMinute)
                .setTitleText(getString(R.string.select_a_time))
                .build()
            timePicker.addOnPositiveButtonClickListener {
                hour = timePicker.hour
                minute = timePicker.minute
                calendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
                calendar.set(Calendar.MINUTE, timePicker.minute)
                val formattedTime = if (isSystem24Hour) DateFormat.format(getString(R.string.time_24), calendar)
                else DateFormat.format(getString(R.string.time_12), calendar)
                (view as MaterialButton).text = formattedTime
            }
            timePicker.addOnDismissListener {
                pickerIsOpen = false
            }
            timePicker.show((callingContext as AppCompatActivity).supportFragmentManager, getString(R.string.time_picker_tag))
        }
    }

    private fun openDatePicker(view: View) {
        if (!pickerIsOpen) {
            pickerIsOpen = true

            val calendar = Calendar.getInstance(TimeZone.getTimeZone(getString(R.string.utc)))

            val initialSelection = if (startDay >= 0 && startMonth >= 0 && startYear >= 0) {
                calendar.set(Calendar.DAY_OF_MONTH, startDay)
                calendar.set(Calendar.MONTH, startMonth)
                calendar.set(Calendar.YEAR, startYear)
                calendar.timeInMillis
            } else {
                MaterialDatePicker.todayInUtcMilliseconds()
            }
            
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setSelection(initialSelection)
                .setTitleText(getString(R.string.select_a_start_date))
                .build()
            datePicker.addOnPositiveButtonClickListener {
                calendar.timeInMillis = datePicker.selection!!
                startDay = calendar.get(Calendar.DATE)
                startMonth = calendar.get(Calendar.MONTH)
                startYear = calendar.get(Calendar.YEAR)
                (view as MaterialButton).text = DateFormat.format(getString(R.string.date_format), calendar)
            }
            datePicker.addOnDismissListener {
                pickerIsOpen = false
            }
            datePicker.show((callingContext as AppCompatActivity).supportFragmentManager, getString(R.string.date_picker_tag))
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        dismissListener?.onDismiss(dialog)
        super.onDismiss(dialog)
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

    fun scheduleIsValid(): Boolean {
       return hour >= 0 && minute >= 0 && startDay >= 0 && startMonth >= 0 && startYear >= 0
    }

}