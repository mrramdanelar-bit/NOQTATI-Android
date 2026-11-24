package com.example.domain

import com.example.data.local.AcademicClassEntity
import com.example.data.local.GradeEntity
import com.example.data.local.SemesterEntity
import com.example.data.local.StudentEntity
import com.example.data.local.SubjectEntity
import kotlin.math.roundToInt

data class SubjectGradeLine(
    val subject: SubjectEntity,
    val grade: GradeEntity?,
    val weightedScore: Double?
)

data class StudentSemesterReport(
    val student: StudentEntity,
    val academicClass: AcademicClassEntity,
    val semester: SemesterEntity,
    val lines: List<SubjectGradeLine>,
    val totalCoefficients: Double,
    val totalWeightedPoints: Double,
    val average: Double?,
    val rank: Int?,
    val appreciation: String,
    val gradedSubjectsCount: Int
)

data class ClassSemesterSummary(
    val academicClass: AcademicClassEntity,
    val semester: SemesterEntity,
    val reports: List<StudentSemesterReport>,
    val classAverage: Double?,
    val highestAverage: Double?,
    val lowestAverage: Double?,
    val passCount: Int,
    val gradedStudentsCount: Int,
    val passRatePercent: Double?
)

object AverageCalculator {

    fun appreciationForAverage(average: Double?): String {
        if (average == null) return "غير مقيم بعد"
        return when {
            average >= 18.0 -> "امتياز"
            average >= 16.0 -> "تهنئة"
            average >= 14.0 -> "تشجيع"
            average >= 12.0 -> "لوحة شرف"
            average >= 10.0 -> "مقبول"
            average >= 8.0 -> "متوسط - يحتاج دعماً"
            else -> "ضعيف - يحتاج متابعة"
        }
    }

    fun remarkForSubjectScore(score: Double, maxScore: Double = 20.0): String {
        val normalized = if (maxScore > 0) (score / maxScore) * 20.0 else score
        return when {
            normalized >= 18.0 -> "ممتاز"
            normalized >= 15.0 -> "جيد جداً"
            normalized >= 12.0 -> "جيد"
            normalized >= 10.0 -> "مقبول"
            normalized >= 8.0 -> "ناقص"
            else -> "ضعيف"
        }
    }

    fun computeClassSemesterSummary(
        academicClass: AcademicClassEntity,
        semester: SemesterEntity,
        students: List<StudentEntity>,
        subjects: List<SubjectEntity>,
        grades: List<GradeEntity>
    ): ClassSemesterSummary {
        val classStudents = students
            .filter { it.classId == academicClass.id }
            .sortedWith(compareBy({ it.lastName }, { it.firstName }))

        val gradeMap = grades
            .filter { it.semesterId == semester.id }
            .associateBy { it.studentId to it.subjectId }

        val preliminary = classStudents.map { student ->
            var sumCoeff = 0.0
            var sumWeighted = 0.0
            var gradedCount = 0

            val lines = subjects.map { subject ->
                val grade = gradeMap[student.id to subject.id]
                val weighted = if (grade != null) {
                    val normalized20 = if (subject.maxScore > 0 && subject.maxScore != 20.0) {
                        (grade.finalScore / subject.maxScore) * 20.0
                    } else {
                        grade.finalScore
                    }
                    sumCoeff += subject.coefficient
                    sumWeighted += normalized20 * subject.coefficient
                    gradedCount++
                    round2(normalized20 * subject.coefficient)
                } else {
                    null
                }
                SubjectGradeLine(subject = subject, grade = grade, weightedScore = weighted)
            }

            val avg = if (sumCoeff > 0.0) round2(sumWeighted / sumCoeff) else null
            StudentSemesterReport(
                student = student,
                academicClass = academicClass,
                semester = semester,
                lines = lines,
                totalCoefficients = round2(sumCoeff),
                totalWeightedPoints = round2(sumWeighted),
                average = avg,
                rank = null,
                appreciation = appreciationForAverage(avg),
                gradedSubjectsCount = gradedCount
            )
        }

        // Compute ranks with standard competition tie-handling (1, 2, 2, 4)
        val sortedByAvg = preliminary
            .filter { it.average != null }
            .sortedByDescending { it.average }

        val rankByStudentId = mutableMapOf<Long, Int>()
        var currentRank = 0
        var lastAvg: Double? = null
        sortedByAvg.forEachIndexed { index, report ->
            if (report.average != lastAvg) {
                currentRank = index + 1
                lastAvg = report.average
            }
            rankByStudentId[report.student.id] = currentRank
        }

        val rankedReports = preliminary
            .map { it.copy(rank = rankByStudentId[it.student.id]) }
            .sortedWith(
                compareBy<StudentSemesterReport> { it.rank ?: Int.MAX_VALUE }
                    .thenBy { it.student.lastName }
                    .thenBy { it.student.firstName }
            )

        val validAverages = rankedReports.mapNotNull { it.average }
        val classAvg = if (validAverages.isNotEmpty()) round2(validAverages.average()) else null
        val highest = validAverages.maxOrNull()
        val lowest = validAverages.minOrNull()
        val passCount = validAverages.count { it >= 10.0 }
        val passRate = if (validAverages.isNotEmpty()) {
            round2((passCount.toDouble() / validAverages.size.toDouble()) * 100.0)
        } else {
            null
        }

        return ClassSemesterSummary(
            academicClass = academicClass,
            semester = semester,
            reports = rankedReports,
            classAverage = classAvg,
            highestAverage = highest,
            lowestAverage = lowest,
            passCount = passCount,
            gradedStudentsCount = validAverages.size,
            passRatePercent = passRate
        )
    }

    fun computeAnnualAverage(
        studentId: Long,
        subjects: List<SubjectEntity>,
        semesters: List<SemesterEntity>,
        grades: List<GradeEntity>
    ): Double? {
        val subjectMap = subjects.associateBy { it.id }
        val semesterAverages = semesters.mapNotNull { semester ->
            val semGrades = grades.filter { it.studentId == studentId && it.semesterId == semester.id }
            if (semGrades.isEmpty()) return@mapNotNull null
            var sumCoeff = 0.0
            var sumWeighted = 0.0
            semGrades.forEach { g ->
                val subj = subjectMap[g.subjectId] ?: return@forEach
                val norm = if (subj.maxScore > 0 && subj.maxScore != 20.0) {
                    (g.finalScore / subj.maxScore) * 20.0
                } else {
                    g.finalScore
                }
                sumCoeff += subj.coefficient
                sumWeighted += norm * subj.coefficient
            }
            if (sumCoeff > 0.0) sumWeighted / sumCoeff else null
        }
        return if (semesterAverages.isNotEmpty()) round2(semesterAverages.average()) else null
    }

    fun round2(value: Double): Double = (value * 100.0).roundToInt() / 100.0
}
