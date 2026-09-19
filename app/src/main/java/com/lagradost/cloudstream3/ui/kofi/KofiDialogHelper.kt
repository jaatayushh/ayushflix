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
import com.lagradost.cloudstream3.syncproviders.firebase.FirebaseAuthManager
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe

object KofiDialogHelper {
    const val KOFI_URL = "https://ko-fi.com/jaatayushh"
    private const val PREF_POPUP_DISMISSED = "welcome_popup_dismissed_forever"

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
        val dismissed = prefs.getBoolean(PREF_POPUP_DISMISSED, false)
        if (!dismissed) {
            activity.runOnUiThread {
                showWelcomeDialog(activity, force = false)
            }
        }
    }

    fun showWelcomeDialog(activity: Activity, force: Boolean = false) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        if (!force && prefs.getBoolean(PREF_POPUP_DISMISSED, false)) return

        val binding = KofiDonationDialogBinding.inflate(LayoutInflater.from(activity))
        val dialog = AlertDialog.Builder(activity, R.style.AlertDialogCustom)
            .setView(binding.root)
            .create()

        val currentUser = FirebaseAuthManager.currentUser
        if (currentUser != null) {
            val name = currentUser.displayName ?: currentUser.email ?: "Google User"
            binding.kofiGoogleLoginBtn.text = "✅ Signed in as $name"
        }

        binding.kofiGoogleLoginBtn.setOnClickListener {
            if (FirebaseAuthManager.isLoggedIn) {
                showToast("Already signed in! Your continue watching syncs to cloud.")
            } else {
                FirebaseAuthManager.startGoogleSignIn(activity)
                dialog.dismissSafe()
            }
        }

        binding.kofiSupportBtn.setOnClickListener {
            openKofiLink(activity)
        }

        binding.kofiDismissForeverBtn.setOnClickListener {
            prefs.edit { putBoolean(PREF_POPUP_DISMISSED, true) }
            showToast("Welcome to Ayushflix! Enjoy ad-free streaming.")
            dialog.dismissSafe()
        }

        dialog.show()
    }
}
