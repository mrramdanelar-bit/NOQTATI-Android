package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.ai.ExtractedGradeRow
import com.example.ai.ExtractionConfidence
import com.example.ai.SmartGradeScanner
import com.example.data.local.StudentEntity
import com.example.data.repository.formatScore
import com.example.export.FileShareHelper
import com.example.ui.NoqtatiUiState
import com.example.ui.ScannerUiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.NoqtatiSectionHeader
import com.example.ui.components.SelectorDropdownField
import com.example.ui.theme.GradeDanger
import com.example.ui.theme.GradeGood
import com.example.ui.theme.GradeWarning

@Composable
fun SmartScannerScreen(
    uiState: NoqtatiUiState,
    scannerState: ScannerUiState,
    onSelectClass: (Long) -> Unit,
    onSelectSubject: (Long) -> Unit,
    onSelectSemester: (Long) -> Unit,
    onImageSelected: (Uri?) -> Unit,
    onToggleOfflineMode: (Boolean) -> Unit,
    onOfflineTextChange: (String) -> Unit,
    onAnalyzeOnlineAi: () -> Unit,
    onAnalyzeOfflineText: () -> Unit,
    onLoadOfflineRosterTable: () -> Unit,
    onUpdateRowScore: (rowId: String, score: String) -> Unit,
    onUpdateRowStudent: (rowId: String, studentId: Long?) -> Unit,
    onToggleRowSelected: (rowId: String) -> Unit,
    onRemoveRow: (rowId: String) -> Unit,
    onConfirmAndSave: () -> Unit,
    onClearResults: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }

    val selectedClass = uiState.classes.firstOrNull { it.id == scannerState.selectedClassId }
        ?: uiState.classes.firstOrNull()
    val selectedSubject = uiState.subjects.firstOrNull { it.id == scannerState.selectedSubjectId }
        ?: uiState.subjects.firstOrNull()
    val selectedSemester = uiState.semesters.firstOrNull { it.id == scannerState.selectedSemesterId }
        ?: uiState.activeSemester

    val classRoster = remember(uiState.students, selectedClass) {
        uiState.students
            .filter { it.classId == selectedClass?.id }
            .sortedWith(compareBy({ it.lastName }, { it.firstName }))
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraUri != null) {
            onImageSelected(pendingCameraUri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPermissionDenied = false
            val uri = FileShareHelper.createCameraCaptureUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionDenied = true
        }
    }

    // Google Play Policy compliant zero-permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImageSelected(uri)
        }
    }

    val isOnlineNow = remember(scannerState.useOfflineMode, scannerState.imageUri) {
        SmartGradeScanner.isNetworkAvailable(context)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NoqtatiSectionHeader(
                title = "التصوير الذكي لكشوف النقاط",
                subtitle = "قراءة الأسماء والعلامات بالذكاء الاصطناعي مع المراجعة قبل الحفظ"
            )
        }

        if (uiState.classes.isEmpty() || uiState.subjects.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.DocumentScanner,
                    title = "جهّز القسم والمادة أولاً",
                    description = "لربط العلامات المستخرجة بالتلاميذ الصحيحين تلقائياً، أضف قسماً وتلاميذه ومادة دراسية واحدة على الأقل."
                )
            }
            return@LazyColumn
        }

        // Mode & Connectivity Banner
        item {
            val offlineActive = scannerState.useOfflineMode || !uiState.settings.aiOcrEnabled
            Surface(
                color = if (!offlineActive && isOnlineNow) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (!offlineActive && isOnlineNow) Icons.Rounded.CloudQueue else Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = if (!offlineActive && isOnlineNow) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                        Text(
                            text = if (!offlineActive && isOnlineNow) {
                                "الوضع الذكي AI متصل وآمن (عبر Firebase AI Logic)"
                            } else {
                                "الوضع الأساسي دون إنترنت (تحليل محلي ومراجعة جانبية)"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !scannerState.useOfflineMode,
                            onClick = { onToggleOfflineMode(false) },
                            label = { Text("الوضع الذكي (AI OCR)") },
                            modifier = Modifier.testTag("scanner_mode_ai")
                        )
                        FilterChip(
                            selected = scannerState.useOfflineMode,
                            onClick = { onToggleOfflineMode(true) },
                            label = { Text("الوضع الأساسي (دون إنترنت)") },
                            modifier = Modifier.testTag("scanner_mode_offline")
                        )
                    }
                }
            }
        }

        // Target Class / Subject / Semester Selection
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectorDropdownField(
                            label = "القسم المستهدف",
                            items = uiState.classes,
                            selectedItem = selectedClass,
                            itemLabel = { it.name },
                            onItemSelected = { onSelectClass(it.id) },
                            modifier = Modifier.weight(1f)
                        )
                        SelectorDropdownField(
                            label = "الفصل",
                            items = uiState.semesters,
                            selectedItem = selectedSemester,
                            itemLabel = { it.name },
                            onItemSelected = { onSelectSemester(it.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    SelectorDropdownField(
                        label = "المادة الدراسية",
                        items = uiState.subjects,
                        selectedItem = selectedSubject,
                        itemLabel = { "${it.name} (/${formatScore(it.maxScore)})" },
                        onItemSelected = { onSelectSubject(it.id) }
                    )
                    Text(
                        text = "عدد التلاميذ المسجلين في القسم المختار للمطابقة: ${classRoster.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Camera & Photo Picker Capture Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val hasCameraPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCameraPerm) {
                            val uri = FileShareHelper.createCameraCaptureUri(context)
                            pendingCameraUri = uri
                            takePictureLauncher.launch(uri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scanner_open_camera_button")
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("تصوير الكشف")
                }

                FilledTonalButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scanner_pick_gallery_button")
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("اختيار صورة")
                }
            }
        }

        if (cameraPermissionDenied) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "تم رفض إذن الكاميرا. يمكنك تفعيله من إعدادات الهاتف أو اختيار صورة محفوظة من المعرض.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Captured Image Preview
        if (scannerState.imageUri != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AsyncImage(
                            model = scannerState.imageUri,
                            contentDescription = "صورة كشف النقاط الملتقطة",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )

                        if (!scannerState.useOfflineMode) {
                            Button(
                                onClick = onAnalyzeOnlineAi,
                                enabled = !scannerState.isAnalyzing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("scanner_analyze_ai_button")
                            ) {
                                if (scannerState.isAnalyzing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("جاري قراءة الأسماء والعلامات بالذكاء الاصطناعي...")
                                } else {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("تحليل الصورة واستخراج العلامات (OCR + AI)")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Offline Basic Mode Card
        if (scannerState.useOfflineMode) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "أدوات الوضع الأساسي (يعمل دون إنترنت)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "الصق أو اكتب أسطر الكشف (مثال: بن علي محمد : 15.5) ليتم ربطها تلقائياً بتلاميذ القسم، أو افتح جدول المراجعة المباشر لمطابقة الصورة يدوياً.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = scannerState.offlineInputText,
                            onValueChange = onOfflineTextChange,
                            label = { Text("نص الكشف (اسم التلميذ : العلامة في كل سطر)") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scanner_offline_text_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onAnalyzeOfflineText,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scanner_parse_offline_button")
                            ) {
                                Text("تحليل النص محلياً")
                            }
                            OutlinedButton(
                                onClick = onLoadOfflineRosterTable,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scanner_load_roster_button")
                            ) {
                                Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("جدول القسم")
                            }
                        }
                    }
                }
            }
        }

        // Error Message Card (transparent error propagation + quick switch to offline mode)
        if (!scannerState.errorMessage.isNullOrBlank()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = scannerState.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        if (BuildConfig.DEBUG) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(
                                        ClipData.newPlainText(
                                            "NOQTATI AI diagnostics",
                                            scannerState.errorMessage.orEmpty()
                                        )
                                    )
                                    Toast.makeText(context, "تم نسخ تفاصيل التشخيص", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("نسخ تفاصيل الخطأ")
                            }
                        }
                        if (!scannerState.useOfflineMode) {
                            OutlinedButton(onClick = { onToggleOfflineMode(true) }) {
                                Text("التبديل إلى الوضع الأساسي دون إنترنت")
                            }
                        }
                    }
                }
            }
        }

        // Extracted Review Table
        if (scannerState.extractedRows.isNotEmpty()) {
            val maxScore = selectedSubject?.maxScore ?: 20.0
            val warningsCount = scannerState.extractedRows.count { it.hasWarning(maxScore) }
            val readyCount = scannerState.extractedRows.count {
                it.isSelected && it.matchedStudentId != null && it.parsedScore(maxScore) != null
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "مراجعة النتائج المستخرجة قبل الحفظ (${scannerState.extractedRows.size} صف)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        if (scannerState.summaryNote.isNotBlank()) {
                            Text(
                                text = scannerState.summaryNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        if (warningsCount > 0) {
                            Text(
                                text = "تنبيه: يوجد $warningsCount صف يحتوي على أرقام غير واضحة أو أسماء غير مطابقة — راجعها وعدّلها قبل التأكيد.",
                                style = MaterialTheme.typography.labelMedium,
                                color = GradeDanger
                            )
                        }
                    }
                }
            }

            items(scannerState.extractedRows, key = { it.rowId }) { row ->
                ExtractedRowReviewCard(
                    row = row,
                    roster = classRoster,
                    maxScore = maxScore,
                    onScoreChange = { onUpdateRowScore(row.rowId, it) },
                    onStudentSelected = { onUpdateRowStudent(row.rowId, it) },
                    onToggleSelected = { onToggleRowSelected(row.rowId) },
                    onRemove = { onRemoveRow(row.rowId) }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onClearResults,
                        modifier = Modifier.weight(0.35f)
                    ) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = onConfirmAndSave,
                        enabled = readyCount > 0,
                        modifier = Modifier
                            .weight(0.65f)
                            .testTag("scanner_confirm_save_button")
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تأكيد وحفظ ($readyCount علامة)")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtractedRowReviewCard(
    row: ExtractedGradeRow,
    roster: List<StudentEntity>,
    maxScore: Double,
    onScoreChange: (String) -> Unit,
    onStudentSelected: (Long?) -> Unit,
    onToggleSelected: () -> Unit,
    onRemove: () -> Unit
) {
    val hasIssue = row.hasWarning(maxScore)
    val borderColor = when {
        row.parsedScore(maxScore) == null || row.matchedStudentId == null -> GradeDanger
        row.confidence != ExtractionConfidence.HIGH -> GradeWarning
        else -> GradeGood.copy(alpha = 0.45f)
    }

    val matchedStudent = roster.firstOrNull { it.id == row.matchedStudentId }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.2.dp, borderColor, MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = row.isSelected,
                    onCheckedChange = { onToggleSelected() }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "المقروء: ${row.detectedName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = borderColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (row.issueNote.isNotBlank()) row.issueNote else row.confidence.labelAr,
                            style = MaterialTheme.typography.labelSmall,
                            color = borderColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "حذف الصف",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectorDropdownField(
                    label = "التلميذ المطابق",
                    items = roster,
                    selectedItem = matchedStudent,
                    itemLabel = { "${it.fullName} (${it.registrationNumber})" },
                    onItemSelected = { onStudentSelected(it.id) },
                    emptyPlaceholder = "اختر التلميذ...",
                    modifier = Modifier.weight(1.6f)
                )
                OutlinedTextField(
                    value = row.scoreText,
                    onValueChange = onScoreChange,
                    label = { Text("العلامة") },
                    isError = row.scoreText.isNotBlank() && row.parsedScore(maxScore) == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(0.9f)
                )
            }

            if (hasIssue && row.matchedStudentId == null) {
                Text(
                    text = "يرجى اختيار التلميذ الصحيح من القائمة أعلاه لربط العلامة.",
                    style = MaterialTheme.typography.labelSmall,
                    color = GradeDanger
                )
            }
        }
    }
}
