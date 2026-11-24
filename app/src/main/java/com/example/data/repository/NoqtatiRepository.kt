package com.example.data.repository

import com.example.data.local.AcademicClassEntity
import com.example.data.local.GradeEntity
import com.example.data.local.NoqtatiDao
import com.example.data.local.SemesterEntity
import com.example.data.local.StudentEntity
import com.example.data.local.SubjectEntity
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.preferences.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

sealed interface ActionResult {
    data class Success(val message: String) : ActionResult
    data class Error(val message: String) : ActionResult
}

data class FullBackupSnapshot(
    val exportedAt: Long,
    val settings: AppSettings,
    val classes: List<AcademicClassEntity>,
    val students: List<StudentEntity>,
    val subjects: List<SubjectEntity>,
    val semesters: List<SemesterEntity>,
    val grades: List<GradeEntity>
)

class NoqtatiRepository(
    private val dao: NoqtatiDao,
    private val settingsStore: SettingsDataStore
) {
    val classes: Flow<List<AcademicClassEntity>> = dao.observeAllClasses()
    val students: Flow<List<StudentEntity>> = dao.observeAllStudents()
    val subjects: Flow<List<SubjectEntity>> = dao.observeAllSubjects()
    val semesters: Flow<List<SemesterEntity>> = dao.observeAllSemesters()
    val grades: Flow<List<GradeEntity>> = dao.observeAllGrades()
    val settings: Flow<AppSettings> = settingsStore.settingsFlow

    suspend fun ensureDefaultSemesters() {
        val existing = dao.getAllSemestersOnce()
        if (existing.isEmpty()) {
            val year = settingsStore.settingsFlow.first().defaultAcademicYear
            dao.insertSemester(SemesterEntity(name = "الفصل الأول", orderIndex = 1, academicYear = year, isActive = true))
            dao.insertSemester(SemesterEntity(name = "الفصل الثاني", orderIndex = 2, academicYear = year, isActive = false))
            dao.insertSemester(SemesterEntity(name = "الفصل الثالث", orderIndex = 3, academicYear = year, isActive = false))
        }
    }

    suspend fun addPresetAlgerianSubjects(): ActionResult {
        val presets = listOf(
            SubjectEntity(name = "اللغة العربية", coefficient = 5.0, maxScore = 20.0, colorIndex = 0),
            SubjectEntity(name = "الرياضيات", coefficient = 4.0, maxScore = 20.0, colorIndex = 1),
            SubjectEntity(name = "العلوم الفيزيائية", coefficient = 2.0, maxScore = 20.0, colorIndex = 2),
            SubjectEntity(name = "علوم الطبيعة والحياة", coefficient = 2.0, maxScore = 20.0, colorIndex = 3),
            SubjectEntity(name = "اللغة الفرنسية", coefficient = 3.0, maxScore = 20.0, colorIndex = 4),
            SubjectEntity(name = "اللغة الإنجليزية", coefficient = 2.0, maxScore = 20.0, colorIndex = 5),
            SubjectEntity(name = "التاريخ والجغرافيا", coefficient = 3.0, maxScore = 20.0, colorIndex = 0),
            SubjectEntity(name = "التربية الإسلامية", coefficient = 2.0, maxScore = 20.0, colorIndex = 3),
            SubjectEntity(name = "التربية المدنية", coefficient = 1.0, maxScore = 20.0, colorIndex = 1),
            SubjectEntity(name = "المعلوماتية", coefficient = 1.0, maxScore = 20.0, colorIndex = 2)
        )
        var added = 0
        presets.forEach { preset ->
            if (dao.findSubjectByName(preset.name) == null) {
                dao.insertSubject(preset)
                added++
            }
        }
        return if (added > 0) {
            ActionResult.Success("تمت إضافة $added مواد دراسية جزائرية بنجاح")
        } else {
            ActionResult.Success("جميع المواد القياسية مضافة مسبقاً")
        }
    }

    // --- Classes ---
    suspend fun addClass(name: String, academicYear: String, level: String): ActionResult {
        val cleanName = name.trim()
        val cleanYear = academicYear.trim()
        if (cleanName.isBlank()) return ActionResult.Error("يرجى إدخال اسم القسم")
        if (cleanYear.isBlank()) return ActionResult.Error("يرجى تحديد السنة الدراسية")
        dao.insertClass(
            AcademicClassEntity(
                name = cleanName,
                academicYear = cleanYear,
                level = level.trim().ifBlank { "متوسط" }
            )
        )
        return ActionResult.Success("تمت إضافة القسم «$cleanName» بنجاح")
    }

    suspend fun updateClass(entity: AcademicClassEntity): ActionResult {
        if (entity.name.isBlank()) return ActionResult.Error("اسم القسم لا يمكن أن يكون فارغاً")
        if (entity.academicYear.isBlank()) return ActionResult.Error("السنة الدراسية مطلوبة")
        dao.updateClass(entity.copy(name = entity.name.trim(), academicYear = entity.academicYear.trim()))
        return ActionResult.Success("تم تحديث بيانات القسم بنجاح")
    }

    suspend fun deleteClass(entity: AcademicClassEntity): ActionResult {
        dao.deleteClass(entity)
        return ActionResult.Success("تم حذف القسم «${entity.name}» وبياناته")
    }

    // --- Students ---
    suspend fun addStudent(
        classId: Long,
        firstName: String,
        lastName: String,
        registrationNumber: String,
        birthDate: String = "",
        notes: String = ""
    ): ActionResult {
        val cleanFirst = firstName.trim()
        val cleanLast = lastName.trim()
        val cleanReg = registrationNumber.trim()

        if (cleanLast.isBlank() || cleanFirst.isBlank()) {
            return ActionResult.Error("يرجى إدخال الاسم واللقب للتلميذ")
        }
        if (cleanReg.isBlank()) {
            return ActionResult.Error("يرجى إدخال رقم التسجيل لمنع التكرار")
        }
        if (dao.findStudentByRegNumber(cleanReg) != null) {
            return ActionResult.Error("رقم التسجيل «$cleanReg» مستعمل مسبقاً لتلميذ آخر")
        }
        if (dao.findStudentByNameInClass(classId, cleanFirst, cleanLast) != null) {
            return ActionResult.Error("التلميذ «$cleanLast $cleanFirst» مسجل مسبقاً في هذا القسم")
        }

        dao.insertStudent(
            StudentEntity(
                classId = classId,
                firstName = cleanFirst,
                lastName = cleanLast,
                registrationNumber = cleanReg,
                birthDate = birthDate.trim(),
                notes = notes.trim()
            )
        )
        return ActionResult.Success("تم تسجيل التلميذ «$cleanLast $cleanFirst» بنجاح")
    }

    suspend fun updateStudent(student: StudentEntity): ActionResult {
        val cleanFirst = student.firstName.trim()
        val cleanLast = student.lastName.trim()
        val cleanReg = student.registrationNumber.trim()
        if (cleanFirst.isBlank() || cleanLast.isBlank()) {
            return ActionResult.Error("الاسم واللقب مطلوبان")
        }
        if (cleanReg.isBlank()) {
            return ActionResult.Error("رقم التسجيل مطلوب")
        }
        val duplicateReg = dao.findStudentByRegNumber(cleanReg)
        if (duplicateReg != null && duplicateReg.id != student.id) {
            return ActionResult.Error("رقم التسجيل «$cleanReg» مرتبط بتلميذ آخر")
        }
        dao.updateStudent(
            student.copy(
                firstName = cleanFirst,
                lastName = cleanLast,
                registrationNumber = cleanReg,
                birthDate = student.birthDate.trim(),
                notes = student.notes.trim()
            )
        )
        return ActionResult.Success("تم تحديث بيانات التلميذ")
    }

    suspend fun deleteStudent(student: StudentEntity): ActionResult {
        dao.deleteStudent(student)
        return ActionResult.Success("تم حذف التلميذ «${student.fullName}»")
    }

    suspend fun transferStudent(studentId: Long, targetClassId: Long): ActionResult {
        dao.transferStudent(studentId, targetClassId)
        return ActionResult.Success("تم نقل التلميذ إلى القسم الجديد بنجاح")
    }

    // --- Subjects ---
    suspend fun addSubject(name: String, coefficient: Double, maxScore: Double = 20.0, colorIndex: Int = 0): ActionResult {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return ActionResult.Error("يرجى إدخال اسم المادة")
        if (coefficient <= 0.0 || coefficient > 20.0) return ActionResult.Error("المعامل يجب أن يكون أكبر من 0")
        if (maxScore <= 0.0 || maxScore > 100.0) return ActionResult.Error("العلامة القصوى غير صالحة")
        if (dao.findSubjectByName(cleanName) != null) {
            return ActionResult.Error("المادة «$cleanName» موجودة مسبقاً")
        }
        dao.insertSubject(
            SubjectEntity(
                name = cleanName,
                coefficient = coefficient,
                maxScore = maxScore,
                colorIndex = colorIndex
            )
        )
        return ActionResult.Success("تمت إضافة مادة «$cleanName»")
    }

    suspend fun updateSubject(subject: SubjectEntity): ActionResult {
        val cleanName = subject.name.trim()
        if (cleanName.isBlank()) return ActionResult.Error("اسم المادة مطلوب")
        if (subject.coefficient <= 0.0) return ActionResult.Error("المعامل يجب أن يكون أكبر من 0")
        val existing = dao.findSubjectByName(cleanName)
        if (existing != null && existing.id != subject.id) {
            return ActionResult.Error("توجد مادة أخرى بنفس الاسم «$cleanName»")
        }
        dao.updateSubject(subject.copy(name = cleanName))
        return ActionResult.Success("تم تعديل المادة بنجاح")
    }

    suspend fun deleteSubject(subject: SubjectEntity): ActionResult {
        dao.deleteSubject(subject)
        return ActionResult.Success("تم حذف مادة «${subject.name}»")
    }

    // --- Semesters ---
    suspend fun addSemester(name: String, academicYear: String): ActionResult {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return ActionResult.Error("اسم الفصل مطلوب")
        val currentCount = dao.getAllSemestersOnce().size
        dao.insertSemester(
            SemesterEntity(
                name = cleanName,
                orderIndex = currentCount + 1,
                academicYear = academicYear.trim().ifBlank { "2025/2026" },
                isActive = currentCount == 0
            )
        )
        return ActionResult.Success("تمت إضافة «$cleanName»")
    }

    suspend fun setActiveSemester(semesterId: Long) {
        dao.setActiveSemester(semesterId)
    }

    suspend fun deleteSemester(semester: SemesterEntity): ActionResult {
        val all = dao.getAllSemestersOnce()
        if (all.size <= 1) {
            return ActionResult.Error("يجب الاحتفاظ بفصل دراسي واحد على الأقل")
        }
        dao.deleteSemester(semester)
        if (semester.isActive) {
            dao.getAllSemestersOnce().firstOrNull()?.let { dao.setActiveSemester(it.id) }
        }
        return ActionResult.Success("تم حذف «${semester.name}»")
    }

    // --- Grades ---
    suspend fun saveGrade(
        studentId: Long,
        subjectId: Long,
        semesterId: Long,
        continuousScore: Double?,
        testScore: Double?,
        examScore: Double?,
        directFinalScore: Double?,
        maxScore: Double = 20.0,
        remark: String = ""
    ): ActionResult {
        val components = listOfNotNull(continuousScore, testScore, examScore)
        if (components.any { it < 0.0 || it > maxScore }) {
            return ActionResult.Error("العلامات يجب أن تكون محصورة بين 0 و ${formatScore(maxScore)}")
        }
        if (directFinalScore != null && (directFinalScore < 0.0 || directFinalScore > maxScore)) {
            return ActionResult.Error("العلامة النهائية يجب أن تكون بين 0 و ${formatScore(maxScore)}")
        }

        val computedFinal = when {
            directFinalScore != null -> directFinalScore
            continuousScore != null && testScore != null && examScore != null -> {
                // Standard Algerian middle/high school formula: (Continuous + Test + Exam * 2) / 4
                (continuousScore + testScore + examScore * 2.0) / 4.0
            }
            testScore != null && examScore != null -> (testScore + examScore * 2.0) / 3.0
            continuousScore != null && examScore != null -> (continuousScore + examScore * 2.0) / 3.0
            components.isNotEmpty() -> components.average()
            else -> return ActionResult.Error("يرجى إدخال علامة صالحة")
        }

        val roundedFinal = (computedFinal * 100.0).roundToInt() / 100.0
        val existing = dao.findGrade(studentId, subjectId, semesterId)
        val entity = GradeEntity(
            id = existing?.id ?: 0L,
            studentId = studentId,
            subjectId = subjectId,
            semesterId = semesterId,
            continuousScore = continuousScore,
            testScore = testScore,
            examScore = examScore,
            finalScore = roundedFinal,
            remark = remark.trim(),
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertGrade(entity)
        return ActionResult.Success("تم حفظ العلامة (${formatScore(roundedFinal)}/${formatScore(maxScore)})")
    }

    suspend fun deleteGrade(studentId: Long, subjectId: Long, semesterId: Long): ActionResult {
        dao.deleteGradeByKey(studentId, subjectId, semesterId)
        return ActionResult.Success("تم مسح العلامة")
    }

    // --- Settings ---
    suspend fun updateThemeMode(mode: ThemeMode) = settingsStore.updateThemeMode(mode)

    suspend fun updateProfile(
        teacherName: String,
        schoolName: String,
        wilaya: String,
        academicYear: String
    ) = settingsStore.updateProfile(teacherName, schoolName, wilaya, academicYear)

    suspend fun updateAiOcrEnabled(enabled: Boolean) = settingsStore.updateAiOcrEnabled(enabled)

    // --- Backup & Restore ---
    suspend fun captureBackupSnapshot(): FullBackupSnapshot {
        return FullBackupSnapshot(
            exportedAt = System.currentTimeMillis(),
            settings = settingsStore.settingsFlow.first(),
            classes = dao.getAllClassesOnce(),
            students = dao.getAllStudentsOnce(),
            subjects = dao.getAllSubjectsOnce(),
            semesters = dao.getAllSemestersOnce(),
            grades = dao.getAllGradesOnce()
        )
    }

    suspend fun restoreBackupSnapshot(snapshot: FullBackupSnapshot): ActionResult {
        if (snapshot.semesters.isEmpty()) {
            return ActionResult.Error("ملف النسخة الاحتياطية غير مكتمل (لا يحتوي على فصول دراسية)")
        }
        dao.replaceAllData(
            classes = snapshot.classes,
            students = snapshot.students,
            subjects = snapshot.subjects,
            semesters = snapshot.semesters,
            grades = snapshot.grades
        )
        settingsStore.restoreSettings(snapshot.settings)
        return ActionResult.Success(
            "تمت الاستعادة بنجاح: ${snapshot.classes.size} قسم، ${snapshot.students.size} تلميذ، ${snapshot.grades.size} علامة"
        )
    }
}

fun formatScore(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", value)
    }
}
