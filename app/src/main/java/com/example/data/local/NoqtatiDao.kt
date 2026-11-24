package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoqtatiDao {

    // --- Classes ---
    @Query("SELECT * FROM classes ORDER BY academicYear DESC, name ASC")
    fun observeAllClasses(): Flow<List<AcademicClassEntity>>

    @Query("SELECT * FROM classes ORDER BY id ASC")
    suspend fun getAllClassesOnce(): List<AcademicClassEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertClass(academicClass: AcademicClassEntity): Long

    @Update
    suspend fun updateClass(academicClass: AcademicClassEntity)

    @Delete
    suspend fun deleteClass(academicClass: AcademicClassEntity)

    // --- Students ---
    @Query("SELECT * FROM students ORDER BY lastName ASC, firstName ASC")
    fun observeAllStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students ORDER BY id ASC")
    suspend fun getAllStudentsOnce(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE LOWER(TRIM(registrationNumber)) = LOWER(TRIM(:regNumber)) LIMIT 1")
    suspend fun findStudentByRegNumber(regNumber: String): StudentEntity?

    @Query(
        """
        SELECT * FROM students
        WHERE classId = :classId
          AND LOWER(TRIM(firstName)) = LOWER(TRIM(:firstName))
          AND LOWER(TRIM(lastName)) = LOWER(TRIM(:lastName))
        LIMIT 1
        """
    )
    suspend fun findStudentByNameInClass(
        classId: Long,
        firstName: String,
        lastName: String
    ): StudentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStudent(student: StudentEntity): Long

    @Update
    suspend fun updateStudent(student: StudentEntity)

    @Delete
    suspend fun deleteStudent(student: StudentEntity)

    @Query("UPDATE students SET classId = :targetClassId WHERE id = :studentId")
    suspend fun transferStudent(studentId: Long, targetClassId: Long)

    // --- Subjects ---
    @Query("SELECT * FROM subjects ORDER BY name ASC")
    fun observeAllSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY id ASC")
    suspend fun getAllSubjectsOnce(): List<SubjectEntity>

    @Query("SELECT * FROM subjects WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun findSubjectByName(name: String): SubjectEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Update
    suspend fun updateSubject(subject: SubjectEntity)

    @Delete
    suspend fun deleteSubject(subject: SubjectEntity)

    // --- Semesters ---
    @Query("SELECT * FROM semesters ORDER BY orderIndex ASC, id ASC")
    fun observeAllSemesters(): Flow<List<SemesterEntity>>

    @Query("SELECT * FROM semesters ORDER BY orderIndex ASC, id ASC")
    suspend fun getAllSemestersOnce(): List<SemesterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSemester(semester: SemesterEntity): Long

    @Update
    suspend fun updateSemester(semester: SemesterEntity)

    @Delete
    suspend fun deleteSemester(semester: SemesterEntity)

    @Query("UPDATE semesters SET isActive = CASE WHEN id = :semesterId THEN 1 ELSE 0 END")
    suspend fun setActiveSemester(semesterId: Long)

    // --- Grades ---
    @Query("SELECT * FROM grades ORDER BY updatedAt DESC")
    fun observeAllGrades(): Flow<List<GradeEntity>>

    @Query("SELECT * FROM grades ORDER BY id ASC")
    suspend fun getAllGradesOnce(): List<GradeEntity>

    @Query(
        """
        SELECT * FROM grades
        WHERE studentId = :studentId AND subjectId = :subjectId AND semesterId = :semesterId
        LIMIT 1
        """
    )
    suspend fun findGrade(studentId: Long, subjectId: Long, semesterId: Long): GradeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGrade(grade: GradeEntity): Long

    @Delete
    suspend fun deleteGrade(grade: GradeEntity)

    @Query("DELETE FROM grades WHERE studentId = :studentId AND subjectId = :subjectId AND semesterId = :semesterId")
    suspend fun deleteGradeByKey(studentId: Long, subjectId: Long, semesterId: Long)

    // --- Bulk Restore Helpers ---
    @Query("DELETE FROM grades")
    suspend fun clearGrades()

    @Query("DELETE FROM students")
    suspend fun clearStudents()

    @Query("DELETE FROM subjects")
    suspend fun clearSubjects()

    @Query("DELETE FROM semesters")
    suspend fun clearSemesters()

    @Query("DELETE FROM classes")
    suspend fun clearClasses()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassesBulk(items: List<AcademicClassEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudentsBulk(items: List<StudentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjectsBulk(items: List<SubjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSemestersBulk(items: List<SemesterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGradesBulk(items: List<GradeEntity>)

    @Transaction
    suspend fun replaceAllData(
        classes: List<AcademicClassEntity>,
        students: List<StudentEntity>,
        subjects: List<SubjectEntity>,
        semesters: List<SemesterEntity>,
        grades: List<GradeEntity>
    ) {
        clearGrades()
        clearStudents()
        clearSubjects()
        clearSemesters()
        clearClasses()
        if (classes.isNotEmpty()) insertClassesBulk(classes)
        if (semesters.isNotEmpty()) insertSemestersBulk(semesters)
        if (subjects.isNotEmpty()) insertSubjectsBulk(subjects)
        if (students.isNotEmpty()) insertStudentsBulk(students)
        if (grades.isNotEmpty()) insertGradesBulk(grades)
    }
}
