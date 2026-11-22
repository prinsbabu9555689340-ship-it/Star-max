package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.AccountSessionManager
import com.example.ui.AppNavigation
import com.example.ui.theme.StarMaxTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean

data class CrashReport(
    val exceptionName: String,
    val message: String,
    val stackTrace: String,
    val threadName: String
)

object AppCrashReporter {
    private val _crashReport = MutableStateFlow<CrashReport?>(null)
    val crashReport: StateFlow<CrashReport?> = _crashReport.asStateFlow()

    fun report(throwable: Throwable, threadName: String = Thread.currentThread().name): CrashReport {
        val report = formatThrowable(throwable, threadName)
        _crashReport.value = report
        return report
    }

    fun setReport(report: CrashReport) {
        _crashReport.value = report
    }

    fun clear() {
        _crashReport.value = null
    }

    fun formatThrowable(throwable: Throwable, threadName: String): CrashReport {
        val rootCause = generateSequence(throwable) { it.cause }.lastOrNull() ?: throwable
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stack = sw.toString().take(4000)
        val excName = rootCause::class.java.simpleName.ifBlank { rootCause::class.java.name }
        val rawMsg = rootCause.localizedMessage
            ?: throwable.localizedMessage
            ?: rootCause.message
            ?: throwable.message
            ?: "An unexpected error occurred."
        return CrashReport(
            exceptionName = excName,
            message = rawMsg,
            stackTrace = stack,
            threadName = threadName
        )
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installGlobalCrashHandler(applicationContext ?: this)

        // Restore crash report if relaunched after a main-thread uncaught exception
        val incomingMessage = intent?.getStringExtra(EXTRA_CRASH_MESSAGE)
        if (!incomingMessage.isNullOrBlank()) {
            AppCrashReporter.setReport(
                CrashReport(
                    exceptionName = intent.getStringExtra(EXTRA_CRASH_TYPE).orEmpty().ifBlank { "Error" },
                    message = incomingMessage,
                    stackTrace = intent.getStringExtra(EXTRA_CRASH_STACK).orEmpty(),
                    threadName = intent.getStringExtra(EXTRA_CRASH_THREAD).orEmpty().ifBlank { "main" }
                )
            )
            intent.removeExtra(EXTRA_CRASH_MESSAGE)
        }

        // Safely warm up Firebase services without ever crashing on launch
        runCatching { FirebaseApp.initializeApp(this) }
            .onFailure { Log.w(TAG, "FirebaseApp.initializeApp skipped", it) }
            .getOrNull()
        runCatching { FirebaseAuth.getInstance() }
            .onFailure { Log.w(TAG, "FirebaseAuth.getInstance skipped", it) }
            .getOrNull()
        runCatching {
            val dbId = getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)
            AccountSessionManager.configureFirestoreOnline(db, "default::$dbId")
            db
        }
            .onFailure { Log.w(TAG, "FirebaseFirestore.getInstance skipped", it) }
            .getOrNull()

        runCatching { enableEdgeToEdge() }
            .onFailure { Log.w(TAG, "enableEdgeToEdge skipped", it) }

        try {
            setContent {
                val activeCrash by AppCrashReporter.crashReport.collectAsStateWithLifecycle()
                val currentReport = activeCrash
                if (currentReport != null) {
                    StarMaxTheme {
                        CrashRecoveryScreen(
                            report = currentReport,
                            onRetry = {
                                AppCrashReporter.clear()
                                isRelaunchingFromCrash.set(false)
                            }
                        )
                    }
                } else {
                    AppNavigation()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed during setContent initialization", t)
            val report = AppCrashReporter.report(t)
            runCatching {
                setContent {
                    StarMaxTheme {
                        CrashRecoveryScreen(
                            report = report,
                            onRetry = {
                                AppCrashReporter.clear()
                                recreate()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun installGlobalCrashHandler(appContext: Context) {
        if (!crashHandlerInstalled.compareAndSet(false, true)) return
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
            val report = AppCrashReporter.report(throwable, thread.name)
            val isMainThread = thread == Looper.getMainLooper().thread
            val isRobolectric = Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

            if (isMainThread && !isRobolectric && isRelaunchingFromCrash.compareAndSet(false, true)) {
                val relaunched = runCatching {
                    val recoveryIntent = Intent(appContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        putExtra(EXTRA_CRASH_TYPE, report.exceptionName)
                        putExtra(EXTRA_CRASH_MESSAGE, report.message)
                        putExtra(EXTRA_CRASH_STACK, report.stackTrace)
                        putExtra(EXTRA_CRASH_THREAD, report.threadName)
                    }
                    appContext.startActivity(recoveryIntent)
                    true
                }.getOrDefault(false)

                if (!relaunched) {
                    previousHandler?.uncaughtException(thread, throwable)
                }
            } else if (isMainThread && !isRobolectric) {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val EXTRA_CRASH_TYPE = "extra_crash_type"
        private const val EXTRA_CRASH_MESSAGE = "extra_crash_message"
        private const val EXTRA_CRASH_STACK = "extra_crash_stack"
        private const val EXTRA_CRASH_THREAD = "extra_crash_thread"
        private val crashHandlerInstalled = AtomicBoolean(false)
        private val isRelaunchingFromCrash = AtomicBoolean(false)
    }
}

@Composable
private fun CrashRecoveryScreen(
    report: CrashReport,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    var copied by remember(report) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.crash_screen_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.crash_screen_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("crash_error_message")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${report.exceptionName} (${report.threadName})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = report.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (report.stackTrace.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = report.stackTrace.lines().take(12).joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val details = buildString {
                                    appendLine("${report.exceptionName} on ${report.threadName}")
                                    appendLine(report.message)
                                    if (report.stackTrace.isNotBlank()) {
                                        appendLine()
                                        append(report.stackTrace)
                                    }
                                }
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Star Max Error", details))
                                copied = true
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("crash_copy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (copied) {
                                stringResource(R.string.crash_screen_copied)
                            } else {
                                stringResource(R.string.crash_screen_copy)
                            },
                            maxLines = 1
                        )
                    }
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("crash_retry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.crash_screen_retry))
                    }
                }
            }
        }
    }
}


