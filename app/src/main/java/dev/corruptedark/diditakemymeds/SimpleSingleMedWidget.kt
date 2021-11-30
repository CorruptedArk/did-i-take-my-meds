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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.concurrent.TimeUnit
import androidx.lifecycle.Observer
import kotlinx.coroutines.*

/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in [SimpleSingleMedWidgetConfigureActivity]
 */
class SimpleSingleMedWidget : AppWidgetProvider() {

    companion object {
        private var appWidgetIds: IntArray? = null
        val DAY_TO_HOURS = 24
        val HOUR_TO_MINUTES = 60
        private val MAXIMUM_DELAY = 60000L // 1 minute in milliseconds
        private val MINIMUM_DELAY = 1000L // 1 second in milliseconds
        val mainScope = MainScope()
        var databaseObserver: Observer<MutableList<Medication>>? = null
        var refresher: Job? = null
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        SimpleSingleMedWidget.appWidgetIds = appWidgetIds

        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // When the user deletes the widget, delete the preference associated with it.
        for (appWidgetId in appWidgetIds) {
            deleteMedIdPref(context, appWidgetId)
        }
    }

    private fun startRefresherLoop(context: Context): Job {
        return GlobalScope.launch(Dispatchers.IO) {

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
                appWidgetIds?.apply {
                    onUpdate(context, AppWidgetManager.getInstance(context), this)
                }
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

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
        databaseObserver = Observer<MutableList<Medication>> {
            appWidgetIds?.apply {
                onUpdate(context, AppWidgetManager.getInstance(context), this)
            }
        }

        refresher = startRefresherLoop(context)

        mainScope.launch {
            medicationDao(context).getAll().observeForever(databaseObserver!!)
        }
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
        databaseObserver?.apply {
            medicationDao(context).getAll().removeObserver(this)
        }

        runBlocking {
            stopRefresherLoop(refresher)
        }
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val medId = loadMedIdPref(context, appWidgetId)

    // Construct the RemoteViews object
    val views = RemoteViews(context.packageName, R.layout.simple_single_med_widget)

    GlobalScope.launch(Dispatchers.IO) {
        val medication:Medication? = if (medId != Medication.INVALID_MED_ID) {
            medicationDao(context).get(medId)
        }
        else {
            null
        }

        medication?.apply {
            val name = medication.name
            val timeSinceTakenDose = medication.timeSinceLastTakenDose()
            val days = TimeUnit.MILLISECONDS.toDays(timeSinceTakenDose)
            val hours =
                TimeUnit.MILLISECONDS.toHours(timeSinceTakenDose) % SimpleSingleMedWidget.DAY_TO_HOURS
            val minutes =
                TimeUnit.MILLISECONDS.toMinutes(timeSinceTakenDose) % SimpleSingleMedWidget.HOUR_TO_MINUTES

            val timeSinceText = context.applicationContext.getString(
                R.string.time_since_dose_template,
                days,
                hours,
                minutes
            )

            val tookMedIntent = Intent(context, ActionReceiver::class.java).apply {
                action = ActionReceiver.TOOK_MED_ACTION
                putExtra(context.getString(R.string.med_id_key), medication.id)
            }
            val tookMedPendingIntent = PendingIntent.getBroadcast(context, medication.id.toInt(), tookMedIntent, 0)
            val justTookItString = context.applicationContext.getString(R.string.i_just_took_it)
            val tookAlreadyString = context.applicationContext.getString(R.string.took_this_already)
            val buttonText = if (medication.closestDoseAlreadyTaken()) {
                tookAlreadyString.uppercase()
            }
            else {
                justTookItString.uppercase()
            }

            SimpleSingleMedWidget.mainScope.launch {
                views.setTextViewText(R.id.name_label, name)
                views.setTextViewText(
                    R.id.time_since_dose_label,
                    timeSinceText
                )
                views.setOnClickPendingIntent(R.id.just_took_it_button, tookMedPendingIntent)
                views.setTextViewText(R.id.just_took_it_button, buttonText)

                // Instruct the widget manager to update the widget
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}