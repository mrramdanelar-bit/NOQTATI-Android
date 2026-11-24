package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ExtractedGradeRow
import com.example.ai.ExtractionConfidence
import com.example.ai.ScanOutcome
import com.example.ai.SmartGradeScanner
import com.example.data.local.AcademicClassEntity
import com.example.data.local.GradeEntity
import com.example.data.local.NoqtatiDatabase
import com.example.data.local.SemesterEntity
import com.example.data.local.StudentEntity
import com.example.data.local.SubjectEntity
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.preferences.ThemeMode
import com.example.data.repository.ActionResult
import com.example.data.repository.NoqtatiRepository
import com.example.domain.ClassSemesterSummary
import com.example.domain.StudentSemesterReport
import com.example.export.BackupSerializer
import com.example.export.ExcelExportGenerator
import com.example.export.PdfReportGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "NoqtatiViewModel"

enum class AppDestination(val titleAr: String) {
    DASHBOARD("الرئيسية"),
    CLASSES("الأقسام"),
    GRADES("العلامات"),
    SCANNER("التصوير الذكي"),
    REPORTS("التقارير"),
    SETTINGS("الإعدادات")
}

data class ExportedFileArtifact(
    val file: File,
    val mimeType: String,
    val displayTitle: String,
    val suggestedFileName: String
)

data class NoqtatiUiState(
    val classes: List<AcademicClassEntity> = emptyList(),
    val students: List<StudentEntity> = emptyList(),
    val subjects: List<SubjectEntity> = emptyList(),
    val semesters: List<SemesterEntity> = emptyList(),
    val grades: List<GradeEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val isReady: Boolean = false
) {
    val activeSemester: SemesterEntity?
        get() = semesters.firstOrNull { it.isActive } ?: semesters.firstOrNull()
}

data class ScannerUiState(
    val selectedClassId: Long? = null,
    val selectedSubjectId: Long? = null,
    val selectedSemesterId: Long? = null,
    val imageUri: Uri? = null,
    val isAnalyzing: Boolean = false,
    val useOfflineMode: Boolean = false,
    val offlineInputText: String = "",
    val summaryNote: String = "",
    val errorMessage: String? = null,
    val usedAiOnline: Boolean = false,
    val extractedRows: List<ExtractedGradeRow> = emptyList()
)

class NoqtatiViewModel(application: Application) : AndroidViewModel(application) {

    private val database = NoqtatiDatabase.getInstance(application)
    private val settingsStore = SettingsDataStore(application)
    private val repository = NoqtatiRepository(database.noqtatiDao(), settingsStore)
    private val scanner = SmartGradeScanner()
    private val pdfGenerator = PdfReportGenerator(application)
    private val excelGenerator = ExcelExportGenerator(application)

    private val _currentDestination = MutableStateFlow(AppDestination.DASHBOARD)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    private val _openStudentsInClasses = MutableStateFlow(false)
    val openStudentsInClasses: StateFlow<Boolean> = _openStudentsInClasses.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _pendingExport = MutableStateFlow<ExportedFileArtifact?>(null)
    val pendingExport: StateFlow<ExportedFileArtifact?> = _pendingExport.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _scannerState = MutableStateFlow(ScannerUiState())
    val scannerState: StateFlow<ScannerUiState> = _scannerState.asStateFlow()

    val uiState: StateFlow<NoqtatiUiState> = combine(
        combine(
            repository.classes,
            repository.students,
            repository.subjects
        ) { classes, students, subjects -> Triple(classes, students, subjects) },
        combine(
            repository.semesters,
            repository.grades,
            repository.settings
        ) { semesters, grades, settings -> Triple(semesters, grades, settings) }
    ) { left, right ->
        NoqtatiUiState(
            classes = left.first,
            students = left.second,
            subjects = left.third,
            semesters = right.first,
            grades = right.second,
            settings = right.third,
            isReady = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NoqtatiUiState()
    )

    init {
        viewModelScope.launch {
            try {
                repository.ensureDefaultSemesters()
            } catch (e: Exception) {
                Log.e(TAG, "Failed ensuring default semesters", e)
            }
        }
    }

    fun navigateTo(destination: AppDestination, focusGlobalStudents: Boolean = false) {
        _openStudentsInClasses.value = focusGlobalStudents
        _currentDestination.value = destination
    }

    fun clearGlobalStudentsFocus() {
        _openStudentsInClasses.value = false
    }

    fun dismissExportSheet() {
        _pendingExport.value = null
    }

    private fun emitResult(result: ActionResult) {
        when (result) {
            is ActionResult.Success -> _messages.tryEmit(result.message)
            is ActionResult.Error -> _messages.tryEmit(result.message)
        }
    }

    // --- Class CRUD ---
    fun addClass(name: String, academicYear: String, level: String) {
        viewModelScope.launch {
            emitResult(repository.addClass(name, academicYear, level))
        }
    }

    fun updateClass(entity: AcademicClassEntity) {
        viewModelScope.launch {
            emitResult(repository.updateClass(entity))
        }
    }

    fun deleteClass(entity: AcademicClassEntity) {
        viewModelScope.launch {
            emitResult(repository.deleteClass(entity))
        }
    }

    // --- Student CRUD ---
    fun addStudent(
        classId: Long,
        firstName: String,
        lastName: String,
        registrationNumber: String,
        birthDate: String = "",
        notes: String = ""
    ) {
        viewModelScope.launch {
            emitResult(
                repository.addStudent(classId, firstName, lastName, registrationNumber, birthDate, notes)
            )
        }
    }

    fun updateStudent(student: StudentEntity) {
        viewModelScope.launch {
            emitResult(repository.updateStudent(student))
        }
    }

    fun deleteStudent(student: StudentEntity) {
        viewModelScope.launch {
            emitResult(repository.deleteStudent(student))
        }
    }

    fun transferStudent(studentId: Long, targetClassId: Long) {
        viewModelScope.launch {
            emitResult(repository.transferStudent(studentId, targetClassId))
        }
    }

    // --- Subject CRUD ---
    fun addSubject(name: String, coefficient: Double, maxScore: Double, colorIndex: Int) {
        viewModelScope.launch {
            emitResult(repository.addSubject(name, coefficient, maxScore, colorIndex))
        }
    }

    fun updateSubject(subject: SubjectEntity) {
        viewModelScope.launch {
            emitResult(repository.updateSubject(subject))
        }
    }

    fun deleteSubject(subject: SubjectEntity) {
        viewModelScope.launch {
            emitResult(repository.deleteSubject(subject))
        }
    }

    fun addPresetSubjects() {
        viewModelScope.launch {
            emitResult(repository.addPresetAlgerianSubjects())
        }
    }

    // --- Semester CRUD ---
    fun addSemester(name: String, academicYear: String) {
        viewModelScope.launch {
            emitResult(repository.addSemester(name, academicYear))
        }
    }

    fun setActiveSemester(semesterId: Long) {
        viewModelScope.launch {
            repository.setActiveSemester(semesterId)
            _messages.tryEmit("تم تفعيل الفصل الدراسي المختار")
        }
    }

    fun deleteSemester(semester: SemesterEntity) {
        viewModelScope.launch {
            emitResult(repository.deleteSemester(semester))
        }
    }

    // --- Grades CRUD ---
    fun saveGrade(
        studentId: Long,
        subjectId: Long,
        semesterId: Long,
        continuousScore: Double?,
        testScore: Double?,
        examScore: Double?,
        directFinalScore: Double?,
        maxScore: Double,
        remark: String
    ) {
        viewModelScope.launch {
            emitResult(
                repository.saveGrade(
                    studentId = studentId,
                    subjectId = subjectId,
                    semesterId = semesterId,
                    continuousScore = continuousScore,
                    testScore = testScore,
                    examScore = examScore,
                    directFinalScore = directFinalScore,
                    maxScore = maxScore,
                    remark = remark
                )
            )
        }
    }

    fun deleteGrade(studentId: Long, subjectId: Long, semesterId: Long) {
        viewModelScope.launch {
            emitResult(repository.deleteGrade(studentId, subjectId, semesterId))
        }
    }

    // --- Settings ---
    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.updateThemeMode(mode)
        }
    }

    fun updateProfile(teacherName: String, schoolName: String, wilaya: String, academicYear: String) {
        viewModelScope.launch {
            repository.updateProfile(teacherName, schoolName, wilaya, academicYear)
            _messages.tryEmit("تم حفظ معلومات الأستاذ والمؤسسة")
        }
    }

    fun updateAiOcrEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAiOcrEnabled(enabled)
            _scannerState.update { it.copy(useOfflineMode = !enabled) }
        }
    }

    // --- Smart OCR + AI Scanner ---
    fun selectScannerClass(classId: Long) {
        _scannerState.update {
            it.copy(selectedClassId = classId, extractedRows = emptyList(), errorMessage = null, summaryNote = "")
        }
    }

    fun selectScannerSubject(subjectId: Long) {
        _scannerState.update { it.copy(selectedSubjectId = subjectId, errorMessage = null) }
    }

    fun selectScannerSemester(semesterId: Long) {
        _scannerState.update { it.copy(selectedSemesterId = semesterId, errorMessage = null) }
    }

    fun setScannerImageUri(uri: Uri?) {
        _scannerState.update { it.copy(imageUri = uri, errorMessage = null) }
    }

    fun setScannerOfflineMode(offline: Boolean) {
        _scannerState.update { it.copy(useOfflineMode = offline, errorMessage = null) }
    }

    fun updateOfflineInputText(text: String) {
        _scannerState.update { it.copy(offlineInputText = text) }
    }

    fun clearScannerResults() {
        _scannerState.update {
            it.copy(extractedRows = emptyList(), summaryNote = "", errorMessage = null)
        }
    }

    fun analyzeCapturedImageWithAi() {
        val state = uiState.value
        val scan = _scannerState.value
        val classId = scan.selectedClassId ?: state.classes.firstOrNull()?.id
        val subjectId = scan.selectedSubjectId ?: state.subjects.firstOrNull()?.id
        val semesterId = scan.selectedSemesterId ?: state.activeSemester?.id

        if (classId == null || subjectId == null || semesterId == null) {
            _scannerState.update {
                it.copy(errorMessage = "يرجى إضافة وتحديد القسم والمادة والفصل الدراسي أولاً.")
            }
            return
        }
        val uri = scan.imageUri
        if (uri == null) {
            _scannerState.update {
                it.copy(errorMessage = "يرجى التقاط صورة لكشف النقاط أو اختيارها من المعرض أولاً.")
            }
            return
        }

        val appCtx = getApplication<Application>()
        if (!SmartGradeScanner.isNetworkAvailable(appCtx)) {
            _scannerState.update {
                it.copy(
                    useOfflineMode = true,
                    errorMessage = "لا يوجد اتصال بالإنترنت حالياً. تم التحويل تلقائياً إلى الوضع الأساسي دون إنترنت."
                )
            }
            return
        }

        val roster = state.students.filter { it.classId == classId }
        val subject = state.subjects.firstOrNull { it.id == subjectId } ?: return

        viewModelScope.launch {
            _scannerState.update {
                it.copy(
                    selectedClassId = classId,
                    selectedSubjectId = subjectId,
                    selectedSemesterId = semesterId,
                    isAnalyzing = true,
                    errorMessage = null
                )
            }
            try {
                val bitmap = SmartGradeScanner.loadScaledBitmap(appCtx, uri)
                if (bitmap == null) {
                    _scannerState.update {
                        it.copy(isAnalyzing = false, errorMessage = "تعذر قراءة ملف الصورة المختار.")
                    }
                    return@launch
                }

                when (val outcome = scanner.scanImageWithGemini(bitmap, roster, subject.name, subject.maxScore)) {
                    is ScanOutcome.Success -> {
                        _scannerState.update {
                            it.copy(
                                isAnalyzing = false,
                                extractedRows = outcome.rows,
                                summaryNote = outcome.summaryNote,
                                usedAiOnline = outcome.usedAiOnline,
                                errorMessage = null
                            )
                        }
                    }
                    is ScanOutcome.Error -> {
                        _scannerState.update {
                            it.copy(
                                isAnalyzing = false,
                                errorMessage = outcome.message
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in analyzeCapturedImageWithAi", e)
                _scannerState.update {
                    it.copy(
                        isAnalyzing = false,
                        errorMessage = e.localizedMessage ?: "حدث خطأ غير متوقع أثناء التحليل."
                    )
                }
            }
        }
    }

    fun analyzeOfflineTextInput() {
        val state = uiState.value
        val scan = _scannerState.value
        val classId = scan.selectedClassId ?: state.classes.firstOrNull()?.id
        val subjectId = scan.selectedSubjectId ?: state.subjects.firstOrNull()?.id
        val semesterId = scan.selectedSemesterId ?: state.activeSemester?.id

        if (classId == null || subjectId == null || semesterId == null) {
            _scannerState.update { it.copy(errorMessage = "يرجى تحديد القسم والمادة والفصل أولاً.") }
            return
        }

        val roster = state.students.filter { it.classId == classId }
        val subject = state.subjects.firstOrNull { it.id == subjectId } ?: return
        when (val outcome = scanner.parseOfflineTextToRows(scan.offlineInputText, roster, subject.maxScore)) {
            is ScanOutcome.Success -> {
                _scannerState.update {
                    it.copy(
                        selectedClassId = classId,
                        selectedSubjectId = subjectId,
                        selectedSemesterId = semesterId,
                        extractedRows = outcome.rows,
                        summaryNote = outcome.summaryNote,
                        usedAiOnline = false,
                        errorMessage = null
                    )
                }
            }
            is ScanOutcome.Error -> {
                _scannerState.update { it.copy(errorMessage = outcome.message) }
            }
        }
    }

    fun loadOfflineRosterReviewTable() {
        val state = uiState.value
        val scan = _scannerState.value
        val classId = scan.selectedClassId ?: state.classes.firstOrNull()?.id
        val subjectId = scan.selectedSubjectId ?: state.subjects.firstOrNull()?.id
        val semesterId = scan.selectedSemesterId ?: state.activeSemester?.id

        if (classId == null || subjectId == null || semesterId == null) {
            _scannerState.update { it.copy(errorMessage = "يرجى تحديد القسم والمادة والفصل أولاً.") }
            return
        }
        val roster = state.students.filter { it.classId == classId }
        if (roster.isEmpty()) {
            _scannerState.update { it.copy(errorMessage = "لا يوجد تلاميذ مسجلون في هذا القسم بعد.") }
            return
        }
        val templateRows = scanner.buildOfflineRosterTemplate(roster)
        _scannerState.update {
            it.copy(
                selectedClassId = classId,
                selectedSubjectId = subjectId,
                selectedSemesterId = semesterId,
                extractedRows = templateRows,
                summaryNote = "الوضع الأساسي دون إنترنت: أدخل أو صحّح العلامات بمحاذاة صورة الكشف ثم أكد الحفظ.",
                usedAiOnline = false,
                errorMessage = null
            )
        }
    }

    fun updateExtractedRowScore(rowId: String, newScoreText: String) {
        _scannerState.update { state ->
            state.copy(
                extractedRows = state.extractedRows.map { row ->
                    if (row.rowId == rowId) {
                        val cleaned = SmartGradeScanner.normalizeDigits(newScoreText)
                        val validNum = cleaned.replace(',', '.').toDoubleOrNull()
                        val updatedIssue = if (validNum != null && validNum in 0.0..20.0 && row.matchedStudentId != null) {
                            ""
                        } else {
                            row.issueNote
                        }
                        row.copy(
                            scoreText = newScoreText,
                            issueNote = updatedIssue,
                            confidence = if (updatedIssue.isBlank() && row.matchedStudentId != null) {
                                ExtractionConfidence.HIGH
                            } else {
                                row.confidence
                            }
                        )
                    } else {
                        row
                    }
                }
            )
        }
    }

    fun updateExtractedRowStudent(rowId: String, studentId: Long?) {
        _scannerState.update { state ->
            state.copy(
                extractedRows = state.extractedRows.map { row ->
                    if (row.rowId == rowId) {
                        row.copy(
                            matchedStudentId = studentId,
                            issueNote = if (studentId != null && row.parsedScore() != null) "" else row.issueNote,
                            confidence = if (studentId != null && row.parsedScore() != null) {
                                ExtractionConfidence.HIGH
                            } else {
                                ExtractionConfidence.MEDIUM
                            }
                        )
                    } else {
                        row
                    }
                }
            )
        }
    }

    fun toggleExtractedRowSelected(rowId: String) {
        _scannerState.update { state ->
            state.copy(
                extractedRows = state.extractedRows.map { row ->
                    if (row.rowId == rowId) row.copy(isSelected = !row.isSelected) else row
                }
            )
        }
    }

    fun removeExtractedRow(rowId: String) {
        _scannerState.update { state ->
            state.copy(extractedRows = state.extractedRows.filterNot { it.rowId == rowId })
        }
    }

    fun confirmAndSaveExtractedGrades() {
        val state = uiState.value
        val scan = _scannerState.value
        val subjectId = scan.selectedSubjectId ?: state.subjects.firstOrNull()?.id
        val semesterId = scan.selectedSemesterId ?: state.activeSemester?.id
        val subject = state.subjects.firstOrNull { it.id == subjectId }

        if (subject == null || semesterId == null) {
            _messages.tryEmit("يرجى تحديد المادة والفصل الدراسي قبل الحفظ")
            return
        }

        val selectedRows = scan.extractedRows.filter { it.isSelected }
        if (selectedRows.isEmpty()) {
            _messages.tryEmit("يرجى تحديد صف واحد على الأقل للحفظ")
            return
        }

        val validRows = selectedRows.mapNotNull { row ->
            val sid = row.matchedStudentId ?: return@mapNotNull null
            val score = row.parsedScore(subject.maxScore) ?: return@mapNotNull null
            sid to score
        }

        if (validRows.isEmpty()) {
            _messages.tryEmit("لا توجد صفوف صالحة (تأكد من ربط كل صف بتلميذ وإدخال علامة بين 0 و ${subject.maxScore.toInt()})")
            return
        }

        viewModelScope.launch {
            var savedCount = 0
            validRows.forEach { (studentId, score) ->
                val res = repository.saveGrade(
                    studentId = studentId,
                    subjectId = subject.id,
                    semesterId = semesterId,
                    continuousScore = null,
                    testScore = null,
                    examScore = null,
                    directFinalScore = score,
                    maxScore = subject.maxScore,
                    remark = ""
                )
                if (res is ActionResult.Success) savedCount++
            }
            val skipped = selectedRows.size - savedCount
            val msg = if (skipped > 0) {
                "تم تأكيد وحفظ $savedCount علامة (تم تجاهل $skipped صف غير مكتمل)"
            } else {
                "تم تأكيد وحفظ $savedCount علامة بنجاح في قاعدة البيانات"
            }
            _messages.tryEmit(msg)
            _scannerState.update {
                it.copy(extractedRows = emptyList(), summaryNote = "", errorMessage = null)
            }
        }
    }

    // --- Reports & Export (PDF / Excel) ---
    fun exportStudentBulletinPdf(report: StudentSemesterReport, summary: ClassSemesterSummary) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val file = pdfGenerator.generateStudentBulletinPdf(
                    report = report,
                    summary = summary,
                    settings = uiState.value.settings
                )
                _pendingExport.value = ExportedFileArtifact(
                    file = file,
                    mimeType = "application/pdf",
                    displayTitle = "كشف نقاط ${report.student.fullName}",
                    suggestedFileName = "كشف_نقاط_${report.student.lastName}_${report.student.firstName}.pdf"
                )
            } catch (e: Exception) {
                Log.e(TAG, "PDF generation failed", e)
                _messages.tryEmit("تعذر إنشاء ملف PDF: ${e.localizedMessage}")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun exportClassGradesPdf(summary: ClassSemesterSummary) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val file = pdfGenerator.generateClassGradesPdf(
                    summary = summary,
                    settings = uiState.value.settings
                )
                _pendingExport.value = ExportedFileArtifact(
                    file = file,
                    mimeType = "application/pdf",
                    displayTitle = "جدول علامات قسم ${summary.academicClass.name}",
                    suggestedFileName = "جدول_${summary.academicClass.name}_${summary.semester.name}.pdf"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Class PDF generation failed", e)
                _messages.tryEmit("تعذر إنشاء جدول PDF: ${e.localizedMessage}")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun exportClassGradesExcel(summary: ClassSemesterSummary) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val file = excelGenerator.generateClassGradesXlsx(
                    summary = summary,
                    settings = uiState.value.settings
                )
                _pendingExport.value = ExportedFileArtifact(
                    file = file,
                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    displayTitle = "ملف Excel لقسم ${summary.academicClass.name}",
                    suggestedFileName = "علامات_${summary.academicClass.name}_${summary.semester.name}.xlsx"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Excel generation failed", e)
                _messages.tryEmit("تعذر إنشاء ملف Excel: ${e.localizedMessage}")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun saveArtifactToUserUri(artifact: ExportedFileArtifact, targetUri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(targetUri)?.use { out ->
                        artifact.file.inputStream().use { input -> input.copyTo(out) }
                    }
                }
                _messages.tryEmit("تم حفظ الملف بنجاح على الهاتف")
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving artifact to URI", e)
                _messages.tryEmit("تعذر حفظ الملف: ${e.localizedMessage}")
            }
        }
    }

    // --- Backup & Restore ---
    fun exportBackupToUri(targetUri: Uri) {
        viewModelScope.launch {
            try {
                val snapshot = repository.captureBackupSnapshot()
                val json = BackupSerializer.toJson(snapshot)
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(targetUri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
                _messages.tryEmit("تم حفظ النسخة الاحتياطية بنجاح")
            } catch (e: Exception) {
                Log.e(TAG, "Backup export failed", e)
                _messages.tryEmit("فشل إنشاء النسخة الاحتياطية: ${e.localizedMessage}")
            }
        }
    }

    fun prepareShareableBackup() {
        viewModelScope.launch {
            try {
                val snapshot = repository.captureBackupSnapshot()
                val json = BackupSerializer.toJson(snapshot)
                val file = withContext(Dispatchers.IO) {
                    val dir = File(getApplication<Application>().filesDir, "backups").apply { mkdirs() }
                    val f = File(dir, "noqtati_backup_${System.currentTimeMillis()}.json")
                    f.writeText(json, Charsets.UTF_8)
                    f
                }
                _pendingExport.value = ExportedFileArtifact(
                    file = file,
                    mimeType = "application/json",
                    displayTitle = "نسخة احتياطية NOQTATI",
                    suggestedFileName = "noqtati_backup.json"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Shareable backup failed", e)
                _messages.tryEmit("تعذر تحضير النسخة الاحتياطية: ${e.localizedMessage}")
            }
        }
    }

    fun restoreBackupFromUri(sourceUri: Uri) {
        viewModelScope.launch {
            try {
                val jsonText = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(sourceUri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                }
                if (jsonText.isNullOrBlank()) {
                    _messages.tryEmit("ملف النسخة الاحتياطية فارغ أو غير قابل للقراءة")
                    return@launch
                }
                val snapshot = BackupSerializer.fromJson(jsonText)
                emitResult(repository.restoreBackupSnapshot(snapshot))
            } catch (e: Exception) {
                Log.e(TAG, "Backup restore failed", e)
                _messages.tryEmit("فشلت استعادة النسخة الاحتياطية: ${e.localizedMessage}")
            }
        }
    }
}
