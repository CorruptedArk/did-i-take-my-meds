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
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.net.Uri
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.File
//import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream


class MainActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var medListView: ListView
    private lateinit var listEmptyLabel: AppCompatTextView
    private lateinit var addMedButton: FloatingActionButton
    private var medicationListAdapter: MedListAdapter? = null
    private lateinit var sortType: String
    private val TIME_SORT = "time"
    private val NAME_SORT = "name"
    private val FOOTER_PADDING_DP = 100.0F
    private val MAXIMUM_DELAY = 60000L // 1 minute in milliseconds
    private val MINIMUM_DELAY = 1000L // 1 second in milliseconds
    private val MIME_TYPES = "application/*"
    @Volatile private var medications: MutableList<Medication>? = null
    private val lifecycleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val context = this
    private val mainScope = MainScope()
    private var refreshJob: Job? = null
    private var imageDir: File? = null
    private var tempDir: File? = null
    @Volatile private var restoreJob: Job? = null
    @Volatile private var backupJob: Job? = null

    private val activityResultStarter =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.resultCode
        }

    private val restoreResultStarter =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val restoreUri: Uri? = result.data?.data

            val tempFolder = tempDir
            val imageFolder = imageDir

            if (result.resultCode == Activity.RESULT_OK) {
                restoreJob = lifecycleScope.launch(lifecycleDispatcher) {
                    if (MedicationDB.databaseFileIsValid(applicationContext, restoreUri)) {
                        stopRefresherLoop(refreshJob)
                        MedicationDB.getInstance(applicationContext).close()
                        MedicationDB.wipeInstance()

                        withContext(Dispatchers.IO) {
                            runCatching {
                                contentResolver.openInputStream(restoreUri!!)!!.use { inputStream ->
                                    applicationContext.getDatabasePath(MedicationDB.DATABASE_NAME).outputStream().use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                            }.onSuccess {
                                if (refreshJob == null || !refreshJob!!.isActive) {
                                    refreshJob = startRefresherLoop()
                                }
                                mainScope.launch {
                                    Toast.makeText(
                                        applicationContext,
                                        getString(R.string.database_restored),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }.onFailure { exception ->
                                exception.printStackTrace()
                                if (refreshJob == null || !refreshJob!!.isActive) {
                                    refreshJob = startRefresherLoop()
                                }
                                mainScope.launch {
                                    Toast.makeText(
                                        applicationContext,
                                        getString(R.string.database_is_invalid),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }

                        mainScope.launch {
                            medicationDao(applicationContext).getAll()
                                .observe(context, { medicationList ->
                                    lifecycleScope.launch(lifecycleDispatcher) {
                                        refreshFromDatabase(medicationList)
                                    }
                                })
                        }
                    }
                    else {
                        if (restoreUri != null && tempFolder != null && imageFolder != null) {

                            withContext(Dispatchers.IO) {
                                tempFolder.deleteRecursively()
                                runCatching {
                                    contentResolver.openInputStream(restoreUri)
                                        ?.use { inputStream ->
                                            ZipFileManager.streamZipToFolder(
                                                inputStream,
                                                tempFolder
                                            )
                                        }

                                    val tempFiles = tempFolder.listFiles()

                                    val databaseFile = tempFiles?.find { file -> file.name == MedicationDB.DATABASE_NAME }

                                    databaseFile?.inputStream()?.use { databaseStream ->
                                        if (MedicationDB.databaseFileIsValid(context, databaseFile.toUri())) {
                                            stopRefresherLoop(refreshJob)
                                            MedicationDB.getInstance(applicationContext).close()
                                            MedicationDB.wipeInstance()
                                            context.getDatabasePath(MedicationDB.DATABASE_NAME).outputStream().use { outStream ->
                                                databaseStream.copyTo(outStream)
                                            }
                                        }
                                    }

                                    imageFolder.listFiles()?.forEach { file ->
                                        if (file.exists()) {
                                            file.deleteRecursively()
                                        }
                                    }

                                    val tempImageFolder = tempFiles?.find { file -> file.isDirectory && file.name == imageFolder.name }

                                    tempImageFolder?.copyRecursively(imageFolder)

                                    if (tempFolder.exists()) {
                                        tempFolder.deleteRecursively()
                                    }
                                }.onFailure { exception ->
                                    exception.printStackTrace()
                                    if (refreshJob == null || !refreshJob!!.isActive) {
                                        refreshJob = startRefresherLoop()
                                    }
                                    mainScope.launch {
                                        Toast.makeText(
                                            applicationContext,
                                            getString(R.string.database_is_invalid),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }.onSuccess {
                                    if (refreshJob == null || !refreshJob!!.isActive) {
                                        refreshJob = startRefresherLoop()
                                    }
                                    mainScope.launch {
                                        medicationDao(applicationContext).getAll()
                                            .observe(context, { medicationList ->
                                                lifecycleScope.launch(lifecycleDispatcher) {
                                                    refreshFromDatabase(medicationList)
                                                }
                                            })

                                        Toast.makeText(
                                            applicationContext,
                                            getString(R.string.database_restored),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    private val backUpResultStarter =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val backupUri: Uri? = result.data?.data

            val tempFolder = tempDir
            val imageFolder = imageDir

            if (result.resultCode == Activity.RESULT_OK && backupUri != null && backupUri.path != null && tempFolder != null && imageFolder != null) {
                backupJob = lifecycleScope.launch(lifecycleDispatcher) {

                    stopRefresherLoop(refreshJob)
                    runCatching {
                        MedicationDB.getInstance(context).close()
                        MedicationDB.wipeInstance()

                        if (!tempFolder.exists()) {
                            tempFolder.mkdir()
                        }

                        tempFolder.listFiles()?.iterator()?.forEach { entry ->
                            entry.deleteRecursively()
                        }

                        val databaseFile = getDatabasePath(MedicationDB.DATABASE_NAME)
                        databaseFile.copyTo(File(tempFolder.path + File.separator + databaseFile.name))

                        imageFolder.copyRecursively(File(tempFolder.path + File.separator + imageFolder.name), true)

                        contentResolver.openOutputStream(backupUri)?.use { outputStream ->
                            ZipFileManager.streamFolderToZip(tempFolder, outputStream)
                        }

                        tempFolder.deleteRecursively()
                    }.onFailure { exception ->
                        exception.printStackTrace()
                        if (refreshJob == null || !refreshJob!!.isActive) {
                            refreshJob = startRefresherLoop()
                        }
                        mainScope.launch {
                            Toast.makeText(context, getString(R.string.back_up_failed), Toast.LENGTH_SHORT).show()
                        }
                    }.onSuccess {
                        if (refreshJob == null || !refreshJob!!.isActive) {
                            refreshJob = startRefresherLoop()
                        }
                        mainScope.launch {
                            Toast.makeText(context, getString(R.string.back_up_successful), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }



    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(name, name, importance).apply {
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
        tempDir = File(filesDir.path + File.separator + getString(R.string.temp_dir))
        imageDir = File(filesDir.path + File.separator + getString(R.string.image_path))
        medListView = findViewById(R.id.med_list_view)
        listEmptyLabel = findViewById(R.id.list_empty_label)
        addMedButton = findViewById(R.id.add_med_button)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.background =
            ColorDrawable(ResourcesCompat.getColor(resources, R.color.purple_700, null))
        toolbar.logo = AppCompatResources.getDrawable(this, R.drawable.bar_logo)
        supportActionBar?.title = getString(R.string.app_name)

        addMedButton.setOnClickListener {
            openAddMedActivity()
        }

        val footerPadding = Space(this)
        footerPadding.minimumHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            FOOTER_PADDING_DP,
            resources.displayMetrics
        ).toInt()
        medListView.addFooterView(footerPadding)
        medListView.setFooterDividersEnabled(false)
        medicationDao(applicationContext).getAll()
            .observe(context, { medicationList ->
                lifecycleScope.launch(lifecycleDispatcher) {
                    refreshFromDatabase(medicationList)
                }
            })
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        lifecycleScope.launch(lifecycleDispatcher) {

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val sharedPref = getPreferences(Context.MODE_PRIVATE)
            sortType = sharedPref.getString(getString(R.string.sort_key), TIME_SORT)!!
            mainScope.launch {
                medListView.onItemClickListener =
                    AdapterView.OnItemClickListener { adapterView, view, i, l ->
                        openMedDetailActivity(medications!![i].id, false)
                    }
            }

            if (BuildConfig.VERSION_CODE > sharedPref.getInt(
                    getString(R.string.last_version_used_key),
                    0
                )
            ) {
                var alarmIntent: PendingIntent
                medicationDao(context).getAllRaw()
                    .forEach { medication ->
                        if (medication.notify) {
                            lifecycleScope.launch(lifecycleDispatcher) {
                                //Create alarm
                                alarmIntent =
                                    AlarmIntentManager.buildNotificationAlarm(context, medication)

                                alarmManager.cancel(alarmIntent)

                                AlarmIntentManager.setExact(
                                    alarmManager,
                                    alarmIntent,
                                    medication.calculateNextDose().timeInMillis
                                )
                            }
                        }
                    }
            }
            with(sharedPref.edit()) {
                putInt(getString(R.string.last_version_used_key), BuildConfig.VERSION_CODE)
                apply()
            }

            val medId = intent.getLongExtra(getString(R.string.med_id_key), Medication.INVALID_MED_ID)
            val takeMed = intent.getBooleanExtra(getString(R.string.take_med_key), false)
            if (medicationDao(context).medicationExists(medId)) {
                openMedDetailActivity(medId, takeMed)
            }
        }
    }

    private fun openMedDetailActivity(medId: Long, takeMed: Boolean) {
        val intent = Intent(this, MedDetailActivity::class.java)
        intent.putExtra(getString(R.string.med_id_key), medId)
        intent.putExtra(getString(R.string.take_med_key), takeMed)
        activityResultStarter.launch(intent)
    }

    override fun onResume() {
        medicationListAdapter?.notifyDataSetChanged()
        if (!medications.isNullOrEmpty()) {
            listEmptyLabel.visibility = View.GONE
        }
        lifecycleScope.launch(lifecycleDispatcher) {
            refreshJob = startRefresherLoop()
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
            R.id.sortType -> {
                val sharedPref = getPreferences(Context.MODE_PRIVATE)
                if (sortType == TIME_SORT) {
                    sortType = NAME_SORT
                    item.icon = AppCompatResources.getDrawable(this, R.drawable.ic_sort_by_alpha)
                    with(sharedPref.edit()) {
                        putString(getString(R.string.sort_key), NAME_SORT)
                        apply()
                    }
                    medications!!.sortWith(Medication::compareByName)
                    medicationListAdapter!!.notifyDataSetChanged()
                } else {
                    sortType = TIME_SORT
                    item.icon = AppCompatResources.getDrawable(this, R.drawable.ic_sort_by_time)
                    with(sharedPref.edit()) {
                        putString(getString(R.string.sort_key), TIME_SORT)
                        apply()
                    }
                    medications!!.sortWith(Medication::compareByTime)
                    medicationListAdapter!!.notifyDataSetChanged()
                }
                true
            }
            R.id.restore_database -> {
                restoreDatabase()
                true
            }
            R.id.back_up_database -> {
                backUpDatabase()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Synchronized
    private fun refreshFromDatabase(localMedications: MutableList<Medication>) {
        medications = localMedications
        if (sortType == NAME_SORT) {
            medications!!.sortWith(Medication::compareByName)
        } else {
            medications!!.sortWith(Medication::compareByTime)
        }
        mainScope.launch {
            medicationListAdapter = MedListAdapter(context, medications!!)
            medListView.adapter = medicationListAdapter
            if (!medications.isNullOrEmpty())
                listEmptyLabel.visibility = View.GONE
            else
                listEmptyLabel.visibility = View.VISIBLE
        }
    }

    private fun openAboutActivity() {
        lifecycleScope.launch (lifecycleDispatcher){
            stopRefresherLoop(refreshJob)
        }

        val intent = Intent(this, AboutActivity::class.java)
        activityResultStarter.launch(intent)
    }

    private fun openAddMedActivity() {
        val intent = Intent(this, AddMedActivity::class.java)
        activityResultStarter.launch(intent)
    }

    private fun restoreDatabase() {
        lifecycleScope.launch (lifecycleDispatcher){
            stopRefresherLoop(refreshJob)
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = Intent.normalizeMimeType(MIME_TYPES)

        restoreResultStarter.launch(intent)
    }

    private fun backUpDatabase() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = Intent.normalizeMimeType(MIME_TYPES)
        intent.putExtra(
            Intent.EXTRA_TITLE,
            MedicationDB.DATABASE_NAME + ZipFileManager.ZIP_FILE_EXTENSION
        )

        backUpResultStarter.launch(intent)
    }

    override fun onPause() {
        runBlocking {
           stopRefresherLoop(refreshJob)
        }
        super.onPause()
    }

    private fun startRefresherLoop(): Job {
        return lifecycleScope.launch(lifecycleDispatcher) {

            runCatching {
                restoreJob?.join()
                backupJob?.join()
            }.onFailure { throwable ->
                throwable.printStackTrace()
            }
            while (medicationDao(context).getAllRaw().isNotEmpty()) {
                val medication = medicationDao(context).getAllRaw()
                    .sortedWith(Medication::compareByClosestDoseTransition).first()

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
                refreshFromDatabase(medicationDao(context).getAllRaw())
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