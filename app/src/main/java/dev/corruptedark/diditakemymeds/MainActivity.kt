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

import android.app.*
import android.content.AbstractThreadedSyncAdapter
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.widget.ListViewCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import androidx.room.Room
import com.google.android.material.appbar.MaterialToolbar
import java.text.FieldPosition
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.prefs.Preferences
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var toolbar: MaterialToolbar
    private lateinit var medListView: ListView
    private lateinit var listEmptyLabel: AppCompatTextView
    private var medicationListAdapter: MedListAdapter? = null
    private lateinit var db: MedicationDB
    private lateinit var medicationDao: MedicationDao


    private val resultStarter = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        runOnUiThread {
            medicationListAdapter?.notifyDataSetChanged()
            if (!medications.isNullOrEmpty())
                listEmptyLabel.visibility = View.GONE
            else
                listEmptyLabel.visibility = View.VISIBLE
        }
    }

    companion object{
        var medications: MutableList<Medication>? = null
        const val CHANNEL_ID = "did_i_take_my_meds"
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        setContentView(R.layout.activity_main)
        medListView = findViewById(R.id.med_list_view)
        listEmptyLabel = findViewById(R.id.list_empty_label)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.background = ColorDrawable(ResourcesCompat.getColor(resources, R.color.purple_700, null))
        toolbar.logo = AppCompatResources.getDrawable(this, R.drawable.bar_logo)
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
                else
                    listEmptyLabel.visibility = View.VISIBLE
                medListView.adapter = medicationListAdapter
                medListView.onItemClickListener = AdapterView.OnItemClickListener { adapterView, view, i, l ->
                    openMedDetailActivity(i)
                }
            }

            val sharedPref = getPreferences(Context.MODE_PRIVATE)
            if (BuildConfig.VERSION_CODE > sharedPref.getInt(getString(R.string.last_version_used_key), 0)) {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                var alarmIntent: PendingIntent
                medications!!.forEach { medication ->
                    if (medication.notify) {
                        //Create alarm
                        alarmIntent =
                            Intent(this, AlarmReceiver::class.java).let { innerIntent ->
                                innerIntent.action = AlarmReceiver.NOTIFY_ACTION
                                innerIntent.putExtra(getString(R.string.med_id_key), medication.id)
                                PendingIntent.getBroadcast(
                                    this,
                                    medication.id.toInt(),
                                    innerIntent,
                                    0
                                )
                            }

                        alarmManager.cancel(alarmIntent)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                medication.calculateNextDose().timeInMillis,
                                alarmIntent
                            )
                        }
                        else {
                            alarmManager.set(
                                AlarmManager.RTC_WAKEUP,
                                medication.calculateNextDose().timeInMillis,
                                alarmIntent
                            )
                        }

                    }
                }
            }
            with (sharedPref.edit()) {
                putInt(getString(R.string.last_version_used_key), BuildConfig.VERSION_CODE)
                apply()
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
            R.id.info -> {
                openAboutActivity()
                true
            }
            R.id.add_med -> {
                openAddMedActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openAboutActivity() {
        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)
    }

    private fun openAddMedActivity() {
        val intent = Intent(this, AddMedActivity::class.java)
        resultStarter.launch(intent)
    }
}