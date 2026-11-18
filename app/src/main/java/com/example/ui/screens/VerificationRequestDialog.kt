package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.firebase.MediaUtils
import com.example.data.model.UserProfile
import com.example.data.model.UserSettings
import com.example.ui.components.StarMaxMediaImage
import com.example.ui.theme.LocalStarMaxColors
import kotlinx.coroutines.launch

private val VERIFICATION_CATEGORIES = listOf(
    "Creator",
    "Public Figure",
    "Gamer",
    "Business",
    "Artist",
    "Journalist",
    "Athlete"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequestVerificationDialog(
    profile: UserProfile,
    userSettings: UserSettings,
    userPhone: String,
    isVerificationPending: Boolean = false,
    onSubmitRequest: (
        fullName: String,
        knownAs: String,
        category: String,
        link: String,
        idProofData: String,
        onDone: () -> Unit
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extended = LocalStarMaxColors.current

    var fullName by remember(profile.fullName, userSettings.verificationFullName) {
        mutableStateOf(userSettings.verificationFullName.ifBlank { profile.fullName })
    }
    var knownAs by remember(userSettings.verificationKnownAs, profile.username) {
        mutableStateOf(userSettings.verificationKnownAs.ifBlank { profile.username })
    }
    var selectedCategory by remember(userSettings.verificationCategory) {
        mutableStateOf(userSettings.verificationCategory.ifBlank { "Creator" })
    }
    var linkOrPhone by remember(userSettings.verificationLink, userPhone, profile.website) {
        mutableStateOf(
            userSettings.verificationLink.ifBlank {
                profile.website.ifBlank { userPhone }
            }
        )
    }
    var idProofData by remember(userSettings.verificationIdProofData) {
        mutableStateOf(userSettings.verificationIdProofData)
    }
    var localSubmittedPending by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val effectiveStatus = when {
        profile.effectiveVerified -> "verified"
        isVerificationPending || localSubmittedPending || userSettings.verificationStatus == "pending" -> "pending"
        else -> "none"
    }

    val idProofPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val encoded = MediaUtils.encodeImageUriToDataUri(
                    context = context,
                    uri = uri,
                    maxDimension = 720,
                    quality = 72
                )
                if (encoded != null) {
                    idProofData = encoded
                    validationError = null
                } else {
                    validationError = "Could not read selected image. Please try another photo."
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("verification_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Star Max Verified",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Verified badge",
                        tint = extended.verifiedBlue,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(22.dp)
                    )
                }
                HorizontalDivider(color = extended.subtleDivider, thickness = 0.5.dp)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Status Card
                    Surface(
                        color = when (effectiveStatus) {
                            "verified" -> extended.verifiedBlue.copy(alpha = 0.12f)
                            "pending" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = BorderStroke(
                            1.dp,
                            when (effectiveStatus) {
                                "verified" -> extended.verifiedBlue.copy(alpha = 0.4f)
                                "pending" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                else -> extended.subtleDivider
                            }
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verification_status_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (effectiveStatus) {
                                            "verified" -> extended.verifiedBlue.copy(alpha = 0.18f)
                                            "pending" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (effectiveStatus) {
                                        "verified" -> Icons.Filled.Verified
                                        "pending" -> Icons.Filled.HourglassTop
                                        else -> Icons.Filled.Badge
                                    },
                                    contentDescription = "Verification status",
                                    tint = when (effectiveStatus) {
                                        "verified" -> extended.verifiedBlue
                                        "pending" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (effectiveStatus) {
                                        "verified" -> "Verified"
                                        "pending" -> "Under Review"
                                        else -> "Request Blue Tick"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = when (effectiveStatus) {
                                        "verified" -> extended.verifiedBlue
                                        "pending" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when (effectiveStatus) {
                                        "verified" -> "@${profile.username} has the Star Max Blue Tick displayed across posts, comments, DMs, and search."
                                        "pending" -> "Your Blue Tick request is under review for @${profile.username}. Once approved, the Blue Tick badge appears across Star Max."
                                        else -> "Verified accounts show a crisp blue checkmark next to their username across Star Max."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        text = "Step 1 • Confirm Authenticity",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = {
                            fullName = it
                            validationError = null
                        },
                        label = { Text("Full Name") },
                        placeholder = { Text("Your legal or full creator name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verification_fullname_input")
                    )

                    OutlinedTextField(
                        value = knownAs,
                        onValueChange = { knownAs = it },
                        label = { Text("Known As (Optional)") },
                        placeholder = { Text("Stage name, brand, or @${profile.username}") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verification_knownas_input")
                    )

                    Text(
                        text = "Step 2 • Category",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        VERIFICATION_CATEGORIES.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category) },
                                modifier = Modifier.testTag("verification_category_${category.lowercase().replace(" ", "_")}")
                            )
                        }
                    }

                    Text(
                        text = "Step 3 • ID Proof & Link",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, extended.subtleDivider),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (idProofData.isNotBlank()) Icons.Filled.CheckCircle else Icons.Filled.UploadFile,
                                    contentDescription = null,
                                    tint = if (idProofData.isNotBlank()) extended.onlineGreen else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (idProofData.isNotBlank()) "ID Proof Selected" else "Upload Government or Creator ID",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Stored privately on your account for verification review only",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (idProofData.isNotBlank()) {
                                StarMaxMediaImage(
                                    mediaSource = idProofData,
                                    contentDescription = "Selected ID proof preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    idProofPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("verification_id_proof_button")
                            ) {
                                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (idProofData.isNotBlank()) "Change ID Proof Photo" else "Select ID Proof Photo")
                            }
                        }
                    }

                    OutlinedTextField(
                        value = linkOrPhone,
                        onValueChange = {
                            linkOrPhone = it
                            validationError = null
                        },
                        label = { Text("Mobile Number or Social Link") },
                        placeholder = { Text("+91 mobile number or https://social-link") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verification_link_input")
                    )

                    if (validationError != null) {
                        Text(
                            text = validationError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = {
                            val cleanName = fullName.trim()
                            val cleanLink = linkOrPhone.trim()
                            if (cleanName.isEmpty()) {
                                validationError = "Please enter your full name."
                                return@Button
                            }
                            if (idProofData.isBlank() && cleanLink.isEmpty()) {
                                validationError = "Upload an ID proof photo or enter a mobile / social link."
                                return@Button
                            }
                            onSubmitRequest(
                                cleanName,
                                knownAs.trim(),
                                selectedCategory,
                                cleanLink,
                                idProofData
                            ) {
                                localSubmittedPending = true
                            }
                        },
                        enabled = effectiveStatus == "none",
                        colors = ButtonDefaults.buttonColors(
                            containerColor = extended.verifiedBlue,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("submit_verification_request_button")
                    ) {
                        Icon(
                            imageVector = if (effectiveStatus == "pending") Icons.Filled.HourglassTop else Icons.Filled.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (effectiveStatus) {
                                "verified" -> "Verified on Star Max"
                                "pending" -> "Under Review"
                                else -> "Submit Request"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
