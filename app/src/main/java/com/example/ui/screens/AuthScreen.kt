package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.firebase.AccountSessionManager
import com.example.data.firebase.PhoneFormatUtils
import com.example.data.firebase.SocialRepository
import com.example.data.firebase.onGoogleSignInClicked
import com.example.data.model.SavedAccountRecord
import com.example.ui.components.StarMaxWordmark
import com.example.ui.theme.LocalStarMaxColors
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private enum class AuthTab(val label: String) {
    LOG_IN("Log In"),
    SIGN_UP("Sign Up")
}

private data class PendingSignUpDraft(
    val uid: String,
    val fullName: String,
    val username: String,
    val mobile: String,
    val email: String
)

private object PendingSignUpDraftStore {
    private const val PREFS_NAME = "star_max_pending_email_signup"
    private const val KEY_UID = "uid"
    private const val KEY_FULL_NAME = "full_name"
    private const val KEY_USERNAME = "username"
    private const val KEY_MOBILE = "mobile"
    private const val KEY_EMAIL = "email"

    fun save(context: Context, draft: PendingSignUpDraft) {
        runCatching {
            val prefs = (context.applicationContext ?: context)
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_UID, draft.uid)
                .putString(KEY_FULL_NAME, draft.fullName)
                .putString(KEY_USERNAME, draft.username)
                .putString(KEY_MOBILE, draft.mobile)
                .putString(KEY_EMAIL, draft.email)
                .apply()
        }
    }

    fun loadForUid(context: Context, uid: String): PendingSignUpDraft? {
        return runCatching {
            val prefs = (context.applicationContext ?: context)
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedUid = prefs.getString(KEY_UID, null) ?: return null
            if (savedUid != uid) return null
            PendingSignUpDraft(
                uid = savedUid,
                fullName = prefs.getString(KEY_FULL_NAME, "").orEmpty(),
                username = prefs.getString(KEY_USERNAME, "").orEmpty(),
                mobile = prefs.getString(KEY_MOBILE, "").orEmpty(),
                email = prefs.getString(KEY_EMAIL, "").orEmpty()
            )
        }.getOrNull()
    }

    fun clear(context: Context) {
        runCatching {
            val prefs = (context.applicationContext ?: context)
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        }
    }
}

private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

private fun isValidEmail(raw: String): Boolean {
    val trimmed = raw.trim()
    return trimmed.length <= 254 && EMAIL_REGEX.matches(trimmed)
}

private fun mapFirebaseAuthError(error: Throwable?): String {
    val message = error?.localizedMessage.orEmpty()
    val upper = message.uppercase()
    return when {
        error is FirebaseAuthUserCollisionException ->
            "An account with this email already exists. Switch to Log In."
        error is FirebaseAuthWeakPasswordException ->
            "Password is too weak. Use at least 6 characters."
        error is FirebaseAuthInvalidCredentialsException ->
            "Invalid email address or incorrect password."
        error is FirebaseAuthInvalidUserException ->
            "No account found with this email. Please Sign Up."
        error is FirebaseTooManyRequestsException ->
            "Too many attempts. Please wait a moment and try again."
        upper.contains("OPERATION_NOT_ALLOWED") || upper.contains("CONFIGURATION_NOT_FOUND") ->
            "Enable Email/Password sign-in in Firebase Console (Authentication → Sign-in method)."
        upper.contains("NETWORK") ->
            "Network error. Check your internet connection and try again."
        message.isNotBlank() -> message
        else -> "Authentication failed. Please try again."
    }
}

@Composable
fun AuthScreen(
    accountSessionManager: AccountSessionManager,
    isAddAccountMode: Boolean = false,
    onAccountAuthenticated: (SavedAccountRecord) -> Unit,
    onCancelAddAccount: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) {
        runCatching { CredentialManager.create(context) }.getOrNull()
    }
    val extended = LocalStarMaxColors.current

    val slotName = remember(isAddAccountMode) {
        accountSessionManager.allocateSlotForAccount()
    }
    var cachedSlotAuth by remember(slotName) {
        mutableStateOf(runCatching { accountSessionManager.resolveAuth(slotName) }.getOrNull())
    }
    var cachedSlotDb by remember(slotName) {
        mutableStateOf(runCatching { accountSessionManager.resolveFirestore(slotName) }.getOrNull())
    }
    var cachedSlotRepository by remember(cachedSlotDb, cachedSlotAuth) {
        mutableStateOf(
            cachedSlotDb?.let { db ->
                runCatching { SocialRepository(db = db, auth = cachedSlotAuth) }.getOrNull()
            }
        )
    }

    fun getOrInitAuth(): FirebaseAuth? {
        cachedSlotAuth?.let { return it }
        val resolved = runCatching { accountSessionManager.resolveAuth(slotName) }.getOrNull()
        if (resolved != null) cachedSlotAuth = resolved
        return resolved
    }

    fun getOrInitRepository(): SocialRepository? {
        cachedSlotRepository?.let { return it }
        val auth = getOrInitAuth()
        val db = cachedSlotDb ?: runCatching { accountSessionManager.resolveFirestore(slotName) }
            .getOrNull()
            ?.also { cachedSlotDb = it }
        if (db == null) return null
        return runCatching { SocialRepository(db = db, auth = auth) }
            .getOrNull()
            ?.also { cachedSlotRepository = it }
    }

    var selectedTab by remember { mutableStateOf(if (isAddAccountMode) AuthTab.SIGN_UP else AuthTab.LOG_IN) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    // Sign Up state
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var signUpEmail by remember { mutableStateOf("") }
    var phoneDigits by remember { mutableStateOf("") }
    var signUpPassword by remember { mutableStateOf("") }
    var signUpPasswordVisible by remember { mutableStateOf(false) }

    // Log In state
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }

    // Email Verification state
    var verifyEmailPending by remember { mutableStateOf(false) }
    var pendingVerifyEmail by remember { mutableStateOf("") }
    var pendingVerifyIsSignUp by remember { mutableStateOf(false) }
    var resendCooldownSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(slotName) {
        if (cachedSlotAuth == null) {
            cachedSlotAuth = runCatching { accountSessionManager.resolveAuth(slotName) }.getOrNull()
        }
        if (cachedSlotDb == null) {
            cachedSlotDb = runCatching { accountSessionManager.resolveFirestore(slotName) }.getOrNull()
        }
        // Resume verification gate if an unverified Email/Password user is still signed in
        if (!isAddAccountMode) {
            val existingUser = runCatching { getOrInitAuth()?.currentUser }.getOrNull()
            if (existingUser != null &&
                !existingUser.isEmailVerified &&
                existingUser.providerData.any { it.providerId == "password" }
            ) {
                val draft = PendingSignUpDraftStore.loadForUid(context, existingUser.uid)
                if (draft != null) {
                    fullName = draft.fullName
                    username = draft.username
                    signUpEmail = draft.email
                    phoneDigits = draft.mobile
                    pendingVerifyIsSignUp = true
                } else {
                    pendingVerifyIsSignUp = false
                }
                pendingVerifyEmail = existingUser.email ?: draft?.email.orEmpty()
                verifyEmailPending = true
            }
        }
    }

    LaunchedEffect(resendCooldownSeconds) {
        if (resendCooldownSeconds > 0) {
            delay(1000L)
            resendCooldownSeconds -= 1
        }
    }

    if (verifyEmailPending) {
        BackHandler(enabled = !isBusy) {
            runCatching { getOrInitAuth()?.signOut() }
            PendingSignUpDraftStore.clear(context)
            verifyEmailPending = false
            pendingVerifyEmail = ""
            errorMessage = null
            infoMessage = null
        }
    } else if (isAddAccountMode && onCancelAddAccount != null) {
        BackHandler(onBack = onCancelAddAccount)
    }

    suspend fun completeVerifiedEmailLogin(user: FirebaseUser) {
        val repo = getOrInitRepository()
        val draft = PendingSignUpDraftStore.loadForUid(context, user.uid)
        val shouldCompleteRegistration = pendingVerifyIsSignUp || draft != null

        val effectiveFullName = fullName.trim()
            .ifBlank { draft?.fullName.orEmpty() }
            .ifBlank { user.displayName?.trim().orEmpty() }
            .ifBlank { "Star Creator" }
        val effectiveUsername = accountSessionManager.sanitizeUsername(
            username.ifBlank { draft?.username.orEmpty() }
        ).ifBlank {
            val emailPrefix = user.email?.substringBefore("@").orEmpty()
            accountSessionManager.sanitizeUsername(emailPrefix)
                .ifBlank { "star_${user.uid.take(6).lowercase()}" }
        }
        val effectiveMobile = phoneDigits.ifBlank { draft?.mobile.orEmpty() }

        if (shouldCompleteRegistration && repo != null) {
            val regRes = repo.completeEmailRegistration(
                user = user,
                fullName = effectiveFullName,
                username = effectiveUsername,
                mobileNumber = effectiveMobile
            )
            regRes.onSuccess { createdProfile ->
                PendingSignUpDraftStore.clear(context)
                verifyEmailPending = false
                isBusy = false
                onAccountAuthenticated(
                    SavedAccountRecord(
                        uid = user.uid,
                        slotName = slotName,
                        username = createdProfile.username,
                        fullName = createdProfile.fullName,
                        email = user.email ?: pendingVerifyEmail,
                        avatarData = createdProfile.effectiveAvatar,
                        phoneE164 = createdProfile.mobile,
                        verified = createdProfile.effectiveVerified,
                        authProvider = "email"
                    )
                )
            }.onFailure { err ->
                isBusy = false
                errorMessage = err.localizedMessage ?: "Failed to save your verified profile."
            }
        } else {
            runCatching { repo?.ensureUserProfile(user) }
            val fetchedProfile = runCatching { repo?.fetchUserProfile(user.uid) }.getOrNull()
            PendingSignUpDraftStore.clear(context)
            verifyEmailPending = false
            isBusy = false
            val resolvedUsername = fetchedProfile?.username?.takeIf { it.isNotBlank() } ?: effectiveUsername
            val resolvedFullName = fetchedProfile?.fullName?.takeIf { it.isNotBlank() } ?: effectiveFullName
            val resolvedAvatar = fetchedProfile?.effectiveAvatar?.takeIf { it.isNotBlank() }
                ?: user.photoUrl?.toString().orEmpty()
            val resolvedMobile = fetchedProfile?.mobile?.takeIf { it.isNotBlank() }
                ?: user.phoneNumber.orEmpty()
            onAccountAuthenticated(
                SavedAccountRecord(
                    uid = user.uid,
                    slotName = slotName,
                    username = resolvedUsername,
                    fullName = resolvedFullName,
                    email = user.email ?: pendingVerifyEmail.ifBlank { loginEmail.trim() },
                    avatarData = resolvedAvatar,
                    phoneE164 = resolvedMobile,
                    verified = fetchedProfile?.effectiveVerified ?: false,
                    authProvider = "email"
                )
            )
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                        )
                    )
                )
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!verifyEmailPending && isAddAccountMode && onCancelAddAccount != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onCancelAddAccount,
                            modifier = Modifier.testTag("add_account_cancel_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to profile"
                            )
                        }
                        Text(
                            text = "Add another account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(72.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_starmax_mark),
                        contentDescription = "Star Max Emblem",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                StarMaxWordmark()
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (verifyEmailPending) {
                        "One quick step to activate your account"
                    } else {
                        "Verified profiles, Stories, Reels & DMs"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (!verifyEmailPending) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AuthTab.entries.forEachIndexed { index, tab ->
                            SegmentedButton(
                                selected = selectedTab == tab,
                                onClick = {
                                    if (!isBusy && selectedTab != tab) {
                                        selectedTab = tab
                                        errorMessage = null
                                        infoMessage = null
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = AuthTab.entries.size
                                ),
                                modifier = Modifier.testTag("auth_tab_${tab.name.lowercase()}")
                            ) {
                                Text(tab.label)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (errorMessage != null) {
                    StatusBanner(text = errorMessage.orEmpty(), isError = true)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (infoMessage != null) {
                    StatusBanner(text = infoMessage.orEmpty(), isError = false)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (verifyEmailPending) {
                    VerifyEmailCard(
                        email = pendingVerifyEmail,
                        isBusy = isBusy,
                        resendCooldownSeconds = resendCooldownSeconds,
                        onCheckVerified = {
                            errorMessage = null
                            infoMessage = null
                            val auth = getOrInitAuth()
                            val currentUser = auth?.currentUser
                            if (auth == null || currentUser == null) {
                                verifyEmailPending = false
                                errorMessage = "Your sign-in session expired. Please log in with your email and password."
                                return@VerifyEmailCard
                            }
                            isBusy = true
                            scope.launch {
                                try {
                                    currentUser.reload().await()
                                    val refreshedUser = auth.currentUser ?: currentUser
                                    if (refreshedUser.isEmailVerified) {
                                        completeVerifiedEmailLogin(refreshedUser)
                                    } else {
                                        isBusy = false
                                        errorMessage = "Email not verified yet. Open $pendingVerifyEmail, tap the verification link, then tap Continue."
                                    }
                                } catch (e: Exception) {
                                    isBusy = false
                                    errorMessage = mapFirebaseAuthError(e)
                                }
                            }
                        },
                        onResendEmail = {
                            errorMessage = null
                            infoMessage = null
                            val user = getOrInitAuth()?.currentUser
                            if (user == null) {
                                verifyEmailPending = false
                                errorMessage = "Session expired. Please log in again to resend the verification email."
                                return@VerifyEmailCard
                            }
                            isBusy = true
                            scope.launch {
                                try {
                                    user.sendEmailVerification().await()
                                    resendCooldownSeconds = 30
                                    isBusy = false
                                    infoMessage = "Verification link resent to $pendingVerifyEmail."
                                } catch (e: Exception) {
                                    isBusy = false
                                    errorMessage = mapFirebaseAuthError(e)
                                }
                            }
                        },
                        onUseDifferentEmail = {
                            runCatching { getOrInitAuth()?.signOut() }
                            PendingSignUpDraftStore.clear(context)
                            verifyEmailPending = false
                            pendingVerifyEmail = ""
                            resendCooldownSeconds = 0
                            errorMessage = null
                            infoMessage = null
                        }
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (selectedTab == AuthTab.SIGN_UP) {
                                OutlinedTextField(
                                    value = fullName,
                                    onValueChange = { fullName = it },
                                    label = { Text("Full Name") },
                                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                    singleLine = true,
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("signup_fullname_input")
                                )
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = {
                                        username = it.filter { ch ->
                                            ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-'
                                        }
                                    },
                                    label = { Text("Username") },
                                    prefix = { Text("@") },
                                    supportingText = { Text("Unique handle (min 3 chars)") },
                                    singleLine = true,
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("signup_username_input")
                                )
                                OutlinedTextField(
                                    value = signUpEmail,
                                    onValueChange = { signUpEmail = it.trim() },
                                    label = { Text("Email Address") },
                                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    singleLine = true,
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("signup_email_input")
                                )
                                OutlinedTextField(
                                    value = phoneDigits,
                                    onValueChange = { input ->
                                        phoneDigits = input.filter { it.isDigit() }.take(10)
                                    },
                                    label = { Text("Mobile Number (for profile)") },
                                    leadingIcon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
                                    prefix = {
                                        Text(
                                            text = "+91 ",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("signup_phone_input")
                                )
                                OutlinedTextField(
                                    value = signUpPassword,
                                    onValueChange = { signUpPassword = it },
                                    label = { Text("Password (min 6 chars)") },
                                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { signUpPasswordVisible = !signUpPasswordVisible }) {
                                            Icon(
                                                imageVector = if (signUpPasswordVisible) {
                                                    Icons.Filled.VisibilityOff
                                                } else {
                                                    Icons.Filled.Visibility
                                                },
                                                contentDescription = "Toggle password visibility"
                                            )
                                        }
                                    },
                                    visualTransformation = if (signUpPasswordVisible) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    singleLine = true,
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("signup_password_input")
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = null,
                                        tint = extended.verifiedBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "We’ll send a free verification link to your email inbox.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                GradientPrimaryButton(
                                    text = "Sign Up & Verify Email",
                                    isLoading = isBusy,
                                    enabled = !isBusy,
                                    testTag = "signup_submit_button",
                                    onClick = {
                                        errorMessage = null
                                        infoMessage = null
                                        val cleanName = fullName.trim()
                                        val cleanUser = accountSessionManager.sanitizeUsername(username)
                                        val cleanEmail = signUpEmail.trim()
                                        val normalizedDigits = PhoneFormatUtils.normalizeIndianPhoneDigits(phoneDigits)

                                        when {
                                            cleanName.isEmpty() ->
                                                errorMessage = "Enter your full name."
                                            cleanUser.length < 3 ->
                                                errorMessage = "Enter a username with at least 3 valid characters."
                                            !isValidEmail(cleanEmail) ->
                                                errorMessage = "Enter a valid email address."
                                            normalizedDigits == null ->
                                                errorMessage = "Enter a valid 10-digit mobile number."
                                            signUpPassword.length < 6 ->
                                                errorMessage = "Password must be at least 6 characters."
                                            else -> {
                                                val auth = getOrInitAuth()
                                                if (auth == null) {
                                                    errorMessage = "Firebase Auth is still initializing. Please try again."
                                                    return@GradientPrimaryButton
                                                }
                                                isBusy = true
                                                scope.launch {
                                                    val repo = getOrInitRepository()
                                                    if (repo != null) {
                                                        val unameRes = repo.checkUsernameAvailable(cleanUser)
                                                        if (unameRes.getOrNull() == false) {
                                                            isBusy = false
                                                            errorMessage = "Username @$cleanUser is already taken."
                                                            return@launch
                                                        }
                                                        if (unameRes.isFailure &&
                                                            !SocialRepository.isOfflineOrUnavailableError(unameRes.exceptionOrNull())
                                                        ) {
                                                            isBusy = false
                                                            errorMessage = unameRes.exceptionOrNull()?.localizedMessage
                                                                ?: "Could not verify username availability."
                                                            return@launch
                                                        }
                                                    }

                                                    try {
                                                        val authResult = auth
                                                            .createUserWithEmailAndPassword(cleanEmail, signUpPassword)
                                                            .await()
                                                        val createdUser = authResult.user
                                                            ?: throw IllegalStateException("Account created but user session was unavailable.")

                                                        runCatching {
                                                            withTimeoutOrNull(2_000L) {
                                                                createdUser.updateProfile(
                                                                    UserProfileChangeRequest.Builder()
                                                                        .setDisplayName(cleanName)
                                                                        .build()
                                                                ).await()
                                                            }
                                                        }

                                                        PendingSignUpDraftStore.save(
                                                            context,
                                                            PendingSignUpDraft(
                                                                uid = createdUser.uid,
                                                                fullName = cleanName,
                                                                username = cleanUser,
                                                                mobile = normalizedDigits,
                                                                email = cleanEmail
                                                            )
                                                        )

                                                        val sendRes = runCatching {
                                                            createdUser.sendEmailVerification().await()
                                                        }
                                                        pendingVerifyEmail = createdUser.email ?: cleanEmail
                                                        pendingVerifyIsSignUp = true
                                                        verifyEmailPending = true
                                                        isBusy = false
                                                        if (sendRes.isSuccess) {
                                                            resendCooldownSeconds = 30
                                                            infoMessage = "Verification link sent to $pendingVerifyEmail."
                                                        } else {
                                                            errorMessage = "Account created, but we couldn't send the email right now. Tap Resend Email."
                                                        }
                                                    } catch (e: Exception) {
                                                        isBusy = false
                                                        errorMessage = mapFirebaseAuthError(e)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            } else {
                                // LOG IN MODE (Email & Password)
                                OutlinedTextField(
                                    value = loginEmail,
                                    onValueChange = { loginEmail = it.trim() },
                                    label = { Text("Email Address") },
                                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    singleLine = true,
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("login_email_input")
                                )
                                OutlinedTextField(
                                    value = loginPassword,
                                    onValueChange = { loginPassword = it },
                                    label = { Text("Password") },
                                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { loginPasswordVisible = !loginPasswordVisible }) {
                                            Icon(
                                                imageVector = if (loginPasswordVisible) {
                                                    Icons.Filled.VisibilityOff
                                                } else {
                                                    Icons.Filled.Visibility
                                                },
                                                contentDescription = "Toggle password visibility"
                                            )
                                        }
                                    },
                                    visualTransformation = if (loginPasswordVisible) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    singleLine = true,
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("login_password_input")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            errorMessage = null
                                            infoMessage = null
                                            val cleanEmail = loginEmail.trim()
                                            if (!isValidEmail(cleanEmail)) {
                                                errorMessage = "Enter your email address above to reset your password."
                                                return@TextButton
                                            }
                                            val auth = getOrInitAuth()
                                            if (auth == null) {
                                                errorMessage = "Firebase Auth is still initializing. Please try again."
                                                return@TextButton
                                            }
                                            isBusy = true
                                            scope.launch {
                                                try {
                                                    auth.sendPasswordResetEmail(cleanEmail).await()
                                                    isBusy = false
                                                    infoMessage = "Password reset link sent to $cleanEmail."
                                                } catch (e: Exception) {
                                                    isBusy = false
                                                    errorMessage = mapFirebaseAuthError(e)
                                                }
                                            }
                                        },
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("forgot_password_button")
                                    ) {
                                        Text("Forgot Password?")
                                    }
                                }

                                GradientPrimaryButton(
                                    text = "Log In",
                                    isLoading = isBusy,
                                    enabled = !isBusy,
                                    testTag = "login_submit_button",
                                    onClick = {
                                        errorMessage = null
                                        infoMessage = null
                                        val cleanEmail = loginEmail.trim()
                                        when {
                                            !isValidEmail(cleanEmail) ->
                                                errorMessage = "Enter a valid email address."
                                            loginPassword.isBlank() ->
                                                errorMessage = "Enter your password."
                                            else -> {
                                                val auth = getOrInitAuth()
                                                if (auth == null) {
                                                    errorMessage = "Firebase Auth is still initializing. Please try again."
                                                    return@GradientPrimaryButton
                                                }
                                                isBusy = true
                                                scope.launch {
                                                    try {
                                                        val result = auth
                                                            .signInWithEmailAndPassword(cleanEmail, loginPassword)
                                                            .await()
                                                        val signedInUser = result.user
                                                            ?: throw IllegalStateException("Log in succeeded but user session was unavailable.")
                                                        runCatching { signedInUser.reload().await() }
                                                        val current = auth.currentUser ?: signedInUser

                                                        if (!current.isEmailVerified) {
                                                            val draft = PendingSignUpDraftStore.loadForUid(context, current.uid)
                                                            pendingVerifyEmail = current.email ?: cleanEmail
                                                            pendingVerifyIsSignUp = draft != null
                                                            verifyEmailPending = true
                                                            isBusy = false
                                                            infoMessage = "Your email isn't verified yet. Check your inbox or tap Resend Email."
                                                        } else {
                                                            completeVerifiedEmailLogin(current)
                                                        }
                                                    } catch (e: Exception) {
                                                        isBusy = false
                                                        errorMessage = mapFirebaseAuthError(e)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = extended.subtleDivider)
                        Text(
                            text = "  OR  ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = extended.subtleDivider)
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            errorMessage = null
                            infoMessage = null
                            isBusy = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                targetAuth = getOrInitAuth(),
                                onAuthSuccess = { user: FirebaseUser ->
                                    scope.launch {
                                        val repo = getOrInitRepository()
                                        runCatching { repo?.ensureUserProfile(user) }
                                        val profile = runCatching { repo?.fetchUserProfile(user.uid) }.getOrNull()
                                        isBusy = false
                                        val handle = profile?.username?.takeIf { it.isNotBlank() }
                                            ?: user.displayName
                                                ?.lowercase()
                                                ?.replace(Regex("[^a-z0-9._]"), "")
                                                ?.take(24)
                                                ?.ifBlank { "star_${user.uid.take(6)}" }
                                            ?: "star_${user.uid.take(6)}"
                                        onAccountAuthenticated(
                                            SavedAccountRecord(
                                                uid = user.uid,
                                                slotName = slotName,
                                                username = handle,
                                                fullName = profile?.fullName?.takeIf { it.isNotBlank() }
                                                    ?: user.displayName
                                                    ?: "Star Creator",
                                                email = user.email.orEmpty(),
                                                avatarData = profile?.effectiveAvatar?.takeIf { it.isNotBlank() }
                                                    ?: user.photoUrl?.toString().orEmpty(),
                                                phoneE164 = profile?.mobile?.takeIf { it.isNotBlank() }
                                                    ?: user.phoneNumber.orEmpty(),
                                                verified = profile?.effectiveVerified ?: false,
                                                authProvider = "google"
                                            )
                                        )
                                    }
                                },
                                onAuthError = { msg ->
                                    isBusy = false
                                    errorMessage = msg
                                },
                                scope = scope,
                                onAuthCancelled = { isBusy = false }
                            )
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Continue with Google")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun VerifyEmailCard(
    email: String,
    isBusy: Boolean,
    resendCooldownSeconds: Int,
    onCheckVerified: () -> Unit,
    onResendEmail: () -> Unit,
    onUseDifferentEmail: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("verify_email_screen")
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MarkEmailUnread,
                    contentDescription = "Verify email icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }

            Text(
                text = stringResource(R.string.verify_email_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (email.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.verify_email_instructions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            GradientPrimaryButton(
                text = stringResource(R.string.verify_email_continue),
                isLoading = isBusy,
                enabled = !isBusy,
                testTag = "verify_email_continue_button",
                onClick = onCheckVerified
            )

            OutlinedButton(
                onClick = onResendEmail,
                enabled = !isBusy && resendCooldownSeconds == 0,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("resend_verification_email_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (resendCooldownSeconds > 0) {
                        "${stringResource(R.string.verify_email_resend)} (${resendCooldownSeconds}s)"
                    } else {
                        stringResource(R.string.verify_email_resend)
                    }
                )
            }

            TextButton(
                onClick = onUseDifferentEmail,
                enabled = !isBusy,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("verify_email_back_button")
            ) {
                Text(stringResource(R.string.verify_email_back))
            }
        }
    }
}

@Composable
private fun StatusBanner(text: String, isError: Boolean) {
    Surface(
        color = if (isError) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(12.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GradientPrimaryButton(
    text: String,
    isLoading: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val extended = LocalStarMaxColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled) extended.brandGradientBrush
                else Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
            .testTag(testTag)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
