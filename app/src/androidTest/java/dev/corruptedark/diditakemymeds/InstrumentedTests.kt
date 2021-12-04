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

import android.Manifest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import android.text.format.DateFormat
import androidx.test.rule.GrantPermissionRule
import com.google.android.things.device.TimeManager
import org.junit.Rule
import java.util.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class InstrumentedTests {

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("dev.corruptedark.diditakemymeds", appContext.packageName)
    }

    //Tests assume test device is using UTC time

    @Test
    fun doseString_en_isCorrect() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        val locale = Locale.ENGLISH
        val localizedResources = ResourceLocalizer.localizedResources(appContext, locale)

        val yesterdayString = localizedResources.getString(R.string.yesterday)
        val todayString = localizedResources.getString(R.string.today)
        val tomorrowString = localizedResources.getString(R.string.tomorrow)
        val dateFormat = localizedResources.getString(R.string.date_format)

        val timeFormat24 = localizedResources.getString(R.string.time_24)
        val timeFormat12 = localizedResources.getString(R.string.time_12)


        val doseString24 = Medication.doseString(
            yesterdayString,
            todayString,
            tomorrowString,
            0L,
            dateFormat,
            timeFormat24,
            locale
        )

        assertEquals("0:00 Jan 1, 1970", doseString24)

        val doseString12 = Medication.doseString(
            yesterdayString,
            todayString,
            tomorrowString,
            0L,
            dateFormat,
            timeFormat12,
            locale
        )

        assertEquals("12:00AM Jan 1, 1970", doseString12)
    }

    @Test
    fun doseString_de_isCorrect() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        val locale = Locale.GERMANY
        val localizedResources = ResourceLocalizer.localizedResources(appContext, locale)

        val yesterdayString = localizedResources.getString(R.string.yesterday)
        val todayString = localizedResources.getString(R.string.today)
        val tomorrowString = localizedResources.getString(R.string.tomorrow)
        val dateFormat = localizedResources.getString(R.string.date_format)

        val timeFormat24 = localizedResources.getString(R.string.time_24)
        val timeFormat12 = localizedResources.getString(R.string.time_12)


        val doseString24 = Medication.doseString(
            yesterdayString,
            todayString,
            tomorrowString,
            23587200000L,
            dateFormat,
            timeFormat24,
            locale
        )

        assertEquals("0:00 Okt. 1, 1970", doseString24)

        val doseString12 = Medication.doseString(
            yesterdayString,
            todayString,
            tomorrowString,
            23587200000L,
            dateFormat,
            timeFormat12,
            locale
        )

        assertEquals("12:00AM Okt. 1, 1970", doseString12)
    }

    @Test
    fun doseString_pt_BR_isCorrect() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        val locale = Locale.Builder().setLanguage("pt").setRegion("BR").build()
        val localizedResources = ResourceLocalizer.localizedResources(appContext, locale)

        val yesterdayString = localizedResources.getString(R.string.yesterday)
        val todayString = localizedResources.getString(R.string.today)
        val tomorrowString = localizedResources.getString(R.string.tomorrow)
        val dateFormat = localizedResources.getString(R.string.date_format)

        val timeFormat24 = localizedResources.getString(R.string.time_24)
        val timeFormat12 = localizedResources.getString(R.string.time_12)


        val doseString24 = Medication.doseString(
            yesterdayString,
            todayString,
            tomorrowString,
            23587200000L,
            dateFormat,
            timeFormat24,
            locale
        )

        assertEquals("0:00 out 1, 1970", doseString24)

        val doseString12 = Medication.doseString(
            yesterdayString,
            todayString,
            tomorrowString,
            23587200000L,
            dateFormat,
            timeFormat12,
            locale
        )

        assertEquals("12:00AM out 1, 1970", doseString12)
    }

    @Test
    fun doseString_zh_CN_isCorrect() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        val locale = Locale.Builder().setLanguage("zh").setRegion("CN").build()
        val localizedResources = ResourceLocalizer.localizedResources(appContext, locale)

        val yesterdayString = localizedResources.getString(R.string.yesterday)
        val todayString = localizedResources.getString(R.string.today)
        val tomorrowString = localizedResources.getString(R.string.tomorrow)
        val dateFormat = localizedResources.getString(R.string.date_format)

        val timeFormat24 = localizedResources.getString(R.string.time_24)
        val timeFormat12 = localizedResources.getString(R.string.time_12)


        val doseString24 = Medication.doseString(
            yesterdayString,
            todayString,
            tomorrowString,
            23587200000L,
            dateFormat,
            timeFormat24,
            locale
        )

        assertEquals("20:00 9月 30, 1970", doseString24)

        val doseString12 = Medication.doseString(
            yesterdayString,
            todayString,
            tomorrowString,
            23587200000L,
            dateFormat,
            timeFormat12,
            locale
        )

        assertEquals("8:00PM 9月 30, 1970", doseString12)
    }

}