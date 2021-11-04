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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.sign

@Entity(tableName = "medication")
data class Medication (var name: String,
                       var hour: Int,
                       var minute: Int,
                       var description: String,
                       var startDay: Int,
                       var startMonth: Int,
                       var startYear: Int,
                       var daysBetween: Int = 1,
                       var weeksBetween: Int = 0,
                       var monthsBetween: Int = 0,
                       var yearsBetween: Int = 0,
                       var notify: Boolean = true,
                       var requirePhotoProof: Boolean = true,
                       var active: Boolean = true,
                       var typeId: Long = 0,
                       var rxNumber: String = UNDEFINED
) {

    @PrimaryKey(autoGenerate = true) var id: Long = 0
    @ColumnInfo(name = "dose_record") var doseRecord: ArrayList<DoseRecord> = ArrayList()
    var moreDosesPerDay: ArrayList<RepeatSchedule> = ArrayList()

    companion object {

        const val FALLBACK_TRANSITION_TIME = Long.MAX_VALUE
        const val INVALID_MED_ID = -1L
        const val UNDEFINED = ""

        fun doseString(yesterdayString: String, todayString: String, tomorrowString: String, doseTime: Long, dateFormat: String, timeFormat: String, locale: Locale): String {
            val localizedFormatter = SimpleDateFormat(dateFormat, locale)

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
                    todayString
                }
                else if (doseCal.get(Calendar.DATE) == yesterday.get(Calendar.DATE) &&
                    doseCal.get(Calendar.MONTH) == yesterday.get(Calendar.MONTH) &&
                    doseCal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR)) {
                    yesterdayString
                } else if (doseCal.get(Calendar.DATE) == tomorrow.get(Calendar.DATE) &&
                    doseCal.get(Calendar.MONTH) == tomorrow.get(Calendar.MONTH) &&
                    doseCal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR)) {
                    tomorrowString
                } else {
                    localizedFormatter.format(doseCal.timeInMillis) as String
                }

            val time = DateFormat.format(timeFormat, doseCal)

            val builder: StringBuilder = StringBuilder()
                .append(time)
                .append(" ")
                .append(dayString)

            return builder.toString()
        }

        fun compareByName(a: Medication, b: Medication): Int {
            val byActive = compareByActive(a, b)

            return if (byActive != 0) {
                byActive
            }
            else {
                a.name.compareTo(b.name)
            }
        }

        fun compareByTime(a: Medication, b: Medication): Int {
            val byActive = compareByActive(a, b)
            val aDose = a.calculateClosestDose()
            val bDose = b.calculateClosestDose()

            return when {
                byActive != 0 -> {
                    byActive
                }
                aDose.timeInMillis == bDose.timeInMillis -> {
                    compareByName(a, b)
                }
                else -> {
                    (aDose.timeInMillis - bDose.timeInMillis).sign
                }
            }
        }

        fun compareByClosestDoseTransition(a: Medication, b: Medication): Int {
            val byActive = compareByActive(a, b)
            val aTransition = a.closestDoseTransitionTime()
            val bTransition = b.closestDoseTransitionTime()


            return if (byActive != 0) {
                byActive
            }
            else {
                (aTransition - bTransition).sign
            }
        }

        fun compareByType(a: Medication, b: Medication): Int {
            val byActive = compareByActive(a, b)
            val byName = compareByName(a, b)
            val byType = (a.typeId - b.typeId).sign

            return when {
                byActive != 0 -> {
                    byActive
                }
                byType == 0 -> {
                    byName
                }
                else -> {
                    byType
                }
            }
        }

        private fun compareByActive(a: Medication, b: Medication): Int {
            return when {
                a.active && !b.active -> {
                    -1
                }
                b.active && !a.active -> {
                    1
                }
                else -> {
                    0
                }
            }
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