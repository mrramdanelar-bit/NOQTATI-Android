package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.SemesterEntity
import com.example.data.preferences.ThemeMode
import com.example.ui.NoqtatiUiState
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.NoqtatiSectionHeader

@Composable
fun SettingsBackupScreen(
    uiState: NoqtatiUiState,
    onUpdateTheme: (ThemeMode) -> Unit,
    onUpdateProfile: (teacherName: String, schoolName: String, wilaya: String, academicYear: String) -> Unit,
    onToggleAiOcr: (Boolean) -> Unit,
    onAddSemester: (name: String, year: String) -> Unit,
    onSetActiveSemester: (Long) -> Unit,
    onDeleteSemester: (SemesterEntity) -> Unit,
    onExportBackupToUri: (Uri) -> Unit,
    onShareBackup: () -> Unit,
    onRestoreBackupFromUri: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = uiState.settings
    var teacherName by remember(settings.teacherName) { mutableStateOf(settings.teacherName) }
    var schoolName by remember(settings.schoolName) { mutableStateOf(settings.schoolName) }
    var wilaya by remember(settings.wilaya) { mutableStateOf(settings.wilaya) }
    var academicYear by remember(settings.defaultAcademicYear) { mutableStateOf(settings.defaultAcademicYear) }

    var showAddSemesterDialog by remember { mutableStateOf(false) }
    var deletingSemester by remember { mutableStateOf<SemesterEntity?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onExportBackupToUri(uri)
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            NoqtatiSectionHeader(
                title = "الإعدادات والنسخ الاحتياطي",
                subtitle = "تخصيص بيانات الأستاذ، المظهر، الفصول وحماية قاعدة البيانات"
            )
        }

        // Teacher & School Info
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "معلومات الأستاذ والمؤسسة (تظهر في كشوف النقاط)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = teacherName,
                        onValueChange = { teacherName = it },
                        label = { Text("اسم الأستاذ") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_teacher_name_input")
                    )
                    OutlinedTextField(
                        value = schoolName,
                        onValueChange = { schoolName = it },
                        label = { Text("اسم المؤسسة التعليمية (متوسطة / ثانوية / ابتدائية)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = wilaya,
                            onValueChange = { wilaya = it },
                            label = { Text("الولاية") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = academicYear,
                            onValueChange = { academicYear = it },
                            label = { Text("السنة الدراسية") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = { onUpdateProfile(teacherName, schoolName, wilaya, academicYear) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_save_profile_button")
                    ) {
                        Text("حفظ معلومات الأستاذ")
                    }
                }
            }
        }

        // Theme Mode
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "المظهر والوضع الليلي",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onUpdateTheme(mode) },
                                label = { Text(mode.labelAr, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // Semesters Management
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الفصول الدراسية",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FilledTonalButton(
                            onClick = { showAddSemesterDialog = true },
                            modifier = Modifier.testTag("add_semester_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("فصل جديد")
                        }
                    }

                    uiState.semesters.forEach { sem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sem.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (sem.isActive) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = if (sem.isActive) "الفصل النشط حالياً • ${sem.academicYear}" else sem.academicYear,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (sem.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!sem.isActive) {
                                TextButton(onClick = { onSetActiveSemester(sem.id) }) {
                                    Text("تفعيل")
                                }
                            } else {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = "نشط",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                            if (uiState.semesters.size > 1) {
                                IconButton(onClick = { deletingSemester = sem }) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "حذف الفصل",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }

        // Smart AI OCR Security Settings
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "التصوير الذكي بالذكاء الاصطناعي عند الاتصال",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "يعمل عبر Firebase AI Logic المحمي بـ App Check دون تخزين أي مفتاح API داخل الهاتف.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = settings.aiOcrEnabled,
                            onCheckedChange = onToggleAiOcr,
                            modifier = Modifier.testTag("settings_ai_ocr_switch")
                        )
                    }
                }
            }
        }

        // Local Database Backup & Restore
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "النسخ الاحتياطي والاستعادة المحلية",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "جميع البيانات محفوظة محلياً في قاعدة بيانات Room (${uiState.classes.size} قسم • ${uiState.students.size} تلميذ • ${uiState.grades.size} علامة). يمكنك حفظ نسخة احتياطية JSON واستعادتها في أي وقت.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { createBackupLauncher.launch("noqtati_backup.json") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_save_file_button")
                    ) {
                        Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("إنشاء وحفظ نسخة احتياطية في الهاتف")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onShareBackup,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("backup_share_button")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("مشاركة النسخة")
                        }
                        OutlinedButton(
                            onClick = { openBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("backup_restore_button")
                        ) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("استعادة نسخة")
                        }
                    }
                }
            }
        }
    }

    // Add Semester Dialog
    if (showAddSemesterDialog) {
        var semName by remember { mutableStateOf("") }
        var semYear by remember { mutableStateOf(settings.defaultAcademicYear) }
        AlertDialog(
            onDismissRequest = { showAddSemesterDialog = false },
            title = { Text("إضافة فصل دراسي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = semName,
                        onValueChange = { semName = it },
                        label = { Text("اسم الفصل (مثال: الفصل الاستدراكي)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = semYear,
                        onValueChange = { semYear = it },
                        label = { Text("السنة الدراسية") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddSemester(semName, semYear)
                        showAddSemesterDialog = false
                    },
                    enabled = semName.isNotBlank()
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSemesterDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // Confirm Delete Semester
    deletingSemester?.let { sem ->
        ConfirmDeleteDialog(
            title = "حذف «${sem.name}»؟",
            message = "سيتم حذف هذا الفصل الدراسي وجميع العلامات المرتبطة به.",
            onConfirm = {
                onDeleteSemester(sem)
                deletingSemester = null
            },
            onDismiss = { deletingSemester = null }
        )
    }

    // Confirm Restore Backup
    pendingRestoreUri?.let { uri ->
        ConfirmDeleteDialog(
            title = "استعادة النسخة الاحتياطية؟",
            message = "ستؤدي استعادة النسخة الاحتياطية إلى استبدال جميع الأقسام والتلاميذ والعلامات الحالية بالبيانات الموجودة في الملف المختار.",
            confirmLabel = "تأكيد الاستعادة",
            onConfirm = {
                onRestoreBackupFromUri(uri)
                pendingRestoreUri = null
            },
            onDismiss = { pendingRestoreUri = null }
        )
    }
}
