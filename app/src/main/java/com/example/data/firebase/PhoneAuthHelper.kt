package com.example.data.firebase

import android.app.Activity
import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

private const val TAG = "StarMaxPhoneAuth"

object PhoneAuthHelper {

    fun sendVerificationOtp(
        activity: Activity,
        auth: FirebaseAuth,
        phoneE164: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
        onAutoVerified: (PhoneAuthCredential) -> Unit,
        onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken) -> Unit,
        onFailed: (String) -> Unit
    ) {
        try {
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    Log.d(TAG, "Phone verification auto-completed")
                    onAutoVerified(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.w(TAG, "Phone verification failed: ${e.message}", e)
                    val friendly = when (e) {
                        is FirebaseAuthInvalidCredentialsException ->
                            "Invalid mobile number or verification request. Check your 10-digit +91 number."
                        is FirebaseTooManyRequestsException ->
                            "Too many OTP requests for this project/device. Please wait a moment and try again."
                        is FirebaseAuthMissingActivityForRecaptchaException ->
                            "Verification requires an active screen for reCAPTCHA."
                        else -> e.localizedMessage ?: "Failed to send OTP. Ensure Phone Auth is enabled in Firebase Console."
                    }
                    onFailed(friendly)
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    Log.d(TAG, "OTP code sent successfully")
                    onCodeSent(verificationId, token)
                }
            }

            val builder = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneE164)
                .setTimeout(30L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)

            if (resendToken != null) {
                builder.setForceResendingToken(resendToken)
            }

            PhoneAuthProvider.verifyPhoneNumber(builder.build())
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start PhoneAuthProvider.verifyPhoneNumber", t)
            onFailed(t.localizedMessage ?: "Unable to initiate phone verification.")
        }
    }

    suspend fun signInWithPhoneCredential(
        auth: FirebaseAuth,
        credential: PhoneAuthCredential
    ): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithCredential(credential).await()
            val user = result.user
                ?: return Result.failure(IllegalStateException("Phone verification succeeded but user was null."))
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "signInWithPhoneCredential failed", e)
            val msg = if (e is FirebaseAuthInvalidCredentialsException) {
                "Incorrect OTP code. Please check the 6-digit SMS code and try again."
            } else {
                e.localizedMessage ?: "Failed to verify OTP."
            }
            Result.failure(IllegalStateException(msg, e))
        }
    }

    fun buildOtpCredential(verificationId: String, smsCode: String): PhoneAuthCredential {
        return PhoneAuthProvider.getCredential(verificationId, smsCode.trim())
    }
}
