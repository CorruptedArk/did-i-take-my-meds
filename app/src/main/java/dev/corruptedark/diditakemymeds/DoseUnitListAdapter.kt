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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import com.google.android.material.textview.MaterialTextView
import java.util.*

class DoseUnitListAdapter(private val context: Context, private val doseUnits: MutableList<DoseUnit>) : BaseAdapter(), Filterable {
    var filteredUnits = doseUnits

    override fun getCount(): Int {
        return filteredUnits.size
    }

    override fun getItem(position: Int): DoseUnit {
        return filteredUnits[position]
    }

    override fun getItemId(position: Int): Long {
        return filteredUnits[position].id
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var view = convertView

        if (view == null)
            view = LayoutInflater.from(context).inflate(R.layout.dose_unit_list_item, parent, false)

        val unitNameLabel = view?.findViewById<MaterialTextView>(R.id.unit_name_label)
        unitNameLabel?.text = getItem(position).unit

        return view!!
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.toLowerCase(Locale.getDefault())

                val results = FilterResults()
                results.values = if (query == null || query.isEmpty()) {
                    doseUnits
                }
                else {
                    doseUnits.filter { doseUnit ->
                        query in doseUnit.unit.toLowerCase(Locale.getDefault())
                    }
                }

                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                @Suppress("UNCHECKED_CAST")
                filteredUnits = results?.values as MutableList<DoseUnit>
                notifyDataSetChanged()
            }
        }
    }


}