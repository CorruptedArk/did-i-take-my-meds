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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.*
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.*
import java.time.Duration
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MedDetailActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var outerScroll: NestedScrollView
    private lateinit var nameLabel: MaterialTextView
    private lateinit var timeLabel: MaterialTextView
    private lateinit var notificationSwitch: SwitchMaterial
    private lateinit var detailLabel: MaterialTextView
    private lateinit var closestDoseLabel: MaterialTextView
    private lateinit var justTookItButton: MaterialButton
    private lateinit var timeSinceDoseLabel: MaterialTextView
    private lateinit var previousDosesList: ListView
    private var medication: Medication? = null
    private lateinit var doseRecordAdapter: DoseRecordListAdapter
    private val calendar = Calendar.getInstance()
    private var closestDose: Long = -1L
    private var dispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val lifecycleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val editResultStarter =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            GlobalScope.launch(dispatcher) {
                medication = MedicationDB.getInstance(context).medicationDao()
                    .get(intent.getLongExtra(getString(R.string.med_id_key), -1L))

                mainScope.launch {
                    nameLabel.text = medication!!.name

                    if (medication!!.isAsNeeded()) {
                        timeLabel.visibility = View.GONE
                        closestDoseLabel.visibility = View.GONE
                        notificationSwitch.visibility = View.GONE
                        notificationSwitch.isChecked = false
                    } else {
                        timeLabel.visibility = View.VISIBLE
                        val nextDose = medication!!.calculateNextDose().timeInMillis
                        timeLabel.text =
                            getString(
                                R.string.next_dose_label,
                                Medication.doseString(context, nextDose)
                            )
                        closestDoseLabel.visibility = View.VISIBLE
                        closestDose = medication!!.calculateClosestDose().timeInMillis
                        closestDoseLabel.text = getString(
                            R.string.closest_dose_label,
                            Medication.doseString(context, closestDose)
                        )
                        notificationSwitch.visibility = View.VISIBLE
                        notificationSwitch.isChecked = medication!!.notify
                    }

                    detailLabel.text = medication!!.description

                    if (medication!!.closestDoseAlreadyTaken() && !medication!!.isAsNeeded()) {
                        justTookItButton.text = getString(R.string.took_this_already)
                    } else {
                        justTookItButton.text = getString(R.string.i_just_took_it)
                    }

                    alarmIntent = AlarmIntentManager.buildNotificationAlarm(context, medication!!)

                    if (medication!!.notify) {
                        //Set alarm
                        alarmManager?.cancel(alarmIntent)

                        AlarmIntentManager.setExact(alarmManager, alarmIntent, medication!!.calculateNextDose().timeInMillis)

                        val receiver = ComponentName(context, ActionReceiver::class.java)

                        context.packageManager.setComponentEnabledSetting(
                            receiver,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    } else {
                        //Cancel alarm
                        alarmManager?.cancel(alarmIntent)
                    }
                }
            }
        }
    private var alarmManager: AlarmManager? = null
    private lateinit var alarmIntent: PendingIntent
    private val context = this
    private val mainScope = MainScope()
    private var refreshJob: Job? = null

    private val FALLBACK_DELAY = 60000L // 1 minute in milliseconds
    private val DAY_TO_HOURS = 24
    private val HOUR_TO_MINUTES = 60


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_med_detail)
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        toolbar = findViewById(R.id.toolbar)
        nameLabel = findViewById(R.id.name_label)
        timeLabel = findViewById(R.id.time_label)
        notificationSwitch = findViewById(R.id.notification_switch)
        detailLabel = findViewById(R.id.detail_label)
        closestDoseLabel = findViewById(R.id.closest_dose_label)
        justTookItButton = findViewById(R.id.just_took_it_button)
        timeSinceDoseLabel = findViewById(R.id.time_since_dose_label)
        previousDosesList = findViewById(R.id.previous_doses_list)
        setSupportActionBar(toolbar)
        toolbar.background =
            ColorDrawable(ResourcesCompat.getColor(resources, R.color.purple_700, null))
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        previousDosesList.onItemLongClickListener = AdapterView.OnItemLongClickListener { adapterView, view, i, l ->
            val dialogBuilder = MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.are_you_sure))
                .setMessage(getString(R.string.dose_record_delete_warning) + "\n\n" + Medication.doseString(context, medication!!.doseRecord[i].doseTime) )
                .setNegativeButton(getString(R.string.cancel)) { dialog, which ->
                    dialog.dismiss()
                }
                .setPositiveButton(getString(R.string.confirm)) { dialog, which ->
                    GlobalScope.launch(dispatcher) {
                        medication!!.doseRecord.removeAt(i)
                        MedicationDB.getInstance(context).medicationDao().updateMedications(medication!!)
                    }
                }

            dialogBuilder.show()
            true
        }

        outerScroll = findViewById(R.id.outer_scroll)
    }

    override fun onResume() {
        super.onResume()
        GlobalScope.launch(dispatcher) {
            refreshFromDatabase()
            if(medication != null)
                MedicationDB.getInstance(context).medicationDao().updateMedications(medication!!)
        }
        MedicationDB.getInstance(context).medicationDao().getAll().observe(context, {
            GlobalScope.launch(dispatcher) {
                refreshFromDatabase()
            }
        })

        refreshJob = startRefresherLoop(intent.getLongExtra(getString(R.string.med_id_key), -1))
    }

    @Synchronized
    private fun refreshFromDatabase() {
        val medId = intent.getLongExtra(getString(R.string.med_id_key), -1L)
        if (MedicationDB.getInstance(this).medicationDao().medicationExists(medId)) {
            medication = MedicationDB.getInstance(context).medicationDao().get(medId)
            medication!!.updateStartsToFuture()

            alarmIntent = AlarmIntentManager.buildNotificationAlarm(context, medication!!)

            calendar.set(Calendar.HOUR_OF_DAY, medication!!.hour)
            calendar.set(Calendar.MINUTE, medication!!.minute)

            val timeSinceTakenDose = medication!!.timeSinceLastTakenDose()
            val days = TimeUnit.MILLISECONDS.toDays(timeSinceTakenDose)
            val hours = TimeUnit.MILLISECONDS.toHours(timeSinceTakenDose) % DAY_TO_HOURS
            val minutes = TimeUnit.MILLISECONDS.toMinutes(timeSinceTakenDose) % HOUR_TO_MINUTES

            mainScope.launch {
                nameLabel.text = medication!!.name

                if (medication!!.isAsNeeded()) {
                    timeLabel.visibility = View.GONE
                    closestDoseLabel.visibility = View.GONE
                    notificationSwitch.visibility = View.GONE
                    notificationSwitch.isChecked = false
                } else {
                    timeLabel.visibility = View.VISIBLE
                    val nextDose = medication!!.calculateNextDose().timeInMillis
                    timeLabel.text =
                        getString(
                            R.string.next_dose_label,
                            Medication.doseString(context, nextDose)
                        )
                    closestDoseLabel.visibility = View.VISIBLE
                    closestDose = medication!!.calculateClosestDose().timeInMillis
                    closestDoseLabel.text = getString(
                        R.string.closest_dose_label,
                        Medication.doseString(context, closestDose)
                    )
                    notificationSwitch.visibility = View.VISIBLE
                    notificationSwitch.isChecked = medication!!.notify
                    notificationSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
                        medication!!.notify = isChecked
                        GlobalScope.launch(dispatcher) {
                            MedicationDB.getInstance(context).medicationDao()
                                .updateMedications(medication!!)
                        }
                        if (isChecked) {
                            //Set alarm
                            AlarmIntentManager.setExact(alarmManager, alarmIntent, medication!!.calculateNextDose().timeInMillis)

                            val receiver = ComponentName(context, ActionReceiver::class.java)

                            context.packageManager.setComponentEnabledSetting(
                                receiver,
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                PackageManager.DONT_KILL_APP
                            )
                            Toast.makeText(
                                context,
                                getString(R.string.notifications_enabled),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            //Cancel alarm
                            alarmManager?.cancel(alarmIntent)
                            Toast.makeText(
                                context,
                                getString(R.string.notifications_disabled),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                detailLabel.text = medication!!.description

                doseRecordAdapter = DoseRecordListAdapter(context, medication!!.doseRecord)

                if (!doseRecordAdapter.isEmpty) {
                    val sampleView = doseRecordAdapter.getView(0, null, previousDosesList)
                    sampleView.measure(0, 0)
                    val height =
                        doseRecordAdapter.count * sampleView.measuredHeight + previousDosesList.dividerHeight * (doseRecordAdapter.count - 1)
                    previousDosesList.layoutParams =
                        LinearLayoutCompat.LayoutParams(
                            LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                            height
                        )
                }

                previousDosesList.adapter = doseRecordAdapter
                ViewCompat.setNestedScrollingEnabled(outerScroll, true)
                ViewCompat.setNestedScrollingEnabled(previousDosesList, true)

                justTookItButton.setOnClickListener {
                    justTookItButtonPressed()
                }

                timeSinceDoseLabel.text = getString(R.string.time_since_dose_template, days, hours, minutes)

                if (medication!!.closestDoseAlreadyTaken() && !medication!!.isAsNeeded()) {
                    justTookItButton.text = getString(R.string.took_this_already)
                } else {
                    justTookItButton.text = getString(R.string.i_just_took_it)
                }
            }
            medication!!.doseRecord.sort()
        }
        else {
            mainScope.launch {
                onBackPressed()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.med_detail_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.delete -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.are_you_sure))
                    .setMessage(getString(R.string.medication_delete_warning))
                    .setNegativeButton(getString(R.string.cancel)) { dialog, which ->
                        dialog.dismiss()
                    }
                    .setPositiveButton(getString(R.string.confirm)) { dialog, which ->

                        GlobalScope.launch(dispatcher) {
                            val db = MedicationDB.getInstance(context)
                            db.medicationDao().delete(medication!!)
                            alarmManager?.cancel(alarmIntent)
                            finish()
                        }
                    }
                    .show()
                true
            }
            R.id.edit -> {
                val editIntent = Intent(this, EditMedActivity::class.java)
                editIntent.putExtra(
                    getString(R.string.med_id_key),
                    intent.getLongExtra(getString(R.string.med_id_key), -1)
                )
                editResultStarter.launch(editIntent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun justTookItButtonPressed() {
        val calendar = Calendar.getInstance()
        medication!!.updateStartsToFuture()
        if (medication!!.closestDoseAlreadyTaken() && !medication!!.isAsNeeded()) {
            Toast.makeText(this, getString(R.string.already_took_dose), Toast.LENGTH_SHORT).show()
        } else {
            val newDose = if (medication!!.isAsNeeded()) {
                DoseRecord(calendar.timeInMillis)
            } else {
                justTookItButton.text = getString(R.string.took_this_already)
                DoseRecord(
                    calendar.timeInMillis,
                    medication!!.calculateClosestDose().timeInMillis
                )
            }

            doseRecordAdapter.notifyDataSetChanged()
            medication!!.addNewTakenDose(newDose)
            GlobalScope.launch(dispatcher) {
                val db = MedicationDB.getInstance(context)
                db.medicationDao().updateMedications(medication!!)
                with(NotificationManagerCompat.from(context.applicationContext)) {
                    cancel(medication!!.id.toInt())
                }
            }
        }
        if (!doseRecordAdapter.isEmpty) {
            val sampleView = doseRecordAdapter.getView(0, null, previousDosesList)
            sampleView.measure(0, 0)
            val height =
                doseRecordAdapter.count * sampleView.measuredHeight + previousDosesList.dividerHeight * (doseRecordAdapter.count - 1)
            previousDosesList.layoutParams =
                LinearLayoutCompat.LayoutParams(
                    LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                    height
                )
        }
    }

    override fun onPause() {
        runBlocking {
            try {
                refreshJob!!.cancelAndJoin()
            }
            catch (e: Exception) {
                e.printStackTrace()
            }
        }
        super.onPause()
    }

    private fun startRefresherLoop(medId: Long): Job {
        return lifecycleScope.launch(lifecycleDispatcher) {
            while (MedicationDB.getInstance(context).medicationDao().medicationExists(medId)) {

                val medication = MedicationDB.getInstance(context).medicationDao().get(medId)

                val transitionDelay = medication.closestDoseTransitionTime() - System.currentTimeMillis()

                val delayDuration =
                    if (transitionDelay < FALLBACK_DELAY) {
                        transitionDelay
                    }
                    else {
                        FALLBACK_DELAY
                    }

                delay(delayDuration)
                refreshFromDatabase()
            }
        }
    }
}