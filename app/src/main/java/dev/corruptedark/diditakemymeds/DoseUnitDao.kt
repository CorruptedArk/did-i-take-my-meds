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

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import dev.corruptedark.diditakemymeds.MedicationDB.Companion.DOSE_UNIT_TABLE

@Dao
interface DoseUnitDao {

    @Insert
    fun insertAll(vararg doseUnit: DoseUnit)

    @Delete
    fun delete(doseUnit: DoseUnit)

    @Query("SELECT * FROM $DOSE_UNIT_TABLE WHERE id = :unitId LIMIT 1")
    fun get(unitId: Long): DoseUnit

    @Query("SELECT * FROM $DOSE_UNIT_TABLE WHERE unit = :unitName LIMIT 1")
    fun get(unitName: String): DoseUnit

    @Query("SELECT * FROM $DOSE_UNIT_TABLE")
    fun getAll(): LiveData<MutableList<DoseUnit>>

    @Query("SELECT * FROM $DOSE_UNIT_TABLE")
    fun getAllRaw(): MutableList<DoseUnit>

    @Query("SELECT EXISTS(SELECT * FROM $DOSE_UNIT_TABLE WHERE id = :unitId)")
    fun unitExists(unitId: Long): Boolean

    @Query("SELECT EXISTS(SELECT * FROM $DOSE_UNIT_TABLE WHERE unit = :unitName)")
    fun unitExists(unitName: String): Boolean
}