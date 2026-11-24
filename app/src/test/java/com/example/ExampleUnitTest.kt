package com.example

import com.example.ai.ScanOutcome
import com.example.ai.SmartGradeScanner
import com.example.data.local.AcademicClassEntity
import com.example.data.local.GradeEntity
import com.example.data.local.SemesterEntity
import com.example.data.local.StudentEntity
import com.example.data.local.SubjectEntity
import com.example.domain.AverageCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun weightedAveragesAndRankingsCalculateAccurately() {
        val cls = AcademicClassEntity(id = 1L, name = "4 متوسط 1", academicYear = "2025/2026")
        val sem = SemesterEntity(id = 1L, name = "الفصل الأول", orderIndex = 1, academicYear = "2025/2026", isActive = true)
        val s1 = StudentEntity(id = 10L, classId = 1L, firstName = "أمين", lastName = "بن علي", registrationNumber = "2025-01")
        val s2 = StudentEntity(id = 11L, classId = 1L, firstName = "سارة", lastName = "بوعلام", registrationNumber = "2025-02")
        val math = SubjectEntity(id = 100L, name = "الرياضيات", coefficient = 4.0)
        val arabic = SubjectEntity(id = 101L, name = "اللغة العربية", coefficient = 5.0)

        val grades = listOf(
            GradeEntity(studentId = 10L, subjectId = 100L, semesterId = 1L, finalScore = 16.0),
            GradeEntity(studentId = 10L, subjectId = 101L, semesterId = 1L, finalScore = 14.0),
            GradeEntity(studentId = 11L, subjectId = 100L, semesterId = 1L, finalScore = 18.0),
            GradeEntity(studentId = 11L, subjectId = 101L, semesterId = 1L, finalScore = 16.0)
        )

        val summary = AverageCalculator.computeClassSemesterSummary(
            academicClass = cls,
            semester = sem,
            students = listOf(s1, s2),
            subjects = listOf(math, arabic),
            grades = grades
        )

        assertEquals(2, summary.reports.size)
        // Sara: (18*4 + 16*5)/9 = 152/9 = 16.89 -> rank 1
        assertEquals(11L, summary.reports[0].student.id)
        assertEquals(1, summary.reports[0].rank)
        assertEquals(16.89, summary.reports[0].average ?: 0.0, 0.01)
        assertEquals("تهنئة", summary.reports[0].appreciation)
        // Amine: (16*4 + 14*5)/9 = 134/9 = 14.89 -> rank 2
        assertEquals(10L, summary.reports[1].student.id)
        assertEquals(2, summary.reports[1].rank)
        assertEquals(100.0, summary.passRatePercent ?: 0.0, 0.01)
    }

    @Test
    fun offlineScannerParsesArabicDigitsAndFuzzyMatchesStudents() {
        val scanner = SmartGradeScanner()
        val roster = listOf(
            StudentEntity(id = 1L, classId = 1L, firstName = "محمد", lastName = "بن علي", registrationNumber = "DZ001"),
            StudentEntity(id = 2L, classId = 1L, firstName = "إيمان", lastName = "حداد", registrationNumber = "DZ002")
        )

        val raw = """
            بن علي محمد : ١٥.٥
            حداد ايمان - 17,25
            اسم غير معروف : 25
        """.trimIndent()

        val outcome = scanner.parseOfflineTextToRows(raw, roster, 20.0)
        assertTrue(outcome is ScanOutcome.Success)
        val rows = (outcome as ScanOutcome.Success).rows
        assertEquals(3, rows.size)
        assertEquals(1L, rows[0].matchedStudentId)
        assertEquals(15.5, rows[0].parsedScore(20.0) ?: -1.0, 0.01)
        assertEquals(2L, rows[1].matchedStudentId)
        assertEquals(17.25, rows[1].parsedScore(20.0) ?: -1.0, 0.01)
        assertEquals(null, rows[2].matchedStudentId)
        assertTrue(rows[2].hasWarning(20.0))
    }
}
