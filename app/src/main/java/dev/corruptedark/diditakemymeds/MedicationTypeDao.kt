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

    @Query("SELECT * FROM $MED_TYPE_TABLE")
    fun getAll(): LiveData<MutableList<MedicationType>>

    @Query("SELECT * FROM $MED_TYPE_TABLE")
    fun getAllRaw(): MutableList<MedicationType>

    @Query("SELECT EXISTS(SELECT * FROM $MED_TYPE_TABLE WHERE id = :typeId)")
    fun typeExists(typeId: Long): Boolean

}