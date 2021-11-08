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

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.*
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@TypeConverters(Converters::class)
@Database(entities = [Medication::class, ProofImage::class, MedicationType::class], version = 9)
abstract  class MedicationDB: RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun proofImageDao(): ProofImageDao
    abstract fun medicationTypeDao(): MedicationTypeDao
    abstract fun doseUnitDao(): DoseUnitDao

    companion object {
        const val DATABASE_NAME = "medications"
        const val TEST_DATABASE_NAME = "test"
        const val MED_TABLE = "medication"
        const val IMAGE_TABLE = "proofImage"
        const val MED_TYPE_TABLE = "medicationType"
        const val DOSE_UNIT_TABLE = "doseUnit"
        const val DATABASE_FILE_EXTENSION = ".db"
        @Volatile private var instance: MedicationDB? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN notify INTEGER DEFAULT 0 NOT NULL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val cal = Calendar.getInstance()
                database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN startDay INTEGER DEFAULT ${cal.get(Calendar.DAY_OF_MONTH)} NOT NULL")
                database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN startMonth INTEGER DEFAULT ${cal.get(Calendar.MONTH)} NOT NULL")
                database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN startYear INTEGER DEFAULT ${cal.get(Calendar.YEAR)} NOT NULL")
                database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN daysBetween INTEGER DEFAULT 1 NOT NULL")
                database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN weeksBetween INTEGER DEFAULT 0 NOT NULL")
                database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN monthsBetween INTEGER DEFAULT 0 NOT NULL")
                database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN yearsBetween INTEGER DEFAULT 0 NOT NULL")
                database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN moreDosesPerDay TEXT DEFAULT '[]' NOT NULL")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN requirePhotoProof INTEGER DEFAULT 0 NOT NULL")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS $IMAGE_TABLE (medId INTEGER NOT NULL, doseTime INTEGER NOT NULL, filePath TEXT NOT NULL, PRIMARY KEY(medId, doseTime))")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN active INTEGER DEFAULT 1 NOT NULL")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN typeId INTEGER DEFAULT 0 NOT NULL")
                database.execSQL("CREATE TABLE IF NOT EXISTS $MED_TYPE_TABLE (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN rxNumber TEXT DEFAULT '' NOT NULL")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN doseUnitId INTEGER DEFAULT ${Medication.DEFAULT_ID} NOT NULL")
                database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN amountPerDose DOUBLE DEFAULT ${Medication.UNDEFINED_AMOUNT} NOT NULL")
                database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN remainingDoses INTEGER DEFAULT ${Medication.UNDEFINED_REMAINING} NOT NULL")
                database.execSQL("ALTER TABLE $MED_TABLE ADD COLUMN takeWithFood INTEGER DEFAULT 0 NOT NULL")
                database.execSQL("CREATE TABLE IF NOT EXISTS $DOSE_UNIT_TABLE (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, unit TEXT NOT NULL)")
            }
        }

        private val MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9
        )

        @Synchronized
        fun getInstance(context: Context): MedicationDB {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also {
                    instance = it
                }
            }
        }

        private fun buildDatabase(context: Context): MedicationDB {
            return Room.databaseBuilder(context, MedicationDB::class.java, DATABASE_NAME)
                .addMigrations(*MIGRATIONS)
                .addCallback(
                    object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            runBlocking {
                                launch {
                                    val undefinedType = MedicationType(context.getString(R.string.undefined))
                                    undefinedType.id = 0
                                    val defaultTypeExists = db.query("SELECT * FROM $MED_TYPE_TABLE WHERE id = 0").count > 0
                                    if (!defaultTypeExists) {
                                        db.insert(
                                            MED_TYPE_TABLE,
                                            SQLiteDatabase.CONFLICT_FAIL,
                                            undefinedType.toContentValues()
                                        )

                                    }

                                    val undefinedUnit = DoseUnit(context.getString(R.string.undefined))
                                    undefinedUnit.id = 0
                                    val defaultUnitExists = db.query("SELECT * FROM $DOSE_UNIT_TABLE WHERE id = 0").count > 0
                                    if (!defaultUnitExists) {
                                        db.insert(
                                            DOSE_UNIT_TABLE,
                                            SQLiteDatabase.CONFLICT_FAIL,
                                            undefinedUnit.toContentValues()
                                        )
                                    }
                                    super.onOpen(db)
                                }
                            }
                        }
                    }
                )
                .build()
        }

        fun databaseFileIsValid(context: Context, databaseUri: Uri?): Boolean {
            return try {
                val restoreFileStream = context.contentResolver.openInputStream(databaseUri!!)!!
                restoreFileStream.copyTo(context.getDatabasePath(TEST_DATABASE_NAME).outputStream())
                restoreFileStream.close()
                val testDatabase = Room.databaseBuilder(context, MedicationDB::class.java, TEST_DATABASE_NAME)
                    .addMigrations(*MIGRATIONS).build()
                val hasEntries = testDatabase.medicationDao().getAllRaw().isNotEmpty()
                testDatabase.close()
                hasEntries
            } catch (exception: Exception) {
                exception.printStackTrace()
                false
            }
        }

        @Synchronized
        fun wipeInstance() {
            instance?.close()
            instance = null
        }

    }
}