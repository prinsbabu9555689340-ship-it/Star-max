package com.example.data.firebase

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.model.SavedAccountRecord
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    private const val ITERATIONS = 10_000
    private const val KEY_LENGTH_BITS = 256

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hashPassword(password: String, saltBase64: String): String {
        val salt = runCatching { Base64.decode(saltBase64, Base64.NO_WRAP) }
            .getOrElse { saltBase64.toByteArray(Charsets.UTF_8) }
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val encoded = runCatching {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            factory.generateSecret(spec).encoded
        }.getOrElse {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(salt)
            digest.digest(password.toByteArray(Charsets.UTF_8))
        }
        spec.clearPassword()
        return encoded.joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(password: String, saltBase64: String, expectedHexHash: String): Boolean {
        if (password.isEmpty() || saltBase64.isBlank() || expectedHexHash.isBlank()) return false
        val actual = hashPassword(password, saltBase64)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expectedHexHash.toByteArray(Charsets.UTF_8)
        )
    }
}

object PhoneFormatUtils {
    fun normalizeIndianPhoneDigits(raw: String): String? {
        val digitsOnly = raw.filter { it.isDigit() }
        val tenDigits = when {
            digitsOnly.length == 10 -> digitsOnly
            digitsOnly.length == 12 && digitsOnly.startsWith("91") -> digitsOnly.substring(2)
            digitsOnly.length == 11 && digitsOnly.startsWith("0") -> digitsOnly.substring(1)
            else -> return null
        }
        return tenDigits.takeIf { it.length == 10 && it.all(Char::isDigit) }
    }

    fun toIndianE164(digits10: String): String = "+91$digits10"

    fun maskPhone(phoneE164: String): String {
        val digits = phoneE164.filter { it.isDigit() }
        if (digits.length < 4) return phoneE164
        return "+91 ••••••${digits.takeLast(4)}"
    }
}

private data class RegisteredCredentialEntry(
    val uid: String,
    val usernameLower: String,
    val phoneDigits10: String,
    val phoneE164: String,
    val passwordSalt: String,
    val passwordHash: String,
    val account: SavedAccountRecord
)

class AccountSessionManager(context: Context) {
    private val appContext = context.applicationContext ?: context
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _savedAccounts = MutableStateFlow(loadSavedAccounts())
    val savedAccounts: StateFlow<List<SavedAccountRecord>> = _savedAccounts.asStateFlow()

    private val _activeUid = MutableStateFlow(loadActiveUid())
    val activeUid: StateFlow<String?> = _activeUid.asStateFlow()

    fun isFirebaseConfigured(): Boolean {
        return ensureDefaultFirebaseApp() != null
    }

    private fun databaseId(): String? {
        return runCatching { appContext.getString(R.string.firestore_database_id) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun ensureDefaultFirebaseApp(): FirebaseApp? {
        return runCatching {
            FirebaseApp.getApps(appContext)
                .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                ?: runCatching { FirebaseApp.initializeApp(appContext) }.getOrNull()
                ?: runCatching { FirebaseApp.getInstance() }.getOrNull()
        }.onFailure { err ->
            Log.w(TAG, "Unable to initialize default FirebaseApp", err)
        }.getOrNull()
    }

    fun resolveFirebaseApp(slotName: String): FirebaseApp? {
        val defaultApp = ensureDefaultFirebaseApp() ?: return null
        if (slotName.isBlank() || slotName == FirebaseApp.DEFAULT_APP_NAME) {
            return defaultApp
        }
        return runCatching {
            FirebaseApp.getApps(appContext).firstOrNull { it.name == slotName }
                ?: FirebaseApp.initializeApp(appContext, defaultApp.options, slotName)
        }.onFailure { err ->
            Log.w(TAG, "Falling back to default FirebaseApp for slot '$slotName'", err)
        }.getOrNull() ?: defaultApp
    }

    fun resolveAuth(slotName: String = ""): FirebaseAuth? {
        val app = resolveFirebaseApp(slotName) ?: ensureDefaultFirebaseApp()
        return if (app != null) {
            runCatching { FirebaseAuth.getInstance(app) }.getOrNull()
                ?: runCatching { FirebaseAuth.getInstance() }.getOrNull()
        } else {
            runCatching { FirebaseAuth.getInstance() }.getOrNull()
        }
    }

    fun resolveFirestore(slotName: String = ""): FirebaseFirestore? {
        val dbId = databaseId() ?: return null
        val app = resolveFirebaseApp(slotName) ?: ensureDefaultFirebaseApp()
        return if (app != null) {
            runCatching { FirebaseFirestore.getInstance(app, dbId) }.getOrNull()
                ?: runCatching { FirebaseFirestore.getInstance(dbId) }.getOrNull()
        } else {
            runCatching { FirebaseFirestore.getInstance(dbId) }.getOrNull()
        }
    }

    fun allocateSlotForAccount(uid: String? = null): String {
        val existing = uid?.let { id -> _savedAccounts.value.firstOrNull { it.uid == id } }
        if (existing != null) return existing.slotName
        val defaultInUse = _savedAccounts.value.any { it.slotName.isBlank() }
        return if (!defaultInUse) "" else "starmax_acct_${System.currentTimeMillis()}"
    }

    fun isLocalUsernameAvailable(rawUsername: String): Boolean {
        val clean = sanitizeUsername(rawUsername)
        if (clean.length < 3) return false
        return loadRegisteredEntries().none { it.usernameLower == clean }
    }

    fun isLocalPhoneAvailable(rawPhone: String): Boolean {
        val digits10 = PhoneFormatUtils.normalizeIndianPhoneDigits(rawPhone) ?: return false
        return loadRegisteredEntries().none { it.phoneDigits10 == digits10 }
    }

    fun registerAccountCredentials(
        uid: String = "starmax_${UUID.randomUUID().toString().replace("-", "").take(16)}",
        slotName: String = "",
        fullName: String,
        username: String,
        phoneDigits10: String,
        password: String,
        verified: Boolean = false,
        authProvider: String = "local_otp"
    ): Result<SavedAccountRecord> {
        val cleanUsername = sanitizeUsername(username)
        if (cleanUsername.length < 3) {
            return Result.failure(IllegalArgumentException("Username must be at least 3 valid characters."))
        }
        val cleanFullName = fullName.trim().take(60)
        if (cleanFullName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Full name is required."))
        }
        val digits = PhoneFormatUtils.normalizeIndianPhoneDigits(phoneDigits10)
            ?: return Result.failure(IllegalArgumentException("Enter a valid 10-digit +91 mobile number."))
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }

        val entries = loadRegisteredEntries()
        if (entries.any { it.usernameLower == cleanUsername && it.uid != uid }) {
            return Result.failure(IllegalStateException("Username @$cleanUsername is already taken."))
        }
        if (entries.any { it.phoneDigits10 == digits && it.uid != uid }) {
            return Result.failure(IllegalStateException("This mobile number is already registered. Please log in."))
        }

        val phoneE164 = PhoneFormatUtils.toIndianE164(digits)
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(password, salt)
        val existingRecord = entries.firstOrNull { it.uid == uid }?.account
        val record = (existingRecord ?: SavedAccountRecord()).copy(
            uid = uid,
            slotName = slotName,
            username = cleanUsername,
            fullName = cleanFullName,
            bio = existingRecord?.bio?.ifBlank { "Capturing moments on Star Max ✨" } ?: "Capturing moments on Star Max ✨",
            phoneE164 = phoneE164,
            verified = verified,
            authProvider = authProvider
        )
        val newEntry = RegisteredCredentialEntry(
            uid = uid,
            usernameLower = cleanUsername,
            phoneDigits10 = digits,
            phoneE164 = phoneE164,
            passwordSalt = salt,
            passwordHash = hash,
            account = record
        )
        persistRegisteredEntries(entries.filterNot { it.uid == uid } + newEntry)
        saveAccount(record, makeActive = true)
        return Result.success(record)
    }

    fun authenticateLocalAccount(identifier: String, password: String): Result<SavedAccountRecord> {
        val trimmed = identifier.trim()
        if (trimmed.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter your mobile number or username and password."))
        }
        val entries = loadRegisteredEntries()
        val digits10 = PhoneFormatUtils.normalizeIndianPhoneDigits(trimmed)
        val cleanHandle = sanitizeUsername(trimmed)
        val match = if (digits10 != null) {
            entries.firstOrNull { it.phoneDigits10 == digits10 }
        } else {
            entries.firstOrNull { it.usernameLower == cleanHandle }
        } ?: return Result.failure(IllegalStateException("No registered account found for \"$trimmed\"."))

        if (!PasswordHasher.verifyPassword(password, match.passwordSalt, match.passwordHash)) {
            return Result.failure(IllegalArgumentException("Incorrect password. Please try again."))
        }

        // Pull latest saved copy if any field was updated
        val latest = _savedAccounts.value.firstOrNull { it.uid == match.uid } ?: match.account
        saveAccount(latest, makeActive = true)
        return Result.success(latest)
    }

    fun updateLocalPassword(uid: String, newPassword: String): Result<Unit> {
        if (newPassword.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }
        val entries = loadRegisteredEntries()
        val match = entries.firstOrNull { it.uid == uid }
            ?: return Result.failure(IllegalStateException("Account record not found."))
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(newPassword, salt)
        val updated = entries.map {
            if (it.uid == uid) it.copy(passwordSalt = salt, passwordHash = hash) else it
        }
        persistRegisteredEntries(updated)
        return Result.success(Unit)
    }

    fun getAllRegisteredAccounts(): List<SavedAccountRecord> {
        val registered = loadRegisteredEntries().map { it.account }
        val savedIds = _savedAccounts.value.associateBy { it.uid }
        return (registered.map { savedIds[it.uid] ?: it } + _savedAccounts.value)
            .distinctBy { it.uid }
    }

    fun saveAccount(record: SavedAccountRecord, makeActive: Boolean = true) {
        val updated = (_savedAccounts.value.filterNot { it.uid == record.uid } + record)
        persistAccounts(updated)
        // Also keep registered entry snapshot in sync if one exists
        val entries = loadRegisteredEntries()
        if (entries.any { it.uid == record.uid }) {
            persistRegisteredEntries(
                entries.map {
                    if (it.uid == record.uid) it.copy(
                        usernameLower = sanitizeUsername(record.username).ifBlank { it.usernameLower },
                        account = record
                    ) else it
                }
            )
        }
        if (makeActive) {
            persistActiveUid(record.uid)
        }
    }

    fun updateAccountProfile(
        uid: String,
        username: String,
        fullName: String,
        avatarData: String,
        verified: Boolean,
        bio: String? = null,
        website: String? = null,
        isPrivate: Boolean? = null
    ) {
        val existing = _savedAccounts.value.firstOrNull { it.uid == uid }
            ?: loadRegisteredEntries().firstOrNull { it.uid == uid }?.account
            ?: return
        val next = existing.copy(
            username = username.ifBlank { existing.username },
            fullName = fullName.ifBlank { existing.fullName },
            bio = bio ?: existing.bio,
            website = website ?: existing.website,
            avatarData = avatarData,
            verified = verified,
            isPrivate = isPrivate ?: existing.isPrivate
        )
        persistAccounts(_savedAccounts.value.map { if (it.uid == uid) next else it })
        val entries = loadRegisteredEntries()
        if (entries.any { it.uid == uid }) {
            persistRegisteredEntries(
                entries.map {
                    if (it.uid == uid) it.copy(
                        usernameLower = sanitizeUsername(next.username).ifBlank { it.usernameLower },
                        account = next
                    ) else it
                }
            )
        }
    }

    fun updateAccountVerification(
        uid: String,
        verified: Boolean,
        verificationStatus: String,
        fullName: String? = null,
        category: String? = null,
        knownAs: String? = null,
        link: String? = null,
        idProofData: String? = null
    ) {
        val existing = _savedAccounts.value.firstOrNull { it.uid == uid }
            ?: loadRegisteredEntries().firstOrNull { it.uid == uid }?.account
            ?: return
        val next = existing.copy(
            verified = verified,
            verificationStatus = verificationStatus,
            verificationFullName = fullName ?: existing.verificationFullName,
            verificationCategory = category ?: existing.verificationCategory,
            verificationKnownAs = knownAs ?: existing.verificationKnownAs,
            verificationLink = link ?: existing.verificationLink,
            verificationIdProofData = idProofData ?: existing.verificationIdProofData
        )
        if (_savedAccounts.value.any { it.uid == uid }) {
            persistAccounts(_savedAccounts.value.map { if (it.uid == uid) next else it })
        }
        val entries = loadRegisteredEntries()
        if (entries.any { it.uid == uid }) {
            persistRegisteredEntries(entries.map { if (it.uid == uid) it.copy(account = next) else it })
        }
    }

    fun switchToSavedAccount(uid: String): Boolean {
        val record = _savedAccounts.value.firstOrNull { it.uid == uid } ?: return false
        if (record.authProvider == "local_otp") {
            persistActiveUid(uid)
            return true
        }
        val auth = runCatching { resolveAuth(record.slotName) }.getOrNull() ?: return false
        if (auth.currentUser?.uid != uid) {
            return false
        }
        persistActiveUid(uid)
        return true
    }

    fun setActiveUid(uid: String?) {
        persistActiveUid(uid)
    }

    fun getActiveRecord(): SavedAccountRecord? {
        val uid = _activeUid.value ?: return null
        return _savedAccounts.value.firstOrNull { it.uid == uid }
    }

    fun logoutActiveAccount(
        credentialManager: CredentialManager?,
        scope: CoroutineScope,
        onComplete: () -> Unit = {}
    ) {
        val active = getActiveRecord()
        persistActiveUid(null)
        if (active != null) {
            persistAccounts(_savedAccounts.value.filterNot { it.uid == active.uid })
            if (active.authProvider != "local_otp") {
                runCatching { resolveAuth(active.slotName)?.signOut() }
            }
        } else {
            runCatching { resolveAuth("")?.signOut() }
        }
        scope.launch {
            withContext(NonCancellable) {
                runCatching { credentialManager?.clearCredentialState(ClearCredentialStateRequest()) }
                onComplete()
            }
        }
    }

    fun logoutAllAccounts(
        credentialManager: CredentialManager?,
        scope: CoroutineScope,
        onComplete: () -> Unit = {}
    ) {
        val all = _savedAccounts.value
        persistActiveUid(null)
        persistAccounts(emptyList())
        all.filter { it.authProvider != "local_otp" }.forEach { rec ->
            runCatching { resolveAuth(rec.slotName)?.signOut() }
        }
        runCatching { resolveAuth("")?.signOut() }
        scope.launch {
            withContext(NonCancellable) {
                runCatching { credentialManager?.clearCredentialState(ClearCredentialStateRequest()) }
                onComplete()
            }
        }
    }

    fun sanitizeUsername(raw: String): String {
        return raw.trim()
            .removePrefix("@")
            .lowercase()
            .replace(" ", "_")
            .replace(Regex("[^a-z0-9._-]"), "")
            .replace(Regex("\\.{2,}"), ".")
            .trim('.')
            .take(30)
    }

    private fun loadActiveUid(): String? {
        return prefs.getString(KEY_ACTIVE_UID, null)?.takeIf { it.isNotBlank() }
    }

    private fun persistActiveUid(uid: String?) {
        if (uid.isNullOrBlank()) {
            prefs.edit().remove(KEY_ACTIVE_UID).apply()
            _activeUid.value = null
        } else {
            prefs.edit().putString(KEY_ACTIVE_UID, uid).apply()
            _activeUid.value = uid
        }
    }

    private fun recordFromJson(obj: JSONObject): SavedAccountRecord? {
        val uid = obj.optString("uid")
        if (uid.isBlank()) return null
        return SavedAccountRecord(
            uid = uid,
            slotName = obj.optString("slotName"),
            username = obj.optString("username"),
            fullName = obj.optString("fullName"),
            bio = obj.optString("bio"),
            website = obj.optString("website"),
            avatarData = obj.optString("avatarData"),
            phoneE164 = obj.optString("phoneE164"),
            verified = obj.optBoolean("verified", false),
            isPrivate = obj.optBoolean("isPrivate", false),
            verificationStatus = obj.optString("verificationStatus", if (obj.optBoolean("verified", false)) "verified" else "none"),
            verificationFullName = obj.optString("verificationFullName"),
            verificationCategory = obj.optString("verificationCategory"),
            verificationKnownAs = obj.optString("verificationKnownAs"),
            verificationLink = obj.optString("verificationLink"),
            verificationIdProofData = obj.optString("verificationIdProofData"),
            authProvider = obj.optString("authProvider", "phone")
        )
    }

    private fun recordToJson(rec: SavedAccountRecord): JSONObject {
        return JSONObject().apply {
            put("uid", rec.uid)
            put("slotName", rec.slotName)
            put("username", rec.username)
            put("fullName", rec.fullName)
            put("bio", rec.bio)
            put("website", rec.website)
            put("avatarData", rec.avatarData.take(250_000))
            put("phoneE164", rec.phoneE164)
            put("verified", rec.verified)
            put("isPrivate", rec.isPrivate)
            put("verificationStatus", rec.verificationStatus)
            put("verificationFullName", rec.verificationFullName)
            put("verificationCategory", rec.verificationCategory)
            put("verificationKnownAs", rec.verificationKnownAs)
            put("verificationLink", rec.verificationLink)
            put("verificationIdProofData", rec.verificationIdProofData.take(250_000))
            put("authProvider", rec.authProvider)
        }
    }

    private fun loadSavedAccounts(): List<SavedAccountRecord> {
        val raw = prefs.getString(KEY_SAVED_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    recordFromJson(obj)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistAccounts(accounts: List<SavedAccountRecord>) {
        val arr = JSONArray()
        accounts.forEach { arr.put(recordToJson(it)) }
        prefs.edit().putString(KEY_SAVED_ACCOUNTS, arr.toString()).apply()
        _savedAccounts.value = accounts
    }

    private fun loadRegisteredEntries(): List<RegisteredCredentialEntry> {
        val raw = prefs.getString(KEY_REGISTERED_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val acctObj = o.optJSONObject("account") ?: continue
                    val acct = recordFromJson(acctObj) ?: continue
                    add(
                        RegisteredCredentialEntry(
                            uid = o.optString("uid"),
                            usernameLower = o.optString("usernameLower"),
                            phoneDigits10 = o.optString("phoneDigits10"),
                            phoneE164 = o.optString("phoneE164"),
                            passwordSalt = o.optString("passwordSalt"),
                            passwordHash = o.optString("passwordHash"),
                            account = acct
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistRegisteredEntries(entries: List<RegisteredCredentialEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("uid", e.uid)
                    put("usernameLower", e.usernameLower)
                    put("phoneDigits10", e.phoneDigits10)
                    put("phoneE164", e.phoneE164)
                    put("passwordSalt", e.passwordSalt)
                    put("passwordHash", e.passwordHash)
                    put("account", recordToJson(e.account))
                }
            )
        }
        prefs.edit().putString(KEY_REGISTERED_ACCOUNTS, arr.toString()).apply()
    }

    private companion object {
        const val TAG = "AccountSessionMgr"
        const val PREFS_NAME = "starmax_saved_accounts"
        const val KEY_SAVED_ACCOUNTS = "saved_accounts_json"
        const val KEY_REGISTERED_ACCOUNTS = "registered_accounts_json"
        const val KEY_ACTIVE_UID = "active_uid"
    }
}
