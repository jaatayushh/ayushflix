package com.lagradost.cloudstream3.ui.kofi

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.KofiDonationDialogBinding
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe

object KofiDialogHelper {
    const val KOFI_URL = "https://ko-fi.com/jaatayushh"
    private const val PREF_KOFI_DISMISSED = "kofi_dismissed_forever"
    private const val PREF_APP_LAUNCHES = "kofi_app_launches_count"

    fun openKofiLink(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(KOFI_URL)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            showToast("Opening browser: $KOFI_URL")
        }
    }

    fun checkAndShowOnHome(activity: Activity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val dismissed = prefs.getBoolean(PREF_KOFI_DISMISSED, false)
        if (dismissed) return

        val launches = prefs.getInt(PREF_APP_LAUNCHES, 0) + 1
        prefs.edit { putInt(PREF_APP_LAUNCHES, launches) }

        // Trigger every 4th launch of the app
        if (launches % 4 == 0) {
            activity.runOnUiThread {
                showKofiDialog(activity, force = false)
            }
        }
    }

    fun showKofiDialog(activity: Activity, force: Boolean = false) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        if (!force && prefs.getBoolean(PREF_KOFI_DISMISSED, false)) return

        val binding = KofiDonationDialogBinding.inflate(LayoutInflater.from(activity))
        val dialog = AlertDialog.Builder(activity, R.style.AlertDialogCustom)
            .setView(binding.root)
            .create()

        binding.kofiSupportBtn.setOnClickListener {
            openKofiLink(activity)
            dialog.dismissSafe()
        }

        binding.kofiRemindLaterBtn.setOnClickListener {
            dialog.dismissSafe()
        }

        binding.kofiDismissForeverBtn.setOnClickListener {
            prefs.edit { putBoolean(PREF_KOFI_DISMISSED, true) }
            showToast("Thank you for using Ayushflix!")
            dialog.dismissSafe()
        }

        dialog.show()
    }
}
