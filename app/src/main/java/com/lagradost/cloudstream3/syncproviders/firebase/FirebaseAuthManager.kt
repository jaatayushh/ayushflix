package com.lagradost.cloudstream3.syncproviders.firebase

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.UiThread
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.net.Uri

object FirebaseAuthManager {
    const val RC_SIGN_IN = 9001
    // Web Client ID from google-services.json (client_type: 3)
    private const val WEB_CLIENT_ID = "235797133248-n01soopds33bs60fdperdkk1t00fiur9.apps.googleusercontent.com"

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }

    private val _currentUserState = MutableStateFlow<FirebaseUser?>(null)
    val currentUserState: StateFlow<FirebaseUser?> = _currentUserState.asStateFlow()

    val currentUser: FirebaseUser? get() = auth.currentUser
    val isLoggedIn: Boolean get() = auth.currentUser != null
    val uid: String? get() = auth.currentUser?.uid
    val displayName: String? get() = auth.currentUser?.displayName
    val photoUrl: String? get() = auth.currentUser?.photoUrl?.toString()
    val email: String? get() = auth.currentUser?.email

    fun init() {
        _currentUserState.value = auth.currentUser
        auth.addAuthStateListener { firebaseAuth ->
            _currentUserState.value = firebaseAuth.currentUser
            if (firebaseAuth.currentUser != null) {
                // Trigger cloud sync on login
                FirebaseSyncManager.syncFromCloud()
            }
        }
    }

    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun startGoogleSignIn(activity: Activity) {
        val signInClient = getGoogleSignInClient(activity)
        val signInIntent = signInClient.signInIntent
        activity.startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    fun handleSignInResult(
        intent: Intent?,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.getResult(ApiException::class.java)
            if (account != null && account.idToken != null) {
                firebaseAuthWithGoogle(account.idToken!!, onSuccess, onError)
            } else {
                onError("Failed to obtain Google account credentials")
            }
        } catch (e: Exception) {
            logError(e)
            onError(e.message ?: "Google Sign-In failed")
        }
    }

    private fun firebaseAuthWithGoogle(
        idToken: String,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        _currentUserState.value = user
                        saveUserProfileToCloud(user)
                        FirebaseSyncManager.syncFromCloud()
                        onSuccess(user)
                    } else {
                        onError("Firebase user is null after authentication")
                    }
                } else {
                    val ex = task.exception
                    ex?.let { logError(it) }
                    onError(ex?.message ?: "Authentication with Firebase failed")
                }
            }
    }

    fun signOut(context: Context, onComplete: (() -> Unit)? = null) {
        try {
            auth.signOut()
            getGoogleSignInClient(context).signOut().addOnCompleteListener {
                _currentUserState.value = null
                onComplete?.invoke()
            }
        } catch (e: Exception) {
            logError(e)
            _currentUserState.value = null
            onComplete?.invoke()
        }
    }

    fun updateUserProfile(
        newName: String?,
        newPhotoUrl: String?,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(false, "User not signed in")
            return
        }

        val profileUpdates = UserProfileChangeRequest.Builder().apply {
            if (!newName.isNullOrBlank()) setDisplayName(newName.trim())
            if (!newPhotoUrl.isNullOrBlank()) setPhotoUri(Uri.parse(newPhotoUrl.trim()))
        }.build()

        user.updateProfile(profileUpdates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _currentUserState.value = auth.currentUser
                    saveUserProfileToCloud(user)
                    onComplete(true, null)
                } else {
                    val msg = task.exception?.message ?: "Failed to update profile"
                    onComplete(false, msg)
                }
            }
    }

    private fun saveUserProfileToCloud(user: FirebaseUser) {
        try {
            val userRef = database.getReference("users").child(user.uid).child("profile")
            val data = mapOf(
                "displayName" to (user.displayName ?: ""),
                "email" to (user.email ?: ""),
                "photoUrl" to (user.photoUrl?.toString() ?: ""),
                "lastActive" to System.currentTimeMillis()
            )
            userRef.updateChildren(data)
        } catch (e: Exception) {
            logError(e)
        }
    }
}
