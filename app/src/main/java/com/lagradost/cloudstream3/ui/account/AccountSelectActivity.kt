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
import com.lagradost.cloudstream3.utils.DataStoreHelper.getDefaultAccount
import com.lagradost.cloudstream3.utils.DataStoreHelper.selectedKeyIndex
import com.lagradost.cloudstream3.utils.DataStoreHelper.setAccount
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
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

        val hasCompletedSetup = settingsManager.getBoolean("has_completed_profile_setup", false)
        if (!hasCompletedSetup && !isFromMainActivity && !isEditingFromMainActivity) {
            val initialAccount = accounts.firstOrNull() ?: getDefaultAccount(this)
            AccountHelper.showAccountEditDialog(
                context = this,
                account = initialAccount,
                isNewAccount = true,
                accountEditCallback = { updatedAccount ->
                    settingsManager.edit().putBoolean("has_completed_profile_setup", true).apply()
                    accountViewModel.handleAccountUpdate(updatedAccount, this)
                    setAccount(updatedAccount)
                    navigateToMainActivity()
                },
                accountDeleteCallback = {}
            )
        }

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

        // 10-second trending movies & series poster backdrop carousel
        setupTrendingCarousel(binding.trendingPoster)

        askBiometricAuth()
    }

    private var carouselHandler: android.os.Handler? = null
    private var carouselRunnable: Runnable? = null

    private fun setupTrendingCarousel(imageView: android.widget.ImageView) {
        val backdropUrls = listOf(
            "https://image.tmdb.org/t/p/original/9BBTo63ANSmhC4e6r62OJFuK2GL.jpg", // Stranger Things
            "https://image.tmdb.org/t/p/original/mDeUmZwuhq0075f9746b19a.jpg", // Wednesday
            "https://image.tmdb.org/t/p/original/ggFHVNu6YYI5L9pCfOacjizRGt.jpg", // Breaking Bad
            "https://image.tmdb.org/t/p/original/etjA2mXO0cu5h259vgCJsm85.jpg", // Money Heist
            "https://image.tmdb.org/t/p/original/reEMJA1uzscCbk5r6xg3ej6.jpg", // Squid Game
            "https://image.tmdb.org/t/p/original/2wP1m4yW000a6f849b389.jpg", // Dark
            "https://image.tmdb.org/t/p/original/o82697x756578b8849b389.jpg"  // Lost in Space
        )
        
        var currentIndex = 0
        imageView.loadImage(backdropUrls[0])

        carouselHandler = android.os.Handler(android.os.Looper.getMainLooper())
        carouselRunnable = object : Runnable {
            override fun run() {
                currentIndex = (currentIndex + 1) % backdropUrls.size
                imageView.animate()
                    .alpha(0.1f)
                    .setDuration(600)
                    .withEndAction {
                        imageView.loadImage(backdropUrls[currentIndex])
                        imageView.animate()
                            .alpha(0.7f)
                            .setDuration(600)
                            .start()
                    }
                    .start()
                carouselHandler?.postDelayed(this, 10000L) // Switch every 10 seconds
            }
        }
        carouselHandler?.postDelayed(carouselRunnable!!, 10000L)
    }

    override fun onDestroy() {
        super.onDestroy()
        carouselRunnable?.let { carouselHandler?.removeCallbacks(it) }
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
        cloneView.loadImage(account.image)
        
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
            .setDuration(450)
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
                    progressBar.visibility = android.view.View.GONE

                    // 1. Reveal the simulated Home Screen & Floating Bottom Nav Pill while the avatar moves!
                    val homePreview = findViewById<android.view.View>(R.id.home_preview_layer)
                    if (homePreview != null) {
                        homePreview.visibility = android.view.View.VISIBLE
                        homePreview.animate()
                            .alpha(1f)
                            .setDuration(400)
                            .start()
                    }

                    // 2. Compute the exact position of the 4th item (Profile button) in the floating bottom nav pill
                    val density = resources.displayMetrics.density
                    val pillMarginHorizontal = 20f * density
                    val pillMarginBottom = 16f * density
                    val pillHeight = 60f * density
                    val pillWidth = rootLayout.width.toFloat() - (pillMarginHorizontal * 2f)

                    // Profile icon is the 4th item (last one):
                    val profileItemCenterX = pillMarginHorizontal + (pillWidth * 7f / 8f)
                    val profileItemCenterY = rootLayout.height.toFloat() - pillMarginBottom - (pillHeight / 2f)

                    val targetX = profileItemCenterX - (view.width / 2f)
                    val targetY = profileItemCenterY - (view.height / 2f)

                    val targetIconSize = 32f * density
                    val targetScale = (targetIconSize / view.width.toFloat()).coerceIn(0.18f, 0.40f)

                    // Bring avatar view to front above home preview
                    cloneView.bringToFront()

                    // 3. Smooth curved animation: Glide down into the bottom-right profile button of the pill
                    cloneView.animate()
                        .x(targetX)
                        .y(targetY)
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .alpha(1.0f)
                        .setDuration(600)
                        .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                        .withEndAction {
                            // Brief settling pause then transition to MainActivity seamlessly
                            cloneView.postDelayed({
                                accountViewModel.handleAccountSelect(account, this)
                            }, 100)
                        }
                        .start()
                }, 750)
            }
            .start()
    }
}
