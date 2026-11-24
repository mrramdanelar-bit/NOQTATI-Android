package com.example.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.preferences.AppSettings
import com.example.data.repository.formatScore
import com.example.domain.AverageCalculator
import com.example.domain.ClassSemesterSummary
import com.example.domain.StudentSemesterReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfReportGenerator(private val context: Context) {

    private fun loadArabicTypeface(bold: Boolean): Typeface {
        val base = try {
            ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
        return if (bold) Typeface.create(base, Typeface.BOLD) else base
    }

    private fun reportsDir(): File {
        return File(context.cacheDir, "reports").apply { mkdirs() }
    }

    suspend fun generateStudentBulletinPdf(
        report: StudentSemesterReport,
        summary: ClassSemesterSummary,
        settings: AppSettings
    ): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        // A4 Portrait (595 x 842 points)
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val regularTf = loadArabicTypeface(false)
        val boldTf = loadArabicTypeface(true)

        val primaryColor = Color.rgb(15, 107, 94)
        val accentGold = Color.rgb(183, 123, 30)
        val softBg = Color.rgb(240, 247, 245)
        val borderColor = Color.rgb(190, 210, 206)
        val darkText = Color.rgb(20, 34, 32)
        val mutedText = Color.rgb(85, 105, 101)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = borderColor
            strokeWidth = 1f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 15f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 11f
            color = Color.rgb(225, 245, 241)
            textAlign = Paint.Align.CENTER
        }
        val rightBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 11.5f
            color = darkText
            textAlign = Paint.Align.RIGHT
        }
        val rightTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 11f
            color = darkText
            textAlign = Paint.Align.RIGHT
        }
        val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 11f
            color = darkText
            textAlign = Paint.Align.CENTER
        }
        val centerBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 11.5f
            color = darkText
            textAlign = Paint.Align.CENTER
        }

        val margin = 32f
        val contentRight = pageWidth - margin

        // Header banner
        fillPaint.color = primaryColor
        canvas.drawRoundRect(margin, margin, contentRight, margin + 88f, 12f, 12f, fillPaint)
        canvas.drawText("الجمهورية الجزائرية الديمقراطية الشعبية", pageWidth / 2f, margin + 24f, subtitlePaint)
        canvas.drawText("كشف النقاط الفردي — ${report.semester.name}", pageWidth / 2f, margin + 48f, titlePaint)
        val schoolLine = buildString {
            append(settings.schoolName.ifBlank { "المؤسسة التعليمية" })
            if (settings.wilaya.isNotBlank()) append(" — ولاية ${settings.wilaya}")
            append(" | السنة الدراسية: ${report.academicClass.academicYear}")
        }
        canvas.drawText(schoolLine, pageWidth / 2f, margin + 70f, subtitlePaint)

        // Student metadata card
        var y = margin + 104f
        fillPaint.color = softBg
        canvas.drawRoundRect(margin, y, contentRight, y + 66f, 10f, 10f, fillPaint)
        canvas.drawRoundRect(margin, y, contentRight, y + 66f, 10f, 10f, strokePaint)

        canvas.drawText("اللقب والاسم: ${report.student.fullName}", contentRight - 14f, y + 24f, rightBoldPaint)
        canvas.drawText("رقم التسجيل: ${report.student.registrationNumber}", margin + 220f, y + 24f, rightTextPaint)
        canvas.drawText("القسم: ${report.academicClass.name} (${report.academicClass.level})", contentRight - 14f, y + 48f, rightTextPaint)
        val teacherText = if (settings.teacherName.isNotBlank()) "الأستاذ: ${settings.teacherName}" else "تطبيق NOQTATI – نقطتي"
        canvas.drawText(teacherText, margin + 220f, y + 48f, rightTextPaint)

        // Table Header (RTL columns from Right to Left: المادة | المعامل | العلامة /20 | المجموع | الملاحظة)
        y += 84f
        val colRemarkLeft = margin
        val colWeightedLeft = margin + 125f
        val colScoreLeft = margin + 210f
        val colCoeffLeft = margin + 295f
        val colSubjectLeft = margin + 355f
        val tableRight = contentRight

        val headerHeight = 28f
        fillPaint.color = primaryColor
        canvas.drawRect(margin, y, tableRight, y + headerHeight, fillPaint)

        val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 11f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("المادة الدراسية", (colSubjectLeft + tableRight) / 2f, y + 18f, tableHeaderPaint)
        canvas.drawText("المعامل", (colCoeffLeft + colSubjectLeft) / 2f, y + 18f, tableHeaderPaint)
        canvas.drawText("العلامة", (colScoreLeft + colCoeffLeft) / 2f, y + 18f, tableHeaderPaint)
        canvas.drawText("العلامة × المعامل", (colWeightedLeft + colScoreLeft) / 2f, y + 18f, tableHeaderPaint)
        canvas.drawText("الملاحظة", (colRemarkLeft + colWeightedLeft) / 2f, y + 18f, tableHeaderPaint)

        y += headerHeight
        val rowHeight = 26f
        report.lines.forEachIndexed { idx, line ->
            if (y + rowHeight > pageHeight - 165f) return@forEachIndexed
            if (idx % 2 == 1) {
                fillPaint.color = Color.rgb(248, 251, 250)
                canvas.drawRect(margin, y, tableRight, y + rowHeight, fillPaint)
            }
            canvas.drawRect(margin, y, tableRight, y + rowHeight, strokePaint)
            listOf(colWeightedLeft, colScoreLeft, colCoeffLeft, colSubjectLeft).forEach { x ->
                canvas.drawLine(x, y, x, y + rowHeight, strokePaint)
            }

            val textY = y + 17f
            canvas.drawText(line.subject.name, tableRight - 8f, textY, rightTextPaint)
            canvas.drawText(formatScore(line.subject.coefficient), (colCoeffLeft + colSubjectLeft) / 2f, textY, centerTextPaint)

            val scoreText = line.grade?.let { "${formatScore(it.finalScore)} / ${formatScore(line.subject.maxScore)}" } ?: "—"
            canvas.drawText(scoreText, (colScoreLeft + colCoeffLeft) / 2f, textY, centerBoldPaint)

            val weightedText = line.weightedScore?.let { formatScore(it) } ?: "—"
            canvas.drawText(weightedText, (colWeightedLeft + colScoreLeft) / 2f, textY, centerTextPaint)

            val remarkText = line.grade?.remark?.ifBlank {
                AverageCalculator.remarkForSubjectScore(line.grade.finalScore, line.subject.maxScore)
            } ?: "غير مقيم"
            canvas.drawText(remarkText, (colRemarkLeft + colWeightedLeft) / 2f, textY, centerTextPaint)

            y += rowHeight
        }

        // Totals Row
        fillPaint.color = Color.rgb(232, 245, 241)
        canvas.drawRect(margin, y, tableRight, y + 28f, fillPaint)
        canvas.drawRect(margin, y, tableRight, y + 28f, strokePaint)
        canvas.drawText("المجموع العام", tableRight - 8f, y + 18f, rightBoldPaint)
        canvas.drawText(formatScore(report.totalCoefficients), (colCoeffLeft + colSubjectLeft) / 2f, y + 18f, centerBoldPaint)
        canvas.drawText(formatScore(report.totalWeightedPoints), (colWeightedLeft + colScoreLeft) / 2f, y + 18f, centerBoldPaint)

        // Summary Results Box
        y += 46f
        fillPaint.color = softBg
        canvas.drawRoundRect(margin, y, tableRight, y + 78f, 10f, 10f, fillPaint)
        strokePaint.color = primaryColor
        canvas.drawRoundRect(margin, y, tableRight, y + 78f, 10f, 10f, strokePaint)
        strokePaint.color = borderColor

        val avgDisplay = report.average?.let { "${formatScore(it)} / 20" } ?: "—"
        val rankDisplay = report.rank?.let { "$it من ${summary.reports.size}" } ?: "—"
        val classAvgDisplay = summary.classAverage?.let { "${formatScore(it)} / 20" } ?: "—"

        val bigAvgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 15f
            color = primaryColor
            textAlign = Paint.Align.RIGHT
        }
        val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 13f
            color = accentGold
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawText("المعدل الفصلي: $avgDisplay", contentRight - 16f, y + 28f, bigAvgPaint)
        canvas.drawText("التقدير: ${report.appreciation}", contentRight - 16f, y + 56f, goldPaint)
        canvas.drawText("الترتيب في القسم: $rankDisplay", margin + 215f, y + 28f, rightBoldPaint)
        canvas.drawText("المعدل العام للقسم: $classAvgDisplay", margin + 215f, y + 56f, rightTextPaint)

        // Footer & Signatures
        val footerY = pageHeight - 72f
        val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 10f
            color = mutedText
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("توقيع الأستاذ: _________________", contentRight - 8f, footerY, rightTextPaint)
        canvas.drawText("توقيع ولي الأمر / الإدارة: _________________", margin + 235f, footerY, rightTextPaint)
        canvas.drawText("أُنشئ بواسطة تطبيق NOQTATI – نقطتي بتاريخ $dateStr", contentRight - 8f, footerY + 28f, footerPaint)

        doc.finishPage(page)

        val safeName = report.student.registrationNumber.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(reportsDir(), "bulletin_${safeName}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        file
    }

    suspend fun generateClassGradesPdf(
        summary: ClassSemesterSummary,
        settings: AppSettings
    ): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        // A4 Landscape (842 x 595 points)
        val pageWidth = 842
        val pageHeight = 595
        val regularTf = loadArabicTypeface(false)
        val boldTf = loadArabicTypeface(true)

        val primaryColor = Color.rgb(15, 107, 94)
        val borderColor = Color.rgb(190, 210, 206)
        val darkText = Color.rgb(20, 34, 32)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = borderColor
            strokeWidth = 0.8f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 14f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val headerCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 9.5f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val cellCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 9.5f
            color = darkText
            textAlign = Paint.Align.CENTER
        }
        val cellBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 9.5f
            color = primaryColor
            textAlign = Paint.Align.CENTER
        }
        val cellRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 9.5f
            color = darkText
            textAlign = Paint.Align.RIGHT
        }

        val subjects = summary.reports.firstOrNull()?.lines?.map { it.subject } ?: emptyList()
        val displaySubjects = subjects.take(8) // Fit cleanly on A4 landscape
        val rowsPerPage = 16
        val chunks = if (summary.reports.isEmpty()) listOf(emptyList()) else summary.reports.chunked(rowsPerPage)

        val margin = 24f
        val tableRight = pageWidth - margin
        val nameColWidth = 145f
        val rankColWidth = 42f
        val avgColWidth = 58f
        val subjCount = displaySubjects.size.coerceAtLeast(1)
        val remainingWidth = (pageWidth - 2 * margin) - nameColWidth - rankColWidth - avgColWidth
        val subjColWidth = remainingWidth / subjCount

        chunks.forEachIndexed { pageIndex, chunk ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            // Header bar
            fillPaint.color = primaryColor
            canvas.drawRoundRect(margin, margin, tableRight, margin + 52f, 10f, 10f, fillPaint)
            val headerTitle = "جدول علامات ومعدلات القسم: ${summary.academicClass.name} — ${summary.semester.name} (${summary.academicClass.academicYear})"
            canvas.drawText(headerTitle, pageWidth / 2f, margin + 24f, titlePaint)
            val subInfo = buildString {
                if (settings.schoolName.isNotBlank()) append("${settings.schoolName} | ")
                append("المعدل العام: ${summary.classAverage?.let { formatScore(it) } ?: "—"} | ")
                append("نسبة النجاح: ${summary.passRatePercent?.let { "${formatScore(it)}%" } ?: "—"} | ")
                append("صفحة ${pageIndex + 1}/${chunks.size}")
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = regularTf
                textSize = 10f
                color = Color.rgb(225, 245, 241)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(subInfo, pageWidth / 2f, margin + 42f, subPaint)

            // Table Header (RTL: Rank | Avg | Subjects... | Student Name)
            var y = margin + 64f
            val hHeight = 26f
            fillPaint.color = primaryColor
            canvas.drawRect(margin, y, tableRight, y + hHeight, fillPaint)

            val rankLeft = margin
            val avgLeft = rankLeft + rankColWidth
            val subjectsLeft = avgLeft + avgColWidth
            val nameLeft = tableRight - nameColWidth

            canvas.drawText("الترتيب", rankLeft + rankColWidth / 2f, y + 17f, headerCellPaint)
            canvas.drawText("المعدل", avgLeft + avgColWidth / 2f, y + 17f, headerCellPaint)
            canvas.drawText("التلميذ", nameLeft + nameColWidth / 2f, y + 17f, headerCellPaint)

            displaySubjects.forEachIndexed { sIdx, subj ->
                // Right to left subject columns
                val colRight = nameLeft - (sIdx * subjColWidth)
                val colLeft = colRight - subjColWidth
                val shortTitle = if (subj.name.length > 12) subj.name.take(11) + "…" else subj.name
                canvas.drawText(shortTitle, (colLeft + colRight) / 2f, y + 17f, headerCellPaint)
            }

            y += hHeight
            val rHeight = 24f
            chunk.forEachIndexed { rIdx, report ->
                if (rIdx % 2 == 1) {
                    fillPaint.color = Color.rgb(246, 250, 249)
                    canvas.drawRect(margin, y, tableRight, y + rHeight, fillPaint)
                }
                canvas.drawRect(margin, y, tableRight, y + rHeight, strokePaint)
                canvas.drawLine(avgLeft, y, avgLeft, y + rHeight, strokePaint)
                canvas.drawLine(subjectsLeft, y, subjectsLeft, y + rHeight, strokePaint)
                canvas.drawLine(nameLeft, y, nameLeft, y + rHeight, strokePaint)

                val ty = y + 16f
                canvas.drawText(report.rank?.toString() ?: "—", rankLeft + rankColWidth / 2f, ty, cellCenterPaint)
                canvas.drawText(report.average?.let { formatScore(it) } ?: "—", avgLeft + avgColWidth / 2f, ty, cellBoldPaint)
                canvas.drawText(report.student.fullName, tableRight - 6f, ty, cellRightPaint)

                displaySubjects.forEachIndexed { sIdx, subj ->
                    val colRight = nameLeft - (sIdx * subjColWidth)
                    val colLeft = colRight - subjColWidth
                    canvas.drawLine(colLeft, y, colLeft, y + rHeight, strokePaint)
                    val line = report.lines.firstOrNull { it.subject.id == subj.id }
                    val scoreTxt = line?.grade?.finalScore?.let { formatScore(it) } ?: "—"
                    canvas.drawText(scoreTxt, (colLeft + colRight) / 2f, ty, cellCenterPaint)
                }

                y += rHeight
            }

            doc.finishPage(page)
        }

        val file = File(reportsDir(), "class_grades_${summary.academicClass.id}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        file
    }
}
