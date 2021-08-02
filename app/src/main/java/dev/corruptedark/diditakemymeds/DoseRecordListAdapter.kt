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
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.google.android.material.textview.MaterialTextView
import java.util.*

class DoseRecordListAdapter(private val context: Context, private val doseRecordList: MutableList<DoseRecord>) : BaseAdapter(){
    private val isSystem24Hour = DateFormat.is24HourFormat(context)
    private val calendar = Calendar.getInstance()

    override fun getCount(): Int {
        return doseRecordList.size
    }

    override fun getItem(position: Int): Any {
        return doseRecordList[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var view = convertView
        if(view == null)
            view = LayoutInflater.from(context).inflate(R.layout.dose_list_item, parent, false)
        val doseTakenTimeLabel = view?.findViewById<MaterialTextView>(R.id.dose_taken_time_label)
        val closestTimeLabel = view?.findViewById<MaterialTextView>(R.id.closest_dose_time_label)

        doseTakenTimeLabel?.text = context.getString(R.string.time_taken, Medication.doseString(context, doseRecordList[position].doseTime))

        closestTimeLabel?.text = context.getString(R.string.closest_dose_label, Medication.doseString(context, doseRecordList[position].closestDose))

        return view!!
    }
}