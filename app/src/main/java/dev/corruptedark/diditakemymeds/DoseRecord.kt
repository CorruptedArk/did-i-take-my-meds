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

import kotlin.math.sign

data class DoseRecord (val doseTime: Long, val closestDose: Long = -1L): Comparable<DoseRecord> {
    override fun compareTo(other: DoseRecord): Int {
        return if (closestDose == other.closestDose)
            (other.doseTime - doseTime).sign
        else
            (other.closestDose - closestDose).sign
    }

    fun isAsNeeded(): Boolean {
        return closestDose < 0
    }
}