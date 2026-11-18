package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sms
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.firebase.AccountSessionManager
import com.example.data.firebase.PasswordHasher
import com.example.data.firebase.PhoneAuthHelper
import com.example.data.firebase.PhoneFormatUtils
import com.example.data.firebase.SocialRepository
import com.example.data.firebase.onGoogleSignInClicked
import com.example.data.model.AuthLookupRecord
import com.example.data.model.SavedAccountRecord
import com.example.ui.components.StarMaxWordmark
import com.example.ui.theme.LocalStarMaxColors
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AuthTab(val label: String) {
    LOG_IN("Log In"),
    SIGN_UP("Sign Up")
}

@Composable
fun AuthScreen(
    accountSessionManager: AccountSessionManager,
    isAddAccountMode: Boolean = false,
    onAccountAuthenticated: (SavedAccountRecord) -> Unit,
    onCancelAddAccount: (() -> Unit)? = null
) {
    if (isAddAccountMode && onCancelAddAccount != null) {
        BackHandler(onBack = onCancelAddAccount)
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) {
        runCatching { CredentialManager.create(context) }.getOrNull()
    }
    val extended = LocalStarMaxColors.current

    val slotName = remember(isAddAccountMode) {
        accountSessionManager.allocateSlotForAccount()
    }
    val slotAuth = remember(slotName) { accountSessionManager.resolveAuth(slotName) }
    val slotDb = remember(slotName) { accountSessionManager.resolveFirestore(slotName) }
    val slotRepository = remember(slotDb, slotAuth) {
        slotDb?.let { SocialRepository(db = it, auth = slotAuth) }
    }

    var selectedTab by remember { mutableStateOf(if (isAddAccountMode) AuthTab.SIGN_UP else AuthTab.LOG_IN) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    // Sign Up state
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var signUpPassword by remember { mutableStateOf("") }
    var signUpPasswordVisible by remember { mutableStateOf(false) }
    var phoneDigits by remember { mutableStateOf("") }

    // Log In state
    var loginIdentifier by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }
    var pendingLoginLookup by remember { mutableStateOf<AuthLookupRecord?>(null) }

    // Shared OTP state
    var otpActive by remember { mutableStateOf(false) }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var otpCode by remember { mutableStateOf("") }
    var otpResendSeconds by remember { mutableIntStateOf(0) }
    var activeOtpPhoneE164 by remember { mutableStateOf("") }
    var otpFlowIsSignUp by remember { mutableStateOf(true) }

    LaunchedEffect(otpResendSeconds) {
        if (otpResendSeconds > 0) {
            delay(1000L)
            otpResendSeconds -= 1
        }
    }

    fun resetOtpFlow() {
        otpActive = false
        verificationId = null
        resendToken = null
        otpCode = ""
        otpResendSeconds = 0
        activeOtpPhoneE164 = ""
        pendingLoginLookup = null
    }

    suspend fun finalizePhoneCredential(credential: PhoneAuthCredential) {
        val auth = slotAuth ?: run {
            isBusy = false
            errorMessage = "Firebase Authentication is unavailable."
            return
        }
        val repo = slotRepository ?: run {
            isBusy = false
            errorMessage = "Database connection is unavailable."
            return
        }

        val signInRes = PhoneAuthHelper.signInWithPhoneCredential(auth, credential)
        val user = signInRes.getOrElse { err ->
            isBusy = false
            errorMessage = err.localizedMessage ?: "OTP verification failed."
            return
        }

        if (otpFlowIsSignUp) {
            val digits10 = PhoneFormatUtils.normalizeIndianPhoneDigits(phoneDigits) ?: phoneDigits
            val regRes = repo.completePhoneRegistration(
                user = user,
                fullName = fullName,
                username = username,
                phoneDigits10 = digits10,
                password = signUpPassword
            )
            regRes.onSuccess { createdProfile ->
                accountSessionManager.registerAccountCredentials(
                    uid = user.uid,
                    slotName = slotName,
                    fullName = createdProfile.fullName,
                    username = createdProfile.username,
                    phoneDigits10 = digits10,
                    password = signUpPassword,
                    verified = createdProfile.effectiveVerified,
                    authProvider = "phone"
                )
                isBusy = false
                onAccountAuthenticated(
                    SavedAccountRecord(
                        uid = user.uid,
                        slotName = slotName,
                        username = createdProfile.username,
                        fullName = createdProfile.fullName,
                        avatarData = createdProfile.effectiveAvatar,
                        phoneE164 = user.phoneNumber ?: PhoneFormatUtils.toIndianE164(digits10),
                        verified = createdProfile.effectiveVerified,
                        authProvider = "phone"
                    )
                )
            }.onFailure { err ->
                runCatching { auth.signOut() }
                isBusy = false
                errorMessage = err.localizedMessage ?: "Failed to create verified account."
            }
        } else {
            val lookup = pendingLoginLookup
            if (lookup != null && lookup.userId != user.uid) {
                runCatching { auth.signOut() }
                isBusy = false
                errorMessage = "Verified number does not match the selected account."
                return
            }
            repo.ensureUserProfile(user)
            val phoneE164 = user.phoneNumber ?: lookup?.phoneE164.orEmpty()
            val digits10 = PhoneFormatUtils.normalizeIndianPhoneDigits(phoneE164).orEmpty()
            val uname = lookup?.usernameLower ?: user.displayName ?: "star_user"
            val fName = user.displayName ?: lookup?.usernameLower ?: "Star Creator"
            if (digits10.isNotBlank() && loginPassword.length >= 6) {
                accountSessionManager.registerAccountCredentials(
                    uid = user.uid,
                    slotName = slotName,
                    fullName = fName,
                    username = uname,
                    phoneDigits10 = digits10,
                    password = loginPassword,
                    verified = false,
                    authProvider = "phone"
                )
            }
            isBusy = false
            onAccountAuthenticated(
                SavedAccountRecord(
                    uid = user.uid,
                    slotName = slotName,
                    username = uname,
                    fullName = fName,
                    avatarData = user.photoUrl?.toString().orEmpty(),
                    phoneE164 = phoneE164,
                    verified = false,
                    authProvider = "phone"
                )
            )
        }
    }

    fun triggerOtpSend(
        targetPhoneE164: String,
        isSignUp: Boolean,
        useResendToken: PhoneAuthProvider.ForceResendingToken? = null
    ) {
        val currentActivity = activity
        val auth = slotAuth
        if (currentActivity == null || auth == null) {
            isBusy = false
            errorMessage = "Firebase Phone Authentication is unavailable. Check your connection and configuration."
            return
        }

        otpFlowIsSignUp = isSignUp
        activeOtpPhoneE164 = targetPhoneE164
        isBusy = true
        errorMessage = null
        infoMessage = "Sending SMS verification code to $targetPhoneE164..."

        PhoneAuthHelper.sendVerificationOtp(
            activity = currentActivity,
            auth = auth,
            phoneE164 = targetPhoneE164,
            resendToken = useResendToken,
            onAutoVerified = { credential ->
                scope.launch { finalizePhoneCredential(credential) }
            },
            onCodeSent = { verId, token ->
                verificationId = verId
                resendToken = token
                otpActive = true
                otpResendSeconds = 30
                isBusy = false
                infoMessage = "6-digit SMS OTP sent to $targetPhoneE164"
            },
            onFailed = { errMsg ->
                isBusy = false
                infoMessage = null
                errorMessage = errMsg
            }
        )
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
                if (isAddAccountMode && onCancelAddAccount != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onCancelAddAccount,
                            modifier = Modifier.testTag("add_account_cancel_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to profile")
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
                    text = "Mobile-verified profiles, Stories, Reels & DMs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AuthTab.entries.forEachIndexed { index, tab ->
                        SegmentedButton(
                            selected = selectedTab == tab,
                            onClick = {
                                if (!isBusy && selectedTab != tab) {
                                    selectedTab = tab
                                    errorMessage = null
                                    infoMessage = null
                                    resetOtpFlow()
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = AuthTab.entries.size),
                            modifier = Modifier.testTag("auth_tab_${tab.name.lowercase()}")
                        ) {
                            Text(tab.label)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (errorMessage != null) {
                    StatusBanner(text = errorMessage.orEmpty(), isError = true)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (infoMessage != null) {
                    StatusBanner(text = infoMessage.orEmpty(), isError = false)
                    Spacer(modifier = Modifier.height(12.dp))
                }

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
                                enabled = !otpActive && !isBusy,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_fullname_input")
                            )
                            OutlinedTextField(
                                value = username,
                                onValueChange = {
                                    username = it.filter { ch -> ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-' }
                                },
                                label = { Text("Username") },
                                prefix = { Text("@") },
                                supportingText = { Text("Unique handle (min 3 chars)") },
                                singleLine = true,
                                enabled = !otpActive && !isBusy,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_username_input")
                            )
                            OutlinedTextField(
                                value = signUpPassword,
                                onValueChange = { signUpPassword = it },
                                label = { Text("Password (min 6 chars)") },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { signUpPasswordVisible = !signUpPasswordVisible }) {
                                        Icon(
                                            imageVector = if (signUpPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = "Toggle password visibility"
                                        )
                                    }
                                },
                                visualTransformation = if (signUpPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                enabled = !otpActive && !isBusy,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_password_input")
                            )
                            OutlinedTextField(
                                value = phoneDigits,
                                onValueChange = { input ->
                                    phoneDigits = input.filter { it.isDigit() }.take(10)
                                },
                                label = { Text("Mobile Number (10 digits)") },
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
                                enabled = !otpActive && !isBusy,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_phone_input")
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
                                    text = "OTP-secured registration. Get Star Max Verified in Settings.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!otpActive) {
                                GradientPrimaryButton(
                                    text = "Send OTP",
                                    isLoading = isBusy,
                                    enabled = !isBusy,
                                    testTag = "send_otp_button",
                                    onClick = {
                                        errorMessage = null
                                        infoMessage = null
                                        val cleanName = fullName.trim()
                                        val cleanUser = accountSessionManager.sanitizeUsername(username)
                                        val digits = PhoneFormatUtils.normalizeIndianPhoneDigits(phoneDigits)
                                        when {
                                            cleanName.isEmpty() -> errorMessage = "Enter your full name."
                                            cleanUser.length < 3 -> errorMessage = "Enter a username with at least 3 valid characters."
                                            signUpPassword.length < 6 -> errorMessage = "Password must be at least 6 characters."
                                            digits == null -> errorMessage = "Enter a valid 10-digit +91 mobile number."
                                            else -> {
                                                val repo = slotRepository
                                                if (repo == null) {
                                                    errorMessage = "Firebase connection is unavailable. Please try again later."
                                                } else {
                                                    isBusy = true
                                                    scope.launch {
                                                        val unameRes = repo.checkUsernameAvailable(cleanUser)
                                                        if (unameRes.isFailure) {
                                                            isBusy = false
                                                            errorMessage = unameRes.exceptionOrNull()?.localizedMessage
                                                                ?: "Could not verify username availability."
                                                            return@launch
                                                        }
                                                        if (unameRes.getOrNull() == false) {
                                                            isBusy = false
                                                            errorMessage = "Username @$cleanUser is already taken."
                                                            return@launch
                                                        }
                                                        val phoneRes = repo.checkPhoneAvailable(digits)
                                                        if (phoneRes.isFailure) {
                                                            isBusy = false
                                                            errorMessage = phoneRes.exceptionOrNull()?.localizedMessage
                                                                ?: "Could not verify mobile number."
                                                            return@launch
                                                        }
                                                        if (phoneRes.getOrNull() == false) {
                                                            isBusy = false
                                                            errorMessage = "This mobile number is already registered. Please log in."
                                                            return@launch
                                                        }
                                                        triggerOtpSend(
                                                            targetPhoneE164 = PhoneFormatUtils.toIndianE164(digits),
                                                            isSignUp = true
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        } else {
                            // LOG IN MODE
                            OutlinedTextField(
                                value = loginIdentifier,
                                onValueChange = { loginIdentifier = it },
                                label = { Text("Mobile Number (+91) or Username") },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                singleLine = true,
                                enabled = !otpActive && !isBusy,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_identifier_input")
                            )
                            OutlinedTextField(
                                value = loginPassword,
                                onValueChange = { loginPassword = it },
                                label = { Text("Password") },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { loginPasswordVisible = !loginPasswordVisible }) {
                                        Icon(
                                            imageVector = if (loginPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = "Toggle password visibility"
                                        )
                                    }
                                },
                                visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                enabled = !otpActive && !isBusy,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_password_input")
                            )

                            if (!otpActive) {
                                GradientPrimaryButton(
                                    text = "Log In",
                                    isLoading = isBusy,
                                    enabled = !isBusy,
                                    testTag = "login_submit_button",
                                    onClick = {
                                        errorMessage = null
                                        infoMessage = null
                                        if (loginIdentifier.isBlank() || loginPassword.isBlank()) {
                                            errorMessage = "Enter your mobile number or username and password."
                                            return@GradientPrimaryButton
                                        }

                                        val repo = slotRepository
                                        if (repo == null) {
                                            errorMessage = "Firebase connection is unavailable. Check internet connectivity."
                                            return@GradientPrimaryButton
                                        }

                                        isBusy = true
                                        scope.launch {
                                            val lookupRes = repo.lookupAccountForLogin(loginIdentifier)
                                            if (lookupRes.isFailure) {
                                                isBusy = false
                                                errorMessage = lookupRes.exceptionOrNull()?.localizedMessage
                                                    ?: "Unable to look up account."
                                                return@launch
                                            }
                                            val lookup = lookupRes.getOrNull()
                                            if (lookup == null) {
                                                isBusy = false
                                                errorMessage = "No account found matching \"$loginIdentifier\". Please check or Sign Up."
                                                return@launch
                                            }
                                            if (!PasswordHasher.verifyPassword(loginPassword, lookup.passwordSalt, lookup.passwordHash)) {
                                                isBusy = false
                                                errorMessage = "Incorrect password. Please try again."
                                                return@launch
                                            }
                                            val existingSaved = accountSessionManager.savedAccounts.value
                                                .firstOrNull { it.uid == lookup.userId }
                                            if (existingSaved != null && accountSessionManager.switchToSavedAccount(existingSaved.uid)) {
                                                isBusy = false
                                                onAccountAuthenticated(existingSaved)
                                                return@launch
                                            }
                                            pendingLoginLookup = lookup
                                            infoMessage = "Password verified. Sending SMS OTP to ${PhoneFormatUtils.maskPhone(lookup.phoneE164)}..."
                                            triggerOtpSend(
                                                targetPhoneE164 = lookup.phoneE164,
                                                isSignUp = false
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        // OTP Verification Section (Firebase SMS OTP only)
                        AnimatedVisibility(visible = otpActive) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                HorizontalDivider(color = extended.subtleDivider)
                                OutlinedTextField(
                                    value = otpCode,
                                    onValueChange = { input ->
                                        otpCode = input.filter { it.isDigit() }.take(6)
                                    },
                                    label = { Text("Enter 6-digit SMS OTP") },
                                    leadingIcon = { Icon(Icons.Filled.Sms, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("otp_code_input")
                                )
                                GradientPrimaryButton(
                                    text = if (otpFlowIsSignUp) "Verify OTP & Create Account" else "Verify OTP & Log In",
                                    isLoading = isBusy,
                                    enabled = !isBusy && otpCode.length == 6,
                                    testTag = "verify_otp_button",
                                    onClick = {
                                        errorMessage = null
                                        val verId = verificationId
                                        if (verId != null) {
                                            isBusy = true
                                            val credential = PhoneAuthHelper.buildOtpCredential(verId, otpCode)
                                            scope.launch { finalizePhoneCredential(credential) }
                                        } else {
                                            errorMessage = "SMS verification session expired. Tap Resend OTP."
                                        }
                                    }
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { resetOtpFlow(); errorMessage = null; infoMessage = null },
                                        enabled = !isBusy
                                    ) {
                                        Text("Edit details")
                                    }
                                    TextButton(
                                        onClick = {
                                            if (activeOtpPhoneE164.isNotBlank()) {
                                                errorMessage = null
                                                isBusy = true
                                                triggerOtpSend(
                                                    targetPhoneE164 = activeOtpPhoneE164,
                                                    isSignUp = otpFlowIsSignUp,
                                                    useResendToken = resendToken
                                                )
                                            }
                                        },
                                        enabled = otpResendSeconds == 0 && !isBusy,
                                        modifier = Modifier.testTag("resend_otp_button")
                                    ) {
                                        Text(
                                            if (otpResendSeconds > 0) "Resend OTP in ${otpResendSeconds}s" else "Resend OTP"
                                        )
                                    }
                                }
                            }
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
                            targetAuth = slotAuth,
                            onAuthSuccess = { user: FirebaseUser ->
                                scope.launch {
                                    slotRepository?.ensureUserProfile(user)
                                    isBusy = false
                                    val handle = user.displayName
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
                                            fullName = user.displayName ?: "Star Creator",
                                            avatarData = user.photoUrl?.toString().orEmpty(),
                                            phoneE164 = user.phoneNumber.orEmpty(),
                                            verified = false,
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

                Spacer(modifier = Modifier.height(16.dp))
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
