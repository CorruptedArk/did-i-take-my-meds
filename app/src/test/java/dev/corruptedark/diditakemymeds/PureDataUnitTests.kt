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

import org.junit.Test

import org.junit.Assert.*

class PureDataUnitTests {

    /**
     * Higher valued doses should be treated as lower in comparisons
     */
    @Test
    fun doseRecordCompare_isCorrect() {
        val dose1 = DoseRecord(1L, 2L)
        val dose2 = DoseRecord(2L, 2L)

        assertEquals(1, dose1.compareTo(dose2))
        assertEquals(-1, dose2.compareTo(dose1))
        assertEquals(0, dose1.compareTo(dose1))
        assertEquals(0, dose2.compareTo(dose2))

        val dose3 = DoseRecord(1L)
        val dose4 = DoseRecord(2L)

        assertEquals(1, dose3.compareTo(dose4))
        assertEquals(-1, dose4.compareTo(dose3))
        assertEquals(0, dose3.compareTo(dose3))
        assertEquals(0, dose4.compareTo(dose4))

        val dose5 = DoseRecord(1L, 1L)
        val dose6 = DoseRecord(1L, 2L)

        assertEquals(1, dose5.compareTo(dose6))
        assertEquals(-1, dose6.compareTo(dose5))
        assertEquals(0, dose5.compareTo(dose5))
        assertEquals(0, dose6.compareTo(dose6))

        val dose7 = DoseRecord(1L, 1L)
        val dose8 = DoseRecord(2L, 2L)

        assertEquals(1, dose7.compareTo(dose8))
        assertEquals(-1, dose8.compareTo(dose7))
        assertEquals(0, dose7.compareTo(dose7))
        assertEquals(0, dose8.compareTo(dose8))

        val dose9 = DoseRecord(2L, 1L)
        val dose10 = DoseRecord(1L, 2L)

        assertEquals(1, dose9.compareTo(dose10))
        assertEquals(-1, dose10.compareTo(dose9))
        assertEquals(0, dose9.compareTo(dose9))
        assertEquals(0, dose10.compareTo(dose10))
    }

    @Test
    fun doseRecordAsNeeded_isCorrect() {
        val dose1 = DoseRecord(1L)
        assert(dose1.isAsNeeded())

        val dose2 = DoseRecord(1L, 5L)
        assert(!dose2.isAsNeeded())
    }

}