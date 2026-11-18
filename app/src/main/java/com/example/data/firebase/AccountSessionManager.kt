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

class AccountSessionManager(context: Context) {
    private val appContext = context.applicationContext ?: context
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _savedAccounts = MutableStateFlow(loadSavedAccounts())
    val savedAccounts: StateFlow<List<SavedAccountRecord>> = _savedAccounts.asStateFlow()

    private val _activeUid = MutableStateFlow(loadActiveUid())
    val activeUid: StateFlow<String?> = _activeUid.asStateFlow()

    fun isFirebaseConfigured(): Boolean {
        return resolveFirebaseApp("") != null && databaseIdOrNull() != null
    }

    private fun databaseIdOrNull(): String? {
        return runCatching { appContext.getString(R.string.firestore_database_id) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun resolveFirebaseApp(slotName: String): FirebaseApp? {
        return try {
            val defaultApp = FirebaseApp.getApps(appContext)
                .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                ?: FirebaseApp.initializeApp(appContext)
                ?: return null
            if (slotName.isBlank() || slotName == FirebaseApp.DEFAULT_APP_NAME) {
                defaultApp
            } else {
                FirebaseApp.getApps(appContext).firstOrNull { it.name == slotName }
                    ?: FirebaseApp.initializeApp(appContext, defaultApp.options, slotName)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to resolve FirebaseApp for slot '$slotName'", t)
            null
        }
    }

    fun resolveAuth(slotName: String = ""): FirebaseAuth? {
        return runCatching {
            resolveFirebaseApp(slotName)?.let { FirebaseAuth.getInstance(it) }
        }.getOrNull()
    }

    fun resolveFirestore(slotName: String = ""): FirebaseFirestore? {
        val dbId = databaseIdOrNull() ?: return null
        return runCatching {
            resolveFirebaseApp(slotName)?.let { FirebaseFirestore.getInstance(it, dbId) }
        }.getOrNull()
    }

    fun allocateSlotForAccount(uid: String? = null): String {
        val existing = uid?.let { id -> _savedAccounts.value.firstOrNull { it.uid == id } }
        if (existing != null) return existing.slotName
        val defaultInUse = _savedAccounts.value.any { it.slotName.isBlank() }
        return if (!defaultInUse) "" else "starmax_acct_${System.currentTimeMillis()}"
    }

    fun saveAccount(record: SavedAccountRecord, makeActive: Boolean = true) {
        val updated = (_savedAccounts.value.filterNot { it.uid == record.uid } + record)
        persistAccounts(updated)
        if (makeActive) {
            persistActiveUid(record.uid)
        }
    }

    fun updateAccountProfile(
        uid: String,
        username: String,
        fullName: String,
        avatarData: String,
        verified: Boolean
    ) {
        val current = _savedAccounts.value
        val existing = current.firstOrNull { it.uid == uid } ?: return
        val next = existing.copy(
            username = username.ifBlank { existing.username },
            fullName = fullName.ifBlank { existing.fullName },
            avatarData = avatarData,
            verified = existing.verified || verified
        )
        persistAccounts(current.map { if (it.uid == uid) next else it })
    }

    fun switchToSavedAccount(uid: String): Boolean {
        val record = _savedAccounts.value.firstOrNull { it.uid == uid } ?: return false
        val auth = resolveAuth(record.slotName) ?: return false
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
            runCatching { resolveAuth(active.slotName)?.signOut() }
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
        all.forEach { rec ->
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

    private fun loadSavedAccounts(): List<SavedAccountRecord> {
        val raw = prefs.getString(KEY_SAVED_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val uid = obj.optString("uid")
                    if (uid.isBlank()) continue
                    add(
                        SavedAccountRecord(
                            uid = uid,
                            slotName = obj.optString("slotName"),
                            username = obj.optString("username"),
                            fullName = obj.optString("fullName"),
                            avatarData = obj.optString("avatarData"),
                            phoneE164 = obj.optString("phoneE164"),
                            verified = obj.optBoolean("verified", false),
                            authProvider = obj.optString("authProvider", "phone")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistAccounts(accounts: List<SavedAccountRecord>) {
        val arr = JSONArray()
        accounts.forEach { rec ->
            arr.put(
                JSONObject().apply {
                    put("uid", rec.uid)
                    put("slotName", rec.slotName)
                    put("username", rec.username)
                    put("fullName", rec.fullName)
                    put("avatarData", rec.avatarData.take(250_000))
                    put("phoneE164", rec.phoneE164)
                    put("verified", rec.verified)
                    put("authProvider", rec.authProvider)
                }
            )
        }
        prefs.edit().putString(KEY_SAVED_ACCOUNTS, arr.toString()).apply()
        _savedAccounts.value = accounts
    }

    private companion object {
        const val TAG = "AccountSessionMgr"
        const val PREFS_NAME = "starmax_saved_accounts"
        const val KEY_SAVED_ACCOUNTS = "saved_accounts_json"
        const val KEY_ACTIVE_UID = "active_uid"
    }
}
