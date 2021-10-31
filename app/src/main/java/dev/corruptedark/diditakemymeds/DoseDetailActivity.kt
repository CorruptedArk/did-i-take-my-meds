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

import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

class DoseDetailActivity : AppCompatActivity() {

    private val context = this
    private val lifecycleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var proofImage: ProofImage? = null
    private lateinit var toolbar: MaterialToolbar
    private lateinit var timeTakenLabel: MaterialTextView
    private lateinit var closestDoseLabel: MaterialTextView
    private lateinit var proofImageView: AppCompatImageView
    private lateinit var noImageLabel: MaterialTextView
    private val mainScope = MainScope()
    private var imageFolder: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dose_detail)

        toolbar = findViewById(R.id.toolbar)
        timeTakenLabel = findViewById(R.id.dose_taken_time_label)
        closestDoseLabel = findViewById(R.id.closest_dose_time_label)
        proofImageView = findViewById(R.id.proof_image_view)
        noImageLabel = findViewById(R.id.no_image_label)

        imageFolder = File(filesDir.path + File.separator + getString(R.string.image_path))

        setSupportActionBar(toolbar)
        toolbar.background = ColorDrawable(ResourcesCompat.getColor(resources, R.color.purple_700, null))
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        val medId = intent.getLongExtra(getString(R.string.med_id_key), Medication.INVALID_MED_ID)
        val doseTime = intent.getLongExtra(getString(R.string.dose_time_key), DoseRecord.INVALID_TIME)
        val closestDose = intent.getLongExtra(getString(R.string.time_taken_key), DoseRecord.NONE)

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

        val doseTimeString = Medication.doseString(
            yesterdayString,
            todayString,
            tomorrowString,
            doseTime,
            dateFormat,
            timeFormat,
            locale
        )

        timeTakenLabel.text = context.getString(R.string.time_taken, doseTimeString)

        if(closestDose == DoseRecord.NONE) {
            closestDoseLabel.visibility = View.GONE
        }
        else {
            closestDoseLabel.visibility = View.VISIBLE
            closestDoseLabel.text = context.getString(
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
        }

        lifecycleScope.launch (lifecycleDispatcher) {
            proofImage = proofImageDao(context).get(medId, doseTime)
            val imageDir = imageFolder
            if(proofImage != null && imageDir != null){
                val imageFile = File(imageDir.absolutePath + File.separator + proofImage!!.filePath)
                if (imageFile.exists() && imageFile.canRead()) {
                    mainScope.launch {
                        proofImageView.setImageURI(imageFile.toUri())
                        noImageLabel.visibility = View.GONE
                        proofImageView.visibility = View.VISIBLE
                    }
                }
            }
            else {
                mainScope.launch {
                    noImageLabel.visibility = View.VISIBLE
                    proofImageView.visibility = View.GONE
                }
            }
        }
    }
}