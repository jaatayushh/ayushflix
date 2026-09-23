package com.lagradost.cloudstream3.ui.account

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.activity.viewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.CommonActivity.loadThemes
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.ActivityAccountSelectBinding
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.account.AccountAdapter.Companion.VIEW_TYPE_EDIT_ACCOUNT
import com.lagradost.cloudstream3.ui.account.AccountAdapter.Companion.VIEW_TYPE_SELECT_ACCOUNT
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.BiometricAuthenticator
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.BiometricCallback
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.biometricPrompt
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.deviceHasPasswordPinLock
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.isAuthEnabled
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.promptInfo
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.startBiometricAuthentication
import com.lagradost.cloudstream3.utils.DataStoreHelper.accounts
import com.lagradost.cloudstream3.utils.DataStoreHelper.selectedKeyIndex
import com.lagradost.cloudstream3.utils.DataStoreHelper.setAccount
import com.lagradost.cloudstream3.utils.UIHelper.enableEdgeToEdgeCompat
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.openActivity
import com.lagradost.cloudstream3.utils.UIHelper.setNavigationBarColorCompat

class AccountSelectActivity : FragmentActivity(), BiometricCallback {

    companion object {
        var hasLoggedIn: Boolean = false
    }

    val accountViewModel: AccountViewModel by viewModels()

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Are we editing and coming from MainActivity?
        val isEditingFromMainActivity = intent.getBooleanExtra(
            "isEditingFromMainActivity",
            false
        )

        val isFromMainActivity = intent.getBooleanExtra(
            "isFromMainActivity",
            false
        )

        // Always show account selection on fresh launch.
        // Only skip if this is an internal navigation (from MainActivity or editing).
        if (hasLoggedIn && (isEditingFromMainActivity || isFromMainActivity)) {
            // Coming from inside the app — only navigate directly if editing
            if (!isEditingFromMainActivity && !isFromMainActivity) {
                navigateToMainActivity()
                return
            }
        }

        loadThemes(this)

        enableEdgeToEdgeCompat()
        setNavigationBarColorCompat(R.attr.primaryBlackBackground)

        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        // Only skip the selector when navigating internally from MainActivity
        val skipStartup = (isFromMainActivity || isEditingFromMainActivity) && (
            settingsManager.getBoolean(
                getString(R.string.skip_startup_account_select_key), false
            ) || accounts.count() <= 1
        )

        fun askBiometricAuth() {

            if (isLayout(PHONE) && isAuthEnabled(this)) {
                if (deviceHasPasswordPinLock(this)) {
                    startBiometricAuthentication(
                        this,
                        R.string.biometric_authentication_title,
                        false
                    )

                    promptInfo?.let { prompt ->
                        biometricPrompt?.authenticate(prompt)
                    }
                }
            }
        }

        observe(accountViewModel.isAllowedLogin) { isAllowedLogin ->
            if (isAllowedLogin) {
                // We are allowed to continue to MainActivity
                navigateToMainActivity()
            }
        }

        // Don't show account selection if there is only
        // one account that exists (only applies for internal navigation)
        if (skipStartup) {
            val currentAccount = accounts.firstOrNull { it.keyIndex == selectedKeyIndex }
            if (currentAccount?.lockPin != null) {
                CommonActivity.init(this)
                accountViewModel.handleAccountSelect(currentAccount, this, true)
            } else {
                if (accounts.count() > 1) {
                    showToast(
                        this, getString(
                            R.string.logged_account,
                            currentAccount?.name
                        )
                    )
                }

                navigateToMainActivity()
            }

            return
        }

        CommonActivity.init(this)

        val binding = ActivityAccountSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fixSystemBarsPadding(binding.root, padTop = false)

        val recyclerView: AutofitRecyclerView = binding.accountRecyclerView

        observe(accountViewModel.accounts) { liveAccounts ->
            val adapter = AccountAdapter(
                // Handle the selected account
                accountSelectCallback = { account, view ->
                    attemptAccountSelect(account, view)
                },
                accountCreateCallback = { accountViewModel.handleAccountUpdate(it, this) },
                accountEditCallback = {
                    accountViewModel.handleAccountUpdate(it, this)
                    // We came from MainActivity, return there
                    // and switch to the edited account
                    if (isEditingFromMainActivity) {
                        setAccount(it)
                        navigateToMainActivity()
                    }
                },
                accountDeleteCallback = { accountViewModel.handleAccountDelete(it, this) }
            ).apply {
                submitList(liveAccounts)
            }

            recyclerView.adapter = adapter

            if (isLayout(TV or EMULATOR)) {
                binding.editAccountButton.setBackgroundResource(
                    R.drawable.player_button_tv_attr_no_bg
                )
            }

            observe(accountViewModel.selectedKeyIndex) { selectedKeyIndex ->
                // Scroll to current account (which is focused by default)
                val layoutManager = recyclerView.layoutManager as GridLayoutManager
                layoutManager.scrollToPositionWithOffset(selectedKeyIndex, 0)
            }

            observe(accountViewModel.isEditing) { isEditing ->
                if (isEditing) {
                    binding.editAccountButton.setImageResource(R.drawable.ic_baseline_close_24)
                    binding.title.setText(R.string.manage_accounts)
                    adapter.viewType = VIEW_TYPE_EDIT_ACCOUNT
                } else {
                    binding.editAccountButton.setImageResource(R.drawable.ic_baseline_edit_24)
                    binding.title.text = "Who's Watching?"
                    adapter.viewType = VIEW_TYPE_SELECT_ACCOUNT
                }

                adapter.notifyDataSetChanged()
            }

            if (isEditingFromMainActivity) {
                accountViewModel.setIsEditing(true)
            }

            binding.editAccountButton.setOnClickListener {
                // We came from MainActivity, return there
                // and resume its state
                if (isEditingFromMainActivity) {
                    navigateToMainActivity()
                    return@setOnClickListener
                }

                accountViewModel.toggleIsEditing()
            }

            if (isLayout(TV or EMULATOR)) {
                recyclerView.spanCount = if (liveAccounts.count() + 1 <= 6) {
                    liveAccounts.count() + 1
                } else 6
            }
        }

        askBiometricAuth()
    }

    @SuppressLint("UnsafeIntentLaunch")
    private fun navigateToMainActivity() {
        hasLoggedIn = true
        // We want to propagate any intent we get here to MainActivity since this is just an intermediary
        openActivity(MainActivity::class.java, baseIntent = intent)
        finish() // Finish the account selection activity
    }

    override fun onAuthenticationSuccess() {
        Log.i(BiometricAuthenticator.TAG, "Authentication successful in AccountSelectActivity")
    }

    override fun onAuthenticationError() {
        finish()
    }

    private fun attemptAccountSelect(account: com.lagradost.cloudstream3.utils.DataStoreHelper.Account, view: android.view.View) {
        if (!account.lockPin.isNullOrEmpty()) {
            val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            builder.setTitle("Profile Lock")
            val input = android.widget.EditText(this)
            input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            builder.setView(input)
            builder.setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == account.lockPin) {
                    animateAndSelectAccount(account, view)
                } else {
                    android.widget.Toast.makeText(this, "Incorrect PIN", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            builder.show()
        } else {
            animateAndSelectAccount(account, view)
        }
    }

    private fun animateAndSelectAccount(account: com.lagradost.cloudstream3.utils.DataStoreHelper.Account, view: android.view.View) {
        val rootLayout = findViewById<android.view.ViewGroup>(android.R.id.content)
        val location = IntArray(2)
        view.getLocationInWindow(location)

        val cloneView = android.widget.ImageView(this)
        val image = account.image
        if (image is com.lagradost.cloudstream3.utils.UiImage.Drawable) {
            cloneView.setImageResource(image.resId)
        } else if (image is com.lagradost.cloudstream3.utils.UiImage.Image) {
            // com.lagradost.cloudstream3.utils.UIHelper.setImage(cloneView, image.url)
        }
        
        val layoutParams = android.widget.FrameLayout.LayoutParams(view.width, view.height)
        cloneView.layoutParams = layoutParams
        cloneView.x = location[0].toFloat()
        cloneView.y = location[1].toFloat()
        
        // Add card corner radius using an outline provider to make it match the original view
        cloneView.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 16f * resources.displayMetrics.density)
            }
        }
        cloneView.clipToOutline = true
        cloneView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP

        rootLayout.addView(cloneView)
        view.visibility = android.view.View.INVISIBLE

        val centerX = rootLayout.width / 2f - view.width / 2f
        val centerY = rootLayout.height / 2f - view.height / 2f

        cloneView.animate()
            .x(centerX)
            .y(centerY)
            .setDuration(500)
            .withEndAction {
                val progressBar = android.widget.ProgressBar(this)
                val pbParams = android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                pbParams.leftMargin = rootLayout.width / 2 - 50
                pbParams.topMargin = (centerY + view.height + 40).toInt()
                progressBar.layoutParams = pbParams
                rootLayout.addView(progressBar)

                cloneView.postDelayed({
                    val targetX = rootLayout.width.toFloat() - view.width
                    val targetY = rootLayout.height.toFloat() - view.height
                    
                    progressBar.visibility = android.view.View.GONE
                    
                    cloneView.animate()
                        .x(targetX)
                        .y(targetY)
                        .scaleX(0.2f)
                        .scaleY(0.2f)
                        .setDuration(500)
                        .withEndAction {
                            accountViewModel.handleAccountSelect(account, this)
                        }
                        .start()
                }, 1500)
            }
            .start()
    }
}
