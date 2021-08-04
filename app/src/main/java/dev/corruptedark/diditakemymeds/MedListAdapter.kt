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

class MedListAdapter(private val context: Context, private val medications: MutableList<Medication>) : BaseAdapter() {
    private val isSystem24Hour = DateFormat.is24HourFormat(context)
    private val calendar = Calendar.getInstance()

    override fun getCount(): Int {
        return medications.size
    }

    override fun getItem(position: Int): Medication {
        return medications[position]
    }

    override fun getItemId(position: Int): Long {
        return medications[position].id
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var view = convertView
        if(view == null)
            view = LayoutInflater.from(context).inflate(R.layout.med_list_item, parent, false)
        val nameLabel = view?.findViewById<MaterialTextView>(R.id.name_label)
        nameLabel?.text = medications[position].name

        val timeLabel = view?.findViewById<MaterialTextView>(R.id.time_label)
        calendar.set(Calendar.HOUR_OF_DAY, medications[position].hour)
        calendar.set(Calendar.MINUTE, medications[position].minute)
        if (medications[position].isAsNeeded()) {
            timeLabel?.visibility = View.GONE
        }
        else {
            timeLabel?.visibility = View.VISIBLE
            timeLabel?.text = if (isSystem24Hour) DateFormat.format(context.getString(R.string.time_24), calendar)
                else DateFormat.format(context.getString(R.string.time_12), calendar)
        }
        return view!!
    }
}