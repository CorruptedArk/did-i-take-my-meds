package dev.corruptedark.diditakemymeds

import androidx.lifecycle.LiveData
import androidx.room.*
import dev.corruptedark.diditakemymeds.MedicationDB.Companion.IMAGE_TABLE

@Dao
interface ProofImageDao {

    @Insert
    fun insertAll(vararg proofImages: ProofImage)

    @Update
    fun updateProofImages(vararg proofImages: ProofImage)

    @Delete
    fun delete(proofImage: ProofImage)

    @Query("SELECT * FROM $IMAGE_TABLE WHERE medId = :medId AND doseTime = :doseTime LIMIT 1")
    fun get(medId: Long, doseTime: Long): ProofImage

    @Query("SELECT * FROM $IMAGE_TABLE")
    fun getAll(): LiveData<MutableList<ProofImage>>

    @Query("SELECT * FROM $IMAGE_TABLE")
    fun getAllRaw(): MutableList<ProofImage>

    @Query("SELECT EXISTS(SELECT * FROM $IMAGE_TABLE WHERE medId = :medId AND doseTime = :doseTime)")
    fun proofImageExists(medId: Long, doseTime: Long): Boolean

}