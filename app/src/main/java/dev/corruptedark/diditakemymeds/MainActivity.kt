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

import android.app.Activity
import android.app.PendingIntent
import android.content.AbstractThreadedSyncAdapter
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.widget.ListViewCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import androidx.room.Room
import java.text.FieldPosition
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
    val DOSE_TIME = 79200000L
    val LAST_DOSE_TIME = "last_dose_time"
    val I_DID = "I did take them"
    val I_DID_NOT = "I did not take them"
    val DATABASE_NAME = "medications"
    val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    lateinit var medListView: ListView
    lateinit var listEmptyLabel: AppCompatTextView
    var medicationListAdapter: MedListAdapter? = null
    private lateinit var db: MedicationDB
    private lateinit var medicationDao: MedicationDao
    val resultStarter = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        runOnUiThread { medicationListAdapter?.notifyDataSetChanged() }
    }

    companion object{
        var medications: MutableList<Medication>? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.setBackgroundDrawable(ColorDrawable(ResourcesCompat.getColor(resources, R.color.purple_700, null)))
        medListView = findViewById(R.id.med_list_view)
        listEmptyLabel = findViewById(R.id.list_empty_label)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        executorService.execute {
            db = MedicationDB.getInstance(this)
            medicationDao = db.medicationDao()
            medications = medicationDao.getAll()
            medicationListAdapter = MedListAdapter(this, medications!!)

            runOnUiThread {
                if (!medications.isNullOrEmpty())
                    listEmptyLabel.visibility = View.GONE
                medListView.adapter = medicationListAdapter
                medListView.onItemClickListener = AdapterView.OnItemClickListener { adapterView, view, i, l ->
                    openMedDetailActivity(i)
                }
            }
        }
    }

    private fun openMedDetailActivity(medPosition: Int) {
        val intent = Intent(this, MedDetailActivity::class.java)
        intent.putExtra(getString(R.string.med_position_key), medPosition)
        resultStarter.launch(intent)
    }

    override fun onResume() {
        runOnUiThread {
            medicationListAdapter?.notifyDataSetChanged()
            if (!medications.isNullOrEmpty())
            {
                listEmptyLabel.visibility = View.GONE
            }
        }
        super.onResume()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.add_med -> {
                openAddMedActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openAddMedActivity() {
        val intent = Intent(this, AddMedActivity::class.java)
        resultStarter.launch(intent)
    }
}