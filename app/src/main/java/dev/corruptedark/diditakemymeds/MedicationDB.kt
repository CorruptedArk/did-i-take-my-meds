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
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase



@TypeConverters(Converters::class)
@Database(entities = [Medication::class], version = 2)
abstract  class MedicationDB: RoomDatabase() {
    abstract fun medicationDao(): MedicationDao

    companion object {
        const val DATABASE_NAME = "medications"
        const val MED_TABLE = "medication"
        @Volatile private var instance: MedicationDB? = null

        fun getInstance(context: Context): MedicationDB {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also {
                    instance = it
                }
            }
        }

        private fun buildDatabase(context: Context): MedicationDB {
            val MIGRATION_1_2 = object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN notify INTEGER DEFAULT 0 NOT NULL")
                }
            }

            return Room.databaseBuilder(context, MedicationDB::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2).build()
        }
    }
}