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

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView

class AboutActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var githubButton: MaterialButton
    private lateinit var supportButton: MaterialButton
    private lateinit var appDescriptionView: MaterialTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        toolbar = findViewById(R.id.toolbar)
        githubButton = findViewById(R.id.view_github_button)
        supportButton = findViewById(R.id.support_button)
        appDescriptionView = findViewById(R.id.app_description_view)
        setSupportActionBar(toolbar)
        toolbar.background = ColorDrawable(ResourcesCompat.getColor(resources, R.color.purple_700, null))
        toolbar.logo = AppCompatResources.getDrawable(this, R.drawable.bar_logo)
        githubButton.setOnClickListener {
            val webpage = Uri.parse(getString(R.string.github_link))
            val intent = Intent(Intent.ACTION_VIEW, webpage)
            startActivity(intent)
        }
        supportButton.setOnClickListener {
            if (BuildConfig.BUILD_TYPE == getString(R.string.play_release)) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.sorry))
                    .setMessage(getString(R.string.cannot_donate_explanation))
                    .setNeutralButton(getString(R.string.okay)) { dialog, which ->
                        dialog.dismiss()
                    }
                    .show()
            }
            else {
                val webpage = Uri.parse(getString(R.string.liberapay_link))
                val intent = Intent(Intent.ACTION_VIEW, webpage)
                startActivity(intent)
            }
        }
        appDescriptionView.text = getString(R.string.app_description, BuildConfig.VERSION_NAME)
    }


}