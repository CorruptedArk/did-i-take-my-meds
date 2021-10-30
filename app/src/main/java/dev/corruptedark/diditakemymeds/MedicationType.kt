package dev.corruptedark.diditakemymeds

import android.content.ContentValues
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicationType")
data class MedicationType(val name: String)
{
    @PrimaryKey(autoGenerate = true) var id: Long = 0

    fun toContentValues(): ContentValues {
        val contentValues = ContentValues()
        contentValues.put(::id.name, id)
        contentValues.put(::name.name, name)
        return contentValues
    }
}
