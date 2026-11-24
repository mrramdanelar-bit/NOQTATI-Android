package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ai.SmartGradeScanner
import com.example.data.local.GradeEntity
import com.example.data.local.StudentEntity
import com.example.data.local.SubjectEntity
import com.example.data.repository.formatScore
import com.example.domain.AverageCalculator
import com.example.ui.NoqtatiUiState
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.NoqtatiSectionHeader
import com.example.ui.components.ScorePillBadge
import com.example.ui.components.SelectorDropdownField

private enum class GradesSection(val label: String) {
    ENTRY("إدخال العلامات"),
    SUBJECTS("المواد والمعاملات")
}

private val SubjectPalette = listOf(
    Color(0xFF0F6B5E),
    Color(0xFF39556B),
    Color(0xFFB77B1E),
    Color(0xFF2E7D32),
    Color(0xFF6B4C8A),
    Color(0xFFAD4E3E)
)

@Composable
fun GradesAndSubjectsScreen(
    uiState: NoqtatiUiState,
    onAddSubject: (name: String, coeff: Double, maxScore: Double, colorIndex: Int) -> Unit,
    onUpdateSubject: (SubjectEntity) -> Unit,
    onDeleteSubject: (SubjectEntity) -> Unit,
    onAddPresetSubjects: () -> Unit,
    onSaveGrade: (
        studentId: Long,
        subjectId: Long,
        semesterId: Long,
        continuous: Double?,
        test: Double?,
        exam: Double?,
        directFinal: Double?,
        maxScore: Double,
        remark: String
    ) -> Unit,
    onDeleteGrade: (studentId: Long, subjectId: Long, semesterId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSection by remember { mutableStateOf(GradesSection.ENTRY) }
    var selectedClassId by remember(uiState.classes) {
        mutableStateOf(uiState.classes.firstOrNull()?.id)
    }
    var selectedSubjectId by remember(uiState.subjects) {
        mutableStateOf(uiState.subjects.firstOrNull()?.id)
    }
    var selectedSemesterId by remember(uiState.semesters) {
        mutableStateOf(uiState.activeSemester?.id)
    }

    var showSubjectDialog by remember { mutableStateOf(false) }
    var editingSubject by remember { mutableStateOf<SubjectEntity?>(null) }
    var deletingSubject by remember { mutableStateOf<SubjectEntity?>(null) }

    var editingGradeStudent by remember { mutableStateOf<StudentEntity?>(null) }

    val selectedClass = uiState.classes.firstOrNull { it.id == selectedClassId } ?: uiState.classes.firstOrNull()
    val selectedSubject = uiState.subjects.firstOrNull { it.id == selectedSubjectId } ?: uiState.subjects.firstOrNull()
    val selectedSemester = uiState.semesters.firstOrNull { it.id == selectedSemesterId } ?: uiState.activeSemester

    val classStudents = remember(uiState.students, selectedClass) {
        uiState.students
            .filter { it.classId == selectedClass?.id }
            .sortedWith(compareBy({ it.lastName }, { it.firstName }))
    }

    val gradesByStudent = remember(uiState.grades, selectedSubject, selectedSemester) {
        uiState.grades
            .filter { it.subjectId == selectedSubject?.id && it.semesterId == selectedSemester?.id }
            .associateBy { it.studentId }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (activeSection == GradesSection.SUBJECTS) {
                FloatingActionButton(
                    onClick = {
                        editingSubject = null
                        showSubjectDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_add_subject")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "إضافة مادة")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                NoqtatiSectionHeader(
                    title = "المواد وإدخال العلامات",
                    subtitle = "تنظيم المعاملات والفصول وحساب علامة المادة آلياً"
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradesSection.entries.forEach { sec ->
                        FilterChip(
                            selected = activeSection == sec,
                            onClick = { activeSection = sec },
                            label = { Text(sec.label) },
                            modifier = Modifier.testTag("grades_tab_${sec.name.lowercase()}")
                        )
                    }
                }
            }

            if (activeSection == GradesSection.SUBJECTS) {
                item {
                    FilledTonalButton(
                        onClick = onAddPresetSubjects,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("load_preset_subjects_button")
                    ) {
                        Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("إضافة المواد الدراسية الجزائرية القياسية تلقائياً")
                    }
                }

                if (uiState.subjects.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.MenuBook,
                            title = "لا توجد مواد دراسية مضافة",
                            description = "أضف المواد الدراسية مع معاملاتها (مثل الرياضيات معامل 4، اللغة العربية معامل 5) أو استخدم الزر أعلاه.",
                            actionLabel = "إضافة مادة يدوياً",
                            onAction = {
                                editingSubject = null
                                showSubjectDialog = true
                            }
                        )
                    }
                } else {
                    items(uiState.subjects, key = { it.id }) { subject ->
                        SubjectCardItem(
                            subject = subject,
                            onEdit = {
                                editingSubject = subject
                                showSubjectDialog = true
                            },
                            onDelete = { deletingSubject = subject }
                        )
                    }
                }
            } else {
                // Grade Entry Section
                if (uiState.classes.isEmpty() || uiState.subjects.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.EditNote,
                            title = "أكمل إعداد الأقسام والمواد أولاً",
                            description = "لإدخال العلامات، تأكد من وجود قسم واحد على الأقل وتلاميذ ومادة دراسية واحدة.",
                            actionLabel = if (uiState.subjects.isEmpty()) "إضافة المواد القياسية" else null,
                            onAction = if (uiState.subjects.isEmpty()) onAddPresetSubjects else null
                        )
                    }
                } else {
                    // Filter selectors
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
                                        label = "القسم",
                                        items = uiState.classes,
                                        selectedItem = selectedClass,
                                        itemLabel = { it.name },
                                        onItemSelected = { selectedClassId = it.id },
                                        modifier = Modifier.weight(1f)
                                    )
                                    SelectorDropdownField(
                                        label = "الفصل",
                                        items = uiState.semesters,
                                        selectedItem = selectedSemester,
                                        itemLabel = { it.name },
                                        onItemSelected = { selectedSemesterId = it.id },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                SelectorDropdownField(
                                    label = "المادة الدراسية",
                                    items = uiState.subjects,
                                    selectedItem = selectedSubject,
                                    itemLabel = { "${it.name} (المعامل ${formatScore(it.coefficient)} • /${formatScore(it.maxScore)})" },
                                    onItemSelected = { selectedSubjectId = it.id }
                                )
                            }
                        }
                    }

                    if (classStudents.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Rounded.EditNote,
                                title = "لا يوجد تلاميذ في هذا القسم",
                                description = "انتقل إلى تبويب الأقسام لإضافة تلاميذ قسم «${selectedClass?.name ?: ""}»."
                            )
                        }
                    } else {
                        val gradedCount = classStudents.count { gradesByStudent.containsKey(it.id) }
                        item {
                            Text(
                                text = "تلاميذ القسم (${gradedCount}/${classStudents.size} تم إدخال علاماتهم) — اضغط على التلميذ لإدخال أو تعديل العلامة:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(classStudents, key = { it.id }) { student ->
                            val grade = gradesByStudent[student.id]
                            StudentGradeRowCard(
                                student = student,
                                grade = grade,
                                maxScore = selectedSubject?.maxScore ?: 20.0,
                                onClick = { editingGradeStudent = student }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Subject Dialog
    if (showSubjectDialog) {
        SubjectEditorDialog(
            initial = editingSubject,
            onDismiss = { showSubjectDialog = false },
            onSave = { name, coeff, maxScore, colorIdx ->
                val curr = editingSubject
                if (curr == null) {
                    onAddSubject(name, coeff, maxScore, colorIdx)
                } else {
                    onUpdateSubject(
                        curr.copy(name = name, coefficient = coeff, maxScore = maxScore, colorIndex = colorIdx)
                    )
                }
                showSubjectDialog = false
            }
        )
    }

    // Confirm Delete Subject
    deletingSubject?.let { subj ->
        ConfirmDeleteDialog(
            title = "حذف مادة «${subj.name}»؟",
            message = "سيتم حذف المادة وجميع العلامات المسجلة فيها لكل الأقسام.",
            onConfirm = {
                onDeleteSubject(subj)
                deletingSubject = null
            },
            onDismiss = { deletingSubject = null }
        )
    }

    // Grade Entry / Edit Dialog
    val targetStudent = editingGradeStudent
    if (targetStudent != null && selectedSubject != null && selectedSemester != null) {
        val existingGrade = uiState.grades.firstOrNull {
            it.studentId == targetStudent.id &&
                it.subjectId == selectedSubject.id &&
                it.semesterId == selectedSemester.id
        }
        GradeEntryDialog(
            student = targetStudent,
            subject = selectedSubject,
            semesterName = selectedSemester.name,
            existingGrade = existingGrade,
            onDismiss = { editingGradeStudent = null },
            onDelete = if (existingGrade != null) {
                {
                    onDeleteGrade(targetStudent.id, selectedSubject.id, selectedSemester.id)
                    editingGradeStudent = null
                }
            } else {
                null
            },
            onSave = { cont, test, exam, directFinal, remark ->
                onSaveGrade(
                    targetStudent.id,
                    selectedSubject.id,
                    selectedSemester.id,
                    cont,
                    test,
                    exam,
                    directFinal,
                    selectedSubject.maxScore,
                    remark
                )
                editingGradeStudent = null
            }
        )
    }
}

@Composable
private fun SubjectCardItem(
    subject: SubjectEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = SubjectPalette[subject.colorIndex.mod(SubjectPalette.size)]
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("subject_card_${subject.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatScore(subject.coefficient),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subject.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "المعامل: ${formatScore(subject.coefficient)} • التنقيط على ${formatScore(subject.maxScore)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "تعديل المادة")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "حذف المادة",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StudentGradeRowCard(
    student: StudentEntity,
    grade: GradeEntity?,
    maxScore: Double,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("grade_row_student_${student.id}")
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val details = if (grade != null) {
                    val parts = buildList {
                        grade.continuousScore?.let { add("تقويم: ${formatScore(it)}") }
                        grade.testScore?.let { add("فرض: ${formatScore(it)}") }
                        grade.examScore?.let { add("اختبار: ${formatScore(it)}") }
                    }
                    if (parts.isNotEmpty()) {
                        parts.joinToString(" • ")
                    } else {
                        grade.remark.ifBlank { AverageCalculator.remarkForSubjectScore(grade.finalScore, maxScore) }
                    }
                } else {
                    "رقم التسجيل: ${student.registrationNumber} • اضغط لإدخال العلامة"
                }
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            ScorePillBadge(score = grade?.finalScore, maxScore = maxScore)
        }
    }
}

@Composable
private fun SubjectEditorDialog(
    initial: SubjectEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, coeff: Double, maxScore: Double, colorIdx: Int) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var coeffText by remember { mutableStateOf(initial?.coefficient?.let { formatScore(it) } ?: "2") }
    var maxScoreText by remember { mutableStateOf(initial?.maxScore?.let { formatScore(it) } ?: "20") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة مادة دراسية" else "تعديل المادة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text("اسم المادة (مثال: الرياضيات)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_subject_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = coeffText,
                        onValueChange = { coeffText = it; errorText = null },
                        label = { Text("المعامل") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_subject_coeff_input")
                    )
                    OutlinedTextField(
                        value = maxScoreText,
                        onValueChange = { maxScoreText = it; errorText = null },
                        label = { Text("العلامة القصوى") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val coeff = SmartGradeScanner.normalizeDigits(coeffText).replace(',', '.').toDoubleOrNull()
                    val maxScore = SmartGradeScanner.normalizeDigits(maxScoreText).replace(',', '.').toDoubleOrNull()
                    when {
                        name.isBlank() -> errorText = "يرجى إدخال اسم المادة"
                        coeff == null || coeff <= 0.0 -> errorText = "المعامل يجب أن يكون رقماً أكبر من 0"
                        maxScore == null || maxScore <= 0.0 || maxScore > 100.0 -> errorText = "العلامة القصوى غير صالحة"
                        else -> onSave(name, coeff, maxScore, initial?.colorIndex ?: (name.hashCode().and(0x7fffffff) % SubjectPalette.size))
                    }
                },
                modifier = Modifier.testTag("dialog_subject_save_button")
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun GradeEntryDialog(
    student: StudentEntity,
    subject: SubjectEntity,
    semesterName: String,
    existingGrade: GradeEntity?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (cont: Double?, test: Double?, exam: Double?, directFinal: Double?, remark: String) -> Unit
) {
    val hasBreakdownInitially = existingGrade?.continuousScore != null ||
        existingGrade?.testScore != null ||
        existingGrade?.examScore != null

    var useFormulaMode by remember { mutableStateOf(hasBreakdownInitially) }
    var directScoreText by remember {
        mutableStateOf(existingGrade?.finalScore?.let { formatScore(it) } ?: "")
    }
    var continuousText by remember {
        mutableStateOf(existingGrade?.continuousScore?.let { formatScore(it) } ?: "")
    }
    var testText by remember {
        mutableStateOf(existingGrade?.testScore?.let { formatScore(it) } ?: "")
    }
    var examText by remember {
        mutableStateOf(existingGrade?.examScore?.let { formatScore(it) } ?: "")
    }
    var remarkText by remember { mutableStateOf(existingGrade?.remark ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun parseScore(raw: String): Double? {
        if (raw.isBlank()) return null
        return SmartGradeScanner.normalizeDigits(raw).replace(',', '.').trim().toDoubleOrNull()
    }

    val previewComputed = remember(useFormulaMode, continuousText, testText, examText) {
        if (!useFormulaMode) return@remember null
        val c = parseScore(continuousText)
        val t = parseScore(testText)
        val e = parseScore(examText)
        when {
            c != null && t != null && e != null -> AverageCalculator.round2((c + t + e * 2.0) / 4.0)
            t != null && e != null -> AverageCalculator.round2((t + e * 2.0) / 3.0)
            c != null && e != null -> AverageCalculator.round2((c + e * 2.0) / 3.0)
            else -> listOfNotNull(c, t, e).takeIf { it.isNotEmpty() }?.let { AverageCalculator.round2(it.average()) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(student.fullName, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${subject.name} • $semesterName (/${formatScore(subject.maxScore)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "حساب مفصل (تقويم + فرض + اختبار)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Switch(
                        checked = useFormulaMode,
                        onCheckedChange = { useFormulaMode = it; validationError = null }
                    )
                }

                if (!useFormulaMode) {
                    OutlinedTextField(
                        value = directScoreText,
                        onValueChange = { directScoreText = it; validationError = null },
                        label = { Text("العلامة النهائية (0 - ${formatScore(subject.maxScore)})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_direct_score_input")
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = continuousText,
                            onValueChange = { continuousText = it; validationError = null },
                            label = { Text("التقويم") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = testText,
                            onValueChange = { testText = it; validationError = null },
                            label = { Text("الفرض") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = examText,
                        onValueChange = { examText = it; validationError = null },
                        label = { Text("الاختبار (×2)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (previewComputed != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("المعدل المحسوب للمادة:", style = MaterialTheme.typography.labelLarge)
                            ScorePillBadge(score = previewComputed, maxScore = subject.maxScore)
                        }
                    }
                }

                OutlinedTextField(
                    value = remarkText,
                    onValueChange = { remarkText = it },
                    label = { Text("ملاحظة الأستاذ (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text("مسح هذه العلامة", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val max = subject.maxScore
                    if (!useFormulaMode) {
                        val v = parseScore(directScoreText)
                        if (v == null || v < 0.0 || v > max) {
                            validationError = "يرجى إدخال علامة صالحة بين 0 و ${formatScore(max)}"
                        } else {
                            onSave(null, null, null, v, remarkText)
                        }
                    } else {
                        val c = parseScore(continuousText)
                        val t = parseScore(testText)
                        val e = parseScore(examText)
                        val vals = listOfNotNull(c, t, e)
                        when {
                            vals.isEmpty() -> validationError = "أدخل علامة واحدة على الأقل"
                            vals.any { it < 0.0 || it > max } -> validationError = "جميع العلامات يجب أن تكون بين 0 و ${formatScore(max)}"
                            else -> onSave(c, t, e, null, remarkText)
                        }
                    }
                },
                modifier = Modifier.testTag("dialog_grade_save_button")
            ) {
                Text("حفظ العلامة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
