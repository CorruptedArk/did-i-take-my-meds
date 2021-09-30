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
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.lang.StringBuilder
import java.math.BigInteger
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
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

    companion object {

        val FALLBACK_TRANSITION_TIME = Long.MAX_VALUE
        val INVALID_MED_ID = -1L

        fun doseString(context: Context, doseTime: Long): String {
            val isSystem24Hour = DateFormat.is24HourFormat(context)
            val calendar = Calendar.getInstance()
            val doseCal: Calendar = Calendar.getInstance()
            doseCal.timeInMillis = doseTime
            val today = calendar.clone() as Calendar
            calendar.add(Calendar.DATE, -1)
            val yesterday = calendar.clone() as Calendar
            calendar.add(Calendar.DATE, 2)
            val tomorrow = calendar.clone() as Calendar

            val dayString: String =
                if (doseCal.get(Calendar.DATE) == today.get(Calendar.DATE) &&
                    doseCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                    doseCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
                    context.getString(R.string.today)
                }
                else if (doseCal.get(Calendar.DATE) == yesterday.get(Calendar.DATE) &&
                    doseCal.get(Calendar.MONTH) == yesterday.get(Calendar.MONTH) &&
                    doseCal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR)) {
                    context.getString(R.string.yesterday)
                } else if (doseCal.get(Calendar.DATE) == tomorrow.get(Calendar.DATE) &&
                    doseCal.get(Calendar.MONTH) == tomorrow.get(Calendar.MONTH) &&
                    doseCal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR)) {
                    context.getString(R.string.tomorrow)
                } else {
                    DateFormat.format(context.getString(R.string.date_format), doseCal) as String
                }

            val time = if (isSystem24Hour)
                DateFormat.format(context.getString(R.string.time_24), doseCal)
            else
                DateFormat.format(context.getString(R.string.time_12), doseCal)

            val builder: StringBuilder = StringBuilder()
                .append(time)
                .append(" ")
                .append(dayString)

            return builder.toString()
        }

        fun compareByName(a: Medication, b: Medication): Int {
            return a.name.compareTo(b.name)
        }

        fun compareByTime(a: Medication, b: Medication): Int {
            val aDose = a.calculateClosestDose()
            val bDose = b.calculateClosestDose()
            return if (aDose.timeInMillis == bDose.timeInMillis)
                compareByName(a, b)
            else
                (aDose.timeInMillis - bDose.timeInMillis).sign
        }

        fun compareByClosestDoseTransition(a: Medication, b: Medication): Int {
            val aTransition = a.closestDoseTransitionTime()
            val bTransition = b.closestDoseTransitionTime()

            return (aTransition - bTransition).sign
        }
    }

    /**
     * Updates the start times of schedules in this medication to future times
     *
     * Warning: This only updates in the current instance of the medication, database updates must happen elsewhere
     * It is recommended to call this before calculating next and closest doses
     */
    fun updateStartsToFuture() {
        if(!isAsNeeded()) {
            val localCalendar = Calendar.getInstance()
            val currentTime = localCalendar.timeInMillis

            localCalendar.set(Calendar.HOUR_OF_DAY, hour)
            localCalendar.set(Calendar.MINUTE, minute)
            localCalendar.set(Calendar.SECOND, 0)
            localCalendar.set(Calendar.MILLISECOND, 0)
            localCalendar.set(Calendar.DAY_OF_MONTH, startDay)
            localCalendar.set(Calendar.MONTH, startMonth)
            localCalendar.set(Calendar.YEAR, startYear)

            if (currentTime >= localCalendar.timeInMillis) {
                while (currentTime >= localCalendar.timeInMillis) {
                    localCalendar.add(Calendar.DATE, daysBetween)
                    localCalendar.add(Calendar.WEEK_OF_YEAR, weeksBetween)
                    localCalendar.add(Calendar.MONTH, monthsBetween)
                    localCalendar.add(Calendar.YEAR, yearsBetween)
                }

                startDay = localCalendar.get(Calendar.DAY_OF_MONTH)
                startMonth = localCalendar.get(Calendar.MONTH)
                startYear = localCalendar.get(Calendar.YEAR)
            }

            moreDosesPerDay.forEach { schedule ->
                localCalendar.set(Calendar.HOUR_OF_DAY, schedule.hour)
                localCalendar.set(Calendar.MINUTE, schedule.minute)
                localCalendar.set(Calendar.SECOND, 0)
                localCalendar.set(Calendar.MILLISECOND, 0)
                localCalendar.set(Calendar.DAY_OF_MONTH, schedule.startDay)
                localCalendar.set(Calendar.MONTH, schedule.startMonth)
                localCalendar.set(Calendar.YEAR, schedule.startYear)

                if (currentTime >= localCalendar.timeInMillis) {
                    while (currentTime >= localCalendar.timeInMillis) {
                        localCalendar.add(Calendar.DATE, schedule.daysBetween)
                        localCalendar.add(Calendar.WEEK_OF_YEAR, schedule.weeksBetween)
                        localCalendar.add(Calendar.MONTH, schedule.monthsBetween)
                        localCalendar.add(Calendar.YEAR, schedule.yearsBetween)
                    }

                    schedule.startDay = localCalendar.get(Calendar.DAY_OF_MONTH)
                    schedule.startMonth = localCalendar.get(Calendar.MONTH)
                    schedule.startYear = localCalendar.get(Calendar.YEAR)
                }
            }
        }
    }

    fun calculateNextDose(): ScheduleSortTriple {
        val scheduleTripleList = ArrayList<ScheduleSortTriple>()

        val localCalendar = Calendar.getInstance()
        val currentTime = localCalendar.timeInMillis
        var scheduleTriple: ScheduleSortTriple
        var nextDose: ScheduleSortTriple

        localCalendar.set(Calendar.HOUR_OF_DAY, hour)
        localCalendar.set(Calendar.MINUTE, minute)
        localCalendar.set(Calendar.SECOND, 0)
        localCalendar.set(Calendar.MILLISECOND, 0)
        localCalendar.set(Calendar.DAY_OF_MONTH, startDay)
        localCalendar.set(Calendar.MONTH, startMonth)
        localCalendar.set(Calendar.YEAR, startYear)
        scheduleTriple = ScheduleSortTriple(
            localCalendar.timeInMillis,
            RepeatSchedule(
                hour,
                minute,
                startDay,
                startMonth,
                startYear,
                daysBetween,
                weeksBetween,
                monthsBetween,
                yearsBetween
            ),
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

    fun calculateClosestDose(): ScheduleSortTriple {
        val scheduleTripleList = ArrayList<ScheduleSortTriple>()

        val localCalendar = Calendar.getInstance()
        val currentTime = localCalendar.timeInMillis
        var scheduleTriple: ScheduleSortTriple
        var closestDose: ScheduleSortTriple

        localCalendar.set(Calendar.HOUR_OF_DAY, hour)
        localCalendar.set(Calendar.MINUTE, minute)
        localCalendar.set(Calendar.SECOND, 0)
        localCalendar.set(Calendar.MILLISECOND, 0)
        localCalendar.set(Calendar.DAY_OF_MONTH, startDay)
        localCalendar.set(Calendar.MONTH, startMonth)
        localCalendar.set(Calendar.YEAR, startYear)
        scheduleTriple = ScheduleSortTriple(
            localCalendar.timeInMillis,
            RepeatSchedule(
                hour,
                minute,
                startDay,
                startMonth,
                startYear,
                daysBetween,
                weeksBetween,
                monthsBetween,
                yearsBetween
            ),
            -1
        )

        closestDose = scheduleTriple

        scheduleTripleList.add(scheduleTriple)

        localCalendar.add(Calendar.DATE, -daysBetween)
        localCalendar.add(Calendar.WEEK_OF_YEAR, -weeksBetween)
        localCalendar.add(Calendar.MONTH, -monthsBetween)
        localCalendar.add(Calendar.YEAR, -yearsBetween)
        scheduleTriple = ScheduleSortTriple(
            localCalendar.timeInMillis,
            RepeatSchedule(
                hour,
                minute,
                startDay,
                startMonth,
                startYear,
                daysBetween,
                weeksBetween,
                monthsBetween,
                yearsBetween
            ),
            -1
        )

        scheduleTripleList.add(scheduleTriple)

        localCalendar.add(Calendar.DATE, 2 * daysBetween)
        localCalendar.add(Calendar.WEEK_OF_YEAR, 2 * weeksBetween)
        localCalendar.add(Calendar.MONTH, 2 * monthsBetween)
        localCalendar.add(Calendar.YEAR, 2 * yearsBetween)
        scheduleTriple = ScheduleSortTriple(
            localCalendar.timeInMillis,
            RepeatSchedule(
                hour,
                minute,
                startDay,
                startMonth,
                startYear,
                daysBetween,
                weeksBetween,
                monthsBetween,
                yearsBetween
            ),
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

            localCalendar.add(Calendar.DATE, -schedule.daysBetween)
            localCalendar.add(Calendar.WEEK_OF_YEAR, -schedule.weeksBetween)
            localCalendar.add(Calendar.MONTH, -schedule.monthsBetween)
            localCalendar.add(Calendar.YEAR, -schedule.yearsBetween)
            scheduleTriple = ScheduleSortTriple(localCalendar.timeInMillis, schedule, index)

            scheduleTripleList.add(scheduleTriple)

            localCalendar.add(Calendar.DATE, 2 * schedule.daysBetween)
            localCalendar.add(Calendar.WEEK_OF_YEAR, 2 * schedule.weeksBetween)
            localCalendar.add(Calendar.MONTH, 2 * schedule.monthsBetween)
            localCalendar.add(Calendar.YEAR, 2 * schedule.yearsBetween)
            scheduleTriple = ScheduleSortTriple(localCalendar.timeInMillis, schedule, index)

            scheduleTripleList.add(scheduleTriple)
        }

        scheduleTripleList.sortWith { schedule1, schedule2 ->
            (abs(schedule1.timeInMillis - currentTime) - abs(schedule2.timeInMillis - currentTime)).sign
        }

        closestDose = scheduleTripleList.first()

        return closestDose
    }

    fun closestDoseAlreadyTaken(): Boolean {
        val lastDose: Long = try {
            doseRecord.first().closestDose
        }
        catch (except: NoSuchElementException) {
            INVALID_MED_ID
        }
        val closestDose = calculateClosestDose().timeInMillis

        return lastDose == closestDose
    }

    /**
     * Finds the time at which the closest dose will change
     */
    fun closestDoseTransitionTime(): Long {
        updateStartsToFuture()
        return if (!isAsNeeded()) {
            ((calculateClosestDose().timeInMillis.toBigInteger() + calculateNextDose().timeInMillis.toBigInteger()) / 2L.toBigInteger()).toLong() + 1L
        }
        else {
            FALLBACK_TRANSITION_TIME
        }
    }

    fun timeSinceLastTakenDose(): Long {

        return if (doseRecord.isNotEmpty()) {
            System.currentTimeMillis() - doseRecord.first().doseTime
        }
        else
        {
            System.currentTimeMillis()
        }
    }

    fun addNewTakenDose(takenDose: DoseRecord) {
        doseRecord.add(takenDose)
        doseRecord.sort()
    }

    fun isAsNeeded(): Boolean {
        return hour < 0 || minute < 0 || startDay < 0 || startMonth < 0 || startYear < 0
    }
}