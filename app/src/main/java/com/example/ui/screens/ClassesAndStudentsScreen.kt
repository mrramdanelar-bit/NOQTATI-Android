package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AcademicClassEntity
import com.example.data.local.StudentEntity
import com.example.ui.NoqtatiUiState
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.NoqtatiSectionHeader
import com.example.ui.components.SelectorDropdownField

private enum class ClassesTabMode(val label: String) {
    CLASSES("الأقسام"),
    ALL_STUDENTS("كل التلاميذ")
}

@Composable
fun ClassesAndStudentsScreen(
    uiState: NoqtatiUiState,
    initialFocusStudents: Boolean,
    onConsumeStudentsFocus: () -> Unit,
    onAddClass: (name: String, year: String, level: String) -> Unit,
    onUpdateClass: (AcademicClassEntity) -> Unit,
    onDeleteClass: (AcademicClassEntity) -> Unit,
    onAddStudent: (classId: Long, firstName: String, lastName: String, regNum: String, birthDate: String, notes: String) -> Unit,
    onUpdateStudent: (StudentEntity) -> Unit,
    onDeleteStudent: (StudentEntity) -> Unit,
    onTransferStudent: (studentId: Long, targetClassId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var tabMode by remember { mutableStateOf(ClassesTabMode.CLASSES) }
    var selectedClassId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    var showClassDialog by remember { mutableStateOf(false) }
    var editingClass by remember { mutableStateOf<AcademicClassEntity?>(null) }
    var deletingClass by remember { mutableStateOf<AcademicClassEntity?>(null) }

    var showStudentDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var deletingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var transferringStudent by remember { mutableStateOf<StudentEntity?>(null) }

    LaunchedEffect(initialFocusStudents) {
        if (initialFocusStudents) {
            tabMode = ClassesTabMode.ALL_STUDENTS
            selectedClassId = null
            onConsumeStudentsFocus()
        }
    }

    val selectedClass = uiState.classes.firstOrNull { it.id == selectedClassId }
    val classMap = remember(uiState.classes) { uiState.classes.associateBy { it.id } }

    BackHandler(enabled = selectedClass != null) {
        selectedClassId = null
        searchQuery = ""
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (selectedClass != null || tabMode == ClassesTabMode.ALL_STUDENTS) {
                if (uiState.classes.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            editingStudent = null
                            showStudentDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_add_student")
                    ) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = "إضافة تلميذ")
                    }
                }
            } else {
                FloatingActionButton(
                    onClick = {
                        editingClass = null
                        showClassDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_add_class")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "إضافة قسم")
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
            // Mode Switcher or Class Detail Back Header
            item {
                if (selectedClass != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                selectedClassId = null
                                searchQuery = ""
                            },
                            modifier = Modifier.testTag("back_to_classes_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع للأقسام")
                        }
                        Spacer(Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "قسم ${selectedClass.name}",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "${selectedClass.level} • السنة الدراسية ${selectedClass.academicYear}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    NoqtatiSectionHeader(
                        title = "إدارة الأقسام والتلاميذ",
                        subtitle = "تنظيم الأقسام، البحث، ونقل التلاميذ بين الأقسام"
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ClassesTabMode.entries.forEach { mode ->
                            FilterChip(
                                selected = tabMode == mode,
                                onClick = {
                                    tabMode = mode
                                    searchQuery = ""
                                },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("classes_tab_${mode.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            if (selectedClass == null && tabMode == ClassesTabMode.CLASSES) {
                // Classes List View
                if (uiState.classes.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Class,
                            title = "لا توجد أقسام مسجلة بعد",
                            description = "أضف أقسامك الدراسية (مثل: 1 متوسط 1 أو 3 ثانوي علوم) مع تحديد السنة الدراسية.",
                            actionLabel = "إضافة قسم جديد",
                            onAction = {
                                editingClass = null
                                showClassDialog = true
                            }
                        )
                    }
                } else {
                    items(uiState.classes, key = { it.id }) { academicClass ->
                        val count = uiState.students.count { it.classId == academicClass.id }
                        ClassCardItem(
                            academicClass = academicClass,
                            studentCount = count,
                            onOpen = {
                                selectedClassId = academicClass.id
                                searchQuery = ""
                            },
                            onEdit = {
                                editingClass = academicClass
                                showClassDialog = true
                            },
                            onDelete = { deletingClass = academicClass }
                        )
                    }
                }
            } else {
                // Students List (either for selectedClass or All Students)
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("ابحث بالاسم أو اللقب أو رقم التسجيل...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_search_input")
                    )
                }

                val baseStudents = if (selectedClass != null) {
                    uiState.students.filter { it.classId == selectedClass.id }
                } else {
                    uiState.students
                }

                val filtered = if (searchQuery.isBlank()) {
                    baseStudents
                } else {
                    val q = searchQuery.trim().lowercase()
                    baseStudents.filter { s ->
                        s.firstName.lowercase().contains(q) ||
                            s.lastName.lowercase().contains(q) ||
                            s.fullName.lowercase().contains(q) ||
                            s.registrationNumber.lowercase().contains(q)
                    }
                }

                if (uiState.classes.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Class,
                            title = "أضف قسماً أولاً قبل تسجيل التلاميذ",
                            description = "كل تلميذ يرتبط بقسم دراسي ويمكن نقله لاحقاً إلى أي قسم آخر.",
                            actionLabel = "إنشاء قسم",
                            onAction = {
                                tabMode = ClassesTabMode.CLASSES
                                showClassDialog = true
                            }
                        )
                    }
                } else if (filtered.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Groups,
                            title = if (searchQuery.isBlank()) "لا يوجد تلاميذ في هذه القائمة" else "لا توجد نتائج مطابقة للبحث",
                            description = if (searchQuery.isBlank()) {
                                "اضغط على زر الإضافة لتسجيل التلاميذ بالاسم واللقب ورقم التسجيل."
                            } else {
                                "جرّب البحث باسم آخر أو برقم التسجيل."
                            },
                            actionLabel = if (searchQuery.isBlank()) "إضافة تلميذ" else null,
                            onAction = if (searchQuery.isBlank()) {
                                {
                                    editingStudent = null
                                    showStudentDialog = true
                                }
                            } else {
                                null
                            }
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { student ->
                        StudentCardItem(
                            student = student,
                            className = classMap[student.classId]?.name ?: "قسم غير محدد",
                            showClassTag = selectedClass == null,
                            canTransfer = uiState.classes.size > 1,
                            onEdit = {
                                editingStudent = student
                                showStudentDialog = true
                            },
                            onTransfer = { transferringStudent = student },
                            onDelete = { deletingStudent = student }
                        )
                    }
                }
            }
        }
    }

    // Add/Edit Class Dialog
    if (showClassDialog) {
        ClassEditorDialog(
            initial = editingClass,
            defaultYear = uiState.settings.defaultAcademicYear,
            onDismiss = { showClassDialog = false },
            onSave = { name, year, level ->
                val current = editingClass
                if (current == null) {
                    onAddClass(name, year, level)
                } else {
                    onUpdateClass(current.copy(name = name, academicYear = year, level = level))
                }
                showClassDialog = false
            }
        )
    }

    // Confirm Delete Class
    deletingClass?.let { cls ->
        val count = uiState.students.count { it.classId == cls.id }
        ConfirmDeleteDialog(
            title = "حذف القسم «${cls.name}»؟",
            message = "سيتم حذف القسم وجميع تلاميذه ($count تلميذ) وعلاماتهم المرتبطة به بشكل نهائي.",
            onConfirm = {
                onDeleteClass(cls)
                if (selectedClassId == cls.id) selectedClassId = null
                deletingClass = null
            },
            onDismiss = { deletingClass = null }
        )
    }

    // Add/Edit Student Dialog
    if (showStudentDialog && uiState.classes.isNotEmpty()) {
        val defaultClassId = editingStudent?.classId ?: selectedClass?.id ?: uiState.classes.first().id
        StudentEditorDialog(
            initial = editingStudent,
            classes = uiState.classes,
            defaultClassId = defaultClassId,
            onDismiss = { showStudentDialog = false },
            onSave = { classId, firstName, lastName, regNum, birthDate, notes ->
                val current = editingStudent
                if (current == null) {
                    onAddStudent(classId, firstName, lastName, regNum, birthDate, notes)
                } else {
                    onUpdateStudent(
                        current.copy(
                            classId = classId,
                            firstName = firstName,
                            lastName = lastName,
                            registrationNumber = regNum,
                            birthDate = birthDate,
                            notes = notes
                        )
                    )
                }
                showStudentDialog = false
            }
        )
    }

    // Confirm Delete Student
    deletingStudent?.let { stu ->
        ConfirmDeleteDialog(
            title = "حذف التلميذ «${stu.fullName}»؟",
            message = "سيتم حذف ملف التلميذ وجميع علاماته المسجلة ولا يمكن التراجع عن ذلك إلا باستعادة نسخة احتياطية.",
            onConfirm = {
                onDeleteStudent(stu)
                deletingStudent = null
            },
            onDismiss = { deletingStudent = null }
        )
    }

    // Transfer Student Dialog
    transferringStudent?.let { stu ->
        TransferStudentDialog(
            student = stu,
            availableClasses = uiState.classes.filter { it.id != stu.classId },
            onConfirm = { targetClassId ->
                onTransferStudent(stu.id, targetClassId)
                transferringStudent = null
            },
            onDismiss = { transferringStudent = null }
        )
    }
}

@Composable
private fun ClassCardItem(
    academicClass: AcademicClassEntity,
    studentCount: Int,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("class_card_${academicClass.id}")
            .clickable { onOpen() },
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
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Class,
                    contentDescription = academicClass.name,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = academicClass.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$studentCount تلميذ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = "${academicClass.level} • ${academicClass.academicYear}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "تعديل القسم")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "حذف القسم",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StudentCardItem(
    student: StudentEntity,
    className: String,
    showClassTag: Boolean,
    canTransfer: Boolean,
    onEdit: () -> Unit,
    onTransfer: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("student_card_${student.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = student.lastName.take(1).ifBlank { "ت" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "رقم التسجيل: ${student.registrationNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (showClassTag) {
                    Spacer(Modifier.height(3.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = className,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (canTransfer) {
                IconButton(onClick = onTransfer) {
                    Icon(
                        Icons.Rounded.SwapHoriz,
                        contentDescription = "نقل لقسم آخر",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "تعديل التلميذ")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "حذف التلميذ",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ClassEditorDialog(
    initial: AcademicClassEntity?,
    defaultYear: String,
    onDismiss: () -> Unit,
    onSave: (name: String, year: String, level: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var year by remember { mutableStateOf(initial?.academicYear ?: defaultYear) }
    var level by remember { mutableStateOf(initial?.level ?: "متوسط") }
    val levels = listOf("ابتدائي", "متوسط", "ثانوي")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة قسم جديد" else "تعديل بيانات القسم") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم القسم (مثال: 4 متوسط 1)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_class_name_input")
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("السنة الدراسية (مثال: 2025/2026)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("الطور التعليمي:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    levels.forEach { lvl ->
                        FilterChip(
                            selected = level == lvl,
                            onClick = { level = lvl },
                            label = { Text(lvl) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, year, level) },
                enabled = name.isNotBlank() && year.isNotBlank(),
                modifier = Modifier.testTag("dialog_class_save_button")
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
private fun StudentEditorDialog(
    initial: StudentEntity?,
    classes: List<AcademicClassEntity>,
    defaultClassId: Long,
    onDismiss: () -> Unit,
    onSave: (classId: Long, firstName: String, lastName: String, regNum: String, birthDate: String, notes: String) -> Unit
) {
    var selectedClass by remember {
        mutableStateOf(classes.firstOrNull { it.id == defaultClassId } ?: classes.first())
    }
    var lastName by remember { mutableStateOf(initial?.lastName ?: "") }
    var firstName by remember { mutableStateOf(initial?.firstName ?: "") }
    var regNumber by remember { mutableStateOf(initial?.registrationNumber ?: "") }
    var birthDate by remember { mutableStateOf(initial?.birthDate ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة تلميذ جديد" else "تعديل بيانات التلميذ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectorDropdownField(
                    label = "القسم",
                    items = classes,
                    selectedItem = selectedClass,
                    itemLabel = { "${it.name} (${it.academicYear})" },
                    onItemSelected = { selectedClass = it }
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("اللقب *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_student_lastname_input")
                )
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("الاسم *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_student_firstname_input")
                )
                OutlinedTextField(
                    value = regNumber,
                    onValueChange = { regNumber = it },
                    label = { Text("رقم التسجيل / التعريف المدرسي *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_student_reg_input")
                )
                OutlinedTextField(
                    value = birthDate,
                    onValueChange = { birthDate = it },
                    label = { Text("تاريخ الميلاد (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات بيداغوجية (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(selectedClass.id, firstName, lastName, regNumber, birthDate, notes)
                },
                enabled = firstName.isNotBlank() && lastName.isNotBlank() && regNumber.isNotBlank(),
                modifier = Modifier.testTag("dialog_student_save_button")
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
private fun TransferStudentDialog(
    student: StudentEntity,
    availableClasses: List<AcademicClassEntity>,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var target by remember { mutableStateOf(availableClasses.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("نقل التلميذ لقسم آخر") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "اختر القسم الجديد لنقل التلميذ «${student.fullName}» مع الاحتفاظ بجميع علاماته:",
                    style = MaterialTheme.typography.bodyMedium
                )
                SelectorDropdownField(
                    label = "القسم المستهدف",
                    items = availableClasses,
                    selectedItem = target,
                    itemLabel = { "${it.name} (${it.academicYear})" },
                    onItemSelected = { target = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { target?.let { onConfirm(it.id) } },
                enabled = target != null,
                modifier = Modifier.testTag("confirm_transfer_button")
            ) {
                Text("تأكيد النقل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
