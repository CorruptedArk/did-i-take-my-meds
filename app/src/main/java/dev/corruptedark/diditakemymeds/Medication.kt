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

import android.text.format.DateFormat
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

@Entity(tableName = "medication")
data class Medication (@ColumnInfo(name = "name") var name: String,
                       @ColumnInfo(name = "hour") var hour: Int,
                       @ColumnInfo(name = "minute") var minute: Int,
                       @ColumnInfo(name = "description") var description: String,
                       @ColumnInfo(name = "startDay") var startDay: Int,
                       @ColumnInfo(name = "startMonth") var startMonth: Int,
                       @ColumnInfo(name = "startYear") var startYear: Int,
                       @ColumnInfo(name = "daysBetween") var daysBetween: Int = 1,
                       @ColumnInfo(name = "weeksBetween") var weeksBetween: Int = 0,
                       @ColumnInfo(name = "monthsBetween") var monthsBetween: Int = 0,
                       @ColumnInfo(name = "yearsBetween") var yearsBetween: Int = 0,
                       @ColumnInfo(name = "notify") var notify: Boolean = true) {

    @PrimaryKey(autoGenerate = true) var id: Long = 0
    @ColumnInfo(name = "dose_record") var doseRecord: ArrayList<DoseRecord> = ArrayList()
    @ColumnInfo(name = "moreDosesPerDay") var moreDosesPerDay: ArrayList<RepeatSchedule> = ArrayList()

    fun calculateNextDose(): ScheduleSortTriple {
        val scheduleTripleList = ArrayList<ScheduleSortTriple>()

        val currentTime = System.currentTimeMillis()
        val localCalendar = Calendar.getInstance()
        var scheduleTriple: ScheduleSortTriple
        var nextDose: ScheduleSortTriple

        localCalendar.set(Calendar.HOUR_OF_DAY, hour)
        localCalendar.set(Calendar.MINUTE, minute)
        localCalendar.set(Calendar.SECOND, 0)
        localCalendar.set(Calendar.MILLISECOND, 0)
        localCalendar.set(Calendar.DAY_OF_MONTH, startDay)
        localCalendar.set(Calendar.MONTH, startMonth)
        localCalendar.set(Calendar.YEAR, startYear)
        scheduleTriple = ScheduleSortTriple(localCalendar.timeInMillis,
            RepeatSchedule(hour, minute, startDay, startMonth, startYear, daysBetween, weeksBetween, monthsBetween, yearsBetween),
            -1
        )

        nextDose = scheduleTriple

        scheduleTripleList.add(scheduleTriple)

        moreDosesPerDay.forEachIndexed { index, schedule ->
            localCalendar.set(Calendar.HOUR_OF_DAY, schedule.hour)
            localCalendar.set(Calendar.MINUTE, schedule.minute)
            localCalendar.set(Calendar.SECOND, 0)
            localCalendar.set(Calendar.MILLISECOND, 0)
            localCalendar.set(Calendar.DAY_OF_MONTH, schedule.startDay)
            localCalendar.set(Calendar.MONTH, schedule.startMonth)
            localCalendar.set(Calendar.YEAR, schedule.startYear)

            scheduleTriple = ScheduleSortTriple(localCalendar.timeInMillis, schedule, index)

            scheduleTripleList.add(scheduleTriple)
        }

        scheduleTripleList.sort()

        for (triple in scheduleTripleList) {
            if (triple.timeInMillis > currentTime) {
                nextDose = triple
                break
            }
        }

        return nextDose
    }

    private fun updateStartsToFuture() {
        //TODO - Implement function
    }

   fun calculateClosestDose(): ScheduleSortTriple {
        val scheduleTripleList = ArrayList<ScheduleSortTriple>()

        val currentTime = System.currentTimeMillis()
        val localCalendar = Calendar.getInstance()
        var scheduleTriple: ScheduleSortTriple
        var closestDose: ScheduleSortTriple

        localCalendar.set(Calendar.HOUR_OF_DAY, hour)
        localCalendar.set(Calendar.MINUTE, minute)
        localCalendar.set(Calendar.SECOND, 0)
        localCalendar.set(Calendar.MILLISECOND, 0)
        localCalendar.set(Calendar.DAY_OF_MONTH, startDay)
        localCalendar.set(Calendar.MONTH, startMonth)
        localCalendar.set(Calendar.YEAR, startYear)
        scheduleTriple = ScheduleSortTriple(localCalendar.timeInMillis,
            RepeatSchedule(hour, minute, startDay, startMonth, startYear, daysBetween, weeksBetween, monthsBetween, yearsBetween),
            -1
        )

        closestDose = scheduleTriple

        scheduleTripleList.add(scheduleTriple)

        localCalendar.add(Calendar.DATE, -daysBetween)
        localCalendar.add(Calendar.WEEK_OF_YEAR, -weeksBetween)
        localCalendar.add(Calendar.MONTH, -monthsBetween)
        localCalendar.add(Calendar.YEAR, -yearsBetween)
        scheduleTriple = ScheduleSortTriple(localCalendar.timeInMillis,
            RepeatSchedule(hour, minute, startDay, startMonth, startYear, daysBetween, weeksBetween, monthsBetween, yearsBetween),
            -1
        )

        scheduleTripleList.add(scheduleTriple)

        localCalendar.add(Calendar.DATE, -daysBetween)
        localCalendar.add(Calendar.WEEK_OF_YEAR, -weeksBetween)
        localCalendar.add(Calendar.MONTH, -monthsBetween)
        localCalendar.add(Calendar.YEAR, -yearsBetween)
        scheduleTriple = ScheduleSortTriple(localCalendar.timeInMillis,
            RepeatSchedule(hour, minute, startDay, startMonth, startYear, daysBetween, weeksBetween, monthsBetween, yearsBetween),
            -1
        )

        scheduleTripleList.add(scheduleTriple)

        localCalendar.add(Calendar.DATE, 2*daysBetween)
        localCalendar.add(Calendar.WEEK_OF_YEAR, 2*weeksBetween)
        localCalendar.add(Calendar.MONTH, 2*monthsBetween)
        localCalendar.add(Calendar.YEAR, 2*yearsBetween)
        scheduleTriple = ScheduleSortTriple(localCalendar.timeInMillis,
            RepeatSchedule(hour, minute, startDay, startMonth, startYear, daysBetween, weeksBetween, monthsBetween, yearsBetween),
            -1
        )

        scheduleTripleList.add(scheduleTriple)

        moreDosesPerDay.forEachIndexed { index, schedule ->
            localCalendar.set(Calendar.HOUR_OF_DAY, schedule.hour)
            localCalendar.set(Calendar.MINUTE, schedule.minute)
            localCalendar.set(Calendar.SECOND, 0)
            localCalendar.set(Calendar.MILLISECOND, 0)
            localCalendar.set(Calendar.DAY_OF_MONTH, schedule.startDay)
            localCalendar.set(Calendar.MONTH, schedule.startMonth)
            localCalendar.set(Calendar.YEAR, schedule.startYear)
            scheduleTriple = ScheduleSortTriple(localCalendar.timeInMillis, schedule, index)

            scheduleTripleList.add(scheduleTriple)

            localCalendar.add(Calendar.DATE, -daysBetween)
            localCalendar.add(Calendar.WEEK_OF_YEAR, -weeksBetween)
            localCalendar.add(Calendar.MONTH, -monthsBetween)
            localCalendar.add(Calendar.YEAR, -yearsBetween)
            scheduleTriple = ScheduleSortTriple(localCalendar.timeInMillis, schedule, index)

            scheduleTripleList.add(scheduleTriple)

            localCalendar.add(Calendar.DATE, 2*daysBetween)
            localCalendar.add(Calendar.WEEK_OF_YEAR, 2*weeksBetween)
            localCalendar.add(Calendar.MONTH, 2*monthsBetween)
            localCalendar.add(Calendar.YEAR, 2*yearsBetween)
            scheduleTriple = ScheduleSortTriple(localCalendar.timeInMillis, schedule, index)
        }

        scheduleTripleList.sortWith { schedule1, schedule2 ->
            (abs(schedule1.timeInMillis - currentTime) - abs(schedule2.timeInMillis - currentTime)).sign
        }

        closestDose = scheduleTripleList.first()

        return closestDose
    }

}