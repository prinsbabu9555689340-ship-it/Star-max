package com.example.data.firebase

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "StarMaxAuth"

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager?,
    targetAuth: FirebaseAuth? = null,
    onAuthSuccess: (FirebaseUser) -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val auth = targetAuth ?: runCatching { Firebase.auth }.getOrNull() ?: run {
        onAuthError("Firebase Authentication is not configured in this build.")
        return
    }
    if (credentialManager == null) {
        onAuthError("Google Sign-In is unavailable on this device.")
        return
    }
    val clientId = resolveDefaultWebClientId(context) ?: run {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    val request = runCatching {
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
    }.getOrElse { err ->
        onAuthError(err.localizedMessage ?: "Unable to initialize Google Sign-In.")
        return
    }

    scope.launch {
        try {
            val activityContext = context as? Activity ?: run {
                onAuthError("Sign-in requires an active Activity context")
                return@launch
            }
            val cred = credentialManager.getCredential(activityContext, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authResult = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                val signedInUser = authResult.user ?: auth.currentUser
                if (signedInUser != null) {
                    onAuthSuccess(signedInUser)
                } else {
                    onAuthError("Sign in succeeded but no user was returned.")
                }
            } else {
                onAuthError("Unexpected credential type returned")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (t: Throwable) {
            Log.e(TAG, "Google Sign-In failed", t)
            onAuthError(t.localizedMessage ?: "Sign in failed. Please try again.")
        }
    }
}

fun signOut(
    credentialManager: CredentialManager?,
    scope: CoroutineScope,
    onBeforeFirebaseSignOut: () -> Unit = {},
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                onBeforeFirebaseSignOut()
                credentialManager?.clearCredentialState(ClearCredentialStateRequest())
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to clear credential state: ${t.message}")
            } finally {
                runCatching { Firebase.auth.signOut() }
                onSignOutComplete()
            }
        }
    }
}

@SuppressLint("DiscouragedApi")
private fun resolveDefaultWebClientId(context: Context): String? {
    return try {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId == 0) return null
        context.getString(resId).takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }
}

