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
import dev.corruptedark.diditakemymeds.MedicationDB.Companion.MED_TYPE_TABLE

@Dao
interface MedicationTypeDao {

    @Insert
    fun insertAll(vararg medicationType: MedicationType)

    @Delete
    fun delete(medicationType: MedicationType)

    @Query("SELECT * FROM $MED_TYPE_TABLE WHERE id = :typeId LIMIT 1")
    fun get(typeId: Long): MedicationType

    @Query("SELECT * FROM $MED_TYPE_TABLE WHERE name = :typeName LIMIT 1")
    fun get(typeName: String): MedicationType

    @Query("SELECT * FROM $MED_TYPE_TABLE")
    fun getAll(): LiveData<MutableList<MedicationType>>

    @Query("SELECT * FROM $MED_TYPE_TABLE")
    fun getAllRaw(): MutableList<MedicationType>

    @Query("SELECT EXISTS(SELECT * FROM $MED_TYPE_TABLE WHERE id = :typeId)")
    fun typeExists(typeId: Long): Boolean

    @Query("SELECT EXISTS(SELECT * FROM $MED_TYPE_TABLE WHERE name = :typeName)")
    fun typeExists(typeName: String): Boolean
}