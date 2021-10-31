package dev.corruptedark.diditakemymeds

import android.content.Context

fun medicationDao(context: Context): MedicationDao = MedicationDB.getInstance(context).medicationDao()

fun proofImageDao(context: Context): ProofImageDao = MedicationDB.getInstance(context).proofImageDao()

fun medicationTypeDao(context: Context): MedicationTypeDao = MedicationDB.getInstance(context).medicationTypeDao()