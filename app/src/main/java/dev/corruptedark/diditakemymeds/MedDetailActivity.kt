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
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.format.DateFormat
import android.view.*
import android.widget.AdapterView
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
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
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.jvm.Throws
import kotlinx.coroutines.launch

class MedDetailActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var outerScroll: NestedScrollView
    private lateinit var nameLabel: MaterialTextView
    private lateinit var rxNumberLabel: MaterialTextView
    private lateinit var typelabel: MaterialTextView
    private lateinit var doseAmountLabel: MaterialTextView
    private lateinit var remainingDosesLabel: MaterialTextView
    private lateinit var timeLabel: MaterialTextView
    private lateinit var activeSwitch: SwitchMaterial
    private lateinit var notificationSwitch: SwitchMaterial
    private lateinit var pharmacyLabel: MaterialTextView
    private lateinit var detailLabel: MaterialTextView
    private lateinit var closestDoseLabel: MaterialTextView
    private lateinit var justTookItButton: MaterialButton
    private lateinit var timeSinceDoseLabel: MaterialTextView
    private lateinit var previousDosesList: ListView
    private var medication: Medication? = null
    private lateinit var doseRecordAdapter: DoseRecordListAdapter
    private val calendar = Calendar.getInstance()
    private var closestDose: Long = -1L
    private val lifecycleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val editResultStarter =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            lifecycleScope.launch(lifecycleDispatcher) {
                medication = medicationDao(context)
                    .get(intent.getLongExtra(getString(R.string.med_id_key), -1L))

                val yesterdayString = context.getString(R.string.yesterday)
                val todayString = context.getString(R.string.today)
                val tomorrowString = context.getString(R.string.tomorrow)
                val dateFormat = context.getString(R.string.date_format)

                val systemIs24Hour = DateFormat.is24HourFormat(context)

                val timeFormat = if (systemIs24Hour) {
                    context.getString(R.string.time_24)
                }
                else {
                    context.getString(R.string.time_12)
                }

                val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Resources.getSystem().configuration.locales[0]
                } else {
                    Resources.getSystem().configuration.locale
                }

                val typeName = medicationTypeDao(context).get(medication!!.typeId).name

                mainScope.launch {
                    nameLabel.text = medication!!.name
                    rxNumberLabel.text = getString(R.string.rx_number_label_format, medication!!.rxNumber)
                    typelabel.text = getString(R.string.type_label_format, typeName)

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
                                Medication.doseString(
                                    yesterdayString,
                                    todayString,
                                    tomorrowString,
                                    nextDose,
                                    dateFormat,
                                    timeFormat,
                                    locale
                                )
                            )
                        closestDoseLabel.visibility = View.VISIBLE
                        closestDose = medication!!.calculateClosestDose().timeInMillis
                        closestDoseLabel.text = getString(
                            R.string.closest_dose_label,
                            Medication.doseString(
                                yesterdayString,
                                todayString,
                                tomorrowString,
                                closestDose,
                                dateFormat,
                                timeFormat,
                                locale
                            )
                        )
                        notificationSwitch.visibility = View.VISIBLE
                        notificationSwitch.isChecked = medication!!.notify
                    }

                    pharmacyLabel.text = medication!!.pharmacy

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

    private val photoResultStarter = registerForActivityResult(ActivityResultContracts.TakePicture()) { pictureTaken ->
        if (pictureTaken) {
            val dose = createDose()
            val proofImage = ProofImage(medication!!.id, dose.doseTime, currentPhotoPath!!)
            saveDose(dose)
            lifecycleScope.launch (lifecycleDispatcher) {
                proofImageDao(context).insertAll(proofImage)
                mainScope.launch {
                    Toast.makeText(context, getString(R.string.dose_and_proof_saved), Toast.LENGTH_SHORT).show()
                }
            }
        }
        else {
            mainScope.launch {
                Toast.makeText(context, getString(R.string.failed_to_get_proof), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var alarmManager: AlarmManager? = null
    private lateinit var alarmIntent: PendingIntent
    private val context = this
    private val mainScope = MainScope()
    private var refreshJob: Job? = null
    private var currentPhotoPath: String? = null

    private val MAXIMUM_DELAY = 60000L // 1 minute in milliseconds
    private val MINIMUM_DELAY = 1000L // 1 second in milliseconds
    private val DAY_TO_HOURS = 24
    private val HOUR_TO_MINUTES = 60

    private val IMAGE_NAME_SEPARATOR = "_"
    private val IMAGE_EXTENSION = ".jpg"
    private var imageFolder: File? = null
    private var takeMed = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_med_detail)
        imageFolder = File(filesDir.path + File.separator + getString(R.string.image_path))
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        toolbar = findViewById(R.id.toolbar)
        nameLabel = findViewById(R.id.name_label)
        rxNumberLabel = findViewById(R.id.rx_number_label)
        typelabel = findViewById(R.id.type_label)
        doseAmountLabel = findViewById(R.id.dose_amount_label)
        remainingDosesLabel = findViewById(R.id.remaining_doses_label)
        timeLabel = findViewById(R.id.time_label)
        activeSwitch = findViewById(R.id.active_switch)
        notificationSwitch = findViewById(R.id.notification_switch)
        pharmacyLabel = findViewById(R.id.pharmacy_label)
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

        val yesterdayString = context.getString(R.string.yesterday)
        val todayString = context.getString(R.string.today)
        val tomorrowString = context.getString(R.string.tomorrow)
        val dateFormat = context.getString(R.string.date_format)

        val systemIs24Hour = DateFormat.is24HourFormat(context)

        val timeFormat = if (systemIs24Hour) {
            context.getString(R.string.time_24)
        }
        else {
            context.getString(R.string.time_12)
        }

        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources.getSystem().configuration.locales[0]
        } else {
            Resources.getSystem().configuration.locale
        }

        previousDosesList.onItemLongClickListener = AdapterView.OnItemLongClickListener { adapterView, view, i, l ->
            val dialogBuilder = MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.are_you_sure))
                .setMessage(
                    getString(R.string.dose_record_delete_warning) + "\n\n" + Medication.doseString(
                        yesterdayString,
                        todayString,
                        tomorrowString,
                        medication!!.doseRecord[i].doseTime,
                        dateFormat,
                        timeFormat,
                        locale
                    )
                )
                .setNegativeButton(getString(R.string.cancel)) { dialog, which ->
                    dialog.dismiss()
                }
                .setPositiveButton(getString(R.string.confirm)) { dialog, which ->
                    lifecycleScope.launch(lifecycleDispatcher) {
                        val medId = medication!!.id
                        val doseTime = medication!!.doseRecord[i].doseTime

                        if (proofImageDao(context).proofImageExists(medId, doseTime)) {
                            val proofImage = proofImageDao(context).get(medId, doseTime)
                            imageFolder?.apply {
                                proofImage.deleteImageFile(imageFolder!!)
                            }
                            proofImageDao(context).delete(proofImage)
                        }

                        medication!!.doseRecord.removeAt(i)
                        medicationDao(context).updateMedications(medication!!)
                    }
                }

            dialogBuilder.show()
            true
        }

        previousDosesList.setOnItemClickListener { parent, view, position, id ->
            val intent = Intent(context, DoseDetailActivity::class.java)
            intent.putExtra(getString(R.string.med_id_key), medication!!.id)
            intent.putExtra(getString(R.string.dose_time_key), medication!!.doseRecord[position].doseTime)
            intent.putExtra(getString(R.string.time_taken_key), medication!!.doseRecord[position].closestDose)
            startActivity(intent)
        }

        outerScroll = findViewById(R.id.outer_scroll)
        takeMed = intent.getBooleanExtra(getString(R.string.take_med_key), false)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(lifecycleDispatcher) {
            refreshFromDatabase()
            if(medication != null) {
                medicationDao(context).updateMedications(medication!!)

                if (takeMed) {
                    takeMed = false
                    justTookItButtonPressed()
                }
            }
        }
        medicationDao(context).getAll().observe(context, {
            lifecycleScope.launch(lifecycleDispatcher) {
                refreshFromDatabase()
            }
        })

        refreshJob = startRefresherLoop(intent.getLongExtra(getString(R.string.med_id_key), -1))
    }

    @Synchronized
    private fun refreshFromDatabase() {
        val medId = intent.getLongExtra(getString(R.string.med_id_key), -1L)

        val yesterdayString = context.getString(R.string.yesterday)
        val todayString = context.getString(R.string.today)
        val tomorrowString = context.getString(R.string.tomorrow)
        val dateFormat = context.getString(R.string.date_format)

        val systemIs24Hour = DateFormat.is24HourFormat(context)

        val timeFormat = if (systemIs24Hour) {
            context.getString(R.string.time_24)
        }
        else {
            context.getString(R.string.time_12)
        }

        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources.getSystem().configuration.locales[0]
        } else {
            Resources.getSystem().configuration.locale
        }

        if (medicationDao(this).medicationExists(medId)) {
            medication = medicationDao(context).get(medId)
            medication!!.updateStartsToFuture()

            alarmIntent = AlarmIntentManager.buildNotificationAlarm(context, medication!!)

            calendar.set(Calendar.HOUR_OF_DAY, medication!!.hour)
            calendar.set(Calendar.MINUTE, medication!!.minute)

            val timeSinceTakenDose = medication!!.timeSinceLastTakenDose()
            val days = TimeUnit.MILLISECONDS.toDays(timeSinceTakenDose)
            val hours = TimeUnit.MILLISECONDS.toHours(timeSinceTakenDose) % DAY_TO_HOURS
            val minutes = TimeUnit.MILLISECONDS.toMinutes(timeSinceTakenDose) % HOUR_TO_MINUTES

            val typeName = medicationTypeDao(context).get(medication!!.typeId).name

            var doseAmountLabelVisibility: Int = View.GONE
            val doseAmountLabelString = if (medication!!.doseUnitId != Medication.DEFAULT_ID && medication!!.amountPerDose != Medication.UNDEFINED_AMOUNT) {
                val doseValueString = medication!!.amountPerDose.toBigDecimal().stripTrailingZeros().toPlainString()
                val doseUnitString = doseUnitDao(context).get(medication!!.doseUnitId).unit
                doseAmountLabelVisibility = View.VISIBLE
                getString(R.string.dose_amount_label_format, doseValueString, doseUnitString)
            }
            else {
                Medication.UNDEFINED
            }

            var remainingDosesLabelVisibility: Int = View.GONE
            val remainingDosesLabelString = if (medication!!.remainingDoses != Medication.UNDEFINED_REMAINING) {
                remainingDosesLabelVisibility = View.VISIBLE
                getString(R.string.remaining_doses_label_format, medication!!.remainingDoses)
            }
            else {
                Medication.UNDEFINED
            }

            mainScope.launch {
                nameLabel.text = medication!!.name
                rxNumberLabel.text = getString(R.string.rx_number_label_format, medication!!.rxNumber)
                typelabel.text = getString(R.string.type_label_format, typeName)
                doseAmountLabel.text = doseAmountLabelString
                doseAmountLabel.visibility = doseAmountLabelVisibility
                remainingDosesLabel.text = remainingDosesLabelString
                remainingDosesLabel.visibility = remainingDosesLabelVisibility

                activeSwitch.isChecked = medication!!.active
                activeSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
                    medication!!.active = isChecked
                    lifecycleScope.launch(lifecycleDispatcher) {
                        medicationDao(context)
                            .updateMedications(medication!!)
                    }
                }

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
                            Medication.doseString(
                                yesterdayString,
                                todayString,
                                tomorrowString,
                                nextDose,
                                dateFormat,
                                timeFormat,
                                locale
                            )
                        )
                    closestDoseLabel.visibility = View.VISIBLE
                    closestDose = medication!!.calculateClosestDose().timeInMillis
                    closestDoseLabel.text = getString(
                        R.string.closest_dose_label,
                        Medication.doseString(
                            yesterdayString,
                            todayString,
                            tomorrowString,
                            closestDose,
                            dateFormat,
                            timeFormat,
                            locale
                        )
                    )
                    notificationSwitch.visibility = View.VISIBLE
                    notificationSwitch.isChecked = medication!!.notify
                    notificationSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
                        medication!!.notify = isChecked
                        lifecycleScope.launch(lifecycleDispatcher) {
                            medicationDao(context)
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
                if (medication!!.description.isEmpty()) {
                    detailLabel.visibility = View.GONE
                }
                else {
                    detailLabel.visibility = View.VISIBLE
                }

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

                        lifecycleScope.launch(lifecycleDispatcher) {
                            val db = MedicationDB.getInstance(context)
                            alarmManager?.cancel(alarmIntent)
                            withContext(Dispatchers.IO) {
                                val proofImages =
                                    db.proofImageDao().getProofImagesByMedId(medication!!.id)
                                proofImages.forEach { proofImage ->

                                    imageFolder?.apply {
                                        proofImage.deleteImageFile(imageFolder!!)
                                    }
                                    db.proofImageDao().delete(proofImage)

                                }

                                db.medicationDao().delete(medication!!)
                            }
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

    @Throws(IOException::class)
    private fun createImageFile(medId: Long, doseTime: Long): File {
        val medIdString = medId.toString()
        val doseTimeString = doseTime.toString()
        val storageDir = imageFolder
        if (storageDir != null && !storageDir.exists()) {
            try {
                storageDir.mkdir()
            }
            catch (exception: SecurityException) {
                exception.printStackTrace()
            }
        }
        return File.createTempFile(
            medIdString + IMAGE_NAME_SEPARATOR + doseTimeString,
            IMAGE_EXTENSION,
            storageDir
        ).apply {
            currentPhotoPath = name
        }
    }

    private fun startTakePictureIntent(medId: Long, doseTime: Long) {
        lifecycleScope.launch(lifecycleDispatcher) {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                takePictureIntent.resolveActivity(packageManager)?.also {

                    val photoFile: File? = withContext(Dispatchers.IO) {
                        runCatching {
                            createImageFile(medId, doseTime)
                        }
                    }.getOrNull()

                    photoFile?.also { file ->
                        val photoURI: Uri = FileProvider.getUriForFile(
                            context,
                            getString(R.string.file_provider),
                            file
                        )
                        photoResultStarter.launch(photoURI)
                    }
                }
            }
        }
    }

    private fun createDose(): DoseRecord {
        val calendar = Calendar.getInstance()
        return if (medication!!.isAsNeeded()) {
            DoseRecord(calendar.timeInMillis)
        } else {
            DoseRecord(
                calendar.timeInMillis,
                medication!!.calculateClosestDose().timeInMillis
            )
        }
    }

    private fun saveDose(newDose: DoseRecord) {
        medication!!.addNewTakenDose(newDose)

        if (!medication!!.isAsNeeded()) {
            justTookItButton.text = getString(R.string.took_this_already)
        }

        doseRecordAdapter.notifyDataSetChanged()
        lifecycleScope.launch(lifecycleDispatcher) {
            val db = MedicationDB.getInstance(context)
            db.medicationDao().updateMedications(medication!!)
            with(NotificationManagerCompat.from(context.applicationContext)) {
                cancel(medication!!.id.toInt())
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

    private fun justTookItButtonPressed() {
        if (medication!!.active) {
            medication!!.updateStartsToFuture()
            if (medication!!.closestDoseAlreadyTaken() && !medication!!.isAsNeeded()) {
                Toast.makeText(this, getString(R.string.already_took_dose), Toast.LENGTH_SHORT)
                    .show()
            }
            else if (!medication!!.hasDoseRemaining()) {
                Toast.makeText(this, getString(R.string.no_remaining_doses_message), Toast.LENGTH_LONG).show()
            }
            else {
                if (medication!!.requirePhotoProof) {
                    startTakePictureIntent(medication!!.id, System.currentTimeMillis())
                } else {
                    saveDose(createDose())
                }
            }
        }
    }

    override fun onPause() {
        runBlocking {
            stopRefresherLoop(refreshJob)
        }
        super.onPause()
    }

    private fun startRefresherLoop(medId: Long): Job {
        return lifecycleScope.launch(lifecycleDispatcher) {
            while (medicationDao(context).medicationExists(medId)) {

                val medication = medicationDao(context).get(medId)

                val transitionDelay = medication.closestDoseTransitionTime() - System.currentTimeMillis()

                val delayDuration =
                    when {
                        transitionDelay < MINIMUM_DELAY -> {
                            MINIMUM_DELAY
                        }
                        transitionDelay in MINIMUM_DELAY until MAXIMUM_DELAY -> {
                            transitionDelay
                        }
                        else -> {
                            MAXIMUM_DELAY
                        }
                    }

                delay(delayDuration)
                refreshFromDatabase()
            }
        }

    }

    private suspend fun stopRefresherLoop(refresher: Job?) {
        runCatching {
            refresher?.cancelAndJoin()
        }.onFailure { throwable ->
            throwable.printStackTrace()
        }
    }
}