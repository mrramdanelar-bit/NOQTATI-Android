package com.example.export

import com.example.data.local.AcademicClassEntity
import com.example.data.local.GradeEntity
import com.example.data.local.SemesterEntity
import com.example.data.local.StudentEntity
import com.example.data.local.SubjectEntity
import com.example.data.preferences.AppSettings
import com.example.data.preferences.ThemeMode
import com.example.data.repository.FullBackupSnapshot
import org.json.JSONArray
import org.json.JSONObject

object BackupSerializer {

    private const val FORMAT_VERSION = 1

    fun toJson(snapshot: FullBackupSnapshot): String {
        val root = JSONObject()
        root.put("app", "NOQTATI")
        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", snapshot.exportedAt)

        val settingsObj = JSONObject().apply {
            put("themeMode", snapshot.settings.themeMode.name)
            put("teacherName", snapshot.settings.teacherName)
            put("schoolName", snapshot.settings.schoolName)
            put("wilaya", snapshot.settings.wilaya)
            put("defaultAcademicYear", snapshot.settings.defaultAcademicYear)
            put("aiOcrEnabled", snapshot.settings.aiOcrEnabled)
        }
        root.put("settings", settingsObj)

        val classesArr = JSONArray()
        snapshot.classes.forEach { c ->
            classesArr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("academicYear", c.academicYear)
                    put("level", c.level)
                    put("createdAt", c.createdAt)
                }
            )
        }
        root.put("classes", classesArr)

        val studentsArr = JSONArray()
        snapshot.students.forEach { s ->
            studentsArr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("classId", s.classId)
                    put("firstName", s.firstName)
                    put("lastName", s.lastName)
                    put("registrationNumber", s.registrationNumber)
                    put("birthDate", s.birthDate)
                    put("notes", s.notes)
                }
            )
        }
        root.put("students", studentsArr)

        val subjectsArr = JSONArray()
        snapshot.subjects.forEach { sub ->
            subjectsArr.put(
                JSONObject().apply {
                    put("id", sub.id)
                    put("name", sub.name)
                    put("coefficient", sub.coefficient)
                    put("maxScore", sub.maxScore)
                    put("colorIndex", sub.colorIndex)
                }
            )
        }
        root.put("subjects", subjectsArr)

        val semestersArr = JSONArray()
        snapshot.semesters.forEach { sem ->
            semestersArr.put(
                JSONObject().apply {
                    put("id", sem.id)
                    put("name", sem.name)
                    put("orderIndex", sem.orderIndex)
                    put("academicYear", sem.academicYear)
                    put("isActive", sem.isActive)
                }
            )
        }
        root.put("semesters", semestersArr)

        val gradesArr = JSONArray()
        snapshot.grades.forEach { g ->
            gradesArr.put(
                JSONObject().apply {
                    put("id", g.id)
                    put("studentId", g.studentId)
                    put("subjectId", g.subjectId)
                    put("semesterId", g.semesterId)
                    if (g.continuousScore != null) put("continuousScore", g.continuousScore)
                    if (g.testScore != null) put("testScore", g.testScore)
                    if (g.examScore != null) put("examScore", g.examScore)
                    put("finalScore", g.finalScore)
                    put("remark", g.remark)
                    put("updatedAt", g.updatedAt)
                }
            )
        }
        root.put("grades", gradesArr)

        return root.toString(2)
    }

    fun fromJson(jsonText: String): FullBackupSnapshot {
        val root = JSONObject(jsonText)
        require(root.optString("app") == "NOQTATI") {
            "هذا الملف ليس نسخة احتياطية صالحة لتطبيق NOQTATI – نقطتي"
        }

        val settingsObj = root.optJSONObject("settings")
        val themeStr = settingsObj?.optString("themeMode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val settings = AppSettings(
            themeMode = ThemeMode.entries.firstOrNull { it.name == themeStr } ?: ThemeMode.SYSTEM,
            teacherName = settingsObj?.optString("teacherName", "") ?: "",
            schoolName = settingsObj?.optString("schoolName", "") ?: "",
            wilaya = settingsObj?.optString("wilaya", "") ?: "",
            defaultAcademicYear = settingsObj?.optString("defaultAcademicYear", "2025/2026") ?: "2025/2026",
            aiOcrEnabled = settingsObj?.optBoolean("aiOcrEnabled", true) ?: true
        )

        val classes = mutableListOf<AcademicClassEntity>()
        val classesArr = root.optJSONArray("classes") ?: JSONArray()
        for (i in 0 until classesArr.length()) {
            val o = classesArr.getJSONObject(i)
            classes.add(
                AcademicClassEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    academicYear = o.optString("academicYear", "2025/2026"),
                    level = o.optString("level", "متوسط"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }

        val students = mutableListOf<StudentEntity>()
        val studentsArr = root.optJSONArray("students") ?: JSONArray()
        for (i in 0 until studentsArr.length()) {
            val o = studentsArr.getJSONObject(i)
            students.add(
                StudentEntity(
                    id = o.getLong("id"),
                    classId = o.getLong("classId"),
                    firstName = o.getString("firstName"),
                    lastName = o.getString("lastName"),
                    registrationNumber = o.getString("registrationNumber"),
                    birthDate = o.optString("birthDate", ""),
                    notes = o.optString("notes", "")
                )
            )
        }

        val subjects = mutableListOf<SubjectEntity>()
        val subjectsArr = root.optJSONArray("subjects") ?: JSONArray()
        for (i in 0 until subjectsArr.length()) {
            val o = subjectsArr.getJSONObject(i)
            subjects.add(
                SubjectEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    coefficient = o.optDouble("coefficient", 1.0),
                    maxScore = o.optDouble("maxScore", 20.0),
                    colorIndex = o.optInt("colorIndex", 0)
                )
            )
        }

        val semesters = mutableListOf<SemesterEntity>()
        val semestersArr = root.optJSONArray("semesters") ?: JSONArray()
        for (i in 0 until semestersArr.length()) {
            val o = semestersArr.getJSONObject(i)
            semesters.add(
                SemesterEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    orderIndex = o.optInt("orderIndex", i + 1),
                    academicYear = o.optString("academicYear", "2025/2026"),
                    isActive = o.optBoolean("isActive", i == 0)
                )
            )
        }

        val grades = mutableListOf<GradeEntity>()
        val gradesArr = root.optJSONArray("grades") ?: JSONArray()
        for (i in 0 until gradesArr.length()) {
            val o = gradesArr.getJSONObject(i)
            grades.add(
                GradeEntity(
                    id = o.getLong("id"),
                    studentId = o.getLong("studentId"),
                    subjectId = o.getLong("subjectId"),
                    semesterId = o.getLong("semesterId"),
                    continuousScore = if (o.has("continuousScore") && !o.isNull("continuousScore")) o.getDouble("continuousScore") else null,
                    testScore = if (o.has("testScore") && !o.isNull("testScore")) o.getDouble("testScore") else null,
                    examScore = if (o.has("examScore") && !o.isNull("examScore")) o.getDouble("examScore") else null,
                    finalScore = o.getDouble("finalScore"),
                    remark = o.optString("remark", ""),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }

        return FullBackupSnapshot(
            exportedAt = root.optLong("exportedAt", System.currentTimeMillis()),
            settings = settings,
            classes = classes,
            students = students,
            subjects = subjects,
            semesters = semesters,
            grades = grades
        )
    }
}
