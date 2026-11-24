package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "classes")
data class AcademicClassEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val academicYear: String,
    val level: String = "متوسط",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "students",
    foreignKeys = [
        ForeignKey(
            entity = AcademicClassEntity::class,
            parentColumns = ["id"],
            childColumns = ["classId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["classId"]),
        Index(value = ["registrationNumber"], unique = true)
    ]
)
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val classId: Long,
    val firstName: String,
    val lastName: String,
    val registrationNumber: String,
    val birthDate: String = "",
    val notes: String = ""
) {
    val fullName: String
        get() = "$lastName $firstName".trim()
}

@Entity(
    tableName = "subjects",
    indices = [Index(value = ["name"], unique = true)]
)
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val coefficient: Double = 1.0,
    val maxScore: Double = 20.0,
    val colorIndex: Int = 0
)

@Entity(tableName = "semesters")
data class SemesterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val orderIndex: Int,
    val academicYear: String,
    val isActive: Boolean = false
)

@Entity(
    tableName = "grades",
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["studentId"]),
        Index(value = ["subjectId"]),
        Index(value = ["semesterId"]),
        Index(value = ["studentId", "subjectId", "semesterId"], unique = true)
    ]
)
data class GradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val studentId: Long,
    val subjectId: Long,
    val semesterId: Long,
    val continuousScore: Double? = null,
    val testScore: Double? = null,
    val examScore: Double? = null,
    val finalScore: Double,
    val remark: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
